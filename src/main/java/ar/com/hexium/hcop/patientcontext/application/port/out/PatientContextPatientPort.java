package ar.com.hexium.hcop.patientcontext.application.port.out;

import ar.com.hexium.hcop.patientcontext.domain.ActivePatientId;

/** Puerto de consulta mínima que necesita el contexto de paciente. */
public interface PatientContextPatientPort {
  boolean exists(ActivePatientId patientId);
}
