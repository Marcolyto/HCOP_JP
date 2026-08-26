package ar.com.hexium.hcop.diagnosis.application.service;

/**
 * Error funcional del módulo, independiente de HTTP. El 404 de paciente/historia inexistente no
 * es una regla de este módulo — sigue viajando como {@code ApiException} sin traducir desde
 * {@code PatientDiagnosisPort} (ver su javadoc).
 */
public final class DiagnosisFailure extends RuntimeException {
  private final Type type;
  private final String code;

  public DiagnosisFailure(Type type, String message) {
    this(type, message, "");
  }

  public DiagnosisFailure(Type type, String message, String code) {
    super(message);
    this.type = type;
    this.code = code == null ? "" : code;
  }

  public Type type() {
    return type;
  }

  public String code() {
    return code;
  }

  public enum Type {
    CONFLICT,
    UNPROCESSABLE
  }
}
