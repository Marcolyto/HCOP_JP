package ar.com.hexium.hcop.patient;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Server authority for the structured current-illness section and its clinical audit trail.
 */
@Component
public class ClinicalCurrentIllnessAuthority {
  static final int MAX_REASON_CHARS = ClinicalNarrativeSectionAuthority.MAX_REASON_CHARS;
  static final String SECTION_KEY = "currentIllness";

  private static final ClinicalNarrativeSectionAuthority.SectionDefinition DEFINITION =
      new ClinicalNarrativeSectionAuthority.SectionDefinition(
          SECTION_KEY,
          "CLINICAL_CURRENT_ILLNESS",
          "Complete los antecedentes de enfermedad actual.",
          true,
          List.of(new ClinicalNarrativeSectionAuthority.NarrativeField("currentIllness", "")));

  private final ClinicalNarrativeSectionAuthority authority;

  public ClinicalCurrentIllnessAuthority(ObjectMapper mapper, Clock clock) {
    this.authority = new ClinicalNarrativeSectionAuthority(mapper, clock);
  }

  public JsonNode canonicalize(JsonNode incoming, JsonNode stored, SessionPrincipal principal) {
    return authority.canonicalize(incoming, stored, principal, DEFINITION);
  }
}
