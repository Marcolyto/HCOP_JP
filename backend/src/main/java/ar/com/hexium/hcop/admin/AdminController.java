package ar.com.hexium.hcop.admin;

import ar.com.hexium.hcop.auth.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
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
  private final AdminService admin;
  private final AuthContext auth;

  public AdminController(AdminService admin, AuthContext auth) {
    this.admin = admin;
    this.auth = auth;
  }

  @GetMapping("/api/admin/users")
  Map<String, Object> users(HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-users");
    var items = admin.users();
    return Map.of("ok", true, "items", items, "users", items);
  }

  @PostMapping("/api/admin/users")
  ResponseEntity<Map<String, Object>> createUser(@RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-users");
    var item = admin.createUser(body, auth.require(request).userId());
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true, "item", item));
  }

  @PutMapping("/api/admin/users/{id}")
  Map<String, Object> updateUser(
      @PathVariable long id, @RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-users");
    return Map.of("ok", true, "item", admin.updateUser(id, body, auth.require(request).userId()));
  }

  @GetMapping("/api/admin/roles")
  Map<String, Object> roles(HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-roles");
    var items = admin.roles();
    return Map.of(
        "ok", true, "items", items, "roles", items,
        "permissionCatalog", admin.permissionCatalog());
  }

  @PostMapping("/api/admin/roles")
  ResponseEntity<Map<String, Object>> createRole(@RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-roles");
    var item = admin.createRole(body, auth.require(request).userId());
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("ok", true, "item", item));
  }

  @PutMapping("/api/admin/roles/{id}")
  Map<String, Object> updateRole(
      @PathVariable long id, @RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-roles");
    return Map.of("ok", true, "item", admin.updateRole(id, body, auth.require(request).userId()));
  }

  @GetMapping("/api/admin/security-settings")
  Map<String, Object> security(HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-security");
    return Map.of("ok", true, "item", admin.security());
  }

  @PutMapping("/api/admin/security-settings")
  Map<String, Object> updateSecurity(@RequestBody JsonNode body, HttpServletRequest request) {
    auth.requirePermission(request, "admin.manage-security");
    return Map.of("ok", true, "item", admin.updateSecurity(body, auth.require(request).userId()));
  }

  @GetMapping("/api/clinical/users")
  Map<String, Object> clinicalUsers(
      @RequestParam(defaultValue = "") String capability,
      HttpServletRequest request) {
    auth.require(request);
    var items = admin.usersWithPermission(capability);
    return Map.of("ok", true, "items", items, "users", items);
  }
}
