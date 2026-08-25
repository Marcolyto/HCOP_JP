package ar.com.hexium.hcop.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.hexium.hcop.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ClinicalDocumentChangeValidatorTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final ClinicalDocumentChangeValidator validator = new ClinicalDocumentChangeValidator();

  @Test
  void acceptsUnchangedInvalidLegacyNarrativeValues() {
    ObjectNode stored = mapper.createObjectNode();
    stored.withObject("/narrative").set(
        "chiefComplaint",
        mapper.createObjectNode().put("legacy", true));
    stored.withObject("/narrative").set(
        "currentIllness",
        mapper.createArrayNode().add("legacy"));
    stored.withObject("/narrative").set(
        "backgroundClinical",
        mapper.createObjectNode().put("legacy", true));
    stored.withObject("/narrative").set(
        "currentMedication",
        mapper.createArrayNode().add("legacy"));
    stored.withObject("/narrative").set(
        "familyOncology",
        mapper.createObjectNode().put("legacy", true));
    stored.withObject("/narrative").set(
        "gynecology",
        mapper.createArrayNode().add("legacy"));
    stored.withObject("/exam").set(
        "weightKg",
        mapper.createObjectNode().put("legacy", true));
    stored.withObject("/exam").set(
        "heightM",
        mapper.createArrayNode().add("legacy"));
    stored.withObject("/narrative").set(
        "physicalExam",
        mapper.createObjectNode().put("legacy", true));
    stored.withObject("/narrative").set("summary", mapper.createObjectNode().put("legacy", true));
    stored.withObject("/narrative").set("plan", mapper.createArrayNode().add("legacy"));

    assertThatCode(() -> validator.validate(stored.deepCopy(), stored))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsMissingLegacyFieldsAndChangedTextAtTheLimit() {
    ObjectNode emptyLegacy = mapper.createObjectNode();
    assertThatCode(() -> validator.validate(emptyLegacy.deepCopy(), emptyLegacy))
        .doesNotThrowAnyException();

    ObjectNode stored = narrative("Anterior", "Plan anterior");
    ObjectNode incoming = stored.deepCopy();
    incoming.withObject("/narrative")
        .put("summary", "s".repeat(ClinicalDocumentChangeValidator.MAX_NARRATIVE_FIELD_CHARS))
        .put("plan", "");

    assertThatCode(() -> validator.validate(incoming, stored))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsOmittedChiefComplaintAndCurrentIllnessWhenStoredValuesAreBlank() {
    ObjectNode stored = narrative("Resumen anterior", "Plan vigente");
    stored.withObject("/narrative")
        .put("chiefComplaint", "   ")
        .put("currentIllness", "");
    ObjectNode incoming = narrative("Resumen actualizado", "Plan vigente");

    assertThatCode(() -> validator.validate(incoming, stored))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsMissingBlankAndNullAsEquivalentForOptionalSingleNarratives() {
    ObjectNode stored = narrative("Resumen", "Plan");
    stored.withObject("/narrative")
        .putNull("chiefComplaint")
        .putNull("currentIllness");
    ObjectNode incoming = narrative("Resumen", "Plan");
    incoming.withObject("/narrative")
        .put("chiefComplaint", "")
        .put("currentIllness", "  \n  ")
        .put("backgroundClinical", "")
        .put("currentMedication", "  ")
        .putNull("familyOncology")
        .put("gynecology", "\n");
    incoming.withObject("/exam")
        .put("weightKg", "")
        .putNull("heightM");
    incoming.withObject("/narrative").put("physicalExam", "  ");

    assertThatCode(() -> validator.validate(incoming, stored))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsOmittingNonBlankChiefComplaintOrCurrentIllness() {
    ObjectNode storedChiefComplaint = narrative("Resumen", "Plan");
    storedChiefComplaint.withObject("/narrative").put("chiefComplaint", "Dolor abdominal");
    assertFailure(
        narrative("Resumen", "Plan"),
        storedChiefComplaint,
        "CLINICAL_CHIEF_COMPLAINT_INVALID");

    ObjectNode storedCurrentIllness = narrative("Resumen", "Plan");
    storedCurrentIllness.withObject("/narrative").put("currentIllness", "Tres meses");
    assertFailure(
        narrative("Resumen", "Plan"),
        storedCurrentIllness,
        "CLINICAL_CURRENT_ILLNESS_INVALID");
  }

  @Test
  void rejectsOmittingAnyNonBlankPersonalHistoryField() {
    String[][] fields = {
        {"backgroundClinical", "CLINICAL_PERSONAL_HISTORY_BACKGROUND_CLINICAL_INVALID"},
        {"currentMedication", "CLINICAL_PERSONAL_HISTORY_CURRENT_MEDICATION_INVALID"},
        {"familyOncology", "CLINICAL_PERSONAL_HISTORY_FAMILY_ONCOLOGY_INVALID"},
        {"gynecology", "CLINICAL_PERSONAL_HISTORY_GYNECOLOGY_INVALID"}
    };

    for (String[] field : fields) {
      ObjectNode stored = narrative("Resumen", "Plan");
      stored.withObject("/narrative").put(field[0], "Contenido clínico real");
      assertFailure(narrative("Resumen", "Plan"), stored, field[1]);
    }
  }

  @Test
  void validatesTypeAndLengthForEveryPersonalHistoryField() {
    String[][] fields = {
        {"backgroundClinical", "CLINICAL_PERSONAL_HISTORY_BACKGROUND_CLINICAL"},
        {"currentMedication", "CLINICAL_PERSONAL_HISTORY_CURRENT_MEDICATION"},
        {"familyOncology", "CLINICAL_PERSONAL_HISTORY_FAMILY_ONCOLOGY"},
        {"gynecology", "CLINICAL_PERSONAL_HISTORY_GYNECOLOGY"}
    };

    for (String[] field : fields) {
      ObjectNode stored = narrative("Resumen", "Plan");
      stored.withObject("/narrative").put(field[0], "Anterior");
      ObjectNode invalid = stored.deepCopy();
      invalid.withObject("/narrative").set(field[0], mapper.createObjectNode());
      assertFailure(invalid, stored, field[1] + "_INVALID");

      ObjectNode oversized = stored.deepCopy();
      oversized.withObject("/narrative").put(
          field[0],
          "x".repeat(ClinicalDocumentChangeValidator.MAX_NARRATIVE_FIELD_CHARS + 1));
      assertFailure(oversized, stored, field[1] + "_TOO_LONG");
    }
  }

  @Test
  void acceptsPhysicalExamNumericBoundariesAndEquivalentLegacyHeightUnits() {
    ObjectNode blank = narrative("Resumen", "Plan");
    ObjectNode minimum = blank.deepCopy();
    minimum.withObject("/exam").put("weightKg", "0,01").put("heightM", "0.3");
    assertThatCode(() -> validator.validate(minimum, blank)).doesNotThrowAnyException();

    ObjectNode maximum = blank.deepCopy();
    maximum.withObject("/exam").put("weightKg", 500).put("heightM", 2.5);
    assertThatCode(() -> validator.validate(maximum, blank)).doesNotThrowAnyException();

    ObjectNode legacy = narrative("Resumen", "Plan");
    legacy.withObject("/exam").put("weightKg", 75).put("heightM", "175");
    ObjectNode equivalent = legacy.deepCopy();
    equivalent.withObject("/exam").put("weightKg", "75.00").put("heightM", "1.75");
    assertThatCode(() -> validator.validate(equivalent, legacy)).doesNotThrowAnyException();
  }

  @Test
  void rejectsInvalidOrOutOfRangePhysicalExamNumbers() {
    ObjectNode stored = narrative("Resumen", "Plan");

    assertNumericFailure(
        stored,
        "weightKg",
        "NaN",
        "CLINICAL_PHYSICAL_EXAM_WEIGHT_INVALID");
    ObjectNode invalidWeightType = stored.deepCopy();
    invalidWeightType.withObject("/exam").set("weightKg", mapper.createObjectNode());
    assertFailure(invalidWeightType, stored, "CLINICAL_PHYSICAL_EXAM_WEIGHT_INVALID");
    assertNumericFailure(
        stored,
        "weightKg",
        "0.009",
        "CLINICAL_PHYSICAL_EXAM_WEIGHT_OUT_OF_RANGE");
    assertNumericFailure(
        stored,
        "weightKg",
        "500.01",
        "CLINICAL_PHYSICAL_EXAM_WEIGHT_OUT_OF_RANGE");

    assertNumericFailure(
        stored,
        "heightM",
        "Infinity",
        "CLINICAL_PHYSICAL_EXAM_HEIGHT_INVALID");
    assertNumericFailure(
        stored,
        "heightM",
        "0.299",
        "CLINICAL_PHYSICAL_EXAM_HEIGHT_OUT_OF_RANGE");
    assertNumericFailure(
        stored,
        "heightM",
        "2.501",
        "CLINICAL_PHYSICAL_EXAM_HEIGHT_OUT_OF_RANGE");
  }

  @Test
  void rejectsOmittingRealPhysicalExamValues() {
    ObjectNode storedWeight = narrative("Resumen", "Plan");
    storedWeight.withObject("/exam").put("weightKg", "75");
    assertFailure(
        narrative("Resumen", "Plan"),
        storedWeight,
        "CLINICAL_PHYSICAL_EXAM_WEIGHT_INVALID");

    ObjectNode storedHeight = narrative("Resumen", "Plan");
    storedHeight.withObject("/exam").put("heightM", "1.75");
    assertFailure(
        narrative("Resumen", "Plan"),
        storedHeight,
        "CLINICAL_PHYSICAL_EXAM_HEIGHT_INVALID");

    ObjectNode storedText = narrative("Resumen", "Plan");
    storedText.withObject("/narrative").put("physicalExam", "Afebril");
    assertFailure(
        narrative("Resumen", "Plan"),
        storedText,
        "CLINICAL_PHYSICAL_EXAM_TEXT_INVALID");
  }

  @Test
  void validatesPhysicalExamTextTypeAndLength() {
    ObjectNode stored = narrative("Resumen", "Plan");
    stored.withObject("/narrative").put("physicalExam", "Afebril");
    ObjectNode invalid = stored.deepCopy();
    invalid.withObject("/narrative").set("physicalExam", mapper.createArrayNode());
    assertFailure(invalid, stored, "CLINICAL_PHYSICAL_EXAM_TEXT_INVALID");

    ObjectNode oversized = stored.deepCopy();
    oversized.withObject("/narrative").put(
        "physicalExam",
        "x".repeat(ClinicalDocumentChangeValidator.MAX_NARRATIVE_FIELD_CHARS + 1));
    assertFailure(oversized, stored, "CLINICAL_PHYSICAL_EXAM_TEXT_TOO_LONG");
  }

  @Test
  void protectsMalformedLegacyPhysicalExamContainersWithoutBlockingUnrelatedEdits() {
    ObjectNode storedExamArray = narrative("Resumen", "Plan");
    storedExamArray.set("exam", mapper.createArrayNode().add("legacy"));
    storedExamArray.withObject("/narrative").put("physicalExam", "Afebril");

    ObjectNode physicalTextEdit = storedExamArray.deepCopy();
    physicalTextEdit.withObject("/narrative").put("physicalExam", "Normohidratado");
    assertFailure(
        physicalTextEdit,
        storedExamArray,
        "CLINICAL_PHYSICAL_EXAM_WEIGHT_INVALID");
    assertThat(physicalTextEdit.path("exam")).isEqualTo(storedExamArray.path("exam"));

    ObjectNode storedNarrativeString = mapper.createObjectNode();
    storedNarrativeString.put("narrative", "legacy");
    storedNarrativeString.withObject("/exam").put("weightKg", "70");
    ObjectNode anthropometricEdit = storedNarrativeString.deepCopy();
    anthropometricEdit.withObject("/exam").put("weightKg", "75");
    assertFailure(
        anthropometricEdit,
        storedNarrativeString,
        "CLINICAL_PHYSICAL_EXAM_TEXT_INVALID");
    assertThat(anthropometricEdit.path("narrative"))
        .isEqualTo(storedNarrativeString.path("narrative"));

    ObjectNode unrelatedEdit = storedExamArray.deepCopy();
    unrelatedEdit.withObject("/oncology").put("status", "En seguimiento");
    assertThatCode(() -> validator.validate(unrelatedEdit, storedExamArray))
        .doesNotThrowAnyException();
    assertThat(unrelatedEdit.path("exam")).isEqualTo(storedExamArray.path("exam"));
  }

  @Test
  void rejectsChangedNonTextSummary() {
    ObjectNode stored = narrative("Anterior", "Plan");
    ObjectNode incoming = stored.deepCopy();
    incoming.withObject("/narrative").set("summary", mapper.createObjectNode());

    assertFailure(incoming, stored, "CLINICAL_SUMMARY_INVALID");
  }

  @Test
  void rejectsChangedNonTextChiefComplaint() {
    ObjectNode stored = narrative("Anterior", "Plan");
    stored.withObject("/narrative").put("chiefComplaint", "Consulta anterior");
    ObjectNode incoming = stored.deepCopy();
    incoming.withObject("/narrative").set("chiefComplaint", mapper.createArrayNode());

    assertFailure(incoming, stored, "CLINICAL_CHIEF_COMPLAINT_INVALID");
  }

  @Test
  void rejectsChangedOversizedChiefComplaint() {
    ObjectNode stored = narrative("Anterior", "Plan");
    stored.withObject("/narrative").put("chiefComplaint", "Consulta anterior");
    ObjectNode incoming = stored.deepCopy();
    incoming.withObject("/narrative").put(
        "chiefComplaint",
        "m".repeat(ClinicalDocumentChangeValidator.MAX_NARRATIVE_FIELD_CHARS + 1));

    assertFailure(incoming, stored, "CLINICAL_CHIEF_COMPLAINT_TOO_LONG");
  }

  @Test
  void rejectsChangedNonTextCurrentIllness() {
    ObjectNode stored = narrative("Anterior", "Plan");
    stored.withObject("/narrative").put("currentIllness", "Historia anterior");
    ObjectNode incoming = stored.deepCopy();
    incoming.withObject("/narrative").set("currentIllness", mapper.createArrayNode());

    assertFailure(incoming, stored, "CLINICAL_CURRENT_ILLNESS_INVALID");
  }

  @Test
  void rejectsChangedOversizedCurrentIllness() {
    ObjectNode stored = narrative("Anterior", "Plan");
    stored.withObject("/narrative").put("currentIllness", "Historia anterior");
    ObjectNode incoming = stored.deepCopy();
    incoming.withObject("/narrative").put(
        "currentIllness",
        "e".repeat(ClinicalDocumentChangeValidator.MAX_NARRATIVE_FIELD_CHARS + 1));

    assertFailure(incoming, stored, "CLINICAL_CURRENT_ILLNESS_TOO_LONG");
  }

  @Test
  void rejectsChangedOversizedSummary() {
    ObjectNode stored = narrative("Anterior", "Plan");
    ObjectNode incoming = stored.deepCopy();
    incoming.withObject("/narrative").put(
        "summary",
        "s".repeat(ClinicalDocumentChangeValidator.MAX_NARRATIVE_FIELD_CHARS + 1));

    assertFailure(incoming, stored, "CLINICAL_SUMMARY_TOO_LONG");
  }

  @Test
  void rejectsChangedNonTextPlan() {
    ObjectNode stored = narrative("Resumen", "Anterior");
    ObjectNode incoming = stored.deepCopy();
    incoming.withObject("/narrative").set("plan", mapper.createArrayNode());

    assertFailure(incoming, stored, "CLINICAL_PLAN_INVALID");
  }

  @Test
  void rejectsChangedOversizedPlan() {
    ObjectNode stored = narrative("Resumen", "Anterior");
    ObjectNode incoming = stored.deepCopy();
    incoming.withObject("/narrative").put(
        "plan",
        "p".repeat(ClinicalDocumentChangeValidator.MAX_NARRATIVE_FIELD_CHARS + 1));

    assertFailure(incoming, stored, "CLINICAL_PLAN_TOO_LONG");
  }

  private ObjectNode narrative(String summary, String plan) {
    ObjectNode document = mapper.createObjectNode();
    document.withObject("/narrative").put("summary", summary).put("plan", plan);
    return document;
  }

  private void assertFailure(ObjectNode incoming, ObjectNode stored, String code) {
    assertThatThrownBy(() -> validator.validate(incoming, stored))
        .isInstanceOfSatisfying(ApiException.class, error -> {
          assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(error.code()).isEqualTo(code);
        });
  }

  private void assertNumericFailure(
      ObjectNode stored,
      String field,
      String value,
      String code) {
    ObjectNode incoming = stored.deepCopy();
    incoming.withObject("/exam").put(field, value);
    assertFailure(incoming, stored, code);
  }
}
