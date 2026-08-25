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

class ClinicalCurrentIllnessAuthorityTest {
  private static final Instant NOW = Instant.parse("2026-08-02T20:40:00Z");

  private final ObjectMapper mapper = new ObjectMapper();
  private ClinicalCurrentIllnessAuthority authority;

  @BeforeEach
  void setUp() {
    authority = new ClinicalCurrentIllnessAuthority(
        mapper,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void primeraCargaDescartaAuditoriaFalsificadaYFirmaConLaSesion() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {"currentIllness": ""},
          "meta": {
            "sectionVersions": {"studies": [{"id": "study-trusted"}]},
            "sectionAudit": {"studies": {"action": "cargado"}},
            "sectionFormModes": {"studies": "structured"}
          }
        }
        """);
    JsonNode incoming = mapper.readTree("""
        {
          "narrative": {"currentIllness": "Cuadro de cuatro meses de evoluci\u00f3n"},
          "meta": {
            "sectionVersions": {
              "studies": [{"id": "study-trusted"}],
              "currentIllness": [{"id": "forged", "author": "Atacante"}]
            },
            "sectionAudit": {
              "studies": {"action": "cargado"},
              "currentIllness": {"action": "modificado", "lastName": "Atacante"}
            },
            "sectionFormModes": {"studies": "structured", "currentIllness": "forged"},
            "sectionChangeRequests": {
              "currentIllness": {"reason": "No se usa en primera carga"},
              "studies": {"reason": "Conservar"}
            },
            "currentProfessional": {"firstName": "Atacante", "custom": "preservar"},
            "currentUser": "Atacante"
          }
        }
        """);

    JsonNode result = authority.canonicalize(incoming, stored, principal());

    JsonNode versions = result.path("meta").path("sectionVersions").path("currentIllness");
    assertThat(versions).hasSize(1);
    assertThat(versions.get(0).path("id").asText()).startsWith("sec-currentIllness-");
    assertThat(versions.get(0).path("id").asText()).isNotEqualTo("forged");
    assertThat(versions.get(0).path("author").asText()).isEqualTo("Dra. Ana Segura");
    assertThat(versions.get(0).path("license").asText()).isEqualTo("MP-4455");
    assertThat(versions.get(0).path("reason").asText()).isEqualTo("Carga inicial");
    assertThat(versions.get(0).path("content").asText())
        .isEqualTo("Cuadro de cuatro meses de evoluci\u00f3n");
    assertThat(versions.get(0).path("createdAt").asText()).isEqualTo(NOW.toString());
    assertThat(versions.get(0).path("audit").path("action").asText()).isEqualTo("cargado");
    assertThat(result.path("meta").path("sectionAudit").path("currentIllness")
        .path("lastName").asText()).isEqualTo("Dra. Ana Segura");
    assertThat(result.path("meta").path("sectionFormModes").path("currentIllness").asText())
        .isEqualTo("structured");
    assertThat(result.path("meta").path("sectionVersions").path("studies").get(0)
        .path("id").asText()).isEqualTo("study-trusted");
    assertThat(result.path("meta").path("sectionChangeRequests").has("currentIllness"))
        .isFalse();
    assertThat(result.path("meta").path("sectionChangeRequests").path("studies")
        .path("reason").asText()).isEqualTo("Conservar");
    assertThat(result.path("meta").path("currentProfessional").path("firstName").asText())
        .isEqualTo("Dra. Ana Segura");
    assertThat(result.path("meta").path("currentProfessional").path("custom").asText())
        .isEqualTo("preservar");
    assertThat(result.path("meta").path("updatedAt").asText()).isEqualTo(NOW.toString());
  }

  @Test
  void modificacionConservaVersionesGuardadasYConsumeSoloSuComando() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {"currentIllness": "Dolor intermitente"},
          "meta": {"sectionVersions": {"currentIllness": [{
            "id": "trusted-initial",
            "content": "Dolor intermitente",
            "reason": "Carga inicial",
            "audit": {"action": "cargado", "lastName": "Dra. Original", "license": "MP-1", "at": "2026-07-01T10:00:00Z"}
          }]}}
        }
        """);
    JsonNode incoming = mapper.readTree("""
        {
          "narrative": {"currentIllness": "Dolor continuo"},
          "meta": {
            "sectionVersions": {"currentIllness": [{"id": "forged"}]},
            "sectionAudit": {"currentIllness": {"lastName": "forged"}},
            "sectionChangeRequests": {
              "currentIllness": {"reason": "  Progresi\u00f3n documentada  "},
              "chiefComplaint": {"reason": "Conservar"}
            }
          }
        }
        """);

    JsonNode result = authority.canonicalize(incoming, stored, principal());
    JsonNode versions = result.path("meta").path("sectionVersions").path("currentIllness");

    assertThat(versions).hasSize(2);
    assertThat(versions.get(0).path("id").asText()).isEqualTo("trusted-initial");
    assertThat(versions.get(1).path("id").asText()).startsWith("sec-currentIllness-");
    assertThat(versions.get(1).path("reason").asText()).isEqualTo("Progresi\u00f3n documentada");
    assertThat(versions.get(1).path("content").asText()).isEqualTo("Dolor continuo");
    assertThat(versions.get(1).path("audit").path("action").asText())
        .isEqualTo("modificado");
    assertThat(result.path("meta").path("sectionChangeRequests").has("currentIllness"))
        .isFalse();
    assertThat(result.path("meta").path("sectionChangeRequests").path("chiefComplaint")
        .path("reason").asText()).isEqualTo("Conservar");
  }

  @Test
  void borrarContenidoExistenteExigeMotivoYDejaVersionDocumentada() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {"currentIllness": "Cuadro previo"},
          "meta": {"sectionVersions": {"currentIllness": [{
            "id": "trusted-initial",
            "content": "Cuadro previo",
            "audit": {"action": "cargado", "at": "2026-07-01T10:00:00Z"}
          }]}}
        }
        """);

    assertCode(incoming("", null), stored, "CLINICAL_CURRENT_ILLNESS_REASON_REQUIRED");
    JsonNode result = authority.canonicalize(
        incoming("", "Correcci\u00f3n de carga"),
        stored,
        principal());

    JsonNode versions = result.path("meta").path("sectionVersions").path("currentIllness");
    assertThat(versions).hasSize(2);
    assertThat(versions.get(1).path("reason").asText()).isEqualTo("Correcci\u00f3n de carga");
    assertThat(versions.get(1).path("content").asText()).isEqualTo("Sin datos cargados.");
  }

  @Test
  void ausenciaLegacyYTextoVacioSinComandoNoCreanVersionEspuria() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {"chiefComplaint": "Anterior"},
          "meta": {"sectionVersions": {}, "sectionAudit": {}, "sectionFormModes": {}}
        }
        """);
    JsonNode incoming = mapper.readTree("""
        {
          "narrative": {"currentIllness": "", "chiefComplaint": "Actualizado"},
          "meta": {
            "sectionVersions": {"currentIllness": [{"id": "forged"}]},
            "sectionAudit": {"currentIllness": {"lastName": "forged"}},
            "sectionFormModes": {"currentIllness": "forged"},
            "sectionChangeRequests": {"currentIllness": {"reason": "forged"}}
          }
        }
        """);
    ((ObjectNode) incoming.path("meta").path("sectionChangeRequests"))
        .remove("currentIllness");

    JsonNode result = authority.canonicalize(incoming, stored, principal());

    assertThat(result.path("narrative").path("chiefComplaint").asText())
        .isEqualTo("Actualizado");
    assertThat(result.path("meta").path("sectionVersions").has("currentIllness")).isFalse();
    assertThat(result.path("meta").path("sectionAudit").has("currentIllness")).isFalse();
    assertThat(result.path("meta").path("sectionFormModes").has("currentIllness")).isFalse();
    assertThat(result.path("meta").has("sectionChangeRequests")).isFalse();
  }

  @Test
  void valorLegacySinCambioSePreservaYUnaMigracionRequiereMotivo() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {"currentIllness": {"texto": "Formato heredado"}},
          "meta": {
            "createdAt": "2025-01-02T03:04:05Z",
            "sectionVersions": {"currentIllness": [{"id": "trusted"}]},
            "sectionAudit": {"currentIllness": {"lastName": "Trusted"}},
            "sectionFormModes": {"currentIllness": "legacy"}
          }
        }
        """);
    JsonNode unchanged = mapper.readTree("""
        {
          "narrative": {"currentIllness": {"texto": "Formato heredado"}},
          "meta": {
            "sectionVersions": {"currentIllness": [{"id": "forged"}]},
            "sectionAudit": {"currentIllness": {"lastName": "Forged"}},
            "sectionFormModes": {"currentIllness": "forged"},
            "sectionChangeRequests": {"currentIllness": {"reason": "forged"}}
          }
        }
        """);

    JsonNode preserved = authority.canonicalize(unchanged, stored, principal());
    assertThat(preserved.path("narrative").path("currentIllness"))
        .isEqualTo(stored.path("narrative").path("currentIllness"));
    assertThat(preserved.path("meta").path("sectionVersions").path("currentIllness").get(0)
        .path("id").asText()).isEqualTo("trusted");
    assertThat(preserved.path("meta").path("sectionAudit").path("currentIllness")
        .path("lastName").asText()).isEqualTo("Trusted");
    assertThat(preserved.path("meta").path("sectionFormModes").path("currentIllness").asText())
        .isEqualTo("legacy");
    assertThat(preserved.path("meta").has("sectionChangeRequests")).isFalse();

    JsonNode legacyWithoutVersions = mapper.readTree("""
        {
          "narrative": {"currentIllness": {"texto": "Formato heredado"}},
          "meta": {"createdAt": "2025-01-02T03:04:05Z"}
        }
        """);
    assertCode(
        incoming("Historia normalizada", null),
        legacyWithoutVersions,
        "CLINICAL_CURRENT_ILLNESS_REASON_REQUIRED");

    JsonNode migrated = authority.canonicalize(
        incoming("Historia normalizada", "Migraci\u00f3n cl\u00ednica"),
        legacyWithoutVersions,
        principal());
    JsonNode versions = migrated.path("meta").path("sectionVersions").path("currentIllness");
    assertThat(versions).hasSize(2);
    assertThat(versions.get(0).path("id").asText()).endsWith("-initial");
    assertThat(versions.get(0).path("createdAt").asText()).isEqualTo("2025-01-02T03:04:05Z");
    assertThat(versions.get(0).path("content").asText()).isEqualTo("Sin datos cargados.");
    assertThat(versions.get(1).path("content").asText()).isEqualTo("Historia normalizada");
  }

  @Test
  void usaCodigosEstablesParaVacioYMotivoInvalido() throws Exception {
    JsonNode blank = mapper.readTree("{\"narrative\": {}}");
    assertCode(
        incoming("", "Carga inicial"),
        blank,
        "CLINICAL_CURRENT_ILLNESS_EMPTY");

    JsonNode stored = mapper.readTree("""
        {"narrative": {"currentIllness": "Anterior"}}
        """);
    ObjectNode invalidReason = incoming("Nuevo", null);
    invalidReason.withObject("/meta/sectionChangeRequests/currentIllness")
        .set("reason", mapper.createObjectNode());
    assertCode(invalidReason, stored, "CLINICAL_CURRENT_ILLNESS_REASON_INVALID");
    assertCode(
        incoming("Nuevo", "x".repeat(ClinicalCurrentIllnessAuthority.MAX_REASON_CHARS + 1)),
        stored,
        "CLINICAL_CURRENT_ILLNESS_REASON_TOO_LONG");
  }

  private ObjectNode incoming(String content, String reason) {
    ObjectNode root = mapper.createObjectNode();
    root.withObject("/narrative").put("currentIllness", content);
    if (reason != null) {
      root.withObject("/meta/sectionChangeRequests/currentIllness").put("reason", reason);
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
        "Oncolog\u00eda",
        "MP-4455",
        true,
        42L,
        List.of(),
        Set.of("section.history.view", "section.history.edit"));
  }
}
