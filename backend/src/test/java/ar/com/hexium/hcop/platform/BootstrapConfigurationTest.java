package ar.com.hexium.hcop.platform;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import ar.com.hexium.hcop.auth.AuthService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.ApplicationArguments;

class BootstrapConfigurationTest {
  @Test
  void createsTheAdministratorBeforeRunningTasksInOrder() throws Exception {
    AuthService auth = mock(AuthService.class);
    BootstrapTask catalogs = mock(BootstrapTask.class);
    BootstrapTask patient = mock(BootstrapTask.class);
    ApplicationArguments arguments = mock(ApplicationArguments.class);

    new BootstrapConfiguration()
        .bootstrapLocalAdministrator(auth, List.of(catalogs, patient))
        .run(arguments);

    InOrder order = inOrder(auth, catalogs, patient);
    order.verify(auth).bootstrapAdministrator();
    order.verify(catalogs).run();
    order.verify(patient).run();
  }
}
