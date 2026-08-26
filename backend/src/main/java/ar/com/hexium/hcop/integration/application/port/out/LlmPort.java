package ar.com.hexium.hcop.integration.application.port.out;

import ar.com.hexium.hcop.integration.domain.AgentAnswer;
import ar.com.hexium.hcop.integration.domain.ChatMessage;
import ar.com.hexium.hcop.integration.domain.LlmConfiguration;
import java.util.List;

public interface LlmPort {

  LlmCompletion complete(LlmConfiguration config, List<ChatMessage> messages, boolean requireEnabled);

  /** Variante estructurada del Agente: pide, interpreta y sanea la respuesta contra el esquema fijo. */
  AgentAnswer completeAgentChat(LlmConfiguration config, List<ChatMessage> messages);

  /** Interpreta el contenido de una {@link LlmCompletion} como JSON — árbol opaco (mapas/listas/escalares). */
  Object parseJson(String content);

  record LlmCompletion(String content, String model) {
  }
}
