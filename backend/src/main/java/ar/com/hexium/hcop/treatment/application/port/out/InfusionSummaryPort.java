package ar.com.hexium.hcop.treatment.application.port.out;

import java.util.List;
import java.util.Map;

/**
 * F3.3.0 (puertos cruzados): rompe la dependencia directa de {@code treatment} hacia
 * {@code infusion} — orden canónico del módulo: {@code patient} (base) ← {@code treatment} ←
 * {@code infusion}; {@code treatment} no puede depender "hacia abajo" de {@code infusion}.
 * Implementado por un adapter que vive en {@code infusion} y delega en
 * {@code InfusionService.list}.
 */
public interface InfusionSummaryPort {
  List<Map<String, Object>> list(long patientId);
}
