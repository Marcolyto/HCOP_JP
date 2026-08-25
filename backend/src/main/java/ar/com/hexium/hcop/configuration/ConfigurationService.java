package ar.com.hexium.hcop.configuration;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase;
import ar.com.hexium.hcop.configuration.application.service.ConfigurationFailure;
import ar.com.hexium.hcop.configuration.infrastructure.web.ConfigurationJsonMapper;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/**
 * Puente temporal para consumidores todavía no migrados al puerto de aplicación.
 */
@Service
public class ConfigurationService {
  private final ConfigurationManagementUseCase configurations;
  private final ConfigurationJsonMapper json;

  public ConfigurationService(
      ConfigurationManagementUseCase configurations,
      ConfigurationJsonMapper json) {
    this.configurations = configurations;
    this.json = json;
  }

  public List<Map<String, Object>> list(String kind, boolean includeInactive) {
    return translate(() -> configurations.list(kind, includeInactive)
        .stream()
        .map(json::view)
        .toList());
  }

  public Map<String, Object> create(String kind, JsonNode input, long actorId) {
    return translate(() -> json.view(configurations.create(
        json.createCommand(kind, input, actorId))));
  }

  public Map<String, Object> update(String kind, long id, JsonNode input, long actorId) {
    return translate(() -> json.view(configurations.update(
        json.updateCommand(kind, id, input, actorId))));
  }

  public Map<String, Object> archive(String kind, long id, long actorId) {
    return translate(() -> json.view(configurations.archive(kind, id, UserId.of(actorId))));
  }

  public List<Map<String, Object>> versions(String kind, long id) {
    return translate(() -> configurations.versions(kind, id)
        .stream()
        .map(json::versionView)
        .toList());
  }

  public Map<String, Object> version(String kind, long id, long revision) {
    return translate(() -> json.versionView(configurations.version(kind, id, revision)));
  }

  private <T> T translate(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (ConfigurationFailure failure) {
      HttpStatus status = switch (failure.type()) {
        case INVALID -> HttpStatus.BAD_REQUEST;
        case NOT_FOUND -> HttpStatus.NOT_FOUND;
        case CONFLICT -> HttpStatus.CONFLICT;
      };
      throw new ApiException(status, failure.getMessage(), failure.code());
    }
  }
}
