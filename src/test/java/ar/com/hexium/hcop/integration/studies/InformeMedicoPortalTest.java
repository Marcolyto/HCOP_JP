package ar.com.hexium.hcop.integration.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

@Timeout(15)
class InformeMedicoPortalTest {
  private static final String DNI = "99000123";
  private InformeMedicoPortalFixture fixture;
  private MockEnvironment environment;
  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach void setUp() throws Exception {
    fixture = new InformeMedicoPortalFixture();
    environment = new MockEnvironment().withProperty("EXTERNAL_STUDIES_SOURCE_TIMEOUT_SECONDS", "5");
    fixture.configure(environment);
  }
  @AfterEach void close() { fixture.close(); }

  @Test void loginJoinYConsultaVaciaEnTresCuentasSinCompartirTokens() throws Exception {
    try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = List.of(ExternalStudySource.CENTRO, ExternalStudySource.ESPANOL, ExternalStudySource.MALARGUE)
          .stream().map(source -> workers.submit(() -> search(source))).toList();
      for (var future : futures) {
        var result = future.get(7, TimeUnit.SECONDS);
        assertThat(result.partial()).isFalse(); assertThat(result.studies()).isEmpty(); assertThat(result.message()).isBlank();
      }
    }
    assertThat(fixture.joins).containsExactlyInAnyOrder("centro", "espanol", "malargue");
    assertThat(fixture.hooks).hasSize(3);
    for (var request : fixture.hooks) {
      assertThat(request.event()).isEqualTo("fetch_data");
      assertThat(request.value().path("id").asText()).isEqualTo("studies");
      assertThat(request.value().path("page").asInt()).isEqualTo(1);
      assertThat(request.value().path("size").asInt()).isEqualTo(10);
      assertThat(request.value().path("filter").get(0).path("field").asText()).isEqualTo("nrodocumento");
      assertThat(request.value().path("filter").get(0).path("type").asText()).isEqualTo("=");
      assertThat(request.value().path("filter").get(0).path("value").asText()).isEqualTo(DNI);
      assertThat(request.value().path("sort")).isEmpty();
    }
    assertThat(fixture.server.requests().stream().filter(request -> request.method().equals("GET") && request.path().equals("/portal/log_in")))
        .allSatisfy(request -> assertThat(request.header("Cookie")).isEmpty());
    assertThat(fixture.server.failures()).isEmpty();
  }

  @Test void loginRechazadoNoAbreCanalNiAfectaOtraCuenta() {
    fixture.rejectedLogins.add("centro");
    assertThatThrownBy(() -> search(ExternalStudySource.CENTRO)).isInstanceOf(ExternalStudyFailure.class).hasMessageContaining("inicio de sesión");
    assertThat(search(ExternalStudySource.ESPANOL).partial()).isFalse();
    assertThat(fixture.joins).containsExactly("espanol");
  }

  @Test void joinUsaUrlFinalTrasRedireccionDelPortalALaPaginaDeSitios() {
    var result = search(ExternalStudySource.ESPANOL);
    assertThat(result.partial()).isFalse();
    assertThat(fixture.server.requests().stream().filter(request -> request.method().equals("GET")))
        .extracting(LocalStudySocketServer.Request::path)
        .containsSubsequence("/portal/log_in", "/", "/sitios", "/live/websocket");
    assertThat(fixture.joins).containsExactly("espanol");
  }

  @Test void dosGeneracionesAbrenSoloHookDisponibleYConservanUrlCompleta() throws Exception {
    for (var source : List.of(ExternalStudySource.CENTRO, ExternalStudySource.ESPANOL, ExternalStudySource.MALARGUE)) {
      fixture.page(source.id, 1, """
          [{"idstudy":17,"accessionnumber":"SAME-ACC","studydate":"20260916","studytime":"120000",
            "nrodocumento":"99.000.123","proceduredescription":"<b>TC de tórax</b>","modality":"TC","patientname":"Paciente ficticio"},
           {"idstudy":17,"accessionnumber":"SAME-ACC","studydate":"20260916","nrodocumento":"99000123"}]
          """, 1);
      var result = search(source);
      assertThat(result.partial()).isFalse(); assertThat(result.studies()).hasSize(1);
      var study = result.studies().getFirst();
      assertThat(study.id()).isEqualTo("external:" + source.id + ":SAME-ACC");
      assertThat(study.sourceId()).isEqualTo(source.id);
      assertThat(study.date()).isEqualTo("2026-09-16");
      assertThat(study.title()).isEqualTo("TC de tórax");
      assertThat(study.reportUrl()).isBlank();
      assertThat(study.studyUrl()).isEqualTo(InformeMedicoPortalFixture.studyUrl(source.id, "SAME-ACC"));
      assertThat(mapper.writeValueAsString(result)).doesNotContain(DNI, "Paciente ficticio", "fixture-session", "fixture-csrf", "fixture-password", "<b>");
      assertThat(fixture.hooks.stream().filter(hook -> hook.source().equals(source.id))).extracting(InformeMedicoPortalFixture.Hook::event)
          .containsExactly("fetch_data", "tabulator_rowselected_studies", source == ExternalStudySource.ESPANOL ? "abrir_sitio" : "encriptar");
    }
  }

  @Test void fallaDePaginaPosteriorConservaEstudiosRecibidos() throws Exception {
    fixture.page("centro", 1, rows(1, 1), 2);
    fixture.failedPages.add("centro:2");
    var result = search(ExternalStudySource.CENTRO);
    assertThat(result.partial()).isTrue(); assertThat(result.studies()).hasSize(1);
    assertThat(result.studies().getFirst().id()).isEqualTo("external:centro:ACC-1");
  }

  @Test void enlaceRechazadoDeUnaFilaNoDescartaSiguientesSiSesionSigueActiva() throws Exception {
    fixture.page("malargue", 1, rows(1, 2), 1);
    fixture.rejectedLinks.add("ACC-1");
    var result = search(ExternalStudySource.MALARGUE);
    assertThat(result.partial()).isTrue(); assertThat(result.studies()).extracting(ExternalStudy::id).containsExactly("external:malargue:ACC-2");
  }

  @Test void cincoPaginasDeDiezFilasLleganAlLimiteConEstadoParcial() throws Exception {
    for (int page = 1; page <= 5; page++) fixture.page("espanol", page, rows((page - 1) * 10 + 1, 10), 999);
    var result = search(ExternalStudySource.ESPANOL);
    assertThat(result.partial()).isTrue(); assertThat(result.studies()).hasSize(50);
    assertThat(result.message()).contains("límite");
    assertThat(fixture.hooks.stream().filter(hook -> hook.event().equals("fetch_data"))).extracting(hook -> hook.value().path("page").asInt()).containsExactly(1, 2, 3, 4, 5);
  }

  @Test void limiteGlobalPorFuenteNoSolicitaEnlacesDeFilasQueNoMostrara() throws Exception {
    environment.setProperty("EXTERNAL_STUDIES_MAX_RESULTS", "2");
    fixture.page("centro", 1, rows(1, 10), 1);
    var result = search(ExternalStudySource.CENTRO);
    assertThat(result.partial()).isTrue(); assertThat(result.studies()).hasSize(2);
    assertThat(fixture.hooks.stream().filter(hook -> hook.event().equals("encriptar"))).hasSize(2);
  }

  @Test void noPublicaDatosDeOtroPacienteAunqueElFiltroRemotoLosDevuelva() throws Exception {
    fixture.page("centro", 1, """
        [{"nrodocumento":"99000124","accessionnumber":"OTHER","studydate":"20260916","patientname":"Otro paciente"},
         {"accessionnumber":"MISSING","studydate":"20260916","patientname":"Identidad ausente"}]
        """, 1);
    var result = search(ExternalStudySource.CENTRO);
    assertThat(result.partial()).isTrue(); assertThat(result.studies()).isEmpty();
    assertThat(mapper.writeValueAsString(result)).doesNotContain("99000124", "Otro paciente", "Identidad ausente", "OTHER", "MISSING");
    assertThat(fixture.hooks).allSatisfy(hook -> assertThat(hook.event()).isEqualTo("fetch_data"));
  }

  @Test void paginaSiguienteUsaMismoFiltroYNoConfundeLastPageNuloConError() throws Exception {
    fixture.page("malargue", 1, "[{\"nrodocumento\":\"99000999\"}]", 2);
    var empty = fixture.page("malargue", 2, "[]", 2);
    ((tools.jackson.databind.node.ObjectNode) empty).putNull("last_page");
    var result = search(ExternalStudySource.MALARGUE);
    assertThat(result.partial()).isTrue();
    assertThat(fixture.hooks).extracting(hook -> hook.value().path("page").asInt()).containsExactly(1, 2);
    assertThat(fixture.hooks).allSatisfy(hook -> assertThat(hook.value().path("filter").get(0).path("value").asText()).isEqualTo(DNI));
  }

  @Test void joinRechazadoOMensajeInvalidoNoExponeTokensNiCredenciales() {
    fixture.rejectedJoins.add("espanol");
    var rejected = search(ExternalStudySource.ESPANOL);
    assertThat(rejected.partial()).isTrue(); assertThat(rejected.studies()).isEmpty();
    fixture.malformedReplies.add("centro");
    var malformed = search(ExternalStudySource.CENTRO);
    assertThat(malformed.partial()).isTrue();
    assertThat(malformed.message()).doesNotContain("fixture-session", "fixture-password", "not-json");
    assertThat(search(ExternalStudySource.MALARGUE).partial()).isFalse();
  }

  @Test void credencialesOCsrfausentesFrenanAntesDeConsultar() {
    environment.setProperty("EXTERNAL_STUDIES_CENTRO_PASSWORD", "");
    assertThatThrownBy(() -> search(ExternalStudySource.CENTRO)).isInstanceOf(ExternalStudyFailure.class).hasMessageContaining("credenciales");
    assertThat(fixture.server.requests()).isEmpty();
    fixture.server.onHttp("/portal/log_in", request -> new LocalStudySocketServer.Reply(200, "<form><input type=password name=password></form>"));
    assertThatThrownBy(() -> search(ExternalStudySource.ESPANOL)).isInstanceOf(ExternalStudyFailure.class).hasMessageContaining("código de seguridad");
    assertThat(fixture.server.requests()).allSatisfy(request -> assertThat(request.method()).isEqualTo("GET"));
  }

  @Test void reconoceAtributosLiveViewSinEjecutarHtml() {
    var html = ExternalStudyHtml.parse("<html><head><meta name='csrf-token' content='fixture&amp;token'></head><body><div data-phx-main id='root' data-phx-session='session&amp;value' data-phx-static='static'><script>bad()</script></div></body></html>");
    assertThat(html.tokens.get("csrf-token")).isEqualTo("fixture&token");
    assertThat(html.liveViews).containsExactly(new ExternalStudyHtml.LiveView("root", "session&value", "static"));
    assertThat(html.text).doesNotContain("bad()");
  }

  @Test void informePdfDeTresInstitucionesSeResuelveSinSolicitarArchivo() throws Exception {
    for (var source : List.of(ExternalStudySource.CENTRO, ExternalStudySource.ESPANOL, ExternalStudySource.MALARGUE)) {
      fixture.page(source.id, 1, rows(1, 2), 1);
      fixture.documents.put(source.id + "-ACC-1", new LocalStudySocketServer.Reply(200,
          "[{\"document_type\":\"pdf\",\"iddocument\":\"REPORT-" + source.id + "\",\"patientname\":\"No publicar\",\"patientid\":\"99000123\"}]"));
      var result = search(source);
      assertThat(result.partial()).isFalse(); assertThat(result.studies()).hasSize(2);
      assertThat(result.studies().getFirst().reportUrl()).isEqualTo(fixture.server.httpUrl("/reports/REPORT-" + source.id));
      assertThat(result.studies().get(1).reportUrl()).isBlank();
      assertThat(mapper.writeValueAsString(result)).doesNotContain("No publicar", DNI);
    }
    assertThat(fixture.server.requests().stream().filter(request -> request.path().contains("/siteconfig/"))).hasSize(3);
    assertThat(fixture.server.requests().stream().filter(request -> request.path().startsWith("/documents/")))
        .hasSize(6).allSatisfy(request -> {
          assertThat(request.method()).isEqualTo("GET");
          assertThat(request.query()).isEqualTo("fromportal=true&fromEmail=fixture%2Bvalue&withaccession=undefined");
        });
    assertThat(fixture.server.requests()).noneMatch(request -> request.path().contains("/reports/") || request.path().contains("generatepdf"));
  }

  @Test void metadataSinPdfNoConfundeImagenesConInformeNiMarcaError() throws Exception {
    fixture.page("centro", 1, rows(1, 1), 1);
    fixture.documents.put("centro-ACC-1", new LocalStudySocketServer.Reply(200,
        "[{\"document_type\":\"url_arrays\",\"iddocument\":\"IMAGES\",\"url\":\"https://images.example/files\"}]"));
    var result = search(ExternalStudySource.CENTRO);
    assertThat(result.partial()).isFalse();
    assertThat(result.studies().getFirst().reportUrl()).isBlank();
    assertThat(result.studies().getFirst().studyUrl()).isEqualTo(InformeMedicoPortalFixture.studyUrl("centro", "ACC-1"));
  }

  @Test void metadataFallidaConservaEstudioYContinuaConInformeDeFilaSiguiente() throws Exception {
    fixture.page("malargue", 1, rows(1, 2), 1);
    fixture.documents.put("malargue-ACC-1", new LocalStudySocketServer.Reply(503, "error " + DNI + " fixture-session-token"));
    fixture.documents.put("malargue-ACC-2", new LocalStudySocketServer.Reply(200, "[{\"document_type\":\"pdf\",\"iddocument\":\"REPORT-2\"}]"));
    var result = search(ExternalStudySource.MALARGUE);
    assertThat(result.partial()).isTrue(); assertThat(result.studies()).hasSize(2);
    assertThat(result.studies().getFirst().reportUrl()).isBlank();
    assertThat(result.studies().getFirst().studyUrl()).isNotBlank();
    assertThat(result.studies().get(1).reportUrl()).isEqualTo(fixture.server.httpUrl("/reports/REPORT-2"));
    assertThat(mapper.writeValueAsString(result)).doesNotContain(DNI, "fixture-session-token", "503");
  }

  @Test void timeoutMetadataConservaFilaActualYResultadosAnteriores() throws Exception {
    fixture.page("espanol", 1, rows(1, 3), 1);
    fixture.documents.put("espanol-ACC-1", new LocalStudySocketServer.Reply(200, "[{\"document_type\":\"pdf\",\"iddocument\":\"REPORT-1\"}]"));
    var expired = new AtomicBoolean();
    var config = new ExternalStudyConfiguration(environment);
    // Expire exactly at the second metadata request. Login and LiveView remain
    // real loopback requests without racing container startup against a 1s timer.
    try (var http = spy(new ExternalStudyHttp(config, System.nanoTime() + TimeUnit.SECONDS.toNanos(30)))) {
      doAnswer(call -> {
        if (expired.get()) throw new ExternalStudyFailure("La fuente excedió el tiempo de espera.");
        return call.callRealMethod();
      }).when(http).remainingMillis();
      String secondMetadata = fixture.server.httpUrl("/documents/83/espanol-ACC-2");
      doAnswer(call -> {
        expired.set(true);
        throw new ExternalStudyFailure("La fuente excedió el tiempo de espera.");
      }).when(http).get(startsWith(secondMetadata));
      var result = new InformeMedicoPortal(mapper, config, http).search(ExternalStudySource.ESPANOL, DNI);
      assertThat(result.partial()).isTrue(); assertThat(result.studies()).hasSize(3);
      assertThat(result.studies()).extracting(ExternalStudy::studyUrl).containsExactly(
          InformeMedicoPortalFixture.studyUrl("espanol", "ACC-1"), InformeMedicoPortalFixture.studyUrl("espanol", "ACC-2"),
          InformeMedicoPortalFixture.studyUrl("espanol", "ACC-3"));
      assertThat(result.studies()).extracting(ExternalStudy::reportUrl).containsExactly(fixture.server.httpUrl("/reports/REPORT-1"), "", "");
      verify(http).get(startsWith(secondMetadata));
      assertThat(fixture.server.requests()).noneMatch(request -> request.path().endsWith("/espanol-ACC-3"));
    }
  }

  @Test void autoridadMetadataNoConfiguradaSeRechazaAntesDeConectar() throws Exception {
    try (var other = new LocalStudySocketServer()) {
      fixture.page("centro", 1, rows(1, 1), 1);
      fixture.server.onHttp("/cgi-bin/displaystudy.bf/siteconfig/124", request -> new LocalStudySocketServer.Reply(200,
          mapper.createObjectNode().put("getstudyfromsite", "True").put("documentlisturl", other.origin() + "/private/{{accession}}")
              .put("pdfdownloadsurl", fixture.server.origin() + "/reports/{{iddocument}}").toString()));
      var result = search(ExternalStudySource.CENTRO);
      assertThat(result.partial()).isTrue(); assertThat(result.studies()).hasSize(1);
      assertThat(other.requests()).isEmpty();
      assertThat(result.studies().getFirst().studyUrl()).isNotBlank();
      assertThat(result.studies().getFirst().reportUrl()).isBlank();
    }
  }

  @Test void fragmentoDelVisorConservaTokenOpacoYFlagsSinReconstruirAccession() {
    var fragment = InformeMedicoReports.fragment("https://estudio.informemedico.com.ar/#/124/opaque%2Ftoken+value%3D?fromportal=true&fromEmail=a%2Bb&withaccession=1&hidereport=true");
    assertThat(fragment.site()).isEqualTo("124");
    assertThat(fragment.token()).isEqualTo("opaque%2Ftoken+value%3D");
    assertThat(fragment.flags()).containsEntry("fromEmail", "a+b").containsEntry("fromportal", "true").containsEntry("withaccession", "1").hasSize(3);
    assertThat(InformeMedicoReports.fragment("https://estudio.informemedico.com.ar/#/83/ACCESSION").flags())
        .containsEntry("fromportal", "undefined").containsEntry("fromEmail", "undefined").containsEntry("withaccession", "undefined");
    for (String invalid : List.of("https://viewer.example/#//other.example/token", "https://viewer.example/#/124/a/b", "https://viewer.example/#/124/..", "https://viewer.example/?accession=123")) {
      assertThatThrownBy(() -> InformeMedicoReports.fragment(invalid)).isInstanceOf(ExternalStudyFailure.class);
    }
  }

  private ExternalStudyResult search(ExternalStudySource source) {
    return search(source, 10);
  }
  private ExternalStudyResult search(ExternalStudySource source, int timeoutSeconds) {
    var config = new ExternalStudyConfiguration(environment);
    try (var http = new ExternalStudyHttp(config, System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds))) {
      return new ExternalStudyProviders(mapper, config, http).search(source, DNI);
    }
  }
  private String rows(int first, int count) {
    var rows = mapper.createArrayNode();
    for (int offset = 0; offset < count; offset++) rows.addObject().put("idstudy", first + offset)
        .put("accessionnumber", "ACC-" + (first + offset)).put("nrodocumento", DNI)
        .put("studydate", "2026-09-16").put("proceduredescription", "Estudio sintético").put("modality", "TC");
    return rows.toString();
  }
}
