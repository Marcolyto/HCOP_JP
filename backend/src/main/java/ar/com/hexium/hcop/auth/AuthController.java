package ar.com.hexium.hcop.auth;

import ar.com.hexium.hcop.auth.AuthService.JwtLoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token Handler JWT (F2.8 — el modo cookie se eliminó, {@code local_sessions} ya no existe:
 * {@code V014}). El navegador nunca habla directo con este controller — es {@code hcop-bff}
 * quien guarda access+refresh y arma la cookie {@code BFF_SESSION} (ver {@code base/03-bff.md}
 * y F2.7.5).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;
  private final AuthContext context;

  public AuthController(AuthService auth, AuthContext context) {
    this.auth = auth;
    this.context = context;
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
  Map<String, Object> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
    JwtLoginResult result = auth.login(
        body.identifier(), body.password(), clientAddress(request), request.getHeader("User-Agent"));
    return jwtResponse(result);
  }

  /** No expuesto al navegador — lo consume el BFF server-to-server. */
  @PostMapping("/refresh")
  Map<String, Object> refresh(@Valid @RequestBody RefreshRequest body, HttpServletRequest request) {
    JwtLoginResult result = auth.refresh(
        body.refreshToken(), clientAddress(request), request.getHeader("User-Agent"));
    return jwtResponse(result);
  }

  @PostMapping("/logout")
  Map<String, Object> logout(@RequestBody(required = false) LogoutRequest body) {
    auth.logout(body == null ? "" : body.refreshToken());
    return Map.of("ok", true, "authenticated", false);
  }

  @PutMapping("/password")
  Map<String, Object> password(
      @Valid @RequestBody PasswordRequest body,
      HttpServletRequest request) {
    auth.changePassword(
        context.require(request),
        context.sessionId(request),
        body.currentPassword(),
        body.newPassword());
    return Map.of("ok", true);
  }

  @PutMapping("/active-patient")
  Map<String, Object> activePatient(
      @RequestBody ActivePatientRequest body,
      HttpServletRequest request) {
    context.require(request);
    auth.setActivePatient(context.sessionId(request), body.patientId());
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

  /** {@code session} anida el mismo objeto que {@link #response}: cero shaping en el BFF, cero
   * divergencia del contrato (desvío consciente del doc base — ver docs de decisiones F2). */
  private Map<String, Object> jwtResponse(JwtLoginResult result) {
    Instant now = Instant.now();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("ok", true);
    body.put("accessToken", result.access().token());
    body.put("refreshToken", result.refresh().token());
    body.put("expiresIn", Duration.between(now, result.access().expiresAt()).toSeconds());
    body.put("refreshExpiresIn", Duration.between(now, result.refresh().expiresAt()).toSeconds());
    body.put("session", response(result.principal()));
    return body;
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

  public record RefreshRequest(@NotBlank String refreshToken) {
  }

  public record LogoutRequest(String refreshToken) {
  }
}
