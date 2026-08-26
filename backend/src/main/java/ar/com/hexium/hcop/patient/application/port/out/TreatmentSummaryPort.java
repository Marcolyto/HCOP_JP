package ar.com.hexium.hcop.patient.application.port.out;

import java.util.List;
import java.util.Map;

/**
 * F3.3.0 (puertos cruzados): rompe la dependencia directa de {@code patient} hacia
 * {@code treatment} (todavía no hexagonal) — patrón #7 del plan F3. Implementado por un adapter
 * que vive en {@code treatment} y delega en {@code TreatmentService.list}, sin cambiar el shape
 * de respuesta que ya consume {@code PatientWorkspaceController}.
 */
public interface TreatmentSummaryPort {
  List<Map<String, Object>> list(long patientId);
}
