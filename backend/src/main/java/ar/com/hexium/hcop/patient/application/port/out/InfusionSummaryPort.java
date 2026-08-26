package ar.com.hexium.hcop.patient.application.port.out;

import java.util.List;
import java.util.Map;

/**
 * F3.3.0 (puertos cruzados): rompe la dependencia directa de {@code patient} hacia
 * {@code infusion} (todavía no hexagonal) — patrón #7 del plan F3. Implementado por un adapter
 * que vive en {@code infusion} y delega en {@code InfusionService.list}, sin cambiar el shape de
 * respuesta que ya consume {@code PatientWorkspaceController}.
 */
public interface InfusionSummaryPort {
  List<Map<String, Object>> list(long patientId);
}
