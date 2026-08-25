package ar.com.hexium.hcop.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ClinicalPhysicalExamAuthorityTest {
  private static final Instant NOW = Instant.parse("2026-08-03T14:00:00Z");

  private final ObjectMapper mapper = new ObjectMapper();
  private ClinicalPhysicalExamAuthority authority;

  @BeforeEach
  void setUp() {
    authority = new ClinicalPhysicalExamAuthority(
        mapper,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void primeraCargaPuedeSerParcialYConvierteLaTallaDelStorageAMetrosEnSnapshotCm()
      throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "exam": {"weightKg": "", "heightM": ""},
          "narrative": {"physicalExam": ""},
          "meta": {
            "sectionVersions": {"studies": [{"id": "study-trusted"}]},
            "sectionAudit": {"studies": {"action": "cargado"}},
            "sectionFormModes": {"studies": "structured"}
          }
        }
        """);
    JsonNode incoming = mapper.readTree("""
        {
          "exam": {"weightKg": "", "heightM": "1.75"},
          "narrative": {"physicalExam": ""},
          "meta": {
            "sectionVersions": {
              "studies": [{"id": "study-trusted"}],
              "physicalExam": [{"id": "forged", "author": "Atacante"}]
            },
            "sectionAudit": {
              "studies": {"action": "cargado"},
              "physicalExam": {"action": "modificado", "lastName": "Atacante"}
            },
            "sectionFormModes": {"studies": "structured", "physicalExam": "forged"},
            "sectionChangeRequests": {
              "physicalExam": {"reason": "No se usa en primera carga"},
              "studies": {"reason": "Conservar"}
            },
            "currentProfessional": {"firstName": "Atacante", "custom": "preservar"}
          }
        }
        """);

    JsonNode result = authority.canonicalize(incoming, stored, principal());

    JsonNode versions = result.path("meta").path("sectionVersions").path("physicalExam");
    assertThat(versions).hasSize(1);
    assertThat(versions.get(0).path("id").asText()).startsWith("sec-physicalExam-");
    assertThat(versions.get(0).path("author").asText()).isEqualTo("Dra. Ana Segura");
    assertThat(versions.get(0).path("license").asText()).isEqualTo("MP-4455");
    assertThat(versions.get(0).path("reason").asText()).isEqualTo("Carga inicial");
    assertThat(versions.get(0).path("content").asText()).isEqualTo("Talla: 175 cm");
    assertThat(versions.get(0).path("createdAt").asText()).isEqualTo(NOW.toString());
    assertThat(versions.get(0).path("audit").path("action").asText()).isEqualTo("cargado");
    assertThat(result.path("meta").path("sectionAudit").path("physicalExam")
        .path("lastName").asText()).isEqualTo("Dra. Ana Segura");
    assertThat(result.path("meta").path("sectionFormModes").path("physicalExam").asText())
        .isEqualTo("structured");
    assertThat(result.path("meta").path("sectionVersions").path("studies").get(0)
        .path("id").asText()).isEqualTo("study-trusted");
    assertThat(result.path("meta").path("sectionChangeRequests").has("physicalExam"))
        .isFalse();
    assertThat(result.path("meta").path("sectionChangeRequests").path("studies")
        .path("reason").asText()).isEqualTo("Conservar");
    assertThat(result.path("meta").path("currentProfessional").path("firstName").asText())
        .isEqualTo("Dra. Ana Segura");
    assertThat(result.path("meta").path("currentProfessional").path("custom").asText())
        .isEqualTo("preservar");
  }

  @Test
  void modificacionConservaVersionesYGeneraSnapshotClinicoEstable() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "exam": {"weightKg": "70", "heightM": "1.75"},
          "narrative": {"physicalExam": "Buen estado general"},
          "meta": {"sectionVersions": {"physicalExam": [{
            "id": "trusted-initial",
            "content": "Peso: 70 kg\\nTalla: 175 cm\\nBuen estado general",
            "reason": "Carga inicial",
            "audit": {"action": "cargado", "lastName": "Dra. Original", "license": "MP-1", "at": "2026-07-01T10:00:00Z"}
          }]}}
        }
        """);
    ObjectNode incoming = (ObjectNode) stored.deepCopy();
    incoming.withObject("/exam").put("weightKg", "75.50").put("heightM", "1.8");
    incoming.withObject("/narrative").put(
        "physicalExam",
        "Afebril. Abdomen blando.\nSin signos neurológicos focales.");
    incoming.withObject("/meta/sectionVersions").set(
        "physicalExam",
        mapper.createArrayNode().addObject().put("id", "forged"));
    incoming.withObject("/meta/sectionChangeRequests/physicalExam")
        .put("reason", "  Control previo a tratamiento  ");
    incoming.withObject("/meta/sectionChangeRequests/personalHistory")
        .put("reason", "Conservar");

    JsonNode result = authority.canonicalize(incoming, stored, principal());
    JsonNode versions = result.path("meta").path("sectionVersions").path("physicalExam");

    assertThat(versions).hasSize(2);
    assertThat(versions.get(0).path("id").asText()).isEqualTo("trusted-initial");
    assertThat(versions.get(1).path("reason").asText())
        .isEqualTo("Control previo a tratamiento");
    assertThat(versions.get(1).path("content").asText()).isEqualTo("""
        Peso: 75.5 kg
        Talla: 180 cm
        Estado general: Afebril.
        Abdomen: blando. Sin signos neurológicos focales.""");
    assertThat(versions.get(1).path("audit").path("action").asText())
        .isEqualTo("modificado");
    assertThat(result.path("meta").path("sectionChangeRequests").has("physicalExam"))
        .isFalse();
    assertThat(result.path("meta").path("sectionChangeRequests").path("personalHistory")
        .path("reason").asText()).isEqualTo("Conservar");
  }

  @Test
  void borrarTodoExigeMotivoYRegistraElVacio() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "exam": {"weightKg": "75", "heightM": "1.75"},
          "narrative": {"physicalExam": "Afebril"},
          "meta": {"sectionVersions": {"physicalExam": [{
            "id": "trusted-initial",
            "audit": {"action": "cargado", "at": "2026-07-01T10:00:00Z"}
          }]}}
        }
        """);

    assertCode(incoming("", "", "", null), stored,
        "CLINICAL_PHYSICAL_EXAM_REASON_REQUIRED");
    JsonNode result = authority.canonicalize(
        incoming("", "", "", "Corrección de carga"),
        stored,
        principal());

    JsonNode versions = result.path("meta").path("sectionVersions").path("physicalExam");
    assertThat(versions).hasSize(2);
    assertThat(versions.get(1).path("content").asText()).isEqualTo("Sin datos cargados.");
  }

  @Test
  void representacionesNumericasEquivalentesNoCreanVersionEspuria() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "exam": {"weightKg": 75, "heightM": "1.7500"},
          "narrative": {"physicalExam": "Afebril"},
          "meta": {
            "sectionVersions": {"physicalExam": [{"id": "trusted"}]},
            "sectionAudit": {"physicalExam": {"lastName": "Trusted"}},
            "sectionFormModes": {"physicalExam": "structured"}
          }
        }
        """);
    JsonNode incoming = mapper.readTree("""
        {
          "exam": {"weightKg": "75.00", "heightM": 1.75},
          "narrative": {"physicalExam": "Afebril"},
          "meta": {
            "sectionVersions": {"physicalExam": [{"id": "forged"}]},
            "sectionAudit": {"physicalExam": {"lastName": "Forged"}},
            "sectionFormModes": {"physicalExam": "forged"}
          }
        }
        """);

    JsonNode result = authority.canonicalize(incoming, stored, principal());

    assertThat(result.path("meta").path("sectionVersions").path("physicalExam").get(0)
        .path("id").asText()).isEqualTo("trusted");
    assertThat(result.path("meta").path("sectionAudit").path("physicalExam")
        .path("lastName").asText()).isEqualTo("Trusted");
    assertThat(result.path("meta").path("sectionFormModes").path("physicalExam").asText())
        .isEqualTo("structured");
    assertThat(result.path("exam").path("weightKg")).isEqualTo(stored.path("exam").path("weightKg"));
    assertThat(result.path("exam").path("heightM")).isEqualTo(stored.path("exam").path("heightM"));
  }

  @Test
  void conservaTallaLegacyEnCentimetrosAlCambiarSoloElTexto() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "exam": {"weightKg": "75", "heightM": "175"},
          "narrative": {"physicalExam": "Afebril"},
          "meta": {"sectionVersions": {"physicalExam": [{
            "id": "trusted-initial",
            "content": "Peso: 75 kg\\nTalla: 175 cm\\nEstado general: Afebril",
            "audit": {"action": "cargado", "at": "2026-07-01T10:00:00Z"}
          }]}}
        }
        """);
    ObjectNode incoming = (ObjectNode) stored.deepCopy();
    incoming.withObject("/exam").put("heightM", "1.75");
    incoming.withObject("/narrative").put("physicalExam", "Afebril y normohidratado");
    incoming.withObject("/meta/sectionChangeRequests/physicalExam")
        .put("reason", "Control clínico");

    JsonNode result = authority.canonicalize(incoming, stored, principal());

    assertThat(result.path("exam").path("heightM").asText()).isEqualTo("175");
    assertThat(result.path("meta").path("sectionVersions").path("physicalExam").get(1)
        .path("content").asText()).isEqualTo("""
            Peso: 75 kg
            Talla: 175 cm
            Estado general: Afebril y normohidratado""");
  }

  @Test
  void formateaMarcadoresLegacyYConservaSuOrdenDeAparicion() throws Exception {
    JsonNode stored = mapper.readTree("{\"exam\": {}, \"narrative\": {}}");
    ObjectNode incoming = incoming(
        "",
        "",
        "Examen físico al ingreso: Conservado. SNC: sin foco. Tórax: MV conservado. "
            + "Corazón: R1 y R2 normofonéticos. Abdomen: blando. "
            + "Tacto rectal: no realizado.",
        null);

    JsonNode result = authority.canonicalize(incoming, stored, principal());

    assertThat(result.path("meta").path("sectionVersions").path("physicalExam").get(0)
        .path("content").asText()).isEqualTo("""
            Estado general: Conservado.
            SNC: sin foco.
            Tórax: MV conservado.
            Corazón: R1 y R2 normofonéticos.
            Abdomen: blando.
            Tacto rectal: no realizado.""");
  }

  @Test
  void valoresLegacySinCambioSePreservanYUnaMigracionRequiereMotivo() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "exam": {"weightKg": {"legacy": true}, "heightM": "1.75"},
          "narrative": {"physicalExam": "Afebril"},
          "meta": {
            "createdAt": "2025-01-02T03:04:05Z",
            "sectionVersions": {"physicalExam": [{"id": "trusted"}]},
            "sectionAudit": {"physicalExam": {"lastName": "Trusted"}},
            "sectionFormModes": {"physicalExam": "legacy"}
          }
        }
        """);
    JsonNode unchanged = stored.deepCopy();
    ((ObjectNode) unchanged.path("meta").path("sectionVersions"))
        .set("physicalExam", mapper.createArrayNode().addObject().put("id", "forged"));

    JsonNode preserved = authority.canonicalize(unchanged, stored, principal());
    assertThat(preserved.path("exam").path("weightKg"))
        .isEqualTo(stored.path("exam").path("weightKg"));
    assertThat(preserved.path("meta").path("sectionVersions").path("physicalExam").get(0)
        .path("id").asText()).isEqualTo("trusted");

    ObjectNode legacyWithoutVersions = (ObjectNode) stored.deepCopy();
    legacyWithoutVersions.withObject("/meta").remove("sectionVersions");
    ObjectNode migrated = legacyWithoutVersions.deepCopy();
    migrated.withObject("/exam").put("weightKg", "75");
    assertCode(migrated, legacyWithoutVersions, "CLINICAL_PHYSICAL_EXAM_REASON_REQUIRED");

    migrated.withObject("/meta/sectionChangeRequests/physicalExam")
        .put("reason", "Migración clínica");
    JsonNode result = authority.canonicalize(migrated, legacyWithoutVersions, principal());
    assertThat(result.path("meta").path("sectionVersions").path("physicalExam"))
        .hasSize(2);
  }

  @Test
  void usaCodigosEstablesParaVacioYMotivoInvalido() throws Exception {
    JsonNode blank = mapper.readTree("{\"exam\": {}, \"narrative\": {}}");
    assertCode(
        incoming("", "", "", "Carga inicial"),
        blank,
        "CLINICAL_PHYSICAL_EXAM_EMPTY");

    JsonNode stored = mapper.readTree("""
        {"exam": {"weightKg": "75"}, "narrative": {"physicalExam": ""}}
        """);
    ObjectNode invalidReason = incoming("76", "", "", null);
    invalidReason.withObject("/meta/sectionChangeRequests/physicalExam")
        .set("reason", mapper.createObjectNode());
    assertCode(invalidReason, stored, "CLINICAL_PHYSICAL_EXAM_REASON_INVALID");
    assertCode(
        incoming(
            "76",
            "",
            "",
            "x".repeat(ClinicalPhysicalExamAuthority.MAX_REASON_CHARS + 1)),
        stored,
        "CLINICAL_PHYSICAL_EXAM_REASON_TOO_LONG");
  }

  private ObjectNode incoming(
      String weightKg,
      String heightM,
      String physicalExam,
      String reason) {
    ObjectNode root = mapper.createObjectNode();
    root.withObject("/exam").put("weightKg", weightKg).put("heightM", heightM);
    root.withObject("/narrative").put("physicalExam", physicalExam);
    if (reason != null) {
      root.withObject("/meta/sectionChangeRequests/physicalExam").put("reason", reason);
    }
    return root;
  }

  private void assertCode(JsonNode incoming, JsonNode stored, String code) {
    assertThatThrownBy(() -> authority.canonicalize(incoming, stored, principal()))
        .isInstanceOfSatisfying(ApiException.class, error -> {
          assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
          assertThat(error.code()).isEqualTo(code);
        });
  }

  private SessionPrincipal principal() {
    return new SessionPrincipal(
        77L,
        "ana.segura",
        "ana@example.test",
        "Dra. Ana Segura",
        "Oncología",
        "MP-4455",
        true,
        42L,
        List.of(),
        Set.of("section.history.view", "section.history.edit"));
  }
}
