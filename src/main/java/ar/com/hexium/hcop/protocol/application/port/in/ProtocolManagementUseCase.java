package ar.com.hexium.hcop.protocol.application.port.in;

import ar.com.hexium.hcop.protocol.domain.ProtocolDocument;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.time.Instant;
import java.util.List;

/**
 * Casos de uso para consultar y administrar protocolos clínicos.
 */
public interface ProtocolManagementUseCase {
  ProtocolList list(boolean includeArchived, boolean includeCatalog);

  ProtocolView get(String id);

  ProtocolView create(SaveProtocolCommand command);

  ProtocolView update(String id, SaveProtocolCommand command);

  ProtocolView archive(String id, UserId actorId);

  List<CatalogView> catalog();

  List<ProtocolDocument> drugs(String query);

  record SaveProtocolCommand(
      String name,
      String description,
      boolean active,
      Long expectedRevision,
      ProtocolDocument definition,
      UserId actorId) {
  }

  record ProtocolList(
      List<ProtocolView> protocols,
      long currentCount,
      int catalogCount) {
  }

  record ProtocolView(
      String id,
      String key,
      String name,
      String description,
      boolean active,
      boolean catalogOnly,
      long revision,
      Instant createdAt,
      Instant updatedAt,
      String category,
      int cycleDays,
      Integer durationMinutes,
      String coirSchemeId,
      List<ProtocolDocument> components,
      int componentCount,
      ProtocolDocument definition) {
  }

  record CatalogView(
      String coirSchemeId,
      String schemeName,
      Integer durationMinutes,
      int cycleDays) {
  }
}
