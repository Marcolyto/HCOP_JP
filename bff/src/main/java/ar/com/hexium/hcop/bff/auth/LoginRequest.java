package ar.com.hexium.hcop.bff.auth;

/** Mismo shape que {@code AuthController.LoginRequest} del backend — se relee sin cambios. */
public record LoginRequest(String username, String email, String password) {}
