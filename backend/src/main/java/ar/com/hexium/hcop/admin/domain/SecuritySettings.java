package ar.com.hexium.hcop.admin.domain;

/** {@code autoUserId} vacío ("") equivale a "sin usuario automático asignado". */
public record SecuritySettings(
    boolean loginRequired,
    String autoUserId,
    String autoUserUsername,
    String autoUserEmail,
    String autoUserName,
    int sessionDurationMinutes,
    long revision) {
}
