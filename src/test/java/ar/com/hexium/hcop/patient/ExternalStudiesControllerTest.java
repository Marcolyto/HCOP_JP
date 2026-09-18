package ar.com.hexium.hcop.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.integration.studies.ExternalStudySearchService;
import ar.com.hexium.hcop.integration.studies.ExternalStudyResourceService;
import ar.com.hexium.hcop.integration.studies.ExternalStudyResource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ExternalStudiesControllerTest {
  private final PatientService patients = mock(PatientService.class);
  private final ExternalStudySearchService studies = mock(ExternalStudySearchService.class);
  private final AuthService sessions = mock(AuthService.class);
  private final ExternalStudyResourceService resources = mock(ExternalStudyResourceService.class);
  private final ExternalStudiesController controller = new ExternalStudiesController(patients, studies, new AuthContext(), sessions, resources);
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @Test void requiresSessionBeforeAnyExternalRequest() {
    assertStatus(HttpStatus.UNAUTHORIZED, () -> controller.search(42, request));
    verifyNoInteractions(studies, patients);
  }

  @Test void requiresStudiesPermissionAndActivePatient() {
    principal(42L, Set.of("section.history.view"));
    assertStatus(HttpStatus.FORBIDDEN, () -> controller.search(42, request));
    principal(43L, Set.of("section.studies.view"));
    assertStatus(HttpStatus.CONFLICT, () -> controller.search(42, request));
    verifyNoInteractions(studies, patients);
  }

  @Test void searchesCanonicalIdentityWithoutSavingAndPreservesPartialResults() {
    var principal = principal(42L, Set.of("section.studies.view"));
    when(patients.require(42)).thenReturn(patient("99.000.123"));
    var results = new ObjectMapper().createObjectNode().put("partial", true).put("total", 1);
    results.putArray("studies").addObject().put("id", "external:centro:123");
    results.putArray("sources").addObject().put("id", "idm").put("status", "error");
    when(studies.search("99000123")).thenReturn(results);
    when(sessions.authenticate("session-token")).thenReturn(Optional.of(principal));
    var response = controller.search(42, request);
    assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    assertThat(response.getBody().path("patientId").asText()).isEqualTo("42");
    assertThat(response.getBody().path("studies").size()).isEqualTo(1);
    assertThat(response.getBody().path("partial").asBoolean()).isTrue();
    assertThat(results.has("patientId")).isFalse();
    verify(studies).search("99000123");
  }

  @Test void refusesResultsWhenActivePatientChangesDuringSearch() {
    principal(42L, Set.of("section.studies.view"));
    when(patients.require(42)).thenReturn(patient("99000123"));
    when(studies.search("99000123")).thenReturn(new ObjectMapper().createObjectNode());
    when(sessions.authenticate("session-token")).thenReturn(Optional.of(actor(43L, Set.of("section.studies.view"))));
    assertStatus(HttpStatus.CONFLICT, () -> controller.search(42, request));
  }

  @Test void refusesResultsWhenDniChangesDuringSearch() {
    var principal = principal(42L, Set.of("section.studies.view"));
    when(patients.require(42)).thenReturn(patient("99000123"), patient("99000124"));
    when(studies.search("99000123")).thenReturn(new ObjectMapper().createObjectNode());
    when(sessions.authenticate("session-token")).thenReturn(Optional.of(principal));
    assertStatus(HttpStatus.CONFLICT, () -> controller.search(42, request));
  }

  @Test void invalidDniNeverReachesProviders() {
    principal(42L, Set.of("section.studies.view"));
    when(patients.require(42)).thenReturn(patient("not-a-dni"));
    assertStatus(HttpStatus.BAD_REQUEST, () -> controller.search(42, request));
    verifyNoInteractions(studies);
  }

  @Test void rewritesInstitutionalResourcesLocallyWithoutMutatingProviderResults() {
    ready();
    var result = new ObjectMapper().createObjectNode();
    var rows = result.putArray("studies");
    List<String> sources = List.of("fuesmen", "idm", "labo", "centro", "espanol", "malargue", "patologia");
    for (String source : sources) {
      rows.addObject().put("id", "external:" + source + ":1.2+3")
          .put("sourceId", source).put("reportUrl", "https://institution.test/report")
          .put("studyUrl", "https://institution.test/viewer");
    }
    when(studies.search("99000123")).thenReturn(result);
    var response = controller.search(42, request).getBody();
    for (int i = 0; i < 6; i++) {
      String base = "/api/patients/42/external-studies/" + sources.get(i) + "/1.2+3/";
      assertThat(response.path("studies").get(i).path("reportUrl").asText()).isEqualTo(base + "report");
      assertThat(response.path("studies").get(i).path("studyUrl").asText())
          .isEqualTo(i < 3 ? base + "study" : "https://institution.test/viewer");
    }
    assertThat(response.path("studies").get(6).path("reportUrl").asText()).isEqualTo("https://institution.test/report");
    assertThat(response.path("studies").get(6).path("studyUrl").asText()).isEqualTo("https://institution.test/viewer");
    for (var original : result.path("studies")) {
      assertThat(original.path("reportUrl").asText()).isEqualTo("https://institution.test/report");
      assertThat(original.path("studyUrl").asText()).isEqualTo("https://institution.test/viewer");
    }
  }

  @Test void reportRewritingPreservesMissingReportsAndRejectsMismatchedSourceIds() {
    ready();
    var result = new ObjectMapper().createObjectNode();
    var rows = result.putArray("studies");
    rows.addObject().put("id", "external:centro:A B?#%")
        .put("sourceId", "centro").put("reportUrl", "https://institution.test/report")
        .put("studyUrl", "https://institution.test/#/124/opaque-token?fromportal=true");
    rows.addObject().put("id", "external:espanol:123").put("sourceId", "espanol")
        .put("reportUrl", "").put("studyUrl", "https://institution.test/viewer");
    rows.addObject().put("id", "external:centro:123").put("sourceId", "malargue")
        .put("reportUrl", "https://institution.test/report");
    rows.addObject().put("id", "external:malargue:123").put("sourceId", "malargue")
        .put("studyUrl", "https://institution.test/viewer");
    when(studies.search("99000123")).thenReturn(result);
    var response = controller.search(42, request).getBody().path("studies");
    assertThat(response.get(0).path("reportUrl").asText())
        .isEqualTo("/api/patients/42/external-studies/centro/A%20B%3F%23%25/report");
    assertThat(response.get(0).path("studyUrl").asText())
        .isEqualTo("https://institution.test/#/124/opaque-token?fromportal=true");
    assertThat(response.get(1).path("reportUrl").asText()).isEmpty();
    assertThat(response.get(2).path("reportUrl").asText()).isEqualTo("https://institution.test/report");
    assertThat(response.get(3).has("reportUrl")).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"fuesmen", "idm", "labo", "centro", "espanol", "malargue"})
  void resourceRequiresSessionPermissionAndActivePatientBeforeFetching(String source) {
    assertStatus(HttpStatus.UNAUTHORIZED, () -> controller.resource(42, source, "123", "report", request));
    principal(42L, Set.of("section.history.view"));
    assertStatus(HttpStatus.FORBIDDEN, () -> controller.resource(42, source, "123", "report", request));
    principal(43L, Set.of("section.studies.view"));
    assertStatus(HttpStatus.CONFLICT, () -> controller.resource(42, source, "123", "report", request));
    verifyNoInteractions(resources, patients);
  }

  @ParameterizedTest
  @ValueSource(strings = {"fuesmen", "idm", "labo", "centro", "espanol", "malargue"})
  void pdfIsReturnedWithoutCachingAndUsesCanonicalPatientDni(String source) {
    ready();
    byte[] bytes = "%PDF-1.7\nfixture".getBytes(StandardCharsets.UTF_8);
    when(resources.resolve("99000123", source, "123", "report"))
        .thenReturn(new ExternalStudyResource(bytes, "application/pdf", "informe.pdf", null));
    var response = controller.resource(42, source, "123", "report", request);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(bytes);
    assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
    assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(response.getHeaders().getFirst("Pragma")).isEqualTo("no-cache");
    assertThat(response.getHeaders().getFirst("Referrer-Policy")).isEqualTo("no-referrer");
    assertThat(response.getHeaders().getFirst("Content-Security-Policy"))
        .contains("default-src 'none'", "frame-ancestors 'none'").doesNotContain("sandbox");
    assertThat(response.getHeaders().getContentDisposition().getType()).isEqualTo("inline");
    verify(resources).resolve("99000123", source, "123", "report");
  }

  @ParameterizedTest
  @ValueSource(strings = {"centro", "espanol", "malargue"})
  void portalReportRechecksSessionPermissionsPatientAndDniAfterDownload(String source) {
    var original = ready();
    when(resources.resolve("99000123", source, "123", "report"))
        .thenReturn(new ExternalStudyResource("%PDF-1.7\nfixture".getBytes(StandardCharsets.UTF_8),
            "application/pdf", "informe.pdf", null));
    when(sessions.authenticate("session-token")).thenReturn(Optional.empty());
    assertStatus(HttpStatus.UNAUTHORIZED, () -> controller.resource(42, source, "123", "report", request));
    when(sessions.authenticate("session-token")).thenReturn(Optional.of(actor(42L, Set.of("section.history.view"))));
    assertStatus(HttpStatus.FORBIDDEN, () -> controller.resource(42, source, "123", "report", request));
    when(sessions.authenticate("session-token")).thenReturn(Optional.of(new SessionPrincipal(
        8, "other", "", "Other", "", "", true, 42L, List.of(), Set.of("section.studies.view"))));
    assertStatus(HttpStatus.FORBIDDEN, () -> controller.resource(42, source, "123", "report", request));
    when(sessions.authenticate("session-token")).thenReturn(Optional.of(actor(43L, Set.of("section.studies.view"))));
    assertStatus(HttpStatus.CONFLICT, () -> controller.resource(42, source, "123", "report", request));
    when(sessions.authenticate("session-token")).thenReturn(Optional.of(original));
    when(patients.require(42)).thenReturn(patient("99000123"), patient("99000124"));
    assertStatus(HttpStatus.CONFLICT, () -> controller.resource(42, source, "123", "report", request));
  }

  @Test void institutionalHtmlHasIsolatedOriginAndCannotRunScriptsOrForms() {
    ready();
    when(resources.resolve("99000123", "labo", "123", "report"))
        .thenReturn(new ExternalStudyResource("<p>Informe</p>".getBytes(StandardCharsets.UTF_8), "text/html", "informe.html", null));
    var response = controller.resource(42, "labo", "123", "report", request);
    assertThat(response.getHeaders().getFirst("Content-Security-Policy"))
        .contains("sandbox;", "default-src 'none'", "form-action 'none'", "base-uri 'none'")
        .doesNotContain("allow-same-origin", "allow-scripts");
    assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/html;charset=UTF-8");
  }

  @Test void resourceRechecksSessionPatientAndIdentityAfterInstitutionResponds() {
    var original = ready();
    when(resources.resolve("99000123", "idm", "123", "study"))
        .thenReturn(new ExternalStudyResource(null, null, null, "https://institution.test/viewer"));
    when(sessions.authenticate("session-token")).thenReturn(Optional.empty());
    assertStatus(HttpStatus.UNAUTHORIZED, () -> controller.resource(42, "idm", "123", "study", request));
    when(sessions.authenticate("session-token")).thenReturn(Optional.of(actor(43L, Set.of("section.studies.view"))));
    assertStatus(HttpStatus.CONFLICT, () -> controller.resource(42, "idm", "123", "study", request));
    when(sessions.authenticate("session-token")).thenReturn(Optional.of(original));
    when(patients.require(42)).thenReturn(patient("99000123"), patient("99000124"));
    assertStatus(HttpStatus.CONFLICT, () -> controller.resource(42, "idm", "123", "study", request));
  }

  @Test void portableViewerRedirectIsUncachedAndRejectsUnsafeSchemes() {
    ready();
    when(resources.resolve("99000123", "fuesmen", "123", "study"))
        .thenReturn(new ExternalStudyResource(null, null, null, "https://institution.test/viewer?study=123"));
    var response = controller.resource(42, "fuesmen", "123", "study", request);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation().toString()).isEqualTo("https://institution.test/viewer?study=123");
    assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    when(resources.resolve("99000123", "fuesmen", "123", "study"))
        .thenReturn(new ExternalStudyResource(null, null, null, "javascript:alert(1)"));
    assertStatus(HttpStatus.BAD_GATEWAY, () -> controller.resource(42, "fuesmen", "123", "study", request));
  }

  private SessionPrincipal ready() {
    var value = principal(42L, Set.of("section.studies.view"));
    when(patients.require(42)).thenReturn(patient("99.000.123"));
    when(sessions.authenticate("session-token")).thenReturn(Optional.of(value));
    return value;
  }

  private SessionPrincipal principal(Long id, Set<String> permissions) {
    var value = actor(id, permissions);
    request.setAttribute(AuthContext.PRINCIPAL_ATTRIBUTE, value);
    request.setAttribute(AuthContext.TOKEN_ATTRIBUTE, "session-token");
    return value;
  }

  private SessionPrincipal actor(Long id, Set<String> permissions) {
    return new SessionPrincipal(7, "qa", "", "QA", "", "", true, id, List.of(), permissions);
  }

  private PatientRepository.Patient patient(String dni) {
    return new PatientRepository.Patient(42, dni, "QA", "Paciente", "Prueba", null,
        "", "", "", "", "", "", true, Instant.EPOCH, Instant.EPOCH);
  }

  private void assertStatus(HttpStatus status, Runnable action) {
    assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
        error -> assertThat(error.status()).isEqualTo(status));
  }
}
