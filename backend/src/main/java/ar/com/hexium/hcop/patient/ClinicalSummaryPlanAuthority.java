package ar.com.hexium.hcop.patient;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Makes the server the sole authority for the audit trail of conclusion/summary and plan.
 * Client-generated audit metadata is treated as an optimistic preview and is never trusted.
 */
@Component
public class ClinicalSummaryPlanAuthority {
  static final int MAX_REASON_CHARS = ClinicalNarrativeSectionAuthority.MAX_REASON_CHARS;
  static final String SECTION_KEY = "summaryPlan";

  private static final ClinicalNarrativeSectionAuthority.SectionDefinition DEFINITION =
      new ClinicalNarrativeSectionAuthority.SectionDefinition(
          SECTION_KEY,
          "CLINICAL_SUMMARY_PLAN",
          "Complete al menos la conclusi\u00f3n / resumen o la conducta / plan.",
          false,
          List.of(
              new ClinicalNarrativeSectionAuthority.NarrativeField(
                  "summary", "Conclusion / resumen"),
              new ClinicalNarrativeSectionAuthority.NarrativeField(
                  "plan", "Conducta / plan")));

  private final ClinicalNarrativeSectionAuthority authority;

  public ClinicalSummaryPlanAuthority(ObjectMapper mapper, Clock clock) {
    this.authority = new ClinicalNarrativeSectionAuthority(mapper, clock);
  }

  public JsonNode canonicalize(JsonNode incoming, JsonNode stored, SessionPrincipal principal) {
    return authority.canonicalize(incoming, stored, principal, DEFINITION);
  }
}
