package ar.com.hexium.hcop.admin.infrastructure.web;

import ar.com.hexium.hcop.admin.application.port.in.AdminManagementUseCase;
import ar.com.hexium.hcop.auth.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public class AdminController {
  private final AdminManagementUseCase admin;
  private final AdminJsonMapper json;
  private final AuthContext auth;

  public AdminController(AdminManagementUseCase admin, AdminJsonMapper json, AuthContext auth) {
    this.admin = admin;
    this.json = json;
    this.auth = auth;
  }

  @GetMapping("/api/admin/users")
  Map<String, Object> users(HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-users");
    List<Map<String, Object>> items = admin.users().stream().map(json::view).toList();
    return Map.of("ok", true, "items", items, "users", items);
  }

  @PostMapping("/api/admin/users")
  ResponseEntity<Map<String, Object>> createUser(@RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-users");
    var item = admin.createUser(json.createUserCommand(body, auth.require(request).userId()));
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true, "item", json.view(item)));
  }

  @PutMapping("/api/admin/users/{id}")
  Map<String, Object> updateUser(
      @PathVariable long id, @RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-users");
    var item = admin.updateUser(json.updateUserCommand(id, body, auth.require(request).userId()));
    return Map.of("ok", true, "item", json.view(item));
  }

  @GetMapping("/api/admin/roles")
  Map<String, Object> roles(HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-roles");
    List<Map<String, Object>> items = admin.roles().stream().map(json::view).toList();
    List<Map<String, Object>> permissionCatalog = admin.permissionCatalog().stream().map(json::view).toList();
    return Map.of("ok", true, "items", items, "roles", items, "permissionCatalog", permissionCatalog);
  }

  @PostMapping("/api/admin/roles")
  ResponseEntity<Map<String, Object>> createRole(@RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-roles");
    var item = admin.createRole(json.createRoleCommand(body, auth.require(request).userId()));
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true, "item", json.view(item)));
  }

  @PutMapping("/api/admin/roles/{id}")
  Map<String, Object> updateRole(
      @PathVariable long id, @RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-roles");
    var item = admin.updateRole(json.updateRoleCommand(id, body, auth.require(request).userId()));
    return Map.of("ok", true, "item", json.view(item));
  }

  @GetMapping("/api/admin/security-settings")
  Map<String, Object> security(HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-security");
    return Map.of("ok", true, "item", admin.security().map(json::view).orElseGet(Map::of));
  }

  @PutMapping("/api/admin/security-settings")
  Map<String, Object> updateSecurity(@RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-security");
    var item = admin.updateSecurity(json.updateSecurityCommand(body, auth.require(request).userId()));
    return Map.of("ok", true, "item", item.map(json::view).orElseGet(Map::of));
  }

  @GetMapping("/api/clinical/users")
  Map<String, Object> clinicalUsers(
      @RequestParam(defaultValue = "") String capability,
      HttpServletRequest request) {
    auth.require(request);
    List<Map<String, Object>> items = admin.usersWithPermission(capability).stream().map(json::view).toList();
    return Map.of("ok", true, "items", items, "users", items);
  }
}
