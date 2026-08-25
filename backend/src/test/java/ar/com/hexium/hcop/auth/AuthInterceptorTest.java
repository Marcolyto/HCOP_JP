package ar.com.hexium.hcop.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * F2.8: quién resuelve el principal es {@code JwtAuthenticationFilter} — este interceptor solo
 * gatea autorización leyendo {@code AuthContext.PRINCIPAL_ATTRIBUTE}, que estos tests simulan
 * seteando el atributo directo en el request, tal como haría el filtro real antes de llegar acá.
 */
class AuthInterceptorTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final AuthInterceptor interceptor = new AuthInterceptor(mapper);

  @Test
  void rechazaAgenteAntesDelBindingSiLaSesionNoTienePermiso() throws Exception {
    MockHttpServletRequest request = agentRequest(Set.of());
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertThat(allowed).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    JsonNode body = mapper.readTree(response.getContentAsByteArray());
    assertThat(body.path("code").asText()).isEqualTo("PERMISSION_DENIED");
  }

  @Test
  void permiteAgenteAntesDelBindingConPermisoEspecifico() throws Exception {
    MockHttpServletRequest request = agentRequest(Set.of("section.agent.view"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertThat(allowed).isTrue();
  }

  @Test
  void conservaElRechazoDeSesionAusente() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/agent/chat");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertThat(allowed).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
    JsonNode body = mapper.readTree(response.getContentAsByteArray());
    assertThat(body.path("code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/protocols", "/api/protocols/detail"})
  void rechazaCatalogosCompatiblesAntesDelControladorSinPermiso(String path)
      throws Exception {
    MockHttpServletRequest request = authenticatedRequest("GET", path, Set.of());
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertThat(allowed).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    JsonNode body = mapper.readTree(response.getContentAsByteArray());
    assertThat(body.path("code").asText()).isEqualTo("PERMISSION_DENIED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/protocols", "/api/protocols/detail"})
  void permiteCatalogosCompatiblesConLecturaDeProtocolos(String path) throws Exception {
    MockHttpServletRequest request = authenticatedRequest("GET", path, Set.of("section.protocols.view"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertThat(allowed).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/ajcc8", "/api/ajcc8/detail"})
  void rechazaAjccAntesDelControladorSinLecturaDeHerramientas(String path) throws Exception {
    MockHttpServletRequest request = authenticatedRequest("GET", path, Set.of());
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertThat(allowed).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(mapper.readTree(response.getContentAsByteArray()).path("code").asText())
        .isEqualTo("PERMISSION_DENIED");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/ajcc8", "/api/ajcc8/detail"})
  void permiteAjccConLecturaDeHerramientas(String path) throws Exception {
    MockHttpServletRequest request = authenticatedRequest("GET", path, Set.of("section.tools.view"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertThat(allowed).isTrue();
  }

  @Test
  void rechazaCalculoAjccAntesDeMaterializarElBodySinPermisoDeUso() throws Exception {
    MockHttpServletRequest request = authenticatedRequest("POST", "/api/ajcc8/stage", Set.of("section.tools.view"));
    request.setContentType("application/json");
    request.setContent("{contenido-invalido".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertThat(allowed).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(mapper.readTree(response.getContentAsByteArray()).path("code").asText())
        .isEqualTo("PERMISSION_DENIED");
  }

  @Test
  void permiteCalculoAjccConPermisoDeUsoDeHerramientas() throws Exception {
    MockHttpServletRequest request = authenticatedRequest("POST", "/api/ajcc8/stage", Set.of("section.tools.use"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertThat(allowed).isTrue();
  }

  @Test
  void rechazaCatalogoOperativoDeCalculadorasSinPermisoDeUso() throws Exception {
    MockHttpServletRequest request =
        authenticatedRequest("GET", "/api/clinical/tools/calculators", Set.of("section.tools.view"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertThat(allowed).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(mapper.readTree(response.getContentAsByteArray()).path("code").asText())
        .isEqualTo("PERMISSION_DENIED");
  }

  @Test
  void permiteCatalogoOperativoDeCalculadorasConPermisoDeUso() throws Exception {
    MockHttpServletRequest request =
        authenticatedRequest("GET", "/api/clinical/tools/calculators", Set.of("section.tools.use"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertThat(allowed).isTrue();
  }

  @Test
  void logoutEsPublicoAunqueNoHayaSesionResuelta() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
    MockHttpServletResponse response = new MockHttpServletResponse();

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertThat(allowed).isTrue();
  }

  @Test
  void loginYRefreshSonPublicos() throws Exception {
    MockHttpServletRequest login = new MockHttpServletRequest("POST", "/api/auth/login");
    MockHttpServletRequest refresh = new MockHttpServletRequest("POST", "/api/auth/refresh");

    assertThat(interceptor.preHandle(login, new MockHttpServletResponse(), new Object())).isTrue();
    assertThat(interceptor.preHandle(refresh, new MockHttpServletResponse(), new Object())).isTrue();
  }

  private MockHttpServletRequest agentRequest(Set<String> permissions) {
    return authenticatedRequest("POST", "/api/agent/chat", permissions);
  }

  private MockHttpServletRequest authenticatedRequest(String method, String path, Set<String> permissions) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.setAttribute(AuthContext.PRINCIPAL_ATTRIBUTE, principal(permissions));
    return request;
  }

  private SessionPrincipal principal(Set<String> permissions) {
    return new SessionPrincipal(
        7L,
        "qa-user",
        "",
        "Usuario QA",
        "",
        "",
        true,
        null,
        List.of(),
        permissions);
  }
}
