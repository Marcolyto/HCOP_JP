package ar.com.hexium.hcop.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenApiConfigurationTest {

  @Test
  void registraLosContratosReutilizablesDeErrorYAutenticacion() {
    OpenAPI openApi = new OpenAPI();

    new OpenApiConfiguration().reusableSchemas().customise(openApi);

    assertThat(openApi.getComponents().getSchemas())
        .containsKeys("ApiError", "AuthenticationRequired");
    assertThat(openApi.getComponents().getSchemas().get("ApiError").getRequired())
        .containsExactlyInAnyOrder("ok", "error", "status");
    assertThat(openApi.getComponents().getSchemas().get("AuthenticationRequired").getRequired())
        .contains(
            "ok",
            "authenticated",
            "loginRequired",
            "error",
            "code",
            "status");
  }
}
