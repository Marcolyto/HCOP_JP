package ar.com.hexium.hcop.patient;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Server authority for the structured personal-history section and its clinical audit trail. */
@Component
public class ClinicalPersonalHistoryAuthority {
  static final int MAX_REASON_CHARS = ClinicalNarrativeSectionAuthority.MAX_REASON_CHARS;
  static final String SECTION_KEY = "personalHistory";

  private static final ClinicalNarrativeSectionAuthority.SectionDefinition DEFINITION =
      new ClinicalNarrativeSectionAuthority.SectionDefinition(
          SECTION_KEY,
          "CLINICAL_PERSONAL_HISTORY",
          "Complete al menos un antecedente personal.",
          true,
          List.of(
              new ClinicalNarrativeSectionAuthority.NarrativeField(
                  "backgroundClinical", "Clínicos / quirúrgicos"),
              new ClinicalNarrativeSectionAuthority.NarrativeField(
                  "currentMedication", "Medicación habitual"),
              new ClinicalNarrativeSectionAuthority.NarrativeField(
                  "familyOncology", "Oncofamiliares"),
              new ClinicalNarrativeSectionAuthority.NarrativeField(
                  "gynecology", "Gineco-obstétricos")));

  private final ClinicalNarrativeSectionAuthority authority;

  public ClinicalPersonalHistoryAuthority(ObjectMapper mapper, Clock clock) {
    this.authority = new ClinicalNarrativeSectionAuthority(mapper, clock);
  }

  public JsonNode canonicalize(JsonNode incoming, JsonNode stored, SessionPrincipal principal) {
    return authority.canonicalize(incoming, stored, principal, DEFINITION);
  }
}
