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

class ClinicalChiefComplaintAuthorityTest {
  private static final Instant NOW = Instant.parse("2026-08-02T20:10:00Z");

  private final ObjectMapper mapper = new ObjectMapper();
  private ClinicalChiefComplaintAuthority authority;

  @BeforeEach
  void setUp() {
    authority = new ClinicalChiefComplaintAuthority(
        mapper,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void primeraCargaIgnoraAuditoriaDelClienteYFirmaConLaSesion() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {"chiefComplaint": ""},
          "meta": {
            "sectionVersions": {"studies": [{"id": "study-trusted"}]},
            "sectionAudit": {"studies": {"action": "cargado"}},
            "sectionFormModes": {"studies": "structured"}
          }
        }
        """);
    JsonNode incoming = mapper.readTree("""
        {
          "narrative": {"chiefComplaint": "Dolor de tres semanas de evoluci\u00f3n"},
          "meta": {
            "sectionVersions": {
              "studies": [{"id": "study-trusted"}],
              "chiefComplaint": [{"id": "forged", "author": "Atacante"}]
            },
            "sectionAudit": {
              "studies": {"action": "cargado"},
              "chiefComplaint": {"action": "modificado", "lastName": "Atacante"}
            },
            "sectionFormModes": {"studies": "structured", "chiefComplaint": "forged"},
            "sectionChangeRequests": {
              "chiefComplaint": {"reason": "No se usa en primera carga"},
              "studies": {"reason": "Conservar"}
            },
            "currentProfessional": {"firstName": "Atacante", "custom": "preservar"},
            "currentUser": "Atacante",
            "updatedAt": "1900-01-01T00:00:00Z"
          }
        }
        """);

    JsonNode result = authority.canonicalize(incoming, stored, principal());

    JsonNode versions = result.path("meta").path("sectionVersions").path("chiefComplaint");
    assertThat(versions).hasSize(1);
    assertThat(versions.get(0).path("id").asText()).startsWith("sec-chiefComplaint-");
    assertThat(versions.get(0).path("id").asText()).isNotEqualTo("forged");
    assertThat(versions.get(0).path("author").asText()).isEqualTo("Dra. Ana Segura");
    assertThat(versions.get(0).path("license").asText()).isEqualTo("MP-4455");
    assertThat(versions.get(0).path("reason").asText()).isEqualTo("Carga inicial");
    assertThat(versions.get(0).path("content").asText())
        .isEqualTo("Dolor de tres semanas de evoluci\u00f3n");
    assertThat(versions.get(0).path("createdAt").asText()).isEqualTo(NOW.toString());
    assertThat(versions.get(0).path("audit").path("action").asText()).isEqualTo("cargado");
    assertThat(result.path("meta").path("sectionAudit").path("chiefComplaint")
        .path("lastName").asText()).isEqualTo("Dra. Ana Segura");
    assertThat(result.path("meta").path("sectionFormModes").path("chiefComplaint").asText())
        .isEqualTo("structured");
    assertThat(result.path("meta").path("sectionVersions").path("studies").get(0)
        .path("id").asText()).isEqualTo("study-trusted");
    assertThat(result.path("meta").path("sectionChangeRequests").has("chiefComplaint")).isFalse();
    assertThat(result.path("meta").path("sectionChangeRequests").path("studies")
        .path("reason").asText()).isEqualTo("Conservar");
    assertThat(result.path("meta").path("currentProfessional").path("firstName").asText())
        .isEqualTo("Dra. Ana Segura");
    assertThat(result.path("meta").path("currentProfessional").path("custom").asText())
        .isEqualTo("preservar");
    assertThat(result.path("meta").path("updatedAt").asText()).isEqualTo(NOW.toString());
  }

  @Test
  void modificacionConservaSoloVersionesGuardadasYUsaMotivoTransitorio() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {"chiefComplaint": "Control programado"},
          "meta": {"sectionVersions": {"chiefComplaint": [{
            "id": "trusted-initial",
            "content": "Control programado",
            "reason": "Carga inicial",
            "audit": {"action": "cargado", "lastName": "Dra. Original", "license": "MP-1", "at": "2026-07-01T10:00:00Z"}
          }]}}
        }
        """);
    JsonNode incoming = mapper.readTree("""
        {
          "narrative": {"chiefComplaint": "Dolor abdominal"},
          "meta": {
            "sectionVersions": {"chiefComplaint": [{"id": "forged"}]},
            "sectionAudit": {"chiefComplaint": {"lastName": "forged"}},
            "sectionChangeRequests": {"chiefComplaint": {"reason": "  Cambio del cuadro  "}}
          }
        }
        """);

    JsonNode result = authority.canonicalize(incoming, stored, principal());
    JsonNode versions = result.path("meta").path("sectionVersions").path("chiefComplaint");

    assertThat(versions).hasSize(2);
    assertThat(versions.get(0).path("id").asText()).isEqualTo("trusted-initial");
    assertThat(versions.get(1).path("id").asText()).startsWith("sec-chiefComplaint-");
    assertThat(versions.get(1).path("reason").asText()).isEqualTo("Cambio del cuadro");
    assertThat(versions.get(1).path("content").asText()).isEqualTo("Dolor abdominal");
    assertThat(versions.get(1).path("audit").path("action").asText()).isEqualTo("modificado");
    assertThat(result.path("meta").has("sectionChangeRequests")).isFalse();
  }

  @Test
  void sinCambioRestauraMetadataProtegidaYConservaValorLegacy() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {"chiefComplaint": {"formato": "legacy"}},
          "meta": {
            "sectionVersions": {"chiefComplaint": [{"id": "trusted"}], "studies": [{"id": "old"}]},
            "sectionAudit": {"chiefComplaint": {"lastName": "Trusted"}},
            "sectionFormModes": {"chiefComplaint": "legacy"}
          }
        }
        """);
    JsonNode incoming = mapper.readTree("""
        {
          "narrative": {"chiefComplaint": {"formato": "legacy"}, "currentIllness": "Cambio permitido"},
          "meta": {
            "sectionVersions": {"chiefComplaint": [{"id": "forged"}], "studies": [{"id": "new"}]},
            "sectionAudit": {"chiefComplaint": {"lastName": "Forged"}},
            "sectionFormModes": {"chiefComplaint": "forged"},
            "sectionChangeRequests": {"chiefComplaint": {"reason": "forged"}, "studies": {"reason": "ok"}}
          }
        }
        """);

    JsonNode result = authority.canonicalize(incoming, stored, principal());

    assertThat(result.path("narrative").path("chiefComplaint"))
        .isEqualTo(stored.path("narrative").path("chiefComplaint"));
    assertThat(result.path("meta").path("sectionVersions").path("chiefComplaint").get(0)
        .path("id").asText()).isEqualTo("trusted");
    assertThat(result.path("meta").path("sectionAudit").path("chiefComplaint")
        .path("lastName").asText()).isEqualTo("Trusted");
    assertThat(result.path("meta").path("sectionFormModes").path("chiefComplaint").asText())
        .isEqualTo("legacy");
    assertThat(result.path("meta").path("sectionVersions").path("studies").get(0)
        .path("id").asText()).isEqualTo("new");
    assertThat(result.path("meta").path("sectionChangeRequests").has("chiefComplaint")).isFalse();
    assertThat(result.path("meta").path("sectionChangeRequests").has("studies")).isTrue();
  }

  @Test
  void ausenciaLegacyYTextoVacioNoCreanUnaVersionEspuria() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {"currentIllness": "Anterior"},
          "meta": {"sectionVersions": {}, "sectionAudit": {}, "sectionFormModes": {}}
        }
        """);
    JsonNode incoming = mapper.readTree("""
        {
          "narrative": {"chiefComplaint": "", "currentIllness": "Actualizado"},
          "meta": {
            "sectionVersions": {"chiefComplaint": [{"id": "forged"}]},
            "sectionAudit": {"chiefComplaint": {"lastName": "forged"}},
            "sectionFormModes": {"chiefComplaint": "forged"}
          }
        }
        """);

    JsonNode result = authority.canonicalize(incoming, stored, principal());

    assertThat(result.path("narrative").path("currentIllness").asText())
        .isEqualTo("Actualizado");
    assertThat(result.path("meta").path("sectionVersions").has("chiefComplaint")).isFalse();
    assertThat(result.path("meta").path("sectionAudit").has("chiefComplaint")).isFalse();
    assertThat(result.path("meta").path("sectionFormModes").has("chiefComplaint")).isFalse();
    assertThat(result.path("meta").has("sectionChangeRequests")).isFalse();
  }

  @Test
  void migracionDesdeLegacyNoTextualExigeMotivoYConservaVersionInicial() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {"chiefComplaint": {"texto": "Formato heredado"}},
          "meta": {"createdAt": "2025-01-02T03:04:05Z"}
        }
        """);

    assertCode(incoming("Motivo normalizado", null), stored,
        "CLINICAL_CHIEF_COMPLAINT_REASON_REQUIRED");

    JsonNode result = authority.canonicalize(
        incoming("Motivo normalizado", "Migraci\u00f3n cl\u00ednica"),
        stored,
        principal());
    JsonNode versions = result.path("meta").path("sectionVersions").path("chiefComplaint");

    assertThat(versions).hasSize(2);
    assertThat(versions.get(0).path("id").asText()).endsWith("-initial");
    assertThat(versions.get(0).path("createdAt").asText()).isEqualTo("2025-01-02T03:04:05Z");
    assertThat(versions.get(0).path("content").asText()).isEqualTo("Sin datos cargados.");
    assertThat(versions.get(1).path("content").asText()).isEqualTo("Motivo normalizado");
    assertThat(versions.get(1).path("reason").asText()).isEqualTo("Migraci\u00f3n cl\u00ednica");
  }

  @Test
  void usaCodigosEstablesParaVacioYMotivoInvalido() throws Exception {
    JsonNode blank = mapper.readTree("{\"narrative\": {}}");
    assertCode(incoming("", "Carga inicial"), blank, "CLINICAL_CHIEF_COMPLAINT_EMPTY");

    JsonNode stored = mapper.readTree("""
        {"narrative": {"chiefComplaint": "Anterior"}}
        """);
    assertCode(incoming("Nuevo", null), stored,
        "CLINICAL_CHIEF_COMPLAINT_REASON_REQUIRED");

    ObjectNode invalidReason = incoming("Nuevo", null);
    invalidReason.withObject("/meta/sectionChangeRequests/chiefComplaint")
        .set("reason", mapper.createObjectNode());
    assertCode(invalidReason, stored, "CLINICAL_CHIEF_COMPLAINT_REASON_INVALID");

    assertCode(
        incoming("Nuevo", "x".repeat(ClinicalChiefComplaintAuthority.MAX_REASON_CHARS + 1)),
        stored,
        "CLINICAL_CHIEF_COMPLAINT_REASON_TOO_LONG");
  }

  private ObjectNode incoming(String content, String reason) {
    ObjectNode root = mapper.createObjectNode();
    root.withObject("/narrative").put("chiefComplaint", content);
    if (reason != null) {
      root.withObject("/meta/sectionChangeRequests/chiefComplaint").put("reason", reason);
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
