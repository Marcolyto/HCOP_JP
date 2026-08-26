package ar.com.hexium.hcop.system.application.port.out;

import ar.com.hexium.hcop.system.domain.DatabaseHealth;

public interface DatabaseHealthStore {

  DatabaseHealth check();
}
