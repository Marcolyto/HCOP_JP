package ar.com.hexium.hcop.integration.domain;

import java.util.List;

public record AgentAnswer(
    String answer,
    String model,
    List<ChatArtifact> artifacts,
    List<String> followUps,
    List<ChatHighlight> highlights) {
}
