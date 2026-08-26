package ar.com.hexium.hcop.admin.domain;

import java.time.Instant;
import java.util.List;

public record AdminUser(
    long id,
    String username,
    String email,
    String displayName,
    String specialty,
    String licenseNumber,
    boolean active,
    Instant lastLoginAt,
    List<RoleSummary> roles) {

  public record RoleSummary(
      long id, String key, String name, String description, boolean system, boolean active) {
  }
}
