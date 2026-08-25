package ar.com.hexium.hcop.patient;

import ar.com.hexium.hcop.common.ApiException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Validates newly edited clinical fields without rejecting unchanged legacy values. */
@Component
public class ClinicalDocumentChangeValidator {
  static final int MAX_NARRATIVE_FIELD_CHARS = 50_000;
  static final BigDecimal MIN_WEIGHT_KG = new BigDecimal("0.01");
  static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("500");
  static final BigDecimal MIN_HEIGHT_M = new BigDecimal("0.3");
  static final BigDecimal MAX_HEIGHT_M = new BigDecimal("2.5");

  public void validate(JsonNode incoming, JsonNode stored) {
    validatePhysicalExamContainers(incoming, stored);
    validateTextChange(
        incoming,
        stored,
        "chiefComplaint",
        "El motivo de consulta",
        "CLINICAL_CHIEF_COMPLAINT",
        true);
    validateTextChange(
        incoming,
        stored,
        "currentIllness",
        "El campo Antecedentes de enfermedad actual",
        "CLINICAL_CURRENT_ILLNESS",
        true);
    validateTextChange(
        incoming,
        stored,
        "backgroundClinical",
        "El campo Clínicos / quirúrgicos",
        "CLINICAL_PERSONAL_HISTORY_BACKGROUND_CLINICAL",
        true);
    validateTextChange(
        incoming,
        stored,
        "currentMedication",
        "El campo Medicación habitual",
        "CLINICAL_PERSONAL_HISTORY_CURRENT_MEDICATION",
        true);
    validateTextChange(
        incoming,
        stored,
        "familyOncology",
        "El campo Oncofamiliares",
        "CLINICAL_PERSONAL_HISTORY_FAMILY_ONCOLOGY",
        true);
    validateTextChange(
        incoming,
        stored,
        "gynecology",
        "El campo Gineco-obstétricos",
        "CLINICAL_PERSONAL_HISTORY_GYNECOLOGY",
        true);
    validateNumericChange(
        incoming,
        stored,
        "weightKg",
        "El peso",
        "CLINICAL_PHYSICAL_EXAM_WEIGHT",
        MIN_WEIGHT_KG,
        MAX_WEIGHT_KG);
    validateNumericChange(
        incoming,
        stored,
        "heightM",
        "La talla",
        "CLINICAL_PHYSICAL_EXAM_HEIGHT",
        MIN_HEIGHT_M,
        MAX_HEIGHT_M);
    validateTextChange(
        incoming,
        stored,
        "physicalExam",
        "El examen físico",
        "CLINICAL_PHYSICAL_EXAM_TEXT",
        true);
    validateTextChange(
        incoming,
        stored,
        "summary",
        "La conclusión / resumen",
        "CLINICAL_SUMMARY",
        false);
    validateTextChange(
        incoming,
        stored,
        "plan",
        "La conducta / plan",
        "CLINICAL_PLAN",
        false);
  }

  private void validateTextChange(
      JsonNode incoming,
      JsonNode stored,
      String field,
      String label,
      String codePrefix,
      boolean blankMissingEquivalent) {
    JsonNode next = incoming.path("narrative").path(field);
    JsonNode previous = stored.path("narrative").path(field);
    if (next.equals(previous)) return;
    if (blankMissingEquivalent && isClinicallyBlank(next) && isClinicallyBlank(previous)) return;

    if (!next.isTextual()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          label + " debe ser texto.",
          codePrefix + "_INVALID");
    }
    if (next.textValue().length() > MAX_NARRATIVE_FIELD_CHARS) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          label + " no puede superar " + MAX_NARRATIVE_FIELD_CHARS + " caracteres.",
          codePrefix + "_TOO_LONG");
    }
  }

  private boolean isClinicallyBlank(JsonNode value) {
    return value.isMissingNode()
        || value.isNull()
        || (value.isTextual() && value.textValue().isBlank());
  }

  private void validateNumericChange(
      JsonNode incoming,
      JsonNode stored,
      String field,
      String label,
      String codePrefix,
      BigDecimal minimum,
      BigDecimal maximum) {
    JsonNode next = incoming.path("exam").path(field);
    JsonNode previous = stored.path("exam").path(field);
    if (next.equals(previous) || numericallyEquivalent(next, previous, field)) return;
    if (isClinicallyBlank(next) && isClinicallyBlank(previous)) return;
    if (isClinicallyBlank(next)) {
      if (next.isTextual()) return;
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          label + " debe ser un número finito.",
          codePrefix + "_INVALID");
    }

    BigDecimal value = decimal(next);
    if (value == null) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          label + " debe ser un número finito.",
          codePrefix + "_INVALID");
    }
    if (value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          label + " debe estar entre " + minimum.toPlainString()
              + " y " + maximum.toPlainString() + ".",
          codePrefix + "_OUT_OF_RANGE");
    }
  }

  private void validatePhysicalExamContainers(JsonNode incoming, JsonNode stored) {
    JsonNode incomingExam = incoming.path("exam");
    JsonNode storedExam = stored.path("exam");
    JsonNode incomingNarrative = incoming.path("narrative");
    JsonNode storedNarrative = stored.path("narrative");
    boolean physicalFieldsChanged = physicalFieldChanged(
        incomingExam.path("weightKg"),
        storedExam.path("weightKg"),
        "weightKg")
        || physicalFieldChanged(
            incomingExam.path("heightM"),
            storedExam.path("heightM"),
            "heightM")
        || physicalFieldChanged(
            incomingNarrative.path("physicalExam"),
            storedNarrative.path("physicalExam"),
            null)
        || malformedContainerChanged(incomingExam, storedExam)
        || malformedContainerChanged(incomingNarrative, storedNarrative);
    if (!physicalFieldsChanged) return;

    if (isMalformedContainer(incomingExam) || isMalformedContainer(storedExam)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "La estructura del examen antropométrico no es válida.",
          "CLINICAL_PHYSICAL_EXAM_WEIGHT_INVALID");
    }
    if (isMalformedContainer(incomingNarrative) || isMalformedContainer(storedNarrative)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "La estructura narrativa del examen físico no es válida.",
          "CLINICAL_PHYSICAL_EXAM_TEXT_INVALID");
    }
  }

  private boolean physicalFieldChanged(
      JsonNode next,
      JsonNode previous,
      String numericField) {
    if (next.equals(previous)) return false;
    if (isClinicallyBlank(next) && isClinicallyBlank(previous)) return false;
    return numericField == null || !numericallyEquivalent(next, previous, numericField);
  }

  private boolean malformedContainerChanged(JsonNode next, JsonNode previous) {
    return (isMalformedContainer(next) || isMalformedContainer(previous))
        && !next.equals(previous);
  }

  private boolean isMalformedContainer(JsonNode value) {
    return !(value.isMissingNode() || value.isNull() || value.isObject());
  }

  private boolean numericallyEquivalent(JsonNode left, JsonNode right, String field) {
    BigDecimal leftNumber = decimal(left);
    BigDecimal rightNumber = decimal(right);
    if ("heightM".equals(field) && leftNumber != null && rightNumber != null) {
      leftNumber = heightCentimeters(leftNumber);
      rightNumber = heightCentimeters(rightNumber);
    }
    return leftNumber != null
        && rightNumber != null
        && leftNumber.compareTo(rightNumber) == 0;
  }

  private BigDecimal heightCentimeters(BigDecimal value) {
    return value.compareTo(BigDecimal.valueOf(3)) <= 0
        ? value.multiply(BigDecimal.valueOf(100))
        : value;
  }

  private BigDecimal decimal(JsonNode value) {
    if (!(value.isNumber() || value.isTextual())) return null;
    String raw = value.asText("").trim().replace(',', '.');
    if (raw.isBlank()) return null;
    try {
      BigDecimal parsed = new BigDecimal(raw);
      return Double.isFinite(parsed.doubleValue()) ? parsed : null;
    } catch (NumberFormatException ignored) {
      return null;
    }
  }
}
