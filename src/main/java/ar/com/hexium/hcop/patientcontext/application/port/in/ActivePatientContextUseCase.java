package ar.com.hexium.hcop.patientcontext.application.port.in;

/**
 * Mantiene el paciente abierto por una sesión sin acoplar la regla de negocio a HTTP o JDBC.
 */
public interface ActivePatientContextUseCase {
  void select(SelectCommand command);

  record SelectCommand(String sessionToken, Long patientId) {
  }
}
