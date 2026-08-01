package ar.com.hexium.hcop.auth;

import ar.com.hexium.hcop.auth.application.port.in.AuthenticationUseCase;
import ar.com.hexium.hcop.auth.application.service.AuthenticationFailure;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.patientcontext.application.port.in.ActivePatientContextUseCase;
import ar.com.hexium.hcop.patientcontext.application.service.ActivePatientContextFailure;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adaptador de compatibilidad de las rutas heredadas hacia los casos de uso de autenticación. */
@Service
public class AuthService {
  private final AuthenticationUseCase authentication;
  private final ActivePatientContextUseCase activePatientContext;
  private final String bootstrapUsername;
  private final String bootstrapPassword;
  private final String bootstrapEmail;
  private final String secondaryUsername;

  public AuthService(
      AuthenticationUseCase authentication,
      ActivePatientContextUseCase activePatientContext,
      @Value("${HCOP_BOOTSTRAP_USERNAME:${HCOP_BOOTSTRAP_USER:marcolyto}}") String bootstrapUsername,
      @Value("${HCOP_BOOTSTRAP_PASSWORD:}") String bootstrapPassword,
      @Value("${HCOP_BOOTSTRAP_EMAIL:local@hcop.invalid}") String bootstrapEmail,
      @Value("${HCOP_BOOTSTRAP_SECOND_USERNAME:marcolyto2}") String secondaryUsername) {
    this.authentication = authentication;
    this.activePatientContext = activePatientContext;
    this.bootstrapUsername = bootstrapUsername;
    if (bootstrapPassword == null || bootstrapPassword.length() < 10) {
      throw new IllegalStateException(
          "HCOP_BOOTSTRAP_PASSWORD es obligatorio y debe tener al menos 10 caracteres.");
    }
    this.bootstrapPassword = bootstrapPassword;
    this.bootstrapEmail = bootstrapEmail;
    this.secondaryUsername = secondaryUsername;
  }

  @Transactional
  public void bootstrapAdministrator() {
    try {
      authentication.bootstrap(new AuthenticationUseCase.BootstrapCommand(
          bootstrapUsername, bootstrapPassword, bootstrapEmail, secondaryUsername));
    } catch (AuthenticationFailure failure) {
      throw new IllegalStateException(failure.getMessage(), failure);
    }
  }

  @Transactional
  public LoginResult login(String identifier, String password, String clientAddress, String userAgent) {
    try {
      AuthenticationUseCase.LoginResult result = authentication.login(
          new AuthenticationUseCase.LoginCommand(identifier, password, clientAddress, userAgent));
      return new LoginResult(result.token(), result.expiresAt(), result.principal());
    } catch (AuthenticationFailure failure) {
      throw translateAuthenticationFailure(failure);
    }
  }

  @Transactional
  public Optional<SessionPrincipal> authenticate(String token) {
    return authentication.authenticate(token);
  }

  @Transactional
  public void logout(String token) {
    authentication.logout(token);
  }

  @Transactional
  public void changePassword(SessionPrincipal principal, String token, String currentPassword, String newPassword) {
    try {
      authentication.changePassword(new AuthenticationUseCase.ChangePasswordCommand(
          principal, token, currentPassword, newPassword));
    } catch (AuthenticationFailure failure) {
      throw translateAuthenticationFailure(failure);
    }
  }

  @Transactional
  public void setActivePatient(String token, Long patientId) {
    try {
      activePatientContext.select(new ActivePatientContextUseCase.SelectCommand(token, patientId));
    } catch (ActivePatientContextFailure failure) {
      if (failure.reason() == ActivePatientContextFailure.Reason.PATIENT_NOT_FOUND) {
        throw new ApiException(HttpStatus.NOT_FOUND, failure.getMessage());
      }
      throw new ApiException(HttpStatus.UNAUTHORIZED, failure.getMessage());
    }
  }

  private ApiException translateAuthenticationFailure(AuthenticationFailure failure) {
    HttpStatus status = failure.reason() == AuthenticationFailure.Reason.INVALID_NEW_PASSWORD
        ? HttpStatus.BAD_REQUEST
        : HttpStatus.UNAUTHORIZED;
    return new ApiException(status, failure.getMessage());
  }

  public record LoginResult(String token, Instant expiresAt, SessionPrincipal principal) {
  }
}
