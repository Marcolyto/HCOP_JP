package ar.com.hexium.hcop.treatment.application.port.out;

/**
 * F3.3.0 (puertos cruzados): rompe la dependencia directa de {@code treatment} hacia
 * {@code infusion.TreatmentApplicationLogisticsService} — {@code TreatmentRepository} necesita
 * disparar la sincronización de logística de aplicación al crear un tratamiento, pero esa regla
 * es dueña de {@code infusion}. Implementado por un adapter que vive en {@code infusion} y delega
 * en {@code TreatmentApplicationLogisticsService.synchronizeTreatment}.
 */
public interface TreatmentApplicationSyncPort {
  void synchronize(String treatmentId);
}
