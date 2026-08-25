package ar.com.hexium.hcop.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Valida hallazgo 9 del plan: jjwt 0.12.6 (con jjwt-gson, ver pom.xml) firma y parsea JWT
 * correctamente conviviendo con Jackson 3 (tools.jackson) en el classpath del proyecto.
 */
class TokenIssuerTest {
  private final JwtProperties properties = new JwtProperties("s".repeat(32), "hcop-test");
  private final TokenIssuer issuer = new TokenIssuer(properties);

  private final SessionPrincipal principal = new SessionPrincipal(
      7L, "marcolyto", "marcolyto@hcop.invalid", "Marco Lyto", "Oncología", "MP-123", true,
      null,
      List.of(new SessionPrincipal.RoleView("1", "administrator", "Administrador")),
      Set.of("section.tools.view", "section.tools.use"));

  @Test
  void issuesTokenAndParsesItsOwnClaims() {
    TokenIssuer.IssuedToken issued = issuer.issueAccessToken(principal, "sid-123", Duration.ofMinutes(15));

    assertThat(issued.token()).isNotBlank();
    var claims = issuer.parse(issued.token()).orElseThrow();
    assertThat(claims.userId()).isEqualTo(7L);
    assertThat(claims.sid()).isEqualTo("sid-123");
    assertThat(claims.roles()).containsExactly("administrator");
    assertThat(claims.permissions()).containsExactlyInAnyOrder("section.tools.view", "section.tools.use");
    assertThat(claims.expiresAt()).isAfter(java.time.Instant.now());
  }

  @Test
  void rejectsTamperedToken() {
    TokenIssuer.IssuedToken issued = issuer.issueAccessToken(principal, "sid-123", Duration.ofMinutes(15));
    String tampered = issued.token().substring(0, issued.token().length() - 2) + "xx";

    assertThat(issuer.parse(tampered)).isEmpty();
  }

  @Test
  void rejectsTokenSignedWithAnotherSecret() {
    TokenIssuer other = new TokenIssuer(new JwtProperties("t".repeat(32), "hcop-test"));
    TokenIssuer.IssuedToken issued = other.issueAccessToken(principal, "sid-123", Duration.ofMinutes(15));

    assertThat(issuer.parse(issued.token())).isEmpty();
  }

  @Test
  void rejectsExpiredToken() {
    TokenIssuer.IssuedToken issued = issuer.issueAccessToken(principal, "sid-123", Duration.ofMillis(1));

    await();
    assertThat(issuer.parse(issued.token())).isEmpty();
  }

  @Test
  void rejectsBlankToken() {
    assertThat(issuer.parse("")).isEmpty();
    assertThat(issuer.parse(null)).isEmpty();
  }

  private void await() {
    try {
      Thread.sleep(20);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}
