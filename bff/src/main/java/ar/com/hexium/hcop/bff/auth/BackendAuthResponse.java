package ar.com.hexium.hcop.bff.auth;

import tools.jackson.databind.JsonNode;

/** Pass-through literal de status + body del backend (mismo principio que el proxy general). */
public record BackendAuthResponse(int status, JsonNode body, String setCookieHeader) {}
