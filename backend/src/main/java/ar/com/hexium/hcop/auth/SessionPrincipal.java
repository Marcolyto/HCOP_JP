package ar.com.hexium.hcop.auth;

import java.util.List;
import java.util.Set;

public record SessionPrincipal(
    long userId,
    String username,
    String email,
    String displayName,
    String specialty,
    String licenseNumber,
    boolean active,
    Long activePatientId,
    List<RoleView> roles,
    Set<String> permissions) {

  public record RoleView(String id, String key, String name) {
  }

  public boolean hasPermission(String permission) {
    return permissions.contains(permission);
  }
}
