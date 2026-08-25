package ar.com.hexium.hcop.patient;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Server-side authority shared by structured narrative sections.
 *
 * <p>The browser may send an optimistic audit preview, but only values already stored in the
 * database and the authenticated session are accepted as audit evidence. Change requests are
 * transient commands and are removed before persistence.
 */
final class ClinicalNarrativeSectionAuthority {
  static final int MAX_REASON_CHARS = 50_000;

  private final ObjectMapper mapper;
  private final Clock clock;

  ClinicalNarrativeSectionAuthority(ObjectMapper mapper, Clock clock) {
    this.mapper = mapper;
    this.clock = clock;
  }

  JsonNode canonicalize(
      JsonNode incoming,
      JsonNode stored,
      SessionPrincipal principal,
      SectionDefinition section) {
    if (!(incoming instanceof ObjectNode incomingRoot)) return incoming;

    ObjectNode result = incomingRoot.deepCopy();
    ObjectNode resultMeta = ensureObject(result, "meta");
    JsonNode sectionRequest = resultMeta.path("sectionChangeRequests").path(section.key());
    JsonNode reasonRequest = sectionRequest.path("reason");
    boolean explicitSectionRequest = !sectionRequest.isMissingNode() && !sectionRequest.isNull();
    removeTransientRequest(resultMeta, section.key());

    boolean contentChanged = section.fields().stream().anyMatch(field -> {
      JsonNode next = result.path("narrative").path(field.name());
      JsonNode previous = stored.path("narrative").path(field.name());
      if (next.equals(previous)) return false;
      return !section.blankMissingEquivalentWithoutRequest()
          || explicitSectionRequest
          || hasClinicalContent(next)
          || hasClinicalContent(previous);
    });
    if (!contentChanged) {
      restoreProtectedSectionMetadata(resultMeta, stored.path("meta"), section.key());
      return result;
    }

    String reason = validatedReason(reasonRequest, section);
    ArrayNode versions = storedVersions(stored, section.key());
    String previousContent = snapshot(stored, section.fields());
    boolean storedHasClinicalContent = section.fields().stream()
        .map(field -> stored.path("narrative").path(field.name()))
        .anyMatch(this::hasClinicalContent);
    boolean initial = !storedHasClinicalContent && versions.isEmpty();
    if (initial && snapshot(result, section.fields()).isBlank()) {
      throw badRequest(section.emptyMessage(), section.codePrefix() + "_EMPTY");
    }
    if (!initial && reason.isBlank()) {
      throw badRequest(
          "Indique el motivo de la modificaci\u00f3n.",
          section.codePrefix() + "_REASON_REQUIRED");
    }

    Instant now = clock.instant();
    String at = now.toString();
    String displayName = actorName(principal);
    String license = actorLicense(principal);
    String versionId = "sec-" + section.key() + "-" + UUID.randomUUID();

    if (!initial && !hasInitialVersion(versions)) {
      String initialAt = firstText(
          stored.path("meta").path("createdAt"),
          stored.path("meta").path("updatedAt"),
          at);
      String firstContent = versions.isEmpty()
          ? previousContent
          : firstText(versions.get(0).path("content"), previousContent);
      versions.insert(0, version(
          versionId + "-initial",
          "Carga inicial",
          firstContent.isBlank() ? "Sin datos cargados." : firstContent,
          audit("cargado", displayName, license, initialAt)));
    }

    String currentContent = snapshot(result, section.fields());
    ObjectNode currentAudit = audit(
        initial ? "cargado" : "modificado",
        displayName,
        license,
        at);
    versions.add(version(
        versionId,
        initial ? "Carga inicial" : reason,
        currentContent.isBlank() ? "Sin datos cargados." : currentContent,
        currentAudit));

    protectedContainer(resultMeta, stored.path("meta"), "sectionVersions")
        .set(section.key(), versions);
    protectedContainer(resultMeta, stored.path("meta"), "sectionAudit")
        .set(section.key(), currentAudit.deepCopy());
    protectedContainer(resultMeta, stored.path("meta"), "sectionFormModes")
        .put(section.key(), "structured");
    applyAuthenticatedActor(resultMeta, principal, displayName, license);
    resultMeta.put("updatedAt", at);
    return result;
  }

  private String validatedReason(JsonNode request, SectionDefinition section) {
    if (!request.isMissingNode() && !request.isNull() && !request.isTextual()) {
      throw badRequest(
          "El motivo de la modificaci\u00f3n debe ser texto.",
          section.codePrefix() + "_REASON_INVALID");
    }
    String reason = request.isTextual() ? request.textValue().trim() : "";
    if (reason.length() > MAX_REASON_CHARS) {
      throw badRequest(
          "El motivo no puede superar " + MAX_REASON_CHARS + " caracteres.",
          section.codePrefix() + "_REASON_TOO_LONG");
    }
    return reason;
  }

  private ApiException badRequest(String message, String code) {
    return new ApiException(HttpStatus.BAD_REQUEST, message, code);
  }

  private ArrayNode storedVersions(JsonNode stored, String sectionKey) {
    JsonNode storedVersions = stored.path("meta").path("sectionVersions").path(sectionKey);
    return storedVersions.isArray()
        ? (ArrayNode) storedVersions.deepCopy()
        : mapper.createArrayNode();
  }

  private void restoreProtectedSectionMetadata(
      ObjectNode resultMeta,
      JsonNode storedMeta,
      String sectionKey) {
    restoreChild(resultMeta, storedMeta, "sectionVersions", sectionKey);
    restoreChild(resultMeta, storedMeta, "sectionAudit", sectionKey);
    restoreChild(resultMeta, storedMeta, "sectionFormModes", sectionKey);
  }

  private void restoreChild(
      ObjectNode resultMeta,
      JsonNode storedMeta,
      String containerName,
      String sectionKey) {
    JsonNode storedContainer = storedMeta.path(containerName);
    JsonNode storedValue = storedContainer.path(sectionKey);
    JsonNode resultContainer = resultMeta.path(containerName);
    if (!(resultContainer instanceof ObjectNode)) {
      if (!storedContainer.isMissingNode()) {
        resultMeta.set(containerName, storedContainer.deepCopy());
      } else {
        resultMeta.remove(containerName);
      }
      return;
    }
    if (!storedValue.isMissingNode()) {
      ((ObjectNode) resultContainer).set(sectionKey, storedValue.deepCopy());
    } else {
      ((ObjectNode) resultContainer).remove(sectionKey);
    }
  }

  private ObjectNode protectedContainer(
      ObjectNode resultMeta,
      JsonNode storedMeta,
      String containerName) {
    JsonNode current = resultMeta.path(containerName);
    if (current instanceof ObjectNode object) return object;
    JsonNode stored = storedMeta.path(containerName);
    ObjectNode replacement = stored instanceof ObjectNode object
        ? object.deepCopy()
        : mapper.createObjectNode();
    resultMeta.set(containerName, replacement);
    return replacement;
  }

  private void removeTransientRequest(ObjectNode meta, String sectionKey) {
    JsonNode requestsNode = meta.path("sectionChangeRequests");
    if (requestsNode.isMissingNode()) return;
    if (!(requestsNode instanceof ObjectNode requests)) {
      meta.remove("sectionChangeRequests");
      return;
    }
    requests.remove(sectionKey);
    if (requests.isEmpty()) meta.remove("sectionChangeRequests");
  }

  private boolean hasInitialVersion(ArrayNode versions) {
    for (JsonNode version : versions) {
      if ("cargado".equals(version.path("audit").path("action").asText(""))) return true;
    }
    return false;
  }

  private ObjectNode version(
      String id,
      String reason,
      String content,
      ObjectNode audit) {
    ObjectNode version = mapper.createObjectNode();
    version.put("id", id);
    version.put("createdAt", audit.path("at").asText());
    version.put("author", audit.path("lastName").asText());
    version.put("license", audit.path("license").asText());
    version.put("reason", reason);
    version.put("content", content);
    version.set("audit", audit.deepCopy());
    return version;
  }

  private ObjectNode audit(String action, String displayName, String license, String at) {
    ObjectNode audit = mapper.createObjectNode();
    audit.put("action", action);
    audit.put("lastName", displayName);
    audit.put("license", license);
    audit.put("at", at);
    return audit;
  }

  private String snapshot(JsonNode document, List<NarrativeField> fields) {
    StringBuilder content = new StringBuilder();
    for (NarrativeField field : fields) {
      String value = scalarText(document.path("narrative").path(field.name()));
      if (value.isBlank()) continue;
      if (!content.isEmpty()) content.append('\n');
      if (field.label() != null && !field.label().isBlank()) {
        content.append(field.label()).append(": ");
      }
      content.append(value);
    }
    return content.toString().trim();
  }

  private String scalarText(JsonNode value) {
    if (value.isTextual() || value.isNumber()) return value.asText().trim();
    return "";
  }

  private boolean hasClinicalContent(JsonNode value) {
    if (value.isMissingNode() || value.isNull()) return false;
    if (value.isTextual() || value.isNumber()) return !value.asText().trim().isBlank();
    return true;
  }

  private void applyAuthenticatedActor(
      ObjectNode resultMeta,
      SessionPrincipal principal,
      String displayName,
      String license) {
    resultMeta.put("currentUser", displayName);
    ObjectNode professional = ensureObject(resultMeta, "currentProfessional");
    professional.put("firstName", displayName);
    professional.put("lastName", displayName);
    professional.put("license", license);
    professional.put("userId", principal.userId());
    professional.put("username", principal.username());
    if (principal.specialty() != null && !principal.specialty().isBlank()) {
      professional.put("specialty", principal.specialty().trim());
    } else {
      professional.remove("specialty");
    }
  }

  private String actorName(SessionPrincipal principal) {
    return firstText(principal.displayName(), principal.username(), "Profesional");
  }

  private String actorLicense(SessionPrincipal principal) {
    return firstText(principal.licenseNumber(), "s/d");
  }

  private String firstText(JsonNode first, JsonNode second, String fallback) {
    return firstText(
        first.isTextual() ? first.textValue() : "",
        second.isTextual() ? second.textValue() : "",
        fallback);
  }

  private String firstText(JsonNode first, String fallback) {
    return firstText(first.isTextual() ? first.textValue() : "", fallback);
  }

  private String firstText(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value.trim();
    }
    return "";
  }

  private ObjectNode ensureObject(ObjectNode parent, String field) {
    JsonNode current = parent.get(field);
    if (current instanceof ObjectNode object) return object;
    ObjectNode replacement = mapper.createObjectNode();
    parent.set(field, replacement);
    return replacement;
  }

  record NarrativeField(String name, String label) {}

  record SectionDefinition(
      String key,
      String codePrefix,
      String emptyMessage,
      boolean blankMissingEquivalentWithoutRequest,
      List<NarrativeField> fields) {
    SectionDefinition {
      fields = List.copyOf(fields);
    }
  }
}
