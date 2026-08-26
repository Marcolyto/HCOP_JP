package ar.com.hexium.hcop.qr.infrastructure.patient;

import ar.com.hexium.hcop.patient.application.port.in.PatientDocumentUseCase;
import ar.com.hexium.hcop.patient.application.service.PatientFailure;
import ar.com.hexium.hcop.patient.domain.EvolutionAppend;
import ar.com.hexium.hcop.qr.application.port.out.PatientEvolutionPort;
import ar.com.hexium.hcop.qr.application.service.QrFailure;
import ar.com.hexium.hcop.qr.domain.EvolutionDraft;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Único lugar del módulo que conoce el formato de evolución de la historia clínica. */
@Component
public class PatientEvolutionAdapter implements PatientEvolutionPort {
  private final PatientDocumentUseCase documents;
  private final ObjectMapper mapper;
  private final Clock clock;

  public PatientEvolutionAdapter(PatientDocumentUseCase documents, ObjectMapper mapper, Clock clock) {
    this.documents = documents;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Override
  public AppendedEvolution append(
      long patientId, EvolutionDraft draft, long actorId, String actorDisplayName) {
    ObjectNode evolution = mapper.createObjectNode();
    evolution.put("id", draft.id());
    evolution.put("date", LocalDate.now(clock).toString());
    evolution.put("datePrecision", "day");
    evolution.put("author", actorDisplayName);
    evolution.put("reason", draft.reason());
    evolution.put("specialty", draft.specialty());
    evolution.put("text", draft.text());
    evolution.put("highlighted", draft.highlighted());
    evolution.put("createdAt", clock.instant().toString());
    evolution.put("updatedAt", clock.instant().toString());
    evolution.set("attachments", mapper.createArrayNode());
    evolution.set("linkedStudyIds", mapper.createArrayNode());
    ObjectNode source = evolution.putObject("sourceRef");
    draft.sourceRef().forEach(source::put);
    try {
      EvolutionAppend appended = documents.appendImmutableEvolution(patientId, evolution, actorId);
      return new AppendedEvolution(appended.evolution(), appended.revision());
    } catch (PatientFailure failure) {
      throw new QrFailure(
          failure.type() == PatientFailure.Type.CONFLICT ? QrFailure.Type.CONFLICT : QrFailure.Type.NOT_FOUND,
          failure.getMessage());
    }
  }
}
