package ar.com.hexium.hcop.integration.application.port.in;

import ar.com.hexium.hcop.integration.domain.AgentAnswer;
import java.util.List;

public interface AgentChatUseCase {

  AgentAnswer chat(AgentChatCommand command);

  record AgentChatCommand(String message, String clinicalText, List<HistoryEntry> history) {
  }

  record HistoryEntry(String role, String content) {
  }
}
