package ar.com.hexium.hcop.integration.application.service;

import ar.com.hexium.hcop.catalog.application.port.in.SystemicFormCatalogUseCase;
import ar.com.hexium.hcop.integration.application.port.in.SystemConfigurationUseCase;
import ar.com.hexium.hcop.integration.application.port.in.SystemicFormFillUseCase;
import ar.com.hexium.hcop.integration.application.port.out.LlmPort;
import ar.com.hexium.hcop.integration.domain.ChatMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SystemicFormFillApplicationService implements SystemicFormFillUseCase {
  private final SystemicFormCatalogUseCase forms;
  private final SystemConfigurationUseCase configuration;
  private final LlmPort llm;

  public SystemicFormFillApplicationService(
      SystemicFormCatalogUseCase forms, SystemConfigurationUseCase configuration, LlmPort llm) {
    this.forms = forms;
    this.configuration = configuration;
    this.llm = llm;
  }

  @Override
  public FormFillResult fill(String templateId, String clinicalText, String notes) {
    Object rawTemplate = forms.find(templateId);
    if (rawTemplate == null) {
      throw new IntegrationFailure(IntegrationFailure.Type.NOT_FOUND, "Formulario no encontrado.");
    }
    Map<String, String> manifest = llmManifest(rawTemplate);
    String prompt = """
        Completá los campos del formulario usando exclusivamente el texto clínico.
        Respondé SOLO un objeto JSON cuyas claves sean exactamente las del manifiesto.
        Para casillas usá true/false. Si falta un dato usá cadena vacía. No inventes.
        MANIFIESTO:
        """ + manifestJson(manifest) + "\nTEXTO CLÍNICO:\n" + ClinicalTextLimits.limit(clinicalText)
        + "\nINDICACIÓN ADICIONAL DEL PROFESIONAL:\n" + ClinicalTextLimits.limit(notes);
    var response = llm.complete(
        configuration.currentConfiguration(),
        List.of(new ChatMessage("system", "Sos un extractor de formularios clínicos."),
            new ChatMessage("user", prompt)),
        true);
    Object parsed = llm.parseJson(response.content());
    return new FormFillResult(templateId, parsed, response.model());
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> llmManifest(Object rawTemplate) {
    Map<String, String> manifest = new LinkedHashMap<>();
    if (!(rawTemplate instanceof Map<?, ?> template)) return manifest;
    if (!(template.get("fields") instanceof List<?> fields)) return manifest;
    for (Object item : fields) {
      if (!(item instanceof Map<?, ?> field)) continue;
      if (!"llm".equals(field.get("source"))) continue;
      manifest.put(text(field.get("id")), text(field.get("label")));
    }
    return manifest;
  }

  private String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private String manifestJson(Map<String, String> manifest) {
    StringBuilder json = new StringBuilder("{");
    boolean first = true;
    for (var entry : manifest.entrySet()) {
      if (!first) json.append(',');
      first = false;
      json.append('"').append(escape(entry.getKey())).append("\":\"")
          .append(escape(entry.getValue())).append('"');
    }
    return json.append('}').toString();
  }

  private String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
  }
}
