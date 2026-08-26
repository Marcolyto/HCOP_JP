package ar.com.hexium.hcop.infusion.application.port.in;

public interface TreatmentApplicationLogisticsUseCase {

  void synchronizeExistingTreatments();

  void synchronizeTreatment(String treatmentId);
}
