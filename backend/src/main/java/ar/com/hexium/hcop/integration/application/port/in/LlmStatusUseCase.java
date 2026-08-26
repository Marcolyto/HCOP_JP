package ar.com.hexium.hcop.integration.application.port.in;

public interface LlmStatusUseCase {

  LlmStatus status();

  record LlmStatus(boolean enabled, String model, String provider, boolean configured) {
  }
}
