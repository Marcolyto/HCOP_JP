package ar.com.hexium.hcop.integration.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class StudyRepositoriesControllerTest {
  private final StudyRepositorySettings settings = mock(StudyRepositorySettings.class);
  private final AuthContext auth = mock(AuthContext.class);
  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final StudyRepositoriesController controller = new StudyRepositoriesController(settings, auth);
  private final ObjectMapper mapper = new ObjectMapper();

  @Test void listadoExigePermisoDeLecturaAntesDeConsultarYNoSeAlmacenaEnCache() {
    Map<String, Object> view = Map.of("ok", true, "items", List.of(Map.of("id", "fuesmen", "hasPassword", true)));
    when(settings.publicView()).thenReturn(view);

    var response = controller.list(request);

    var order = inOrder(auth, settings);
    order.verify(auth).requirePermission(request, "section.configuration.view");
    order.verify(settings).publicView();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(response.getBody()).isSameAs(view);
    verifyNoMoreInteractions(auth, settings);
  }

  @Test void guardadoExigeGestionYEntregaIdYCuerpoExactosConActorDeLaSesion() {
    ObjectNode body = body().put("id", "idm").put("actorId", 9999);
    var original = body.deepCopy();
    var actor = actor();
    Map<String, Object> updated = Map.of("ok", true, "items", List.of(Map.of("id", "fuesmen", "hasPassword", true)));
    when(auth.require(request)).thenReturn(actor);
    when(settings.update("fuesmen", body, actor.userId())).thenReturn(updated);

    var response = controller.save("fuesmen", body, request);

    var order = inOrder(auth, settings);
    order.verify(auth).requirePermission(request, "section.configuration.manage");
    order.verify(auth).require(request);
    order.verify(settings).update(eq("fuesmen"), same(body), eq(actor.userId()));
    assertThat(body).isEqualTo(original);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(response.getBody()).isSameAs(updated);
    verifyNoMoreInteractions(auth, settings);
  }

  @Test void lecturaDenegadaNoAccedeAConfiguracion() {
    var denied = new ApiException(HttpStatus.FORBIDDEN, "Sin permiso de lectura.");
    doThrow(denied).when(auth).requirePermission(request, "section.configuration.view");

    assertThatThrownBy(() -> controller.list(request)).isSameAs(denied);

    verifyNoInteractions(settings);
  }

  @Test void gestionDenegadaNoConsultaActorNiModificaConfiguracion() {
    var denied = new ApiException(HttpStatus.FORBIDDEN, "Sin permiso de gestión.");
    doThrow(denied).when(auth).requirePermission(request, "section.configuration.manage");

    assertThatThrownBy(() -> controller.save("fuesmen", body(), request)).isSameAs(denied);

    verify(auth, never()).require(request);
    verifyNoInteractions(settings);
  }

  @Test void sesionAusenteImpideAmbasOperacionesAntesDeAccederASettings() {
    var unauthorized = new ApiException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión.");
    doThrow(unauthorized).when(auth).requirePermission(request, "section.configuration.view");
    doThrow(unauthorized).when(auth).requirePermission(request, "section.configuration.manage");

    assertThatThrownBy(() -> controller.list(request)).isSameAs(unauthorized);
    assertThatThrownBy(() -> controller.save("fuesmen", body(), request)).isSameAs(unauthorized);

    verifyNoInteractions(settings);
  }

  @Test void validacionDeSettingsSePropagaSinConvertirElErrorEnGuardadoExitoso() {
    var actor = actor();
    var body = body();
    var invalid = new ApiException(HttpStatus.BAD_REQUEST, "El repositorio no existe.");
    when(auth.require(request)).thenReturn(actor);
    when(settings.update("unknown", body, actor.userId())).thenThrow(invalid);

    assertThatThrownBy(() -> controller.save("unknown", body, request)).isSameAs(invalid);

    verify(settings).update(eq("unknown"), same(body), eq(actor.userId()));
    verify(settings, never()).publicView();
  }

  private ObjectNode body() {
    var body = mapper.createObjectNode().put("username", "fixture-user").put("passwordAction", "replace")
        .put("password", "fixture-new-password");
    body.putArray("endpoints").addObject().put("key", "BASE_URL").put("url", "https://repository.fixture.invalid/");
    return body;
  }

  private static SessionPrincipal actor() {
    return new SessionPrincipal(71L, "fixture-admin", "", "Administración", "", "", true, null,
        List.of(), Set.of("section.configuration.view", "section.configuration.manage"));
  }
}
