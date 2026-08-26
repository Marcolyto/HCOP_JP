package ar.com.hexium.hcop.workflow.infrastructure.configuration;

import ar.com.hexium.hcop.workflow.application.port.in.TreatmentWorkflowUseCase;
import ar.com.hexium.hcop.workflow.application.port.out.PatientEvolutionPort;
import ar.com.hexium.hcop.workflow.application.port.out.TreatmentWorkflowStore;
import ar.com.hexium.hcop.workflow.application.service.TreatmentWorkflowApplicationService;
import ar.com.hexium.hcop.workflow.domain.WorkflowRequest;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aplica los límites transaccionales sin contaminar la capa de aplicación con Spring. */
@Service
public class TransactionalTreatmentWorkflowManagement implements TreatmentWorkflowUseCase {
  private final TreatmentWorkflowApplicationService delegate;

  public TransactionalTreatmentWorkflowManagement(
      TreatmentWorkflowStore store, PatientEvolutionPort evolutions, Clock clock) {
    this.delegate = new TreatmentWorkflowApplicationService(store, evolutions, clock);
  }

  @Override
  @Transactional
  public ManagementActionResult suspend(SuspendCommand command) {
    return delegate.suspend(command);
  }

  @Override
  @Transactional
  public ManagementActionResult resume(ResumeCommand command) {
    return delegate.resume(command);
  }

  @Override
  @Transactional
  public RequestActionResult createRequest(CreateRequestCommand command) {
    return delegate.createRequest(command);
  }

  @Override
  @Transactional(readOnly = true)
  public List<WorkflowRequest> inbox(long actorUserId) {
    return delegate.inbox(actorUserId);
  }

  @Override
  @Transactional
  public WorkflowRequest seen(long id, long actorUserId) {
    return delegate.seen(id, actorUserId);
  }

  @Override
  @Transactional
  public RequestActionResult resolveRequest(ResolveCommand command) {
    return delegate.resolveRequest(command);
  }
}
