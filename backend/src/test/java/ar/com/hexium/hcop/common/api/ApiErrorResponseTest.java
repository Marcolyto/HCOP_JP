package ar.com.hexium.hcop.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ApiErrorResponseTest {
  private final JsonMapper mapper = JsonMapper.builder().build();

  @Test
  void conservaElContratoHistoricoCuandoNoExisteUnCodigoEstable() {
    JsonNode json = mapper.valueToTree(ApiErrorResponse.of(400, "Solicitud inválida."));

    assertThat(json.get("ok").booleanValue()).isFalse();
    assertThat(json.get("error").stringValue()).isEqualTo("Solicitud inválida.");
    assertThat(json.has("code")).isFalse();
    assertThat(json.get("status").intValue()).isEqualTo(400);
  }

  @Test
  void publicaElCodigoEstableCuandoEstaDisponible() {
    JsonNode json = mapper.valueToTree(
        ApiErrorResponse.of(503, "El servicio LLM está desactivado.", "LLM_DISABLED"));

    assertThat(json.get("code").stringValue()).isEqualTo("LLM_DISABLED");
    assertThat(json.get("status").intValue()).isEqualTo(503);
  }

  @Test
  void rechazaEstadosQueNoRepresentanErroresHttp() {
    assertThatThrownBy(() -> ApiErrorResponse.of(200, "No corresponde."))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void laRespuestaDeAutenticacionConservaCompatibilidadYAgregaContratoEstable() {
    JsonNode json = mapper.valueToTree(AuthenticationRequiredResponse.required());

    assertThat(json.get("ok").booleanValue()).isFalse();
    assertThat(json.get("authenticated").booleanValue()).isFalse();
    assertThat(json.get("loginRequired").booleanValue()).isTrue();
    assertThat(json.get("error").stringValue()).isEqualTo("Debe iniciar sesión.");
    assertThat(json.get("code").stringValue()).isEqualTo("AUTHENTICATION_REQUIRED");
    assertThat(json.get("status").intValue()).isEqualTo(401);
  }
}
