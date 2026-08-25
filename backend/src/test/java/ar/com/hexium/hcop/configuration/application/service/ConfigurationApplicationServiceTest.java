package ar.com.hexium.hcop.configuration.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.CreateCommand;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.UpdateCommand;
import ar.com.hexium.hcop.configuration.application.port.out.ConfigurationKeyConflictException;
import ar.com.hexium.hcop.configuration.application.port.out.ConfigurationStore;
import ar.com.hexium.hcop.configuration.domain.ConfigurationDefinition;
import ar.com.hexium.hcop.configuration.domain.ConfigurationItem;
import ar.com.hexium.hcop.configuration.domain.ConfigurationKind;
import ar.com.hexium.hcop.configuration.domain.ConfigurationVersion;
import ar.com.hexium.hcop.sharedkernel.domain.Revision;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfigurationApplicationServiceTest {
  private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");
  private static final UserId ACTOR = UserId.of(7);

  private final MemoryStore store = new MemoryStore();
  private final ConfigurationApplicationService service = new ConfigurationApplicationService(store);

  @Test
  void entregaLaConfiguracionPredeterminadaSinPersistirDatosFicticios() {
    var settings = service.list("day-hospital-settings", false);

    assertThat(settings).hasSize(1);
    assertThat(settings.getFirst().id()).isEmpty();
    assertThat(settings.getFirst().revision()).isZero();
    Map<?, ?> definition = (Map<?, ?>) settings.getFirst().definition().value();
    assertThat(definition.get("slotMinutes")).isEqualTo(10);
    assertThat(definition.get("chairCount")).isEqualTo(6);
    assertThat(store.items).isEmpty();
  }

  @Test
  void creaUnaClaveNormalizadaYEvitaColisiones() {
    store.insert(new ConfigurationStore.NewItem(
        ConfigurationKind.CALCULATOR,
        "indice-masa-corporal",
        "Existente",
        "",
        true,
        ConfigurationDefinition.emptyObject(),
        ACTOR));

    var created = service.create(new CreateCommand(
        "calculator",
        "",
        "Índice masa corporal",
        "Cálculo clínico",
        true,
        ConfigurationDefinition.of(Map.of("expression", "peso / talla")),
        ACTOR));

    assertThat(created.key()).isEqualTo("indice-masa-corporal-2");
    assertThat(created.revision()).isEqualTo(1);
  }

  @Test
  void conservaCamposOmitidosYActualizaConRevisionOptimista() {
    ConfigurationItem initial = store.insert(new ConfigurationStore.NewItem(
        ConfigurationKind.RESEARCH_FORM,
        "seguimiento",
        "Seguimiento",
        "Original",
        true,
        ConfigurationDefinition.of(Map.of("fields", List.of("peso"))),
        ACTOR));

    var updated = service.update(new UpdateCommand(
        "research-form",
        initial.id(),
        initial.revision().value(),
        "",
        "Seguimiento ampliado",
        null,
        null,
        null,
        ACTOR));

    assertThat(updated.name()).isEqualTo("Seguimiento ampliado");
    assertThat(updated.description()).isEqualTo("Original");
    assertThat(updated.definition()).isEqualTo(initial.definition());
    assertThat(updated.revision()).isEqualTo(2);
  }

  @Test
  void rechazaUnaRevisionDesactualizadaConCodigoEstable() {
    ConfigurationItem initial = store.insert(new ConfigurationStore.NewItem(
        ConfigurationKind.GUIDE,
        "pulmon",
        "Pulmón",
        "",
        true,
        ConfigurationDefinition.emptyObject(),
        ACTOR));

    assertThatThrownBy(() -> service.update(new UpdateCommand(
        "guide",
        initial.id(),
        99L,
        "",
        "",
        null,
        null,
        null,
        ACTOR)))
        .isInstanceOfSatisfying(ConfigurationFailure.class, failure -> {
          assertThat(failure.type()).isEqualTo(ConfigurationFailure.Type.CONFLICT);
          assertThat(failure.code()).isEqualTo("VERSION_CONFLICT");
        });
  }

  @Test
  void rechazaFamiliasDesconocidas() {
    assertThatThrownBy(() -> service.list("desconocida", false))
        .isInstanceOfSatisfying(ConfigurationFailure.class, failure ->
            assertThat(failure.type()).isEqualTo(ConfigurationFailure.Type.NOT_FOUND));
  }

  private static final class MemoryStore implements ConfigurationStore {
    private final Map<Long, ConfigurationItem> items = new LinkedHashMap<>();
    private final Map<Long, List<ConfigurationVersion>> history = new LinkedHashMap<>();
    private long sequence = 1;

    @Override
    public List<ConfigurationItem> list(ConfigurationKind kind, boolean includeInactive) {
      return items.values().stream()
          .filter(item -> item.kind() == kind)
          .filter(item -> includeInactive || item.active())
          .toList();
    }

    @Override
    public Optional<ConfigurationItem> find(long id, ConfigurationKind kind) {
      return Optional.ofNullable(items.get(id)).filter(item -> item.kind() == kind);
    }

    @Override
    public Optional<ConfigurationItem> findByKey(ConfigurationKind kind, String key) {
      return items.values().stream()
          .filter(item -> item.kind() == kind && item.key().equals(key))
          .findFirst();
    }

    @Override
    public ConfigurationItem insert(NewItem item) {
      if (findByKey(item.kind(), item.key()).isPresent()) {
        throw new ConfigurationKeyConflictException(new IllegalStateException("duplicate"));
      }
      ConfigurationItem stored = new ConfigurationItem(
          sequence++,
          item.kind(),
          item.key(),
          item.name(),
          item.description(),
          item.active(),
          item.definition(),
          Revision.initial(),
          NOW,
          NOW);
      items.put(stored.id(), stored);
      appendVersion(stored, item.actorId());
      return stored;
    }

    @Override
    public Optional<ConfigurationItem> update(ItemUpdate update) {
      ConfigurationItem current = items.get(update.id());
      if (current == null
          || current.kind() != update.kind()
          || !current.revision().equals(update.expectedRevision())) {
        return Optional.empty();
      }
      ConfigurationItem stored = new ConfigurationItem(
          current.id(),
          current.kind(),
          update.key(),
          update.name(),
          update.description(),
          update.active(),
          update.definition(),
          current.revision().next(),
          current.createdAt(),
          NOW.plusSeconds(current.revision().value()));
      items.put(stored.id(), stored);
      appendVersion(stored, update.actorId());
      return Optional.of(stored);
    }

    @Override
    public List<ConfigurationVersion> versions(long itemId, ConfigurationKind kind) {
      return find(itemId, kind).isEmpty()
          ? List.of()
          : List.copyOf(history.getOrDefault(itemId, List.of()));
    }

    private void appendVersion(ConfigurationItem item, UserId actor) {
      history.computeIfAbsent(item.id(), ignored -> new ArrayList<>())
          .addFirst(new ConfigurationVersion(
              item.revision(),
              item.name(),
              item.description(),
              item.active(),
              item.definition(),
              actor,
              "Usuario de prueba",
              item.updatedAt()));
    }
  }
}
