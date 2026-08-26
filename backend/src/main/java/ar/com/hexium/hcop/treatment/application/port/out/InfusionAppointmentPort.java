package ar.com.hexium.hcop.treatment.application.port.out;

import java.time.Instant;
import java.util.List;

/**
 * F3.3.0 (puertos cruzados): rompe la dependencia directa de {@code treatment} hacia
 * {@code infusion.domain.Infusion} — {@code TreatmentDocumentService.treatmentSheet}
 * solo necesita los 5 campos de {@link InfusionAppointment}, no el registro completo de
 * {@code infusion}. Implementado por un adapter que vive en {@code infusion}.
 */
public interface InfusionAppointmentPort {
  List<InfusionAppointment> forCycle(long patientId, String treatmentId, int cycleNumber);

  record InfusionAppointment(
      Instant scheduledAt, String chair, String clinicalStatus, String pharmacyStatus,
      String administrationStatus) {
  }
}
