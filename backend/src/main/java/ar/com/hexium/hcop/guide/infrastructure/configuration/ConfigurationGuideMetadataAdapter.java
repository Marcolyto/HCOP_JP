package ar.com.hexium.hcop.guide.infrastructure.configuration;

import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase;
import ar.com.hexium.hcop.guide.application.port.out.GuideMetadataPort;
import ar.com.hexium.hcop.guide.domain.GuideFileName;
import ar.com.hexium.hcop.guide.domain.GuideMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ConfigurationGuideMetadataAdapter implements GuideMetadataPort {
  private final ConfigurationManagementUseCase configurations;

  public ConfigurationGuideMetadataAdapter(ConfigurationManagementUseCase configurations) {
    this.configurations = configurations;
  }

  @Override
  public List<GuideMetadata> listAll() {
    List<GuideMetadata> result = new ArrayList<>();
    configurations.list("guide", true).forEach(item -> {
      if (!(item.definition().value() instanceof Map<?, ?> source)) return;
      String fileName = text(source.get("fileName"));
      if (fileName.isBlank()) return;
      try {
        result.add(new GuideMetadata(
            GuideFileName.fromRaw(fileName),
            item.name(),
            text(source.get("category")),
            text(source.get("audience")),
            text(source.get("source")),
            text(source.get("version")),
            strings(source.get("tags")),
            item.description(),
            item.active(),
            item.id(),
            item.revision()));
      } catch (IllegalArgumentException ignored) {
        // Un metadato inválido no debe ocultar el resto de la biblioteca.
      }
    });
    return List.copyOf(result);
  }

  private List<String> strings(Object value) {
    if (!(value instanceof List<?> list)) return List.of();
    return list.stream().map(this::text).filter(item -> !item.isBlank()).toList();
  }

  private String text(Object value) {
    return value == null ? "" : String.valueOf(value).strip();
  }
}
