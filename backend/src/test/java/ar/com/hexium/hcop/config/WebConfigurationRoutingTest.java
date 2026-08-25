package ar.com.hexium.hcop.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WebConfigurationRoutingTest {
  @Test
  void rootAndHistoricalApplicationAliasesEnterTheNativeAngularFrontend() {
    Map<String, String> redirects = WebConfiguration.ANGULAR_ENTRY_REDIRECTS;

    assertEquals("/app/", redirects.get("/"));
    assertEquals("/app/", redirects.get("/index.html"));
    assertEquals("/app/", redirects.get("/app"));
    assertEquals("/app/#/configuration", redirects.get("/configuration"));
    assertEquals("/app/#/configuration", redirects.get("/configuration/index.html"));
    assertEquals(
        "/app/#/configuration?tab=protocols",
        redirects.get("/protocol-admin/index.html"));
    assertEquals("/app/#/herramientas", redirects.get("/herramientas/index.html"));
    assertTrue(redirects.values().stream().allMatch(target -> target.startsWith("/app/")));
    assertFalse(redirects.values().stream().anyMatch(target ->
        target.contains("configuration/index.html")
            || target.contains("protocol-admin/index.html")
            || target.contains("herramientas/index.html")));
  }

  @Test
  void technicalAndDocumentationRoutesAreNotCapturedByApplicationRedirects() {
    Map<String, String> redirects = WebConfiguration.ANGULAR_ENTRY_REDIRECTS;

    assertFalse(redirects.containsKey("/api"));
    assertFalse(redirects.containsKey("/api/clinical/status"));
    assertFalse(redirects.containsKey("/swagger-ui.html"));
    assertFalse(redirects.containsKey("/v3/api-docs"));
    assertFalse(redirects.containsKey("/actuator/health"));
    assertFalse(redirects.containsKey("/docs"));
    assertFalse(redirects.containsKey("/docs/"));
  }
}
