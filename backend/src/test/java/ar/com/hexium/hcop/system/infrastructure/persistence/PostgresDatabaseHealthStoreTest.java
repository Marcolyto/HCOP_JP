package ar.com.hexium.hcop.system.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresDatabaseHealthStoreTest {

  @Test
  void baseArribaCuandoLaConsultaDevuelveUno() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject("select 1", Integer.class)).thenReturn(1);
    PostgresDatabaseHealthStore store = new PostgresDatabaseHealthStore(jdbc);

    assertThat(store.check().up()).isTrue();
  }

  @Test
  void baseAbajoCuandoLaConsultaNoDevuelveUno() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForObject("select 1", Integer.class)).thenReturn(null);
    PostgresDatabaseHealthStore store = new PostgresDatabaseHealthStore(jdbc);

    assertThat(store.check().up()).isFalse();
  }
}
