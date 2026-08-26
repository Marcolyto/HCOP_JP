package ar.com.hexium.hcop.treatment.domain;

/**
 * Regla de dominio pura (sin Jackson) — el rango válido de "día de aplicación" de Hospital de
 * Día. Las reglas que sí navegan el JSON del protocolo ({@code requiresDayHospital}/
 * {@code applicationDays}) viven en
 * {@code treatment.infrastructure.legacy.DayHospitalProtocolRules} porque {@code domain} no
 * puede importar {@code tools.jackson} — pero este fragmento es genuinamente puro y lo consumen
 * directo otros módulos ya hexagonales (ej. {@code qr.application.service.QrApplicationService}),
 * que tampoco pueden depender de infraestructura de otro módulo (regla incondicional
 * {@code applicationDoesNotDependOnInfrastructure}).
 */
public final class DayHospitalApplicationPolicy {
  public static final int MAX_APPLICATION_DAY = 3650;

  private DayHospitalApplicationPolicy() {
  }

  public static boolean isValidApplicationDay(int day) {
    return day >= 1 && day <= MAX_APPLICATION_DAY;
  }
}
