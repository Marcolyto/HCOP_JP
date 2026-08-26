package ar.com.hexium.hcop.workflow.application.service;

/** Error funcional del módulo, independiente de HTTP. */
public final class WorkflowFailure extends RuntimeException {
  private final Type type;

  public WorkflowFailure(Type type, String message) {
    super(message);
    this.type = type;
  }

  public Type type() {
    return type;
  }

  public enum Type {
    INVALID,
    NOT_FOUND,
    CONFLICT,
    FORBIDDEN
  }
}
