package ar.com.hexium.hcop.patient;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Server authority for the structured chief-complaint section and its clinical audit trail. */
@Component
public class ClinicalChiefComplaintAuthority {
  static final int MAX_REASON_CHARS = ClinicalNarrativeSectionAuthority.MAX_REASON_CHARS;
  static final String SECTION_KEY = "chiefComplaint";

  private static final ClinicalNarrativeSectionAuthority.SectionDefinition DEFINITION =
      new ClinicalNarrativeSectionAuthority.SectionDefinition(
          SECTION_KEY,
          "CLINICAL_CHIEF_COMPLAINT",
          "Complete el motivo de consulta.",
          true,
          List.of(new ClinicalNarrativeSectionAuthority.NarrativeField("chiefComplaint", "")));

  private final ClinicalNarrativeSectionAuthority authority;

  public ClinicalChiefComplaintAuthority(ObjectMapper mapper, Clock clock) {
    this.authority = new ClinicalNarrativeSectionAuthority(mapper, clock);
  }

  public JsonNode canonicalize(JsonNode incoming, JsonNode stored, SessionPrincipal principal) {
    return authority.canonicalize(incoming, stored, principal, DEFINITION);
  }
}
