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

class ClinicalSummaryPlanAuthorityTest {
  private static final Instant NOW = Instant.parse("2026-08-02T18:45:00Z");

  private final ObjectMapper mapper = new ObjectMapper();
  private ClinicalSummaryPlanAuthority authority;

  @BeforeEach
  void setUp() {
    authority = new ClinicalSummaryPlanAuthority(
        mapper,
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void primeraCargaIgnoraAuditoriaDelClienteYFirmaConLaSesion() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {"summary": "", "plan": ""},
          "meta": {
            "sectionVersions": {"studies": [{"id": "study-trusted"}]},
            "sectionAudit": {"studies": {"action": "cargado"}},
            "sectionFormModes": {"studies": "structured"}
          }
        }
        """);
    JsonNode incoming = mapper.readTree("""
        {
          "narrative": {"summary": "Respuesta parcial", "plan": "Continuar controles"},
          "meta": {
            "sectionVersions": {
              "studies": [{"id": "study-trusted"}],
              "summaryPlan": [{"id": "forged", "author": "Atacante"}]
            },
            "sectionAudit": {
              "studies": {"action": "cargado"},
              "summaryPlan": {"action": "modificado", "lastName": "Atacante"}
            },
            "sectionFormModes": {"studies": "structured", "summaryPlan": "forged"},
            "sectionChangeRequests": {
              "summaryPlan": {"reason": "No debe usarse en la carga inicial"},
              "studies": {"reason": "Conservar"}
            },
            "currentProfessional": {"firstName": "Atacante", "custom": "preservar"},
            "currentUser": "Atacante",
            "updatedAt": "1900-01-01T00:00:00Z"
          }
        }
        """);

    JsonNode result = authority.canonicalize(incoming, stored, principal());

    JsonNode versions = result.path("meta").path("sectionVersions").path("summaryPlan");
    assertThat(versions).hasSize(1);
    assertThat(versions.get(0).path("id").asText()).startsWith("sec-summaryPlan-");
    assertThat(versions.get(0).path("id").asText()).isNotEqualTo("forged");
    assertThat(versions.get(0).path("author").asText()).isEqualTo("Dra. Ana Segura");
    assertThat(versions.get(0).path("license").asText()).isEqualTo("MP-4455");
    assertThat(versions.get(0).path("reason").asText()).isEqualTo("Carga inicial");
    assertThat(versions.get(0).path("createdAt").asText()).isEqualTo(NOW.toString());
    assertThat(versions.get(0).path("audit").path("action").asText()).isEqualTo("cargado");
    assertThat(result.path("meta").path("sectionAudit").path("summaryPlan").path("lastName").asText())
        .isEqualTo("Dra. Ana Segura");
    assertThat(result.path("meta").path("sectionFormModes").path("summaryPlan").asText())
        .isEqualTo("structured");
    assertThat(result.path("meta").path("sectionVersions").path("studies").get(0).path("id").asText())
        .isEqualTo("study-trusted");
    assertThat(result.path("meta").path("sectionChangeRequests").has("summaryPlan")).isFalse();
    assertThat(result.path("meta").path("sectionChangeRequests").path("studies").path("reason").asText())
        .isEqualTo("Conservar");
    assertThat(result.path("meta").path("currentProfessional").path("firstName").asText())
        .isEqualTo("Dra. Ana Segura");
    assertThat(result.path("meta").path("currentProfessional").path("custom").asText())
        .isEqualTo("preservar");
    assertThat(result.path("meta").path("updatedAt").asText()).isEqualTo(NOW.toString());
  }

  @Test
  void eliminaEspecialidadDelClienteCuandoLaSesionNoLaAporta() throws Exception {
    JsonNode stored = mapper.readTree("""
        {"narrative": {"summary": "", "plan": ""}}
        """);
    ObjectNode incoming = incoming("Resumen", "Plan", null);
    incoming.withObject("/meta/currentProfessional").put("specialty", "Especialidad falsificada");
    SessionPrincipal withoutSpecialty = new SessionPrincipal(
        77L,
        "ana.segura",
        "ana@example.test",
        "Dra. Ana Segura",
        "",
        "MP-4455",
        true,
        42L,
        List.of(),
        Set.of("section.history.view", "section.history.edit"));

    JsonNode result = authority.canonicalize(incoming, stored, withoutSpecialty);

    assertThat(result.path("meta").path("currentProfessional").has("specialty")).isFalse();
  }

  @Test
  void modificacionConservaSoloVersionesGuardadasYUsaMotivoTransitorio() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {"summary": "Resumen inicial", "plan": "Plan inicial"},
          "meta": {"sectionVersions": {"summaryPlan": [{
            "id": "trusted-initial",
            "content": "Contenido inicial",
            "reason": "Carga inicial",
            "audit": {"action": "cargado", "lastName": "Dra. Original", "license": "MP-1", "at": "2026-07-01T10:00:00Z"}
          }]}}
        }
        """);
    JsonNode incoming = mapper.readTree("""
        {
          "narrative": {"summary": "Resumen corregido", "plan": "Plan inicial"},
          "meta": {
            "sectionVersions": {"summaryPlan": [{"id": "forged"}]},
            "sectionAudit": {"summaryPlan": {"lastName": "forged"}},
            "sectionChangeRequests": {"summaryPlan": {"reason": "  Corrección documentada  "}}
          }
        }
        """);

    JsonNode result = authority.canonicalize(incoming, stored, principal());
    JsonNode versions = result.path("meta").path("sectionVersions").path("summaryPlan");

    assertThat(versions).hasSize(2);
    assertThat(versions.get(0).path("id").asText()).isEqualTo("trusted-initial");
    assertThat(versions.get(1).path("id").asText()).startsWith("sec-summaryPlan-");
    assertThat(versions.get(1).path("reason").asText()).isEqualTo("Corrección documentada");
    assertThat(versions.get(1).path("content").asText())
        .isEqualTo("Conclusion / resumen: Resumen corregido\nConducta / plan: Plan inicial");
    assertThat(versions.get(1).path("audit").path("action").asText()).isEqualTo("modificado");
    assertThat(result.path("meta").has("sectionChangeRequests")).isFalse();
  }

  @Test
  void retroversionaContenidoExistenteConFechaGuardada() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {"summary": "Resumen heredado", "plan": "Plan heredado"},
          "meta": {"createdAt": "2025-01-02T03:04:05Z", "sectionVersions": {}}
        }
        """);
    JsonNode incoming = mapper.readTree("""
        {
          "narrative": {"summary": "Resumen vigente", "plan": "Plan heredado"},
          "meta": {"sectionChangeRequests": {"summaryPlan": {"reason": "Corrección"}}}
        }
        """);

    JsonNode result = authority.canonicalize(incoming, stored, principal());
    JsonNode versions = result.path("meta").path("sectionVersions").path("summaryPlan");

    assertThat(versions).hasSize(2);
    assertThat(versions.get(0).path("id").asText()).endsWith("-initial");
    assertThat(versions.get(0).path("createdAt").asText()).isEqualTo("2025-01-02T03:04:05Z");
    assertThat(versions.get(0).path("content").asText())
        .isEqualTo("Conclusion / resumen: Resumen heredado\nConducta / plan: Plan heredado");
    assertThat(versions.get(1).path("reason").asText()).isEqualTo("Corrección");
  }

  @Test
  void sinCambioRestauraMetadataProtegidaYDescartaLaSolicitudTransitoria() throws Exception {
    JsonNode stored = mapper.readTree("""
        {
          "narrative": {"summary": "Vigente", "plan": "Plan"},
          "meta": {
            "sectionVersions": {"summaryPlan": [{"id": "trusted"}], "studies": [{"id": "study-old"}]},
            "sectionAudit": {"summaryPlan": {"lastName": "Trusted"}},
            "sectionFormModes": {"summaryPlan": "structured"}
          }
        }
        """);
    JsonNode incoming = mapper.readTree("""
        {
          "narrative": {"summary": "Vigente", "plan": "Plan"},
          "meta": {
            "sectionVersions": {"summaryPlan": [{"id": "forged"}], "studies": [{"id": "study-new"}]},
            "sectionAudit": {"summaryPlan": {"lastName": "Forged"}},
            "sectionFormModes": {"summaryPlan": "forged"},
            "sectionChangeRequests": {"summaryPlan": {"reason": "forged"}, "studies": {"reason": "ok"}}
          }
        }
        """);

    JsonNode result = authority.canonicalize(incoming, stored, principal());

    assertThat(result.path("meta").path("sectionVersions").path("summaryPlan").get(0).path("id").asText())
        .isEqualTo("trusted");
    assertThat(result.path("meta").path("sectionAudit").path("summaryPlan").path("lastName").asText())
        .isEqualTo("Trusted");
    assertThat(result.path("meta").path("sectionFormModes").path("summaryPlan").asText())
        .isEqualTo("structured");
    assertThat(result.path("meta").path("sectionVersions").path("studies").get(0).path("id").asText())
        .isEqualTo("study-new");
    assertThat(result.path("meta").path("sectionChangeRequests").has("summaryPlan")).isFalse();
    assertThat(result.path("meta").path("sectionChangeRequests").has("studies")).isTrue();
  }

  @Test
  void preservaValorLegacyNoTextualCuandoSoloCambiaElPlan() throws Exception {
    JsonNode stored = mapper.readTree("""
        {"narrative": {"summary": {"formato": "legacy"}, "plan": "Plan anterior"}}
        """);
    JsonNode incoming = mapper.readTree("""
        {
          "narrative": {"summary": {"formato": "legacy"}, "plan": "Plan actualizado"},
          "meta": {"sectionChangeRequests": {"summaryPlan": {"reason": "Cambio de conducta"}}}
        }
        """);

    JsonNode result = authority.canonicalize(incoming, stored, principal());

    assertThat(result.path("narrative").path("summary")).isEqualTo(stored.path("narrative").path("summary"));
    assertThat(result.path("meta").path("sectionVersions").path("summaryPlan")).hasSize(2);
    assertThat(result.path("meta").path("sectionVersions").path("summaryPlan").get(1).path("content").asText())
        .isEqualTo("Conducta / plan: Plan actualizado");
  }

  @Test
  void unResumenLegacyNoTextualNoSeConsideraHistoriaVacia() throws Exception {
    JsonNode stored = mapper.readTree("""
        {"narrative": {"summary": {"formato": "legacy"}, "plan": ""}}
        """);

    assertCode(
        incomingWithLegacySummary("Plan nuevo", null),
        stored,
        "CLINICAL_SUMMARY_PLAN_REASON_REQUIRED");

    JsonNode result = authority.canonicalize(
        incomingWithLegacySummary("Plan nuevo", "Motivo clínico"),
        stored,
        principal());
    assertThat(result.path("narrative").path("summary"))
        .isEqualTo(stored.path("narrative").path("summary"));
    assertThat(result.path("meta").path("sectionVersions").path("summaryPlan")).hasSize(2);
    assertThat(result.path("meta").path("sectionVersions").path("summaryPlan").get(0)
        .path("audit").path("action").asText()).isEqualTo("cargado");
    assertThat(result.path("meta").path("sectionVersions").path("summaryPlan").get(1)
        .path("audit").path("action").asText()).isEqualTo("modificado");
  }

  @Test
  void exigeMotivoTextualAcotadoEnModificaciones() throws Exception {
    JsonNode stored = mapper.readTree("""
        {"narrative": {"summary": "Anterior", "plan": "Plan"}}
        """);

    assertCode(
        incoming("Nuevo", "Plan", null),
        stored,
        "CLINICAL_SUMMARY_PLAN_REASON_REQUIRED");
    assertCode(
        incomingWithReasonNode("Nuevo", "Plan", mapper.createObjectNode()),
        stored,
        "CLINICAL_SUMMARY_PLAN_REASON_INVALID");
    assertCode(
        incoming("Nuevo", "Plan", "x".repeat(ClinicalSummaryPlanAuthority.MAX_REASON_CHARS + 1)),
        stored,
        "CLINICAL_SUMMARY_PLAN_REASON_TOO_LONG");
  }

  @Test
  void rechazaPrimeraCargaVacia() throws Exception {
    JsonNode stored = mapper.readTree("""
        {"narrative": {}}
        """);

    assertCode(
        incoming("", "", null),
        stored,
        "CLINICAL_SUMMARY_PLAN_EMPTY");
  }

  private ObjectNode incoming(String summary, String plan, String reason) {
    ObjectNode root = mapper.createObjectNode();
    root.withObject("/narrative").put("summary", summary).put("plan", plan);
    if (reason != null) {
      root.withObject("/meta/sectionChangeRequests/summaryPlan").put("reason", reason);
    }
    return root;
  }

  private ObjectNode incomingWithReasonNode(
      String summary,
      String plan,
      JsonNode reason) {
    ObjectNode root = incoming(summary, plan, null);
    root.withObject("/meta/sectionChangeRequests/summaryPlan").set("reason", reason);
    return root;
  }

  private ObjectNode incomingWithLegacySummary(String plan, String reason) {
    ObjectNode root = mapper.createObjectNode();
    root.withObject("/narrative").withObject("/summary").put("formato", "legacy");
    root.withObject("/narrative").put("plan", plan);
    if (reason != null) {
      root.withObject("/meta/sectionChangeRequests/summaryPlan").put("reason", reason);
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
