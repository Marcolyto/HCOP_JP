package ar.com.hexium.hcop.admin.infrastructure.web;

import ar.com.hexium.hcop.admin.application.port.in.AdminManagementUseCase.CreateRoleCommand;
import ar.com.hexium.hcop.admin.application.port.in.AdminManagementUseCase.CreateUserCommand;
import ar.com.hexium.hcop.admin.application.port.in.AdminManagementUseCase.UpdateRoleCommand;
import ar.com.hexium.hcop.admin.application.port.in.AdminManagementUseCase.UpdateSecurityCommand;
import ar.com.hexium.hcop.admin.application.port.in.AdminManagementUseCase.UpdateUserCommand;
import ar.com.hexium.hcop.admin.domain.AdminRole;
import ar.com.hexium.hcop.admin.domain.AdminUser;
import ar.com.hexium.hcop.admin.domain.Permission;
import ar.com.hexium.hcop.admin.domain.SecuritySettings;
import ar.com.hexium.hcop.sharedkernel.domain.UserId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Traduce el contrato JSON histórico a comandos tipados y viceversa.
 */
@Component
public class AdminJsonMapper {

  public CreateUserCommand createUserCommand(JsonNode input, long actorId) {
    return new CreateUserCommand(
        text(input, "username"),
        text(input, "email"),
        text(input, "displayName", "name"),
        text(input, "specialty"),
        text(input, "licenseNumber"),
        input.path("active").asBoolean(true),
        text(input, "password"),
        roleIds(input),
        UserId.of(actorId));
  }

  public UpdateUserCommand updateUserCommand(long id, JsonNode input, long actorId) {
    return new UpdateUserCommand(
        id,
        text(input, "username"),
        text(input, "email"),
        text(input, "displayName", "name"),
        text(input, "specialty"),
        text(input, "licenseNumber"),
        input.path("active").asBoolean(true),
        text(input, "password"),
        roleIds(input),
        UserId.of(actorId));
  }

  public CreateRoleCommand createRoleCommand(JsonNode input, long actorId) {
    return new CreateRoleCommand(
        text(input, "key"),
        text(input, "name", "displayName"),
        text(input, "description"),
        input.path("active").asBoolean(true),
        permissions(input),
        UserId.of(actorId));
  }

  public UpdateRoleCommand updateRoleCommand(long id, JsonNode input, long actorId) {
    return new UpdateRoleCommand(
        id,
        text(input, "name", "displayName"),
        text(input, "description"),
        input.path("active").asBoolean(true),
        permissions(input),
        UserId.of(actorId));
  }

  public UpdateSecurityCommand updateSecurityCommand(JsonNode input, long actorId) {
    return new UpdateSecurityCommand(
        input.path("loginRequired").asBoolean(true),
        input.path("sessionDurationMinutes").asInt(43200),
        UserId.of(actorId));
  }

  public Map<String, Object> view(AdminUser item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", String.valueOf(item.id()));
    result.put("username", item.username());
    result.put("email", item.email());
    result.put("displayName", item.displayName());
    result.put("specialty", item.specialty());
    result.put("licenseNumber", item.licenseNumber());
    result.put("active", item.active());
    result.put("lastLoginAt", item.lastLoginAt() == null ? "" : item.lastLoginAt().toString());
    result.put("roles", item.roles().stream().map(this::view).toList());
    return result;
  }

  public Map<String, Object> view(AdminUser.RoleSummary item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", String.valueOf(item.id()));
    result.put("key", item.key());
    result.put("name", item.name());
    result.put("description", item.description());
    result.put("system", item.system());
    result.put("active", item.active());
    return result;
  }

  public Map<String, Object> view(AdminRole item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", String.valueOf(item.id()));
    result.put("key", item.key());
    result.put("name", item.name());
    result.put("description", item.description());
    result.put("system", item.system());
    result.put("active", item.active());
    result.put("userCount", item.userCount());
    result.put("permissions", item.permissions());
    return result;
  }

  public Map<String, Object> view(Permission item) {
    return Map.of("key", item.key(), "name", item.name(), "description", item.description());
  }

  public Map<String, Object> view(SecuritySettings item) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("loginRequired", item.loginRequired());
    result.put("autoUserId", item.autoUserId());
    result.put("autoUserUsername", item.autoUserUsername());
    result.put("autoUserEmail", item.autoUserEmail());
    result.put("autoUserName", item.autoUserName());
    result.put("sessionDurationMinutes", item.sessionDurationMinutes());
    result.put("revision", item.revision());
    return result;
  }

  private List<String> roleIds(JsonNode input) {
    List<String> roleIds = new ArrayList<>();
    input.path("roleIds").forEach(node -> roleIds.add(node.asText()));
    return roleIds;
  }

  private List<String> permissions(JsonNode input) {
    List<String> permissions = new ArrayList<>();
    input.path("permissions").forEach(node -> permissions.add(node.asText("")));
    return permissions;
  }

  private String text(JsonNode node, String... keys) {
    for (String key : keys) {
      String value = node.path(key).asText("").trim();
      if (!value.isBlank()) return value;
    }
    return "";
  }
}
