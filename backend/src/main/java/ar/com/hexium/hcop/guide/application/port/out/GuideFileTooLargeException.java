package ar.com.hexium.hcop.guide.application.port.out;

public final class GuideFileTooLargeException extends RuntimeException {
  public GuideFileTooLargeException() {
    super("El PDF supera el tamaño máximo permitido.");
  }
}
