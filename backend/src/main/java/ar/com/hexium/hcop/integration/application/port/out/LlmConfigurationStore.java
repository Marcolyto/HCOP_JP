package ar.com.hexium.hcop.integration.application.port.out;

import ar.com.hexium.hcop.integration.domain.LlmConfiguration;

public interface LlmConfigurationStore {

  LlmConfiguration find();

  /** {@code preserveApiKey}: conserva la API key ya persistida en vez de la de {@code value}. */
  LlmConfiguration upsert(LlmConfiguration value, boolean preserveApiKey, long actorId);
}
