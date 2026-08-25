package ar.com.hexium.hcop.patient;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Server authority for physical-exam audit, versioning and authenticated authorship. */
@Component
public class ClinicalPhysicalExamAuthority {
  static final String SECTION_KEY = "physicalExam";
  static final int MAX_REASON_CHARS = 50_000;
  private static final int REGEX_FLAGS = Pattern.CASE_INSENSITIVE
      | Pattern.UNICODE_CASE
      | Pattern.UNICODE_CHARACTER_CLASS;
  private static final List<MarkerPattern> PHYSICAL_EXAM_MARKERS = List.of(
      marker("Tórax", "\\b(?:aparato respiratorio|respiratorio|t[oó]rax)\\b\\s*:?\\s*"),
      marker("Corazón", "\\b(?:aparato cardiovascular|cardiovascular|coraz[oó]n)\\b\\s*:?\\s*"),
      marker("Abdomen", "\\babdomen\\b\\s*:?\\s*"),
      marker("SNC", "\\b(?:sistema nervioso central|snc)\\b\\s*:?\\s*"),
      marker("Tacto rectal", "\\btacto rectal\\b\\s*:?\\s*"));
  private static final Pattern PHYSICAL_EXAM_PREFIX = Pattern.compile(
      "^examen f[ií]sico(?: al ingreso)?\\s*:?\\s*",
      REGEX_FLAGS);
  private static final Pattern SEGMENT_PREFIX = Pattern.compile(
      "^(?:estado general|general|aparato respiratorio|respiratorio|t[oó]rax|"
          + "aparato cardiovascular|cardiovascular|coraz[oó]n|abdomen|"
          + "sistema nervioso central|snc|tacto rectal)\\s*:?\\s*",
      REGEX_FLAGS);

  private final ObjectMapper mapper;
  private final Clock clock;

  public ClinicalPhysicalExamAuthority(ObjectMapper mapper, Clock clock) {
    this.mapper = mapper;
    this.clock = clock;
  }

  public JsonNode canonicalize(JsonNode incoming, JsonNode stored, SessionPrincipal principal) {
    if (!(incoming instanceof ObjectNode incomingRoot)) return incoming;

    ObjectNode result = incomingRoot.deepCopy();
    ObjectNode resultMeta = ensureObject(result, "meta");
    JsonNode sectionRequest = resultMeta.path("sectionChangeRequests").path(SECTION_KEY);
    JsonNode reasonRequest = sectionRequest.path("reason");
    boolean explicitSectionRequest = !sectionRequest.isMissingNode() && !sectionRequest.isNull();
    removeTransientRequest(resultMeta);
    restoreEquivalentNumericRepresentation(result, stored, "weightKg", FieldKind.WEIGHT);
    restoreEquivalentNumericRepresentation(result, stored, "heightM", FieldKind.HEIGHT);

    boolean contentChanged = changed(
        result.path("exam").path("weightKg"),
        stored.path("exam").path("weightKg"),
        FieldKind.WEIGHT,
        explicitSectionRequest)
        || changed(
            result.path("exam").path("heightM"),
            stored.path("exam").path("heightM"),
            FieldKind.HEIGHT,
            explicitSectionRequest)
        || changed(
            result.path("narrative").path("physicalExam"),
            stored.path("narrative").path("physicalExam"),
            FieldKind.TEXT,
            explicitSectionRequest);
    if (!contentChanged) {
      restoreProtectedSectionMetadata(resultMeta, stored.path("meta"));
      return result;
    }

    String reason = validatedReason(reasonRequest);
    ArrayNode versions = storedVersions(stored);
    boolean storedHasClinicalContent = hasClinicalContent(stored.path("exam").path("weightKg"))
        || hasClinicalContent(stored.path("exam").path("heightM"))
        || hasClinicalContent(stored.path("narrative").path("physicalExam"));
    boolean initial = !storedHasClinicalContent && versions.isEmpty();
    if (initial && snapshot(result).isBlank()) {
      throw badRequest(
          "Complete al menos un campo del examen físico.",
          "CLINICAL_PHYSICAL_EXAM_EMPTY");
    }
    if (!initial && reason.isBlank()) {
      throw badRequest(
          "Indique el motivo de la modificación.",
          "CLINICAL_PHYSICAL_EXAM_REASON_REQUIRED");
    }

    String at = clock.instant().toString();
    String displayName = actorName(principal);
    String license = actorLicense(principal);
    String versionId = "sec-" + SECTION_KEY + "-" + UUID.randomUUID();

    if (!initial && !hasInitialVersion(versions)) {
      String initialAt = firstText(
          stored.path("meta").path("createdAt"),
          stored.path("meta").path("updatedAt"),
          at);
      String previousContent = snapshot(stored);
      String firstContent = versions.isEmpty()
          ? previousContent
          : firstText(versions.get(0).path("content"), previousContent);
      versions.insert(0, version(
          versionId + "-initial",
          "Carga inicial",
          firstContent.isBlank() ? "Sin datos cargados." : firstContent,
          audit("cargado", displayName, license, initialAt)));
    }

    String currentContent = snapshot(result);
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
        .set(SECTION_KEY, versions);
    protectedContainer(resultMeta, stored.path("meta"), "sectionAudit")
        .set(SECTION_KEY, currentAudit.deepCopy());
    protectedContainer(resultMeta, stored.path("meta"), "sectionFormModes")
        .put(SECTION_KEY, "structured");
    applyAuthenticatedActor(resultMeta, principal, displayName, license);
    resultMeta.put("updatedAt", at);
    return result;
  }

  private boolean changed(
      JsonNode next,
      JsonNode previous,
      FieldKind kind,
      boolean explicitSectionRequest) {
    if (equivalent(next, previous, kind)) return false;
    if (!explicitSectionRequest && isClinicallyBlank(next) && isClinicallyBlank(previous)) {
      return false;
    }
    return explicitSectionRequest || hasClinicalContent(next) || hasClinicalContent(previous);
  }

  private void restoreEquivalentNumericRepresentation(
      ObjectNode result,
      JsonNode stored,
      String field,
      FieldKind kind) {
    JsonNode next = result.path("exam").path(field);
    JsonNode previous = stored.path("exam").path(field);
    if (next.equals(previous)
        || decimal(next) == null
        || decimal(previous) == null
        || !equivalent(next, previous, kind)) {
      return;
    }
    ensureObject(result, "exam").set(field, previous.deepCopy());
  }

  private boolean equivalent(JsonNode left, JsonNode right, FieldKind kind) {
    if (left.equals(right)) return true;
    if (kind == FieldKind.TEXT) return false;
    BigDecimal leftNumber = decimal(left);
    BigDecimal rightNumber = decimal(right);
    if (kind == FieldKind.HEIGHT && leftNumber != null && rightNumber != null) {
      leftNumber = heightCentimeters(leftNumber);
      rightNumber = heightCentimeters(rightNumber);
    }
    return leftNumber != null
        && rightNumber != null
        && leftNumber.compareTo(rightNumber) == 0;
  }

  private String validatedReason(JsonNode request) {
    if (!request.isMissingNode() && !request.isNull() && !request.isTextual()) {
      throw badRequest(
          "El motivo de la modificación debe ser texto.",
          "CLINICAL_PHYSICAL_EXAM_REASON_INVALID");
    }
    String reason = request.isTextual() ? request.textValue().trim() : "";
    if (reason.length() > MAX_REASON_CHARS) {
      throw badRequest(
          "El motivo no puede superar " + MAX_REASON_CHARS + " caracteres.",
          "CLINICAL_PHYSICAL_EXAM_REASON_TOO_LONG");
    }
    return reason;
  }

  private String snapshot(JsonNode document) {
    StringBuilder content = new StringBuilder();
    appendSnapshot(
        content,
        "Peso",
        formattedNumber(document.path("exam").path("weightKg")),
        "kg");
    appendSnapshot(
        content,
        "Talla",
        formattedHeightCm(document.path("exam").path("heightM")),
        "cm");
    String physicalExam = scalarText(document.path("narrative").path("physicalExam"));
    String formattedPhysicalExam = formatPhysicalExamPlainText(physicalExam);
    if (!formattedPhysicalExam.isBlank()) {
      if (!content.isEmpty()) content.append('\n');
      content.append(formattedPhysicalExam);
    }
    return content.toString().trim();
  }

  private void appendSnapshot(
      StringBuilder content,
      String label,
      String value,
      String unit) {
    if (value.isBlank()) return;
    if (!content.isEmpty()) content.append('\n');
    content.append(label).append(": ").append(value);
    if (!unit.isBlank()) content.append(' ').append(unit);
  }

  private String formattedNumber(JsonNode value) {
    if (isClinicallyBlank(value)) return "";
    BigDecimal number = decimal(value);
    if (number == null) return scalarText(value);
    return number.stripTrailingZeros().toPlainString();
  }

  private String formattedHeightCm(JsonNode value) {
    if (isClinicallyBlank(value)) return "";
    BigDecimal number = decimal(value);
    if (number == null) return scalarText(value);
    return heightCentimeters(number)
        .setScale(1, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString();
  }

  private BigDecimal heightCentimeters(BigDecimal value) {
    return value.compareTo(BigDecimal.valueOf(3)) <= 0
        ? value.multiply(BigDecimal.valueOf(100))
        : value;
  }

  private String formatPhysicalExamPlainText(String value) {
    String text = cleanPhysicalExamText(value);
    if (text.isBlank()) return "";

    List<Marker> markers = findPhysicalExamMarkers(text);
    if (markers.isEmpty()) return "Estado general: " + text;

    List<PhysicalRow> rows = new ArrayList<>();
    String general = cleanPhysicalExamSegment(text.substring(0, markers.get(0).start()));
    if (!general.isBlank()) rows.add(new PhysicalRow("Estado general", general));
    for (int index = 0; index < markers.size(); index++) {
      Marker current = markers.get(index);
      int nextStart = index + 1 < markers.size()
          ? markers.get(index + 1).start()
          : text.length();
      String segment = cleanPhysicalExamSegment(text.substring(current.end(), nextStart));
      if (!segment.isBlank()) rows.add(new PhysicalRow(current.label(), segment));
    }
    if (rows.isEmpty()) return "Estado general: " + text;
    return rows.stream()
        .map(row -> row.label() + ": " + row.text())
        .reduce((left, right) -> left + "\n" + right)
        .orElse("");
  }

  private List<Marker> findPhysicalExamMarkers(String text) {
    List<Marker> matches = new ArrayList<>();
    for (MarkerPattern markerPattern : PHYSICAL_EXAM_MARKERS) {
      Matcher matcher = markerPattern.pattern().matcher(text);
      while (matcher.find()) {
        matches.add(new Marker(markerPattern.label(), matcher.start(), matcher.end()));
      }
    }
    matches.sort(Comparator.comparingInt(Marker::start).thenComparing(
        Comparator.comparingInt(Marker::end).reversed()));

    List<Marker> filtered = new ArrayList<>();
    for (Marker match : matches) {
      boolean overlaps = filtered.stream().anyMatch(previous -> match.start() < previous.end());
      if (!overlaps) filtered.add(match);
    }
    return filtered;
  }

  private String cleanPhysicalExamText(String value) {
    String collapsed = value == null ? "" : value.replaceAll("\\s+", " ").trim();
    return PHYSICAL_EXAM_PREFIX.matcher(collapsed).replaceFirst("");
  }

  private String cleanPhysicalExamSegment(String value) {
    String clean = value == null ? "" : value.trim();
    clean = clean.replaceFirst("^[.:;\\-\\s]+", "");
    return SEGMENT_PREFIX.matcher(clean).replaceFirst("").trim();
  }

  private static MarkerPattern marker(String label, String regex) {
    return new MarkerPattern(label, Pattern.compile(regex, REGEX_FLAGS));
  }

  private BigDecimal decimal(JsonNode value) {
    if (!(value.isNumber() || value.isTextual())) return null;
    String raw = value.asText("").trim().replace(',', '.');
    if (raw.isBlank()) return null;
    try {
      BigDecimal parsed = new BigDecimal(raw);
      double finiteCheck = parsed.doubleValue();
      return Double.isFinite(finiteCheck) ? parsed : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
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

  private boolean isClinicallyBlank(JsonNode value) {
    return value.isMissingNode()
        || value.isNull()
        || (value.isTextual() && value.textValue().isBlank());
  }

  private ApiException badRequest(String message, String code) {
    return new ApiException(HttpStatus.BAD_REQUEST, message, code);
  }

  private ArrayNode storedVersions(JsonNode stored) {
    JsonNode storedVersions = stored.path("meta").path("sectionVersions").path(SECTION_KEY);
    return storedVersions.isArray()
        ? (ArrayNode) storedVersions.deepCopy()
        : mapper.createArrayNode();
  }

  private void restoreProtectedSectionMetadata(ObjectNode resultMeta, JsonNode storedMeta) {
    restoreChild(resultMeta, storedMeta, "sectionVersions");
    restoreChild(resultMeta, storedMeta, "sectionAudit");
    restoreChild(resultMeta, storedMeta, "sectionFormModes");
  }

  private void restoreChild(ObjectNode resultMeta, JsonNode storedMeta, String containerName) {
    JsonNode storedContainer = storedMeta.path(containerName);
    JsonNode storedValue = storedContainer.path(SECTION_KEY);
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
      ((ObjectNode) resultContainer).set(SECTION_KEY, storedValue.deepCopy());
    } else {
      ((ObjectNode) resultContainer).remove(SECTION_KEY);
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

  private void removeTransientRequest(ObjectNode meta) {
    JsonNode requestsNode = meta.path("sectionChangeRequests");
    if (requestsNode.isMissingNode()) return;
    if (!(requestsNode instanceof ObjectNode requests)) {
      meta.remove("sectionChangeRequests");
      return;
    }
    requests.remove(SECTION_KEY);
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

  private enum FieldKind {
    WEIGHT,
    HEIGHT,
    TEXT
  }

  private record MarkerPattern(String label, Pattern pattern) {}

  private record Marker(String label, int start, int end) {}

  private record PhysicalRow(String label, String text) {}
}
