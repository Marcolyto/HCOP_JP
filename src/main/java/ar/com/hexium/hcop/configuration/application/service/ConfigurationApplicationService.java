package ar.com.hexium.hcop.configuration.application.service;

import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase;
import ar.com.hexium.hcop.configuration.application.port.out.ConfigurationKeyConflictException;
import ar.com.hexium.hcop.configuration.application.port.out.ConfigurationStore;
import ar.com.hexium.hcop.configuration.application.port.out.ConfigurationStore.ItemUpdate;
import ar.com.hexium.hcop.configuration.application.port.out.ConfigurationStore.NewItem;
import ar.com.hexium.hcop.configuration.domain.ConfigurationDefinition;
import ar.com.hexium.hcop.configuration.domain.ConfigurationItem;
import ar.com.hexium.hcop.configuration.domain.ConfigurationKind;
import ar.com.hexium.hcop.configuration.domain.ConfigurationVersion;
import ar.com.hexium.hcop.sharedkernel.domain.Revision;
import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Casos de uso puros de configuración.
 */
public final class ConfigurationApplicationService implements ConfigurationManagementUseCase {
  private final ConfigurationStore store;

  public ConfigurationApplicationService(ConfigurationStore store) {
    this.store = store;
  }

  @Override
  public List<ConfigurationView> list(String externalKind, boolean includeInactive) {
    ConfigurationKind kind = kind(externalKind);
    List<ConfigurationItem> items = store.list(kind, includeInactive);
    if (items.isEmpty() && kind == ConfigurationKind.DAY_HOSPITAL_SETTINGS) {
      return List.of(defaultDayHospitalSettings());
    }
    if (items.isEmpty() && kind == ConfigurationKind.TOOL_SETTINGS) {
      return List.of(defaultToolSettings());
    }
    return items.stream().map(this::view).toList();
  }

  @Override
  public ConfigurationView create(CreateCommand command) {
    ConfigurationKind kind = kind(command.kind());
    String name = normalize(command.name());
    if (name.isBlank()) {
      throw new ConfigurationFailure(
          ConfigurationFailure.Type.INVALID,
          "El nombre es obligatorio.");
    }
    String key = normalize(command.key());
    if (key.isBlank()) key = uniqueKey(kind, name);
    try {
      return view(store.insert(new NewItem(
          kind,
          key,
          name,
          normalize(command.description()),
          command.active(),
          command.definition(),
          command.actorId())));
    } catch (ConfigurationKeyConflictException conflict) {
      throw keyConflict();
    }
  }

  @Override
  public ConfigurationView update(UpdateCommand command) {
    ConfigurationKind kind = kind(command.kind());
    ConfigurationItem current = store.find(command.id(), kind)
        .orElseThrow(() -> notFound("Configuración no encontrada."));
    Revision expected = command.expectedRevision() == null
        ? current.revision()
        : revision(command.expectedRevision());
    String name = normalize(command.name());
    if (name.isBlank()) name = current.name();
    String key = normalize(command.key());
    if (key.isBlank()) key = current.key();
    String description = command.description() == null
        ? current.description()
        : command.description();
    boolean active = command.active() == null ? current.active() : command.active();
    ConfigurationDefinition definition = command.definition() == null
        ? current.definition()
        : command.definition();
    try {
      return view(store.update(new ItemUpdate(
          current.id(),
          kind,
          expected,
          key,
          name,
          description,
          active,
          definition,
          command.actorId()))
          .orElseThrow(() -> new ConfigurationFailure(
              ConfigurationFailure.Type.CONFLICT,
              "La configuración fue modificada por otro usuario.",
              "VERSION_CONFLICT")));
    } catch (ConfigurationKeyConflictException conflict) {
      throw keyConflict();
    }
  }

  @Override
  public ConfigurationView archive(String externalKind, long id, ar.com.hexium.hcop.sharedkernel.domain.UserId actorId) {
    ConfigurationKind kind = kind(externalKind);
    ConfigurationItem current = store.find(id, kind)
        .orElseThrow(() -> notFound("Configuración no encontrada."));
    return view(store.update(new ItemUpdate(
        current.id(),
        kind,
        current.revision(),
        current.key(),
        current.name(),
        current.description(),
        false,
        current.definition(),
        actorId))
        .orElseThrow(() -> new ConfigurationFailure(
            ConfigurationFailure.Type.CONFLICT,
            "La configuración fue modificada por otro usuario.",
            "VERSION_CONFLICT")));
  }

  @Override
  public List<ConfigurationVersionView> versions(String externalKind, long id) {
    ConfigurationKind kind = kind(externalKind);
    if (store.find(id, kind).isEmpty()) throw notFound("Configuración no encontrada.");
    return store.versions(id, kind).stream().map(this::versionView).toList();
  }

  @Override
  public ConfigurationVersionView version(String externalKind, long id, long revision) {
    ConfigurationKind kind = kind(externalKind);
    return store.versions(id, kind).stream()
        .filter(candidate -> candidate.revision().value() == revision)
        .findFirst()
        .map(this::versionView)
        .orElseThrow(() -> notFound("Versión no encontrada."));
  }

  private ConfigurationView view(ConfigurationItem item) {
    return new ConfigurationView(
        Long.toString(item.id()),
        item.kind().externalName(),
        item.key(),
        item.name(),
        item.description(),
        item.active(),
        item.definition(),
        item.revision().value(),
        item.createdAt(),
        item.updatedAt());
  }

  private ConfigurationVersionView versionView(ConfigurationVersion version) {
    return new ConfigurationVersionView(
        version.revision().value(),
        version.name(),
        version.description(),
        version.active(),
        version.definition(),
        Long.toString(version.changedBy().value()),
        version.changedByName(),
        version.createdAt());
  }

  private String uniqueKey(ConfigurationKind kind, String name) {
    String base = slug(name);
    String candidate = base;
    int suffix = 2;
    while (store.findByKey(kind, candidate).isPresent()) candidate = base + "-" + suffix++;
    return candidate;
  }

  private String slug(String value) {
    String slug = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-|-$)", "");
    return slug.isBlank() ? "item" : slug;
  }

  private ConfigurationKind kind(String externalKind) {
    return ConfigurationKind.fromExternalName(externalKind)
        .orElseThrow(() -> notFound("Tipo de configuración desconocido."));
  }

  private Revision revision(long value) {
    try {
      return new Revision(value);
    } catch (IllegalArgumentException invalid) {
      throw new ConfigurationFailure(
          ConfigurationFailure.Type.CONFLICT,
          "La configuración fue modificada por otro usuario.",
          "VERSION_CONFLICT");
    }
  }

  private ConfigurationFailure keyConflict() {
    return new ConfigurationFailure(
        ConfigurationFailure.Type.CONFLICT,
        "Ya existe una configuración con esa clave.");
  }

  private ConfigurationFailure notFound(String message) {
    return new ConfigurationFailure(ConfigurationFailure.Type.NOT_FOUND, message);
  }

  private String normalize(String value) {
    return value == null ? "" : value.strip();
  }

  private ConfigurationView defaultDayHospitalSettings() {
    return new ConfigurationView(
        "",
        ConfigurationKind.DAY_HOSPITAL_SETTINGS.externalName(),
        "default",
        "Hospital de día",
        "",
        true,
        ConfigurationDefinition.of(Map.of(
            "chairCount", 6,
            "slotMinutes", 10,
            "startTime", "08:00",
            "endTime", "16:00",
            "workdayStart", "08:00",
            "workdayEnd", "16:00",
            "visibleChairs", 6)),
        0,
        Instant.EPOCH,
        Instant.EPOCH);
  }

  private ConfigurationView defaultToolSettings() {
    return new ConfigurationView(
        "",
        ConfigurationKind.TOOL_SETTINGS.externalName(),
        "default",
        "Herramientas",
        "",
        true,
        ConfigurationDefinition.of(Map.of("enabled", true)),
        0,
        Instant.EPOCH,
        Instant.EPOCH);
  }
}
