package ar.com.hexium.hcop.patient;

import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalEvolutionUseCase;
import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalHistoryTemplateUseCase;
import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalHistoryReadUseCase;
import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalHistoryReadUseCase.HistorySnapshot;
import ar.com.hexium.hcop.clinicalhistory.application.service.ClinicalHistoryReadApplicationService.ClinicalHistoryReadFailure;
import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalEvolutionUseCase.AppendCommand;
import ar.com.hexium.hcop.clinicalhistory.application.service.ClinicalEvolutionApplicationService.ClinicalEvolutionFailure;
import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalHistorySaveUseCase;
import ar.com.hexium.hcop.clinicalhistory.application.port.in.ClinicalHistorySaveUseCase.SaveCommand;
import ar.com.hexium.hcop.clinicalhistory.application.service.ClinicalHistorySaveApplicationService.ClinicalHistorySaveFailure;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.patient.PatientDocumentRepository.StoredDocument;
import ar.com.hexium.hcop.patient.PatientRepository.Patient;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Service
public class PatientDocumentService {
  private final PatientRepository patients;
  private final PatientDocumentRepository documents;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final ClinicalHistorySaveUseCase historySave;
  private final ClinicalEvolutionUseCase clinicalEvolution;
  private final ClinicalHistoryTemplateUseCase clinicalHistoryTemplate;

  private final ClinicalHistoryReadUseCase historyRead;
  public PatientDocumentService(
      PatientRepository patients,
      PatientDocumentRepository documents,
      ObjectMapper mapper,
      Clock clock,
      ClinicalHistorySaveUseCase historySave,
      ClinicalEvolutionUseCase clinicalEvolution,
      ClinicalHistoryReadUseCase historyRead,
      ClinicalHistoryTemplateUseCase clinicalHistoryTemplate) {
    this.patients = patients;
    this.documents = documents;
    this.mapper = mapper;
    this.clock = clock;
    this.historySave = historySave;
    this.clinicalEvolution = clinicalEvolution;
    this.historyRead = historyRead;
    this.clinicalHistoryTemplate = clinicalHistoryTemplate;
  }

  @Transactional
  public StoredDocument createBlank(Patient patient, long actorId) {
    JsonNode document = clinicalHistoryTemplate.blankTemplate().deepCopy();
    applyPatient(document, patient, 1);
    return documents.insert(patient.id(), document, actorId, false);
  }

  public JsonNode blankTemplate() {
    return clinicalHistoryTemplate.blankTemplate().deepCopy();
  }

  public StoredDocument require(long patientId) {
    try {
      return stored(historyRead.require(patientId));
    } catch (ClinicalHistoryReadFailure failure) {
      throw new ApiException(HttpStatus.NOT_FOUND, failure.getMessage());
    }
  }

  @Transactional
  public StoredDocument save(long patientId, JsonNode document, long expectedRevision, long actorId) {
    try {
      historySave.save(new SaveCommand(patientId, document.toString(),
          document.path("meta").path("liraImport").path("patientId").asText(""),
          document.path("patient").path("liraId").asText(""),
          expectedRevision, actorId));
    } catch (ClinicalHistorySaveFailure failure) {
      throw new ApiException(HttpStatus.CONFLICT, failure.getMessage());
    }
    StoredDocument saved = require(patientId);
    applyRevision(saved.document(), saved.revision());
    return saved;
  }

  public JsonNode state(StoredDocument stored) {
    JsonNode copy = stored.document().deepCopy();
    applyRevision(copy, stored.revision());
    return copy;
  }

  @Transactional
  public EvolutionAppend appendImmutableEvolution(long patientId, ObjectNode evolution, long actorId) {
    ObjectNode immutable = evolution.deepCopy();
    immutable.put("immutable", true);
    try {
      var appended = clinicalEvolution.append(new AppendCommand(
          patientId, immutable.path("id").asText(""), immutable.toString(), actorId));
      return new EvolutionAppend(immutable, appended.revision());
    } catch (ClinicalEvolutionFailure failure) {
      HttpStatus status = failure.getMessage().contains("no está disponible")
          ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
      throw new ApiException(status, failure.getMessage());
    }
  }

  public void applyPatient(JsonNode value, Patient patient, long revision) {
    if (!(value instanceof ObjectNode root)) return;
    ObjectNode patientNode = root.withObject("/patient");
    patientNode.put("fullName", patient.fullName());
    patientNode.put("dni", patient.dni());
    patientNode.put("medicalRecord", patient.medicalRecord());
    patientNode.put("birthDate", patient.birthDate() == null ? "" : patient.birthDate().toString());
    patientNode.put("sex", patient.sex());
    patientNode.put("insurance", patient.insurance());
    patientNode.put("affiliateNumber", patient.affiliateNumber());
    patientNode.put("phone", patient.phone());
    patientNode.put("email", patient.email());
    patientNode.put("address", patient.address());
    patientNode.put("liraId", Long.toString(patient.id()));
    ArrayNode coverages = mapper.createArrayNode();
    if (!patient.insurance().isBlank() || !patient.affiliateNumber().isBlank()) {
      ObjectNode coverage = coverages.addObject();
      coverage.put("id", "local:primary");
      coverage.put("name", patient.insurance());
      coverage.put("affiliateNumber", patient.affiliateNumber());
      coverage.put("primary", true);
    }
    patientNode.set("coverages", coverages);

    ObjectNode meta = root.withObject("/meta");
    meta.put("version", Math.max(1, meta.path("version").asInt(1)));
    meta.put("updatedAt", clock.instant().toString());
    if (!meta.hasNonNull("createdAt")) meta.put("createdAt", clock.instant().toString());
    ObjectNode imported = meta.withObject("/liraImport");
    imported.put("system", "HCOP JP");
    imported.put("origin", patient.localOnly() ? "local" : "migration");
    imported.put("patientId", Long.toString(patient.id()));
    imported.put("importedAt", clock.instant().toString());
    meta.put("persistenceRevision", revision);
  }

  private StoredDocument stored(HistorySnapshot snapshot) {
    return new StoredDocument(
        snapshot.patientId(),
        mapper.readTree(snapshot.documentJson()),
        snapshot.revision(),
        snapshot.importedAt(),
        snapshot.createdAt(),
        snapshot.updatedAt());
  }

  private void applyRevision(JsonNode value, long revision) {
    if (value instanceof ObjectNode root) {
      root.withObject("/meta").put("persistenceRevision", revision);
    }
  }

  public record EvolutionAppend(ObjectNode evolution, long revision) {
  }
}
