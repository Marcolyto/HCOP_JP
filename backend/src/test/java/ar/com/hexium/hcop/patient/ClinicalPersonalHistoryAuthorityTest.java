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

class ClinicalPersonalHistoryAuthorityTest {
  private static final Instant NOW = Instant.parse("2026-08-02T21:30:00Z");

  private final ObjectMapper mapper = new ObjectMapper();
  private ClinicalPersonalHistoryAuthority authority;

  @BeforeEach
  void setUp() {
    authority = new ClinicalPersonalHistoryAuthority(
        mapper,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void primeraCargaAgrupaLosCuatroCamposYFirmaConLaSesion() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {},
          "meta": {
            "sectionVersions": {"studies": [{"id": "study-trusted"}]},
            "sectionAudit": {"studies": {"action": "cargado"}},
            "sectionFormModes": {"studies": "structured"}
          }
        }
        """);
    JsonNode incoming = mapper.readTree("""
        {
          "narrative": {
            "backgroundClinical": "HTA controlada",
            "currentMedication": "Losartán 50 mg/día",
            "familyOncology": "Madre con cáncer de mama",
            "gynecology": "G2 P2"
          },
          "meta": {
            "sectionVersions": {
              "studies": [{"id": "study-trusted"}],
              "personalHistory": [{"id": "forged", "author": "Atacante"}]
            },
            "sectionAudit": {
              "studies": {"action": "cargado"},
              "personalHistory": {"action": "modificado", "lastName": "Atacante"}
            },
            "sectionFormModes": {"studies": "structured", "personalHistory": "forged"},
            "sectionChangeRequests": {
              "personalHistory": {"reason": "No se usa en primera carga"},
              "studies": {"reason": "Conservar"}
            },
            "currentProfessional": {"firstName": "Atacante", "custom": "preservar"},
            "currentUser": "Atacante"
          }
        }
        """);

    JsonNode result = authority.canonicalize(incoming, stored, principal());

    JsonNode versions = result.path("meta").path("sectionVersions").path("personalHistory");
    assertThat(versions).hasSize(1);
    assertThat(versions.get(0).path("id").asText()).startsWith("sec-personalHistory-");
    assertThat(versions.get(0).path("author").asText()).isEqualTo("Dra. Ana Segura");
    assertThat(versions.get(0).path("license").asText()).isEqualTo("MP-4455");
    assertThat(versions.get(0).path("reason").asText()).isEqualTo("Carga inicial");
    assertThat(versions.get(0).path("content").asText()).isEqualTo("""
        Clínicos / quirúrgicos: HTA controlada
        Medicación habitual: Losartán 50 mg/día
        Oncofamiliares: Madre con cáncer de mama
        Gineco-obstétricos: G2 P2""");
    assertThat(versions.get(0).path("createdAt").asText()).isEqualTo(NOW.toString());
    assertThat(versions.get(0).path("audit").path("action").asText()).isEqualTo("cargado");
    assertThat(result.path("meta").path("sectionAudit").path("personalHistory")
        .path("lastName").asText()).isEqualTo("Dra. Ana Segura");
    assertThat(result.path("meta").path("sectionFormModes").path("personalHistory").asText())
        .isEqualTo("structured");
    assertThat(result.path("meta").path("sectionVersions").path("studies").get(0)
        .path("id").asText()).isEqualTo("study-trusted");
    assertThat(result.path("meta").path("sectionChangeRequests").has("personalHistory"))
        .isFalse();
    assertThat(result.path("meta").path("sectionChangeRequests").path("studies")
        .path("reason").asText()).isEqualTo("Conservar");
    assertThat(result.path("meta").path("currentProfessional").path("firstName").asText())
        .isEqualTo("Dra. Ana Segura");
    assertThat(result.path("meta").path("currentProfessional").path("custom").asText())
        .isEqualTo("preservar");
  }

  @Test
  void modificacionConservaVersionesYDocumentaTodosLosCamposPresentes() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {
            "backgroundClinical": "HTA",
            "currentMedication": "Losartán",
            "familyOncology": "Niega",
            "gynecology": ""
          },
          "meta": {"sectionVersions": {"personalHistory": [{
            "id": "trusted-initial",
            "content": "Clínicos / quirúrgicos: HTA",
            "reason": "Carga inicial",
            "audit": {"action": "cargado", "lastName": "Dra. Original", "license": "MP-1", "at": "2026-07-01T10:00:00Z"}
          }]}}
        }
        """);
    ObjectNode incoming = (ObjectNode) stored.deepCopy();
    incoming.withObject("/narrative")
        .put("backgroundClinical", "HTA y apendicectomía")
        .put("currentMedication", "Losartán 50 mg")
        .put("familyOncology", "Niega")
        .put("gynecology", "G2 P2");
    incoming.withObject("/meta/sectionVersions").set(
        "personalHistory",
        mapper.createArrayNode().addObject().put("id", "forged"));
    incoming.withObject("/meta/sectionChangeRequests/personalHistory")
        .put("reason", "  Actualización de antecedentes  ");
    incoming.withObject("/meta/sectionChangeRequests/chiefComplaint")
        .put("reason", "Conservar");

    JsonNode result = authority.canonicalize(incoming, stored, principal());
    JsonNode versions = result.path("meta").path("sectionVersions").path("personalHistory");

    assertThat(versions).hasSize(2);
    assertThat(versions.get(0).path("id").asText()).isEqualTo("trusted-initial");
    assertThat(versions.get(1).path("reason").asText())
        .isEqualTo("Actualización de antecedentes");
    assertThat(versions.get(1).path("content").asText()).isEqualTo("""
        Clínicos / quirúrgicos: HTA y apendicectomía
        Medicación habitual: Losartán 50 mg
        Oncofamiliares: Niega
        Gineco-obstétricos: G2 P2""");
    assertThat(versions.get(1).path("audit").path("action").asText())
        .isEqualTo("modificado");
    assertThat(result.path("meta").path("sectionChangeRequests").has("personalHistory"))
        .isFalse();
    assertThat(result.path("meta").path("sectionChangeRequests").path("chiefComplaint")
        .path("reason").asText()).isEqualTo("Conservar");
  }

  @Test
  void borrarTodosLosCamposExistentesExigeMotivoYRegistraElVacio() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {
            "backgroundClinical": "HTA",
            "currentMedication": "Losartán",
            "familyOncology": "Niega",
            "gynecology": ""
          },
          "meta": {"sectionVersions": {"personalHistory": [{
            "id": "trusted-initial",
            "content": "Clínicos / quirúrgicos: HTA",
            "audit": {"action": "cargado", "at": "2026-07-01T10:00:00Z"}
          }]}}
        }
        """);

    assertCode(incoming("", "", "", "", null), stored,
        "CLINICAL_PERSONAL_HISTORY_REASON_REQUIRED");
    JsonNode result = authority.canonicalize(
        incoming("", "", "", "", "Corrección de carga"),
        stored,
        principal());

    JsonNode versions = result.path("meta").path("sectionVersions").path("personalHistory");
    assertThat(versions).hasSize(2);
    assertThat(versions.get(1).path("reason").asText()).isEqualTo("Corrección de carga");
    assertThat(versions.get(1).path("content").asText()).isEqualTo("Sin datos cargados.");
  }

  @Test
  void ausenciaLegacyYCamposVaciosSinComandoNoCreanVersionEspuria() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {"chiefComplaint": "Anterior"},
          "meta": {"sectionVersions": {}, "sectionAudit": {}, "sectionFormModes": {}}
        }
        """);
    JsonNode incoming = mapper.readTree("""
        {
          "narrative": {
            "chiefComplaint": "Actualizado",
            "backgroundClinical": "",
            "currentMedication": "  ",
            "familyOncology": null
          },
          "meta": {
            "sectionVersions": {"personalHistory": [{"id": "forged"}]},
            "sectionAudit": {"personalHistory": {"lastName": "forged"}},
            "sectionFormModes": {"personalHistory": "forged"}
          }
        }
        """);

    JsonNode result = authority.canonicalize(incoming, stored, principal());

    assertThat(result.path("narrative").path("chiefComplaint").asText())
        .isEqualTo("Actualizado");
    assertThat(result.path("meta").path("sectionVersions").has("personalHistory")).isFalse();
    assertThat(result.path("meta").path("sectionAudit").has("personalHistory")).isFalse();
    assertThat(result.path("meta").path("sectionFormModes").has("personalHistory")).isFalse();
  }

  @Test
  void valoresLegacySinCambioSePreservanYUnaMigracionRequiereMotivo() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {
            "backgroundClinical": {"texto": "Formato heredado"},
            "currentMedication": "",
            "familyOncology": "Niega",
            "gynecology": ""
          },
          "meta": {
            "createdAt": "2025-01-02T03:04:05Z",
            "sectionVersions": {"personalHistory": [{"id": "trusted"}]},
            "sectionAudit": {"personalHistory": {"lastName": "Trusted"}},
            "sectionFormModes": {"personalHistory": "legacy"}
          }
        }
        """);
    JsonNode unchanged = stored.deepCopy();
    ((ObjectNode) unchanged.path("meta").path("sectionVersions"))
        .set("personalHistory", mapper.createArrayNode().addObject().put("id", "forged"));
    ((ObjectNode) unchanged.path("meta").path("sectionAudit"))
        .set("personalHistory", mapper.createObjectNode().put("lastName", "Forged"));
    ((ObjectNode) unchanged.path("meta").path("sectionFormModes"))
        .put("personalHistory", "forged");

    JsonNode preserved = authority.canonicalize(unchanged, stored, principal());
    assertThat(preserved.path("narrative").path("backgroundClinical"))
        .isEqualTo(stored.path("narrative").path("backgroundClinical"));
    assertThat(preserved.path("meta").path("sectionVersions").path("personalHistory").get(0)
        .path("id").asText()).isEqualTo("trusted");
    assertThat(preserved.path("meta").path("sectionAudit").path("personalHistory")
        .path("lastName").asText()).isEqualTo("Trusted");
    assertThat(preserved.path("meta").path("sectionFormModes").path("personalHistory").asText())
        .isEqualTo("legacy");

    ObjectNode legacyWithoutVersions = (ObjectNode) stored.deepCopy();
    legacyWithoutVersions.withObject("/meta").remove("sectionVersions");
    ObjectNode migrated = legacyWithoutVersions.deepCopy();
    migrated.withObject("/narrative").put("backgroundClinical", "Historia normalizada");
    assertCode(
        migrated,
        legacyWithoutVersions,
        "CLINICAL_PERSONAL_HISTORY_REASON_REQUIRED");

    migrated.withObject("/meta/sectionChangeRequests/personalHistory")
        .put("reason", "Migración clínica");
    JsonNode result = authority.canonicalize(migrated, legacyWithoutVersions, principal());
    assertThat(result.path("meta").path("sectionVersions").path("personalHistory"))
        .hasSize(2);
  }

  @Test
  void usaCodigosEstablesParaVacioYMotivoInvalido() throws Exception {
    JsonNode blank = mapper.readTree("{\"narrative\": {}}");
    assertCode(
        incoming("", "", "", "", "Carga inicial"),
        blank,
        "CLINICAL_PERSONAL_HISTORY_EMPTY");

    JsonNode stored = mapper.readTree("""
        {"narrative": {"backgroundClinical": "HTA"}}
        """);
    ObjectNode invalidReason = incoming("DBT", "", "", "", null);
    invalidReason.withObject("/meta/sectionChangeRequests/personalHistory")
        .set("reason", mapper.createObjectNode());
    assertCode(invalidReason, stored, "CLINICAL_PERSONAL_HISTORY_REASON_INVALID");
    assertCode(
        incoming(
            "DBT",
            "",
            "",
            "",
            "x".repeat(ClinicalPersonalHistoryAuthority.MAX_REASON_CHARS + 1)),
        stored,
        "CLINICAL_PERSONAL_HISTORY_REASON_TOO_LONG");
  }

  private ObjectNode incoming(
      String backgroundClinical,
      String currentMedication,
      String familyOncology,
      String gynecology,
      String reason) {
    ObjectNode root = mapper.createObjectNode();
    root.withObject("/narrative")
        .put("backgroundClinical", backgroundClinical)
        .put("currentMedication", currentMedication)
        .put("familyOncology", familyOncology)
        .put("gynecology", gynecology);
    if (reason != null) {
      root.withObject("/meta/sectionChangeRequests/personalHistory").put("reason", reason);
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
