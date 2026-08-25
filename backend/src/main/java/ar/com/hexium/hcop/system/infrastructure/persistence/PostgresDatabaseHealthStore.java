package ar.com.hexium.hcop.system.infrastructure.persistence;

import ar.com.hexium.hcop.system.application.port.out.DatabaseHealthStore;
import ar.com.hexium.hcop.system.domain.DatabaseHealth;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresDatabaseHealthStore implements DatabaseHealthStore {
  private final JdbcTemplate jdbc;

  public PostgresDatabaseHealthStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public DatabaseHealth check() {
    Integer result = jdbc.queryForObject("select 1", Integer.class);
    return new DatabaseHealth(result != null && result == 1);
  }
}
