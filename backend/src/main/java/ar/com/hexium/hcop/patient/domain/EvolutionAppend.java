package ar.com.hexium.hcop.patient.domain;

/** {@code evolution} es el mismo árbol de evolución opaco (con {@code immutable:true} ya
 * agregado) que consumen {@code workflow}/{@code qr}/{@code treatment} al anexar una nota. */
public record EvolutionAppend(Object evolution, long revision) {
}
