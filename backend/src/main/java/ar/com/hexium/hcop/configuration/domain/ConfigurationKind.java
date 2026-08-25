package ar.com.hexium.hcop.configuration.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Familias de configuración admitidas por el sistema.
 */
public enum ConfigurationKind {
  GUIDE("guide"),
  STUDY_TEMPLATE("study-template"),
  DIAGNOSIS_SETTING("diagnosis-setting"),
  DIAGNOSIS_EQUIVALENCE("diagnosis-equivalence"),
  CALCULATOR("calculator"),
  TOOL_SETTINGS("tool-settings"),
  DAY_HOSPITAL_SETTINGS("day-hospital-settings"),
  RESEARCH_FORM("research-form"),
  PROTOCOL("protocol");

  private final String externalName;

  ConfigurationKind(String externalName) {
    this.externalName = externalName;
  }

  public String externalName() {
    return externalName;
  }

  public static Optional<ConfigurationKind> fromExternalName(String value) {
    return Arrays.stream(values())
        .filter(kind -> kind.externalName.equals(value))
        .findFirst();
  }
}
