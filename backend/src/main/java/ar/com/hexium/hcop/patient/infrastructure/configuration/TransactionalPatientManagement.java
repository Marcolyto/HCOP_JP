package ar.com.hexium.hcop.patient.infrastructure.configuration;

import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.patient.application.port.in.PatientUseCase;
import ar.com.hexium.hcop.patient.application.port.out.PatientDocumentStore;
import ar.com.hexium.hcop.patient.application.port.out.PatientStore;
import ar.com.hexium.hcop.patient.application.service.PatientApplicationService;
import ar.com.hexium.hcop.patient.domain.NewPatient;
import ar.com.hexium.hcop.patient.domain.Patient;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica el límite transaccional que abarca {@code PatientStore.insert} +
 * {@code PatientDocumentStore.createBlank} + {@code AuthService.setActivePatient} — spanea dos
 * stores, así que no alcanza con el {@code @Transactional} propio de cada uno (mismo criterio
 * que {@code TransactionalAdminManagement}).
 */
@Service
public class TransactionalPatientManagement implements PatientUseCase {
  private final PatientApplicationService delegate;

  public TransactionalPatientManagement(
      PatientStore patients, PatientDocumentStore documents, AuthService auth, Clock clock) {
    this.delegate = new PatientApplicationService(patients, documents, auth, clock);
  }

  @Override
  public List<Patient> search(String query) {
    return delegate.search(query);
  }

  @Override
  public Patient require(long patientId) {
    return delegate.require(patientId);
  }

  @Override
  @Transactional
  public Creation create(NewPatient input, long actorId, String sessionId) {
    return delegate.create(input, actorId, sessionId);
  }
}
