package ar.com.hexium.hcop.system.infrastructure.configuration;

import ar.com.hexium.hcop.system.application.port.out.ApplicationVersionPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

@Component
public class BuildPropertiesVersionAdapter implements ApplicationVersionPort {
  private final String version;

  public BuildPropertiesVersionAdapter(ObjectProvider<BuildProperties> buildProperties) {
    BuildProperties build = buildProperties.getIfAvailable();
    this.version = build == null ? "1.0.0-dev" : build.getVersion();
  }

  @Override
  public String current() {
    return version;
  }
}
