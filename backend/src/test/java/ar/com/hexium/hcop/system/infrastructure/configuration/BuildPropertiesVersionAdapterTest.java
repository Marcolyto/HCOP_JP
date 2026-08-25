package ar.com.hexium.hcop.system.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

class BuildPropertiesVersionAdapterTest {

  @Test
  void usaLaVersionDeBuildPropertiesCuandoEstaDisponible() {
    @SuppressWarnings("unchecked")
    ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
    Properties properties = new Properties();
    properties.setProperty("version", "9.9.9");
    when(provider.getIfAvailable()).thenReturn(new BuildProperties(properties));

    BuildPropertiesVersionAdapter adapter = new BuildPropertiesVersionAdapter(provider);

    assertThat(adapter.current()).isEqualTo("9.9.9");
  }

  @Test
  void caeAVersionDeDesarrolloSinBuildProperties() {
    @SuppressWarnings("unchecked")
    ObjectProvider<BuildProperties> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);

    BuildPropertiesVersionAdapter adapter = new BuildPropertiesVersionAdapter(provider);

    assertThat(adapter.current()).isEqualTo("1.0.0-dev");
  }
}
