package ar.com.hexium.hcop.protocol.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase;
import ar.com.hexium.hcop.configuration.application.port.in.ConfigurationManagementUseCase.ConfigurationView;
import ar.com.hexium.hcop.configuration.domain.ConfigurationDefinition;
import ar.com.hexium.hcop.protocol.application.port.in.ProtocolManagementUseCase.SaveProtocolCommand;
import ar.com.hexium.hcop.protocol.application.port.out.DrugCatalogPort;
import ar.com.hexium.hcop.protocol.application.port.out.ProtocolCatalogPort;
import ar.com.hexium.hcop.protocol.application.port.out.ProtocolCatalogPort.CatalogScheme;
import ar.com.hexium.hcop.protocol.domain.ProtocolDocument;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProtocolApplicationServiceTest {
  private final ConfigurationManagementUseCase configurations =
      mock(ConfigurationManagementUseCase.class);
  private final ProtocolCatalogPort catalog = mock(ProtocolCatalogPort.class);
  private final DrugCatalogPort drugs = mock(DrugCatalogPort.class);
  private final ProtocolApplicationService service =
      new ProtocolApplicationService(configurations, catalog, drugs);

  @BeforeEach
  void catalogEntries() {
    when(catalog.schemes()).thenReturn(List.of(
        scheme("347", "COLON - FOLFIRI", 14, 120),
        scheme("999", "MAMA - AC", 21, 90)));
  }

  @Test
  void mergesCustomProtocolsAndOnlyUnlinkedCatalogEntries() {
    when(configurations.list("protocol", false)).thenReturn(List.of(custom()));

    var result = service.list(false, true);

    assertThat(result.protocols()).extracting(item -> item.id())
        .containsExactly("12", "coir-999");
    assertThat(result.currentCount()).isEqualTo(1);
    assertThat(result.catalogCount()).isEqualTo(1);
    assertThat(result.protocols().getFirst().componentCount()).isEqualTo(1);
    assertThat(result.protocols().getFirst().durationMinutes()).isEqualTo(75);
  }

  @Test
  void opensCatalogDetailWithItsClinicalComponents() {
    when(catalog.scheme("347")).thenReturn(Optional.of(
        scheme("347", "COLON - FOLFIRI", 14, 120)));
    when(catalog.components("347")).thenReturn(List.of(
        ProtocolDocument.of(Map.of("drugName", "Irinotecan", "day", "1"))));

    var result = service.get("coir-347");

    assertThat(result.catalogOnly()).isTrue();
    assertThat(result.category()).isEqualTo("COLON");
    assertThat(result.components()).singleElement()
        .satisfies(component -> assertThat(component.text("drugName")).isEqualTo("Irinotecan"));
  }

  @Test
  void createsThroughConfigurationPortAndInvalidatesTheMergedCatalog() {
    ConfigurationView saved = custom();
    when(configurations.create(any())).thenReturn(saved);
    SaveProtocolCommand command = new SaveProtocolCommand(
        "FOLFIRI local",
        "Descripción",
        true,
        null,
        ProtocolDocument.of(Map.of(
            "cycleDays", 14,
            "durationMinutes", 120,
            "components", List.of(Map.of("drugName", "Irinotecan")))),
        UserId.of(7));

    var result = service.create(command);

    ArgumentCaptor<ConfigurationManagementUseCase.CreateCommand> captured =
        ArgumentCaptor.forClass(ConfigurationManagementUseCase.CreateCommand.class);
    verify(configurations).create(captured.capture());
    verify(catalog).invalidate();
    assertThat(captured.getValue().kind()).isEqualTo("protocol");
    assertThat(captured.getValue().definition().value()).isEqualTo(command.definition().value());
    assertThat(result.id()).isEqualTo("12");
  }

  @Test
  void rejectsInvalidDurationsBeforePersistence() {
    SaveProtocolCommand command = new SaveProtocolCommand(
        "Esquema inválido",
        "",
        true,
        null,
        ProtocolDocument.of(Map.of("durationMinutes", 0)),
        UserId.of(7));

    assertThatThrownBy(() -> service.create(command))
        .isInstanceOf(ProtocolFailure.class)
        .hasMessage("La duración operativa debe ser mayor que cero.");
  }

  @Test
  void rejectsCatalogMutationAndMalformedIdentifiersAsNotFound() {
    SaveProtocolCommand command = new SaveProtocolCommand(
        "No editable",
        "",
        true,
        null,
        ProtocolDocument.empty(),
        UserId.of(7));

    assertThatThrownBy(() -> service.update("coir-347", command))
        .isInstanceOfSatisfying(
            ProtocolFailure.class,
            failure -> assertThat(failure.type()).isEqualTo(ProtocolFailure.Type.NOT_FOUND));
    assertThatThrownBy(() -> service.get("desconocido"))
        .isInstanceOfSatisfying(
            ProtocolFailure.class,
            failure -> assertThat(failure.type()).isEqualTo(ProtocolFailure.Type.NOT_FOUND));
  }

  private ConfigurationView custom() {
    return new ConfigurationView(
        "12",
        "protocol",
        "folfiri-local",
        "FOLFIRI local",
        "Descripción",
        true,
        ConfigurationDefinition.of(Map.of(
            "category", "Colon",
            "cycleDays", 14,
            "durationMinutes", 75,
            "coirSchemeId", "347",
            "components", List.of(Map.of("drugName", "Irinotecan")))),
        3,
        Instant.parse("2026-07-30T10:00:00Z"),
        Instant.parse("2026-07-30T11:00:00Z"));
  }

  private CatalogScheme scheme(String id, String name, int cycleDays, int duration) {
    return new CatalogScheme(
        id,
        name,
        cycleDays,
        duration,
        ProtocolDocument.of(Map.of(
            "drugs", List.of(Map.of("id", "drug-1"), Map.of("id", "drug-2")))));
  }
}
