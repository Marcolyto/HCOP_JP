package ar.com.hexium.hcop.system.domain;

/** Resultado de comprobar que la base de datos responde. */
public record DatabaseHealth(boolean up) {
}
