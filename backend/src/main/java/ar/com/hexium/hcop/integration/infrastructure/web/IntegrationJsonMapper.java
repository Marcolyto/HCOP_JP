package ar.com.hexium.hcop.integration.infrastructure.web;

import ar.com.hexium.hcop.integration.application.port.in.SystemConfigurationUseCase.LlmConfigurationCommand;
import ar.com.hexium.hcop.integration.application.port.in.SystemConfigurationUseCase.PublicConfiguration;
import ar.com.hexium.hcop.integration.domain.ChartArtifact;
import ar.com.hexium.hcop.integration.domain.ChatArtifact;
import ar.com.hexium.hcop.integration.domain.ChatHighlight;
import ar.com.hexium.hcop.integration.domain.TableArtifact;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class IntegrationJsonMapper {
  private final ObjectMapper mapper;

  public IntegrationJsonMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public LlmConfigurationCommand command(JsonNode llmInput) {
    return new LlmConfigurationCommand(
        llmInput.has("enabled") ? llmInput.path("enabled").asBoolean() : null,
        text(llmInput, "provider"),
        text(llmInput, "baseUrl"),
        text(llmInput, "model"),
        llmInput.has("temperature") ? llmInput.path("temperature").asDouble() : null,
        llmInput.has("maxTokens") ? llmInput.path("maxTokens").asInt() : null,
        llmInput.has("timeoutMs") ? llmInput.path("timeoutMs").asInt() : null,
        text(llmInput, "apiKeyAction"),
        llmInput.has("apiKey") ? llmInput.path("apiKey").asText("") : "",
        llmInput.has("apiKey"));
  }

  public Map<String, Object> view(PublicConfiguration config) {
    Map<String, Object> llm = new LinkedHashMap<>();
    llm.put("enabled", config.enabled());
    llm.put("provider", config.provider());
    llm.put("baseUrl", config.baseUrl());
    llm.put("model", config.model());
    llm.put("temperature", config.temperature());
    llm.put("maxTokens", config.maxTokens());
    llm.put("timeoutMs", config.timeoutMs());
    llm.put("hasApiKey", config.hasApiKey());
    llm.put("lockedFields", List.of());
    return Map.of(
        "ok", true,
        "llm", llm,
        "ajcc", Map.of("endpoint", "local://ajcc8", "hasApiKey", false, "offline", true));
  }

  public List<JsonNode> artifacts(List<ChatArtifact> artifacts) {
    return artifacts.stream().map(this::artifact).toList();
  }

  private JsonNode artifact(ChatArtifact artifact) {
    var node = mapper.createObjectNode();
    if (artifact instanceof TableArtifact table) {
      node.put("type", "table");
      node.put("title", table.title());
      var columns = node.putArray("columns");
      table.columns().forEach(columns::add);
      var rows = node.putArray("rows");
      table.rows().forEach(row -> {
        var rowNode = rows.addArray();
        row.forEach(rowNode::add);
      });
      return node;
    }
    ChartArtifact chart = (ChartArtifact) artifact;
    node.put("type", "chart");
    node.put("title", chart.title());
    node.put("chartType", chart.chartType());
    node.put("xLabel", chart.xLabel());
    var series = node.putArray("series");
    chart.series().forEach(item -> {
      var seriesNode = series.addObject();
      seriesNode.put("name", item.name());
      if (item.color() != null) seriesNode.put("color", item.color());
      var points = seriesNode.putArray("points");
      item.points().forEach(point -> {
        var pointNode = points.addObject();
        pointNode.put("x", point.x());
        pointNode.put("y", point.y());
        pointNode.put("label", point.label());
      });
    });
    return node;
  }

  public List<JsonNode> highlights(List<ChatHighlight> highlights) {
    return highlights.stream().map(item -> {
      var node = mapper.createObjectNode();
      var terms = node.putArray("terms");
      item.terms().forEach(terms::add);
      node.put("color", item.color());
      return (JsonNode) node;
    }).toList();
  }

  private String text(JsonNode node, String key) {
    return node.path(key).asText("").trim();
  }
}
