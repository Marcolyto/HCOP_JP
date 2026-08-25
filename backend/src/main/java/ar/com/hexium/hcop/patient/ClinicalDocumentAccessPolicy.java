package ar.com.hexium.hcop.patient;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Applies field-level RBAC to the versioned clinical document. */
@Component
public class ClinicalDocumentAccessPolicy {

  public JsonNode visibleState(JsonNode state, SessionPrincipal principal) {
    JsonNode visible = state.deepCopy();
    if (visible instanceof ObjectNode root) {
      if (!principal.hasPermission("section.prescriptions.view")) {
        root.remove("prescriptions");
      }
      if (!principal.hasPermission("section.studies.view")) {
        root.remove("studies");
        root.remove("externalStudies");
      }
    }
    return visible;
  }

  public JsonNode writableState(JsonNode incoming, JsonNode stored, SessionPrincipal principal) {
    JsonNode protectedState = protectFields(
        incoming,
        stored,
        principal,
        "section.prescriptions.view",
        "section.prescriptions.edit",
        "No tiene permiso para modificar prescripciones.",
        "prescriptions");
    return protectFields(
        protectedState,
        stored,
        principal,
        "section.studies.view",
        "section.studies.edit",
        "No tiene permiso para modificar estudios.",
        "studies",
        "externalStudies");
  }

  private JsonNode protectFields(
      JsonNode incoming,
      JsonNode stored,
      SessionPrincipal principal,
      String viewPermission,
      String editPermission,
      String deniedMessage,
      String... fields) {
    if (principal.hasPermission(viewPermission) && principal.hasPermission(editPermission)) {
      return incoming;
    }
    for (String field : fields) {
      if (incoming.has(field) && !stored.path(field).equals(incoming.path(field))) {
        throw new ApiException(HttpStatus.FORBIDDEN, deniedMessage);
      }
    }
    if (!(incoming instanceof ObjectNode)) return incoming;
    ObjectNode result = (ObjectNode) incoming.deepCopy();
    for (String field : fields) {
      if (stored.has(field)) {
        result.set(field, stored.path(field).deepCopy());
      } else {
        result.remove(field);
      }
    }
    return result;
  }
}
