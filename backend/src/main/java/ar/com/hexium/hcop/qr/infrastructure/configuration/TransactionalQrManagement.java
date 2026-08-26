package ar.com.hexium.hcop.qr.infrastructure.configuration;

import ar.com.hexium.hcop.config.HcopProperties;
import ar.com.hexium.hcop.qr.application.port.in.QrUseCase;
import ar.com.hexium.hcop.qr.application.port.out.PatientEvolutionPort;
import ar.com.hexium.hcop.qr.application.port.out.QrInfusionPort;
import ar.com.hexium.hcop.qr.application.port.out.QrPatientPort;
import ar.com.hexium.hcop.qr.application.port.out.QrScanStore;
import ar.com.hexium.hcop.qr.application.port.out.QrTreatmentPort;
import ar.com.hexium.hcop.qr.application.service.QrApplicationService;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aplica los límites transaccionales sin contaminar la capa de aplicación con Spring. */
@Service
public class TransactionalQrManagement implements QrUseCase {
  private final QrApplicationService delegate;

  public TransactionalQrManagement(
      HcopProperties properties, QrPatientPort patients, QrTreatmentPort treatments,
      QrInfusionPort infusions, QrScanStore scans, PatientEvolutionPort evolutions, Clock clock) {
    this.delegate = new QrApplicationService(
        properties.qrSecret(), patients, treatments, infusions, scans, evolutions, clock);
  }

  @Override
  public String code(long patientId, String treatmentId, int cycle, int applicationDay) {
    return delegate.code(patientId, treatmentId, cycle, applicationDay);
  }

  @Override
  public String printableHtml(long patientId, String treatmentId, int cycle, int applicationDay) {
    return delegate.printableHtml(patientId, treatmentId, cycle, applicationDay);
  }

  @Override
  @Transactional
  public ScanResult scan(ScanCommand command) {
    return delegate.scan(command);
  }
}
