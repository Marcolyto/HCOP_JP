package ar.com.hexium.hcop.workflow.infrastructure.patient;

import ar.com.hexium.hcop.patient.PatientDocumentService;
import ar.com.hexium.hcop.patient.PatientDocumentService.EvolutionAppend;
import ar.com.hexium.hcop.workflow.application.port.out.PatientEvolutionPort;
import ar.com.hexium.hcop.workflow.domain.EvolutionDraft;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Único lugar del módulo que conoce el formato de evolución de la historia clínica. */
@Component
public class PatientEvolutionAdapter implements PatientEvolutionPort {
  private final PatientDocumentService documents;
  private final ObjectMapper mapper;
  private final Clock clock;

  public PatientEvolutionAdapter(PatientDocumentService documents, ObjectMapper mapper, Clock clock) {
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
    evolution.put("specialty", "Oncología / Hospital de día");
    evolution.put("text", draft.text());
    evolution.put("highlighted", true);
    evolution.put("createdAt", clock.instant().toString());
    evolution.put("updatedAt", clock.instant().toString());
    evolution.set("attachments", mapper.createArrayNode());
    evolution.set("linkedStudyIds", mapper.createArrayNode());
    ObjectNode source = evolution.putObject("sourceRef");
    source.put("kind", "treatment-workflow");
    source.put("treatmentId", draft.treatmentId());
    if (draft.requestId() != null) source.put("requestId", draft.requestId());
    if (draft.cycleNumber() != null) source.put("cycleNumber", draft.cycleNumber());
    EvolutionAppend appended = documents.appendImmutableEvolution(patientId, evolution, actorId);
    return new AppendedEvolution(appended.evolution(), appended.revision());
  }
}
