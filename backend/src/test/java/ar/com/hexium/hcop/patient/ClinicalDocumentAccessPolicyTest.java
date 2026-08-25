package ar.com.hexium.hcop.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ClinicalDocumentAccessPolicyTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final ClinicalDocumentAccessPolicy policy = new ClinicalDocumentAccessPolicy();

  @Test
  void ocultaEstudiosLocalesYExternosSinPermisoDeLecturaSinMutarElOriginal() {
    ObjectNode stored = stateWithStudies();

    JsonNode visible = policy.visibleState(
        stored,
        principal("section.history.view", "section.history.edit"));

    assertThat(visible.has("studies")).isFalse();
    assertThat(visible.has("externalStudies")).isFalse();
    assertThat(stored.path("studies")).hasSize(1);
    assertThat(stored.path("externalStudies")).hasSize(1);
  }

  @Test
  void conservaEstudiosOcultosAlGuardarOtraParteDeLaHistoria() {
    ObjectNode stored = stateWithStudies();
    ObjectNode incoming = mapper.createObjectNode();
    incoming.withObject("/narrative").put("summary", "Control sin cambios en estudios");

    JsonNode writable = policy.writableState(
        incoming,
        stored,
        principal("section.history.view", "section.history.edit"));

    assertThat(writable.path("studies")).isEqualTo(stored.path("studies"));
    assertThat(writable.path("externalStudies")).isEqualTo(stored.path("externalStudies"));
    assertThat(writable.path("narrative").path("summary").asText())
        .isEqualTo("Control sin cambios en estudios");
  }

  @Test
  void rechazaCambiarEstudiosLocalesSinPermisoDeEdicion() {
    ObjectNode stored = stateWithStudies();
    ObjectNode incoming = stored.deepCopy();
    incoming.withArray("studies").addObject().put("id", "local-2");

    assertThatThrownBy(() -> policy.writableState(
        incoming,
        stored,
        principal("section.history.view", "section.history.edit", "section.studies.view")))
        .isInstanceOfSatisfying(ApiException.class, error -> {
          assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(error.getMessage()).contains("estudios");
        });
  }

  @Test
  void rechazaCambiarEstudiosExternosSinPermisoDeEdicion() {
    ObjectNode stored = stateWithStudies();
    ObjectNode incoming = stored.deepCopy();
    incoming.withArray("externalStudies").removeAll();

    assertThatThrownBy(() -> policy.writableState(
        incoming,
        stored,
        principal("section.history.view", "section.history.edit", "section.studies.view")))
        .isInstanceOfSatisfying(ApiException.class, error -> {
          assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(error.getMessage()).contains("estudios");
        });
  }

  @Test
  void permiteCambiarAmbasColeccionesConLecturaYEdicionDeEstudios() {
    ObjectNode stored = stateWithStudies();
    ObjectNode incoming = stored.deepCopy();
    incoming.withArray("studies").addObject().put("id", "local-2");
    incoming.withArray("externalStudies").removeAll();

    JsonNode writable = policy.writableState(
        incoming,
        stored,
        principal(
            "section.history.view",
            "section.history.edit",
            "section.studies.view",
            "section.studies.edit"));

    assertThat(writable).isEqualTo(incoming);
    assertThat(writable.path("studies")).hasSize(2);
    assertThat(writable.path("externalStudies")).isEmpty();
  }

  @Test
  void rechazaUnaColeccionInyectadaSinLecturaNiEdicionDeEstudios() {
    ObjectNode stored = stateWithStudies();
    ObjectNode incoming = mapper.createObjectNode();
    incoming.putArray("studies").addObject().put("id", "inyectado");

    assertThatThrownBy(() -> policy.writableState(
        incoming,
        stored,
        principal("section.history.view", "section.history.edit")))
        .isInstanceOfSatisfying(ApiException.class, error ->
            assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN));
  }

  private ObjectNode stateWithStudies() {
    ObjectNode state = mapper.createObjectNode();
    state.putArray("studies").addObject().put("id", "local-1");
    state.putArray("externalStudies").addObject().put("id", "externo-1");
    return state;
  }

  private SessionPrincipal principal(String... permissions) {
    return new SessionPrincipal(
        7L,
        "profesional",
        "",
        "Profesional",
        "",
        "",
        true,
        42L,
        List.of(),
        Set.of(permissions));
  }
}
