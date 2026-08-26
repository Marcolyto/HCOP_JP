package ar.com.hexium.hcop.admin.domain;

import java.util.Set;

public record AdminRole(
    long id,
    String key,
    String name,
    String description,
    boolean system,
    boolean active,
    long userCount,
    Set<String> permissions) {
}
