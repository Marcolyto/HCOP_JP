package ar.com.hexium.hcop.treatment.infrastructure.configuration;

import ar.com.hexium.hcop.catalog.application.port.in.TreatmentCatalogUseCase;
import ar.com.hexium.hcop.treatment.application.port.in.TreatmentUseCase;
import ar.com.hexium.hcop.treatment.application.port.out.InfusionSummaryPort;
import ar.com.hexium.hcop.treatment.application.port.out.PatientDiagnosisOptionsPort;
import ar.com.hexium.hcop.treatment.application.port.out.TreatmentPatientPort;
import ar.com.hexium.hcop.treatment.application.port.out.TreatmentStore;
import ar.com.hexium.hcop.treatment.application.service.TreatmentApplicationService;
import ar.com.hexium.hcop.treatment.domain.TreatmentProtocolCompatibility;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aplica los límites transaccionales sin contaminar la capa de aplicación con Spring. */
@Service
public class TransactionalTreatmentManagement implements TreatmentUseCase {
  private final TreatmentApplicationService delegate;

  public TransactionalTreatmentManagement(
      TreatmentStore store, TreatmentCatalogUseCase catalog, TreatmentPatientPort patients,
      PatientDiagnosisOptionsPort diagnosisOptions, InfusionSummaryPort infusions, Clock clock) {
    this.delegate = new TreatmentApplicationService(
        store, catalog, patients, diagnosisOptions, infusions,
        new TreatmentProtocolCompatibility(), clock);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(long patientId) {
    return delegate.list(patientId);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> options(long patientId) {
    return delegate.options(patientId);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> requirements(long patientId, String schemeId) {
    return delegate.requirements(patientId, schemeId);
  }

  @Override
  @Transactional
  public CreationResult create(CreateTreatmentCommand command) {
    return delegate.create(command);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Object> detail(long patientId, String treatmentId) {
    return delegate.detail(patientId, treatmentId);
  }
}
