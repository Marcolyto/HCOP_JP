package ar.com.hexium.hcop.protocol.application.service;

import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.ConfigurationView;
import ar.com.hexium.hcop.configuration.application.service.ConfigurationFailure;
import ar.com.hexium.hcop.configuration.domain.ConfigurationDefinition;
import ar.com.hexium.hcop.protocol.application.port.in.ProtocolManagementUseCase;
import ar.com.hexium.hcop.protocol.application.port.out.DrugCatalogPort;
import ar.com.hexium.hcop.protocol.application.port.out.ProtocolCatalogPort;
import ar.com.hexium.hcop.protocol.application.port.out.ProtocolCatalogPort.CatalogScheme;
import ar.com.hexium.hcop.protocol.domain.ProtocolDocument;
import ar.com.hexium.hcop.protocol.domain.ProtocolId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Orquesta protocolos locales y entradas de catálogo sin depender de HTTP, JSON, JDBC ni archivos.
 */
public final class ProtocolApplicationService implements ProtocolManagementUseCase {
  private static final String KIND = "protocol";

  private final ConfigurationManagementUseCase configurations;
  private final ProtocolCatalogPort catalog;
  private final DrugCatalogPort drugs;

  public ProtocolApplicationService(
      ConfigurationManagementUseCase configurations,
      ProtocolCatalogPort catalog,
      DrugCatalogPort drugs) {
    this.configurations = configurations;
    this.catalog = catalog;
    this.drugs = drugs;
  }

  @Override
  public ProtocolList list(boolean includeArchived, boolean includeCatalog) {
    List<ProtocolView> custom = translate(() -> configurations.list(KIND, includeArchived))
        .stream()
        .map(this::custom)
        .toList();
    List<ProtocolView> result = new ArrayList<>(custom);
    if (includeCatalog) {
      Set<String> linked = custom.stream()
          .map(ProtocolView::coirSchemeId)
          .filter(value -> value != null && !value.isBlank())
          .collect(Collectors.toSet());
      catalog.schemes().stream()
          .filter(item -> !linked.contains(item.id()))
          .map(item -> catalog(item, false))
          .forEach(result::add);
    }
    long current = custom.stream().filter(ProtocolView::active).count();
    return new ProtocolList(List.copyOf(result), current, result.size() - custom.size());
  }

  @Override
  public ProtocolView get(String externalId) {
    ProtocolId id = id(externalId);
    if (id.catalog()) {
      CatalogScheme scheme = catalog.scheme(id.coirValue())
          .orElseThrow(this::notFound);
      return catalog(scheme, true);
    }
    return translate(() -> configurations.list(KIND, true)).stream()
        .filter(candidate -> id.value().equals(candidate.id()))
        .findFirst()
        .map(this::custom)
        .orElseThrow(this::notFound);
  }

  @Override
  public ProtocolView create(SaveProtocolCommand command) {
    validate(command);
    ConfigurationView created = translate(() -> configurations.create(
        new ConfigurationManagementUseCase.CreateCommand(
            KIND,
            "",
            command.name(),
            command.description(),
            command.active(),
            configuration(command.definition()),
            command.actorId())));
    catalog.invalidate();
    return custom(created);
  }

  @Override
  public ProtocolView update(String externalId, SaveProtocolCommand command) {
    ProtocolId id = id(externalId);
    if (id.catalog()) throw notFound();
    validate(command);
    ConfigurationView updated = translate(() -> configurations.update(
        new ConfigurationManagementUseCase.UpdateCommand(
            KIND,
            id.customValue(),
            command.expectedRevision(),
            "",
            command.name(),
            command.description(),
            command.active(),
            configuration(command.definition()),
            command.actorId())));
    catalog.invalidate();
    return custom(updated);
  }

  @Override
  public ProtocolView archive(String externalId, ar.com.hexium.hcop.sharedkernel.domain.UserId actorId) {
    ProtocolId id = id(externalId);
    if (id.catalog()) throw notFound();
    ConfigurationView archived = translate(() ->
        configurations.archive(KIND, id.customValue(), actorId));
    catalog.invalidate();
    return custom(archived);
  }

  @Override
  public List<CatalogView> catalog() {
    return catalog.schemes().stream()
        .map(item -> new CatalogView(
            item.id(),
            item.name(),
            item.durationMinutes(),
            item.cycleDays()))
        .toList();
  }

  @Override
  public List<ProtocolDocument> drugs(String query) {
    return drugs.search(query == null ? "" : query);
  }

  private ProtocolView custom(ConfigurationView item) {
    ProtocolDocument definition = protocolDocument(item.definition());
    List<ProtocolDocument> components = definition.documents("components");
    Integer cycleDays = definition.integer("cycleDays");
    Integer durationMinutes = definition.integer("durationMinutes");
    String coirSchemeId = definition.text("coirSchemeId");
    return new ProtocolView(
        item.id(),
        item.key(),
        item.name(),
        item.description(),
        item.active(),
        false,
        item.revision(),
        item.createdAt(),
        item.updatedAt(),
        definition.text("category"),
        cycleDays == null ? 21 : cycleDays,
        durationMinutes,
        coirSchemeId,
        components,
        components.size(),
        definition);
  }

  private ProtocolView catalog(CatalogScheme scheme, boolean includeComponents) {
    List<ProtocolDocument> components = includeComponents
        ? catalog.components(scheme.id())
        : List.of();
    return new ProtocolView(
        ProtocolId.coir(scheme.id()).value(),
        "",
        scheme.name(),
        "Esquema operativo importado del catálogo COIR.",
        true,
        true,
        0,
        null,
        null,
        category(scheme.name()),
        scheme.cycleDays(),
        scheme.durationMinutes(),
        scheme.id(),
        components,
        catalogComponentCount(scheme),
        scheme.definition());
  }

  private int catalogComponentCount(CatalogScheme scheme) {
    for (String key : List.of("drugs", "drogas", "components")) {
      List<ProtocolDocument> values = scheme.definition().documents(key);
      if (!values.isEmpty()) return values.size();
    }
    return 0;
  }

  private String category(String name) {
    String value = name == null ? "" : name.strip();
    int separator = value.indexOf(" - ");
    if (separator < 0) separator = value.indexOf(':');
    if (separator > 0) value = value.substring(0, separator);
    return value.isBlank() ? "Otros" : value;
  }

  private ProtocolId id(String externalId) {
    try {
      return ProtocolId.parse(externalId);
    } catch (IllegalArgumentException invalid) {
      throw notFound();
    }
  }

  private void validate(SaveProtocolCommand command) {
    if (command == null || command.name() == null || command.name().isBlank()) {
      throw new ProtocolFailure(ProtocolFailure.Type.INVALID, "El nombre es obligatorio.");
    }
    if (command.definition() == null) {
      throw new ProtocolFailure(ProtocolFailure.Type.INVALID, "La definición del protocolo es obligatoria.");
    }
    Integer cycleDays = command.definition().integer("cycleDays");
    if (cycleDays != null && cycleDays < 1) {
      throw new ProtocolFailure(
          ProtocolFailure.Type.INVALID,
          "La duración del ciclo debe ser mayor que cero.");
    }
    Integer durationMinutes = command.definition().integer("durationMinutes");
    if (durationMinutes != null && durationMinutes < 1) {
      throw new ProtocolFailure(
          ProtocolFailure.Type.INVALID,
          "La duración operativa debe ser mayor que cero.");
    }
  }

  private ConfigurationDefinition configuration(ProtocolDocument document) {
    return ConfigurationDefinition.of(document.value());
  }

  @SuppressWarnings("unchecked")
  private ProtocolDocument protocolDocument(ConfigurationDefinition definition) {
    Object value = definition.value();
    if (!(value instanceof Map<?, ?> map)) return ProtocolDocument.empty();
    return ProtocolDocument.of((Map<String, Object>) map);
  }

  private <T> T translate(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (ConfigurationFailure failure) {
      ProtocolFailure.Type type = switch (failure.type()) {
        case INVALID -> ProtocolFailure.Type.INVALID;
        case NOT_FOUND -> ProtocolFailure.Type.NOT_FOUND;
        case CONFLICT -> ProtocolFailure.Type.CONFLICT;
      };
      throw new ProtocolFailure(type, failure.getMessage(), failure.code());
    }
  }

  private ProtocolFailure notFound() {
    return new ProtocolFailure(ProtocolFailure.Type.NOT_FOUND, "Protocolo no encontrado.");
  }
}
