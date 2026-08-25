package ar.com.hexium.hcop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Component;

class JwtAuthenticationFilterTest {
  private final TokenIssuer tokens = mock(TokenIssuer.class);
  private final SessionStateRepository sessions = mock(SessionStateRepository.class);
  private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens, sessions, true);

  private final TokenIssuer.AccessTokenClaims claims = new TokenIssuer.AccessTokenClaims(
      7L, UUID.randomUUID().toString(), "marcolyto", "marcolyto@hcop.invalid", "Marco Lyto",
      "Oncología", "MP-123", true,
      List.of(new SessionPrincipal.RoleView("1", "administrator", "Administrador")),
      List.of("section.tools.view"), Instant.now().plusSeconds(900));

  /** Trap del plan (F2.6): con @Component, Boot registraría este filtro dos veces (component
   * scan + el FilterRegistrationBean manual de SecurityConfiguration) y toda request con Bearer
   * válido termina en 401 sin error visible. Guarda la invariante sin levantar un contexto Spring
   * completo. */
  @Test
  void noEstaAnotadoComoComponentParaNoRegistrarseDosVeces() {
    assertThat(JwtAuthenticationFilter.class.getAnnotation(Component.class)).isNull();
  }

  @Test
  void puebaLosAtributosDeSesionConUnJwtValidoYSesionViva() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer valid-jwt");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    UUID sid = UUID.fromString(claims.sid());
    when(tokens.parse("valid-jwt")).thenReturn(Optional.of(claims));
    when(sessions.find(sid)).thenReturn(
        Optional.of(new SessionStateRepository.SessionState(sid, 7L, 99L, false)));

    filter.doFilter(request, response, chain);

    SessionPrincipal principal = (SessionPrincipal) request.getAttribute(AuthContext.PRINCIPAL_ATTRIBUTE);
    assertThat(principal.userId()).isEqualTo(7L);
    assertThat(principal.activePatientId()).isEqualTo(99L);
    assertThat(principal.permissions()).isEqualTo(Set.of("section.tools.view"));
    assertThat(request.getAttribute(AuthContext.SESSION_ID_ATTRIBUTE)).isEqualTo(claims.sid());
    verify(chain).doFilter(request, response);
  }

  @Test
  void noPueblaNadaSinEncabezadoAuthorization() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(request.getAttribute(AuthContext.PRINCIPAL_ATTRIBUTE)).isNull();
    verify(chain).doFilter(any(), any());
  }

  @Test
  void noPueblaNadaSiElTokenNoEsUnJwtValido() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer garbage");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    when(tokens.parse("garbage")).thenReturn(Optional.empty());

    filter.doFilter(request, response, chain);

    assertThat(request.getAttribute(AuthContext.PRINCIPAL_ATTRIBUTE)).isNull();
    verify(chain).doFilter(any(), any());
  }

  @Test
  void noPueblaNadaSiLaSesionNoExisteEnLocalSessionState() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer valid-jwt");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    UUID sid = UUID.fromString(claims.sid());
    when(tokens.parse("valid-jwt")).thenReturn(Optional.of(claims));
    when(sessions.find(sid)).thenReturn(Optional.empty());

    filter.doFilter(request, response, chain);

    assertThat(request.getAttribute(AuthContext.PRINCIPAL_ATTRIBUTE)).isNull();
    verify(chain).doFilter(any(), any());
  }

  @Test
  void noPueblaNadaSiLaSesionEstaRevocadaConElChequeoActivo() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer valid-jwt");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    UUID sid = UUID.fromString(claims.sid());
    when(tokens.parse("valid-jwt")).thenReturn(Optional.of(claims));
    when(sessions.find(sid)).thenReturn(
        Optional.of(new SessionStateRepository.SessionState(sid, 7L, null, true)));

    filter.doFilter(request, response, chain);

    assertThat(request.getAttribute(AuthContext.PRINCIPAL_ATTRIBUTE)).isNull();
    verify(chain).doFilter(any(), any());
  }

  @Test
  void ignoraSesionRevocadaSiElChequeoEstaDesactivado() throws Exception {
    JwtAuthenticationFilter filterSinChequeo = new JwtAuthenticationFilter(tokens, sessions, false);
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer valid-jwt");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    UUID sid = UUID.fromString(claims.sid());
    when(tokens.parse("valid-jwt")).thenReturn(Optional.of(claims));
    when(sessions.find(sid)).thenReturn(
        Optional.of(new SessionStateRepository.SessionState(sid, 7L, null, true)));

    filterSinChequeo.doFilter(request, response, chain);

    assertThat(request.getAttribute(AuthContext.PRINCIPAL_ATTRIBUTE)).isNotNull();
  }

  @Test
  void ignoraUnEncabezadoAuthorizationSinEsquemaBearer() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Basic dXNlcjpwYXNz");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);

    filter.doFilter(request, response, chain);

    assertThat(request.getAttribute(AuthContext.PRINCIPAL_ATTRIBUTE)).isNull();
    verify(chain).doFilter(any(), any());
  }
}
