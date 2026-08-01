package ar.com.hexium.hcop.protocol.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.hexium.hcop.protocol.application.port.in.ProtocolManagementUseCase.ProtocolView;
import ar.com.hexium.hcop.protocol.domain.ProtocolDocument;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ProtocolJsonMapperTest {
  private final ProtocolJsonMapper json = new ProtocolJsonMapper(JsonMapper.builder().build());

  @Test
  void preservesTheHistoricalFlatCustomProtocolShape() {
    ProtocolView source = new ProtocolView(
        "12",
        "folfiri-local",
        "FOLFIRI local",
        "Descripción",
        true,
        false,
        3,
        Instant.parse("2026-07-30T10:00:00Z"),
        Instant.parse("2026-07-30T11:00:00Z"),
        "Colon",
        14,
        75,
        "347",
        List.of(ProtocolDocument.of(Map.of("drugName", "Irinotecan"))),
        1,
        ProtocolDocument.of(Map.of(
            "category", "Colon",
            "cycleDays", 14,
            "durationMinutes", 75,
            "coirSchemeId", "347",
            "components", List.of(Map.of("drugName", "Irinotecan")))));

    Map<String, Object> result = json.view(source);

    assertThat(result)
        .containsEntry("id", "12")
        .containsEntry("kind", "protocol")
        .containsEntry("itemKind", "protocol")
        .containsEntry("name", "FOLFIRI local")
        .containsEntry("displayName", "FOLFIRI local")
        .containsEntry("category", "Colon")
        .containsEntry("cycleDays", 14)
        .containsEntry("durationMinutes", 75)
        .containsEntry("durationText", "1 h 15 min")
        .containsEntry("componentCount", 1)
        .containsEntry("catalogOnly", false);
    assertThat((List<?>) result.get("coirLinks"))
        .singleElement()
        .isEqualTo(Map.of("coirSchemeId", "347"));
  }

  @Test
  void mapsCatalogEntriesWithoutLocalVersionMetadata() {
    ProtocolView source = new ProtocolView(
        "coir-347",
        "",
        "COLON - FOLFIRI",
        "Catálogo",
        true,
        true,
        0,
        null,
        null,
        "COLON",
        14,
        120,
        "347",
        List.of(),
        2,
        ProtocolDocument.empty());

    Map<String, Object> result = json.view(source);

    assertThat(result)
        .containsEntry("id", "coir-347")
        .containsEntry("coirSchemeId", "347")
        .containsEntry("catalogOnly", true)
        .containsEntry("durationText", "2 h")
        .doesNotContainKeys("revision", "itemKind", "createdAt");
    assertThat((List<?>) result.get("coirLinks")).isEmpty();
  }
}
