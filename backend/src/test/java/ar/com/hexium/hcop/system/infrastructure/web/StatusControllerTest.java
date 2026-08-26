package ar.com.hexium.hcop.system.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.system.application.port.in.SystemStatusUseCase;
import ar.com.hexium.hcop.system.application.port.in.SystemStatusUseCase.ClinicalStatusView;
import ar.com.hexium.hcop.system.application.port.in.SystemStatusUseCase.RuntimeStatusView;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatusControllerTest {

  @Test
  void clinicalProyectaLaVistaDelUseCase() {
    SystemStatusUseCase status = mock(SystemStatusUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    Instant now = Instant.parse("2026-08-25T10:00:00Z");
    when(status.clinicalStatus()).thenReturn(new ClinicalStatusView(true, "2.3.4", now));
    StatusController controller = new StatusController(status, auth);

    Map<String, Object> response = controller.clinical(request);

    assertThat(response).containsExactly(
        Map.entry("ok", true),
        Map.entry("service", "hcop-jp"),
        Map.entry("engine", "java-postgresql"),
        Map.entry("version", "2.3.4"),
        Map.entry("database", "postgresql"),
        Map.entry("unified", true),
        Map.entry("localOnly", true),
        Map.entry("timestamp", now.toString()));
  }

  @Test
  void clinicalReflejaLaBaseCaida() {
    SystemStatusUseCase status = mock(SystemStatusUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(status.clinicalStatus()).thenReturn(new ClinicalStatusView(false, "2.3.4", Instant.EPOCH));
    StatusController controller = new StatusController(status, auth);

    Map<String, Object> response = controller.clinical(request);

    assertThat(response).containsEntry("ok", false);
  }

  @Test
  void liraCompatibilityEsEstaticoYNoExigePermiso() {
    SystemStatusUseCase status = mock(SystemStatusUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    StatusController controller = new StatusController(status, auth);

    Map<String, Object> response = controller.liraCompatibility(request);

    assertThat(response).containsEntry("ok", true)
        .containsEntry("available", true)
        .containsEntry("authenticated", true)
        .containsEntry("local", true)
        .containsEntry("independent", true)
        .containsEntry("message", "HCOP JP opera con su base PostgreSQL local.");
  }

  @Test
  void runtimeProyectaLaVistaDelUseCase() {
    SystemStatusUseCase status = mock(SystemStatusUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    Instant now = Instant.parse("2026-08-25T10:00:00Z");
    when(status.runtimeStatus()).thenReturn(new RuntimeStatusView("2.3.4", now));
    StatusController controller = new StatusController(status, auth);

    Map<String, Object> response = controller.runtime();

    assertThat(response).containsOnly(
        Map.entry("ok", true),
        Map.entry("running", true),
        Map.entry("service", "hcop-jp"),
        Map.entry("engine", "java-postgresql"),
        Map.entry("version", "2.3.4"),
        Map.entry("managedBy", "docker-compose"),
        Map.entry("timestamp", now.toString()));
  }

  @Test
  void stopExigePermisoYNoDetieneNada() {
    SystemStatusUseCase status = mock(SystemStatusUseCase.class);
    AuthContext auth = mock(AuthContext.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    StatusController controller = new StatusController(status, auth);

    Map<String, Object> response = controller.stop(request);

    verify(auth).requirePermission(request, "admin.manage-security");
    assertThat(response).containsEntry("ok", false).containsEntry("stopped", false);
  }
}
