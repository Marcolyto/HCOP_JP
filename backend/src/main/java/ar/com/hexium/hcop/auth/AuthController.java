package ar.com.hexium.hcop.auth;

import ar.com.hexium.hcop.auth.AuthService.LoginResult;
import ar.com.hexium.hcop.config.HcopProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;
  private final AuthContext context;
  private final HcopProperties properties;

  public AuthController(AuthService auth, AuthContext context, HcopProperties properties) {
    this.auth = auth;
    this.context = context;
    this.properties = properties;
  }

  @GetMapping("/me")
  Map<String, Object> me(HttpServletRequest request) {
    Object value = request.getAttribute(AuthContext.PRINCIPAL_ATTRIBUTE);
    if (!(value instanceof SessionPrincipal principal)) {
      return Map.of(
          "ok", true,
          "authenticated", false,
          "loginRequired", true,
          "autoLoginEnabled", false);
    }
    return response(principal);
  }

  @PostMapping("/login")
  Map<String, Object> login(
      @Valid @RequestBody LoginRequest body,
      HttpServletRequest request,
      HttpServletResponse response) {
    LoginResult result = auth.login(
        body.identifier(),
        body.password(),
        clientAddress(request),
        request.getHeader("User-Agent"));
    ResponseCookie cookie = ResponseCookie.from(properties.sessionCookieName(), result.token())
        .httpOnly(true)
        .sameSite("Strict")
        .secure(request.isSecure())
        .path("/")
        .maxAge(Duration.between(java.time.Instant.now(), result.expiresAt()))
        .build();
    response.addHeader("Set-Cookie", cookie.toString());
    return response(result.principal());
  }

  @PostMapping("/logout")
  Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response) {
    auth.logout(context.token(request));
    expireCookie(request, response);
    return Map.of("ok", true, "authenticated", false);
  }

  @PutMapping("/password")
  Map<String, Object> password(
      @Valid @RequestBody PasswordRequest body,
      HttpServletRequest request) {
    auth.changePassword(
        context.require(request),
        context.token(request),
        body.currentPassword(),
        body.newPassword());
    return Map.of("ok", true);
  }

  @PutMapping("/active-patient")
  Map<String, Object> activePatient(
      @RequestBody ActivePatientRequest body,
      HttpServletRequest request) {
    context.require(request);
    auth.setActivePatient(context.token(request), body.patientId());
    return Map.of("ok", true, "activePatientId", body.patientId() == null ? "" : body.patientId().toString());
  }

  private Map<String, Object> response(SessionPrincipal principal) {
    Map<String, Object> user = new LinkedHashMap<>();
    user.put("id", Long.toString(principal.userId()));
    user.put("username", principal.username());
    user.put("email", principal.email());
    user.put("displayName", principal.displayName());
    user.put("specialty", principal.specialty());
    user.put("licenseNumber", principal.licenseNumber());
    user.put("active", principal.active());
    user.put("roles", principal.roles());
    user.put("permissions", principal.permissions().stream().sorted().toList());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("ok", true);
    result.put("authenticated", true);
    result.put("loginRequired", true);
    result.put("autoLoginEnabled", false);
    result.put("user", user);
    result.put("activePatientId", principal.activePatientId() == null ? null : principal.activePatientId().toString());
    return result;
  }

  private void expireCookie(HttpServletRequest request, HttpServletResponse response) {
    ResponseCookie cookie = ResponseCookie.from(properties.sessionCookieName(), "")
        .httpOnly(true)
        .sameSite("Strict")
        .secure(request.isSecure())
        .path("/")
        .maxAge(Duration.ZERO)
        .build();
    response.addHeader("Set-Cookie", cookie.toString());
  }

  private String clientAddress(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",", 2)[0].trim();
    return request.getRemoteAddr();
  }

  public record LoginRequest(
      String username,
      String email,
      @NotBlank @Size(max = 256) String password) {
    String identifier() {
      if (username != null && !username.isBlank()) return username.trim();
      return email == null ? "" : email.trim();
    }
  }

  public record PasswordRequest(
      @NotBlank String currentPassword,
      @NotBlank @Size(min = 10, max = 256) String newPassword) {
  }

  public record ActivePatientRequest(Long patientId) {
  }
}
