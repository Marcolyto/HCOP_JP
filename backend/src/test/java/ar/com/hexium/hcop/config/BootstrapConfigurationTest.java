package ar.com.hexium.hcop.config;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.patient.DefaultDemoPatientBootstrap;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.ApplicationArguments;

class BootstrapConfigurationTest {
  @Test
  void createsTheAdministratorBeforeAuditedCatalogAndPatientSeeds() throws Exception {
    AuthService auth = mock(AuthService.class);
    ClinicalCatalogBootstrap catalogs = mock(ClinicalCatalogBootstrap.class);
    DefaultDemoPatientBootstrap patient = mock(DefaultDemoPatientBootstrap.class);
    ApplicationArguments arguments = mock(ApplicationArguments.class);

    new BootstrapConfiguration()
        .bootstrapLocalAdministrator(auth, catalogs, patient)
        .run(arguments);

    InOrder order = inOrder(auth, catalogs, patient);
    order.verify(auth).bootstrapAdministrator();
    order.verify(catalogs).seed();
    order.verify(patient).seed();
  }
}
