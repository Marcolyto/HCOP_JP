package ar.com.hexium.hcop.infusion.application.port.out;

import ar.com.hexium.hcop.infusion.domain.Candidate;
import ar.com.hexium.hcop.infusion.domain.Infusion;
import ar.com.hexium.hcop.infusion.domain.Logistics;
import ar.com.hexium.hcop.infusion.domain.MedicationView;
import ar.com.hexium.hcop.infusion.domain.NewInfusion;
import ar.com.hexium.hcop.infusion.domain.Patch;
import ar.com.hexium.hcop.infusion.domain.ScheduleSettings;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * F3.3 PR2/3 (infusions): persistencia de turnos/aplicaciones de Hospital de Día.
 * {@code InfusionService}/{@code InfusionController} siguen legacy en este PR — están acoplados
 * por visibilidad de paquete a {@code ApplicationWorkflowRepository}/{@code ApplicationWorkflowPolicy}
 * (package-private, PR3) y no pueden moverse de paquete todavía. Ver DECISIONES-F3.md.
 */
public interface InfusionStore {

  List<Infusion> list(Long patientId, LocalDate date);

  Optional<Infusion> find(long id);

  Optional<Infusion> findByCycle(long patientId, String treatmentId, int cycleNumber);

  Optional<Infusion> findByApplication(
      long patientId, String treatmentId, int cycleNumber, int applicationDay);

  /**
   * Puede lanzar {@code org.springframework.dao.DataIntegrityViolationException} sin traducir si
   * el sillón/horario ya está ocupado — {@code InfusionService} (todavía legacy en este PR, ver
   * javadoc de la interfaz) sigue capturándola directo, mismo criterio que antes de F3.3.
   */
  Infusion insert(NewInfusion input, long actorId);

  Optional<Infusion> update(long id, long expectedRevision, Patch patch, long actorId);

  List<MedicationView> medications(long infusionId);

  List<Candidate> candidates(String query, boolean includeScheduled, boolean onlySchedulingEligible);

  ScheduleSettings scheduleSettings();

  Optional<Logistics> logistics(long patientId, String treatmentId, int cycleNumber, int applicationDay);

  Optional<Logistics> updateLogistics(
      long patientId, String treatmentId, int cycleNumber, int applicationDay, long expectedRevision,
      LocalDate plannedDate, String medicationState, String prescriptionState, String notes,
      long actorId);
}
