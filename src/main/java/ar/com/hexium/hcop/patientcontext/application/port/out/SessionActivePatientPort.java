package ar.com.hexium.hcop.patientcontext.application.port.out;

import ar.com.hexium.hcop.patientcontext.domain.ActivePatientId;
import java.time.Instant;

/** Puerto para persistir el paciente abierto en una sesión autenticada. */
public interface SessionActivePatientPort {
  void assign(String sessionToken, ActivePatientId patientId, Instant occurredAt);
}
