package ar.com.hexium.hcop.treatment.domain;

/** Una fila de droga de un ciclo, ya proyectada para la hoja de tratamiento imprimible. */
public record DrugLine(String drugName, String doseText, String doseUnit, String route, String administrationTime) {
}
