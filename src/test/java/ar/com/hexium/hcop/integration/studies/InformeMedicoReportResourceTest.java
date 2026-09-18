package ar.com.hexium.hcop.integration.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ar.com.hexium.hcop.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

/** The click path rechecks patient ownership before downloading a portal report. */
@Timeout(20)
class InformeMedicoReportResourceTest {
  private static final String DNI = "99000123";
  private static final String ACCESSION = "ACC-7";
  private static final String PDF = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n";
  private final ObjectMapper mapper = new ObjectMapper();
  private InformeMedicoPortalFixture fixture;
  private MockEnvironment environment;
  private volatile LocalStudySocketServer.Reply reportReply;

  @BeforeEach void setUp() throws Exception {
    fixture = new InformeMedicoPortalFixture();
    environment = new MockEnvironment().withProperty("EXTERNAL_STUDIES_SOURCE_TIMEOUT_SECONDS", "5")
        .withProperty("EXTERNAL_STUDIES_MAX_DOCUMENT_BYTES", "4096");
    fixture.configure(environment);
    reportReply = new LocalStudySocketServer.Reply(200, PDF, Map.of("Content-Type", "application/pdf"));
  }

  @AfterEach void close() { fixture.close(); }

  @ParameterizedTest
  @ValueSource(strings = {"centro", "espanol", "malargue"})
  void tresFuentesRevalidanSoloAccessionPedidoYDescarganConRefererDelVisor(String source) throws Exception {
    prepare(source);
    for (String id : List.of(ACCESSION, "external:" + source + ":" + ACCESSION)) {
      var resource = service().resolve(DNI, source, id, "report");
      assertThat(resource.contentType()).isEqualTo("application/pdf");
      assertThat(resource.content()).isEqualTo(PDF.getBytes(StandardCharsets.UTF_8));
      assertThat(resource.filename()).endsWith(".pdf");
      assertThat(resource.redirectUrl()).isBlank();
    }
    assertThat(fixture.hooks).extracting(InformeMedicoPortalFixture.Hook::event)
        .containsExactly("fetch_data", "tabulator_rowselected_studies", action(source),
            "fetch_data", "tabulator_rowselected_studies", action(source));
    assertThat(fixture.hooks.stream().filter(hook -> hook.event().equals("fetch_data")))
        .allSatisfy(hook -> assertThat(hook.value().path("filter").get(0).path("value").asText()).isEqualTo(DNI));
    assertThat(fixture.hooks.stream().filter(hook -> hook.event().equals("tabulator_rowselected_studies")))
        .allSatisfy(hook -> assertThat(hook.value().path("accessionnumber").asText()).isEqualTo(ACCESSION));
    assertThat(fixture.server.requests().stream().filter(request -> request.path().equals("/portal/log_in") && request.method().equals("GET")))
        .hasSize(2).allSatisfy(request -> assertThat(request.header("Cookie")).isEmpty());
    assertThat(fixture.server.requests().stream().filter(request -> request.path().startsWith("/documents/")))
        .hasSize(2).allSatisfy(request -> {
          assertThat(request.path()).isEqualTo(metadataPath(source));
          assertThat(request.query()).isEqualTo("fromportal=true&fromEmail=fixture%2Bvalue&withaccession=undefined");
        });
    assertThat(fixture.server.requests().stream().filter(request -> request.path().startsWith("/reports/")))
        .hasSize(2).allSatisfy(request -> {
          assertThat(request.path()).isEqualTo(reportPath(source));
          assertThat(request.method()).isEqualTo("GET");
          assertThat(request.header("Referer")).isEqualTo(fixture.server.origin() + "/");
          assertThat(request.header("Referer")).doesNotContain("#", ACCESSION, "fromportal", "fixture-password");
          assertThat(request.header("Cookie")).contains("portal_account=" + source, "report_fixture=" + source);
        });
    assertThat(fixture.server.requests()).extracting(LocalStudySocketServer.Request::path)
        .containsSubsequence("/portal/log_in", "/portal/log_in", "/", "/sitios", "/live/websocket",
            "/cgi-bin/displaystudy.bf/siteconfig/" + site(source), metadataPath(source), reportPath(source));
    assertThat(fixture.server.requests()).noneMatch(request -> request.path().contains("generatepdf"));
    assertThat(fixture.server.failures()).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"centro", "espanol", "malargue"})
  void dniAjenoAusenteOAccessionNoEncontradoNoSeleccionanNiDescargan(String source) throws Exception {
    prepare(source);
    for (var rows : List.of(rows(ACCESSION, "99000124"), rows(ACCESSION, ""), rows("OTHER", DNI), mapper.createArrayNode())) {
      fixture.page(source, 1, rows.toString(), 1);
      var failure = failure(() -> service().resolve(DNI, source, ACCESSION, "report"), HttpStatus.BAD_GATEWAY);
      assertSafe(failure);
    }
    assertThat(fixture.hooks).allSatisfy(hook -> assertThat(hook.event()).isEqualTo("fetch_data"));
    assertThat(fixture.server.requests()).noneMatch(request -> request.path().contains("/siteconfig/")
        || request.path().startsWith("/documents/") || request.path().startsWith("/reports/"));
  }

  @Test void accessionEnPaginaPosteriorConservaFiltroYNoResuelveOtrasFilas() throws Exception {
    prepare("centro");
    fixture.page("centro", 1, rows("OTHER", DNI).toString(), 2);
    fixture.page("centro", 2, rows(ACCESSION, DNI).toString(), 2);
    assertThat(service().resolve(DNI, "centro", ACCESSION, "report").content()).isEqualTo(PDF.getBytes(StandardCharsets.UTF_8));
    assertThat(fixture.hooks.stream().filter(hook -> hook.event().equals("fetch_data")))
        .extracting(hook -> hook.value().path("page").asInt()).containsExactly(1, 2);
    assertThat(fixture.hooks.stream().filter(hook -> hook.event().equals("tabulator_rowselected_studies")))
        .hasSize(1).allSatisfy(hook -> assertThat(hook.value().path("accessionnumber").asText()).isEqualTo(ACCESSION));
    assertThat(fixture.server.requests().stream().filter(request -> request.path().startsWith("/documents/")))
        .hasSize(1).allSatisfy(request -> assertThat(request.path()).isEqualTo(metadataPath("centro")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"centro", "espanol", "malargue"})
  void tipoVisorYPrefijoDeOtraFuenteSeRechazanAntesDeConsultar(String source) {
    failure(() -> service().resolve(DNI, source, ACCESSION, "study"), HttpStatus.BAD_REQUEST);
    failure(() -> service().resolve(DNI, source, "external:idm:" + ACCESSION, "report"), HttpStatus.BAD_REQUEST);
    assertThat(fixture.server.requests()).isEmpty();
  }

  @Test void htmlQueDeclaraPdfYLoginNoSePublicanComoInforme() throws Exception {
    prepare("centro");
    for (String body : List.of("<html>fixture-password " + DNI + "</html>", "{\"error\":\"fixture-session\"}",
        "<form><input type='password' name='password'></form>")) {
      reportReply = new LocalStudySocketServer.Reply(200, body, Map.of("Content-Type", "application/pdf"));
      assertSafe(failure(() -> service().resolve(DNI, "centro", ACCESSION, "report"), HttpStatus.BAD_GATEWAY));
    }
  }

  @Test void firmaPdfEsObligatoriaAunqueElServidorDevuelvaOctetStream() throws Exception {
    prepare("espanol");
    reportReply = new LocalStudySocketServer.Reply(200, PDF, Map.of("Content-Type", "application/octet-stream"));
    assertThat(service().resolve(DNI, "espanol", ACCESSION, "report").contentType()).isEqualTo("application/pdf");
    reportReply = new LocalStudySocketServer.Reply(200, "archivo sin firma", Map.of("Content-Type", "application/octet-stream"));
    assertSafe(failure(() -> service().resolve(DNI, "espanol", ACCESSION, "report"), HttpStatus.BAD_GATEWAY));
  }

  @Test void refererUsaSoloOrigenAunqueLaBaseDelVisorTengaRuta() throws Exception {
    prepare("centro");
    environment.setProperty("EXTERNAL_STUDIES_CENTRO_VIEWER_BASE_URL", fixture.server.origin() + "/nested/viewer/");
    fixture.server.onHttp("/nested/viewer/cgi-bin/displaystudy.bf/siteconfig/124", request -> new LocalStudySocketServer.Reply(200,
        mapper.createObjectNode().put("getstudyfromsite", "True")
            .put("documentlisturl", fixture.server.origin() + "/documents/124/{{accession}}")
            .put("pdfdownloadsurl", fixture.server.origin() + "/reports/{{iddocument}}").toString()));
    assertThat(service().resolve(DNI, "centro", ACCESSION, "report").content()).isEqualTo(PDF.getBytes(StandardCharsets.UTF_8));
    assertThat(fixture.server.requests().stream().filter(request -> request.path().equals(reportPath("centro"))))
        .hasSize(1).allSatisfy(request -> assertThat(request.header("Referer")).isEqualTo(fixture.server.origin() + "/"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"metadata", "download"})
  void autoridadNoConfiguradaSeRechazaSinContactarla(String destination) throws Exception {
    prepare("centro");
    try (var other = new LocalStudySocketServer()) {
      String metadata = destination.equals("metadata") ? other.origin() + "/documents/{{accession}}"
          : fixture.server.origin() + "/documents/124/{{accession}}";
      String download = destination.equals("download") ? other.origin() + "/reports/{{iddocument}}"
          : fixture.server.origin() + "/reports/{{iddocument}}";
      configureSite("centro", metadata, download);
      assertSafe(failure(() -> service().resolve(DNI, "centro", ACCESSION, "report"), HttpStatus.BAD_GATEWAY));
      assertThat(other.requests()).isEmpty();
      assertThat(fixture.server.requests()).noneMatch(request -> request.path().startsWith("/reports/"));
    }
  }

  @Test void redireccionDePdfAOtroDestinoNoEnviaSesionNiReferer() throws Exception {
    prepare("malargue");
    try (var other = new LocalStudySocketServer()) {
      reportReply = new LocalStudySocketServer.Reply(302, "", Map.of("Location", other.httpUrl("/private-report")));
      assertSafe(failure(() -> service().resolve(DNI, "malargue", ACCESSION, "report"), HttpStatus.BAD_GATEWAY));
      assertThat(other.requests()).isEmpty();
    }
  }

  @Test void redireccionEnMismaInstitucionConservaRefererYDevuelveSoloPdf() throws Exception {
    prepare("centro");
    reportReply = new LocalStudySocketServer.Reply(302, "", Map.of("Location", "/completed-report"));
    fixture.server.onHttp("/completed-report", request -> request.header("Referer").equals(fixture.server.origin() + "/")
        ? new LocalStudySocketServer.Reply(200, PDF, Map.of("Content-Type", "application/pdf"))
        : new LocalStudySocketServer.Reply(403, "missing viewer context"));
    assertThat(service().resolve(DNI, "centro", ACCESSION, "report").content()).isEqualTo(PDF.getBytes(StandardCharsets.UTF_8));
    assertThat(fixture.server.requests()).extracting(LocalStudySocketServer.Request::path)
        .containsSubsequence(reportPath("centro"), "/completed-report");
  }

  @Test void sinDocumentoPdfNoDescargaImagenesNiGeneraArchivos() throws Exception {
    prepare("espanol");
    fixture.documents.put("espanol-" + ACCESSION, new LocalStudySocketServer.Reply(200,
        "[{\"document_type\":\"url_arrays\",\"iddocument\":\"IMAGES\",\"url\":\"https://images.example/private\"}]"));
    assertSafe(failure(() -> service().resolve(DNI, "espanol", ACCESSION, "report"), HttpStatus.BAD_GATEWAY));
    assertThat(fixture.server.requests()).noneMatch(request -> request.path().startsWith("/reports/") || request.path().contains("generatepdf"));
  }

  @Test void limiteDeBytesSeMantieneEnLaDescargaConReferer() throws Exception {
    prepare("centro");
    reportReply = new LocalStudySocketServer.Reply(200, "%PDF-" + "x".repeat(4092), Map.of("Content-Type", "application/pdf"));
    assertThat(failure(() -> service().resolve(DNI, "centro", ACCESSION, "report"), HttpStatus.BAD_GATEWAY).getMessage()).contains("tamaño");
  }

  private void prepare(String source) throws Exception {
    ArrayNode rows = rows("OTHER", DNI);
    rows.addAll(rows(ACCESSION, DNI));
    fixture.page(source, 1, rows.toString(), 1);
    fixture.documents.put(source + "-" + ACCESSION, new LocalStudySocketServer.Reply(200,
        "[{\"document_type\":\"pdf\",\"iddocument\":\"REPORT-" + source + "\",\"patientid\":\"" + DNI + "\"}]",
        Map.of("Content-Type", "application/json", "Set-Cookie", "report_fixture=" + source + "; Path=/; HttpOnly")));
    fixture.server.onHttp(reportPath(source), request -> request.header("Referer").equals(fixture.server.origin() + "/")
        && request.header("Cookie").contains("portal_account=" + source)
        && request.header("Cookie").contains("report_fixture=" + source)
        ? reportReply : new LocalStudySocketServer.Reply(403, "missing viewer context or fixture session"));
  }

  private void configureSite(String source, String metadata, String download) {
    fixture.server.onHttp("/cgi-bin/displaystudy.bf/siteconfig/" + site(source), request -> new LocalStudySocketServer.Reply(200,
        mapper.createObjectNode().put("getstudyfromsite", "True").put("documentlisturl", metadata)
            .put("pdfdownloadsurl", download).toString()));
  }

  private ArrayNode rows(String accession, String dni) {
    var rows = mapper.createArrayNode();
    rows.addObject().put("idstudy", 7).put("accessionnumber", accession).put("nrodocumento", dni)
        .put("studydate", "2026-09-17").put("proceduredescription", "Estudio sintético");
    return rows;
  }

  private ExternalStudyResourceService service() { return new ExternalStudyResourceService(mapper, environment); }
  private static String site(String source) { return source.equals("espanol") ? "83" : "124"; }
  private static String action(String source) { return source.equals("espanol") ? "abrir_sitio" : "encriptar"; }
  private static String metadataPath(String source) { return "/documents/" + site(source) + "/" + source + "-" + ACCESSION; }
  private static String reportPath(String source) { return "/reports/REPORT-" + source; }
  private static ApiException failure(Runnable action, HttpStatus status) {
    var failure = assertThrows(ApiException.class, action::run);
    assertThat(failure.status()).isEqualTo(status);
    return failure;
  }
  private static void assertSafe(ApiException failure) {
    assertThat(failure.getMessage()).doesNotContain(DNI, "99000124", "fixture-password", "fixture-session", "<html>", "<form>", "OTHER");
  }
}
