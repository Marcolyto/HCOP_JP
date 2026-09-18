package ar.com.hexium.hcop.integration.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ar.com.hexium.hcop.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

@Timeout(15)
class ExternalStudyResourceServiceTest {
  private static final String DNI = "99000123";
  private static final String STUDY = "S-001";
  private static final String PDF = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n";
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<String, String> searchReplies = new ConcurrentHashMap<>();
  private final Map<String, LocalStudySocketServer.Reply> reportReplies = new ConcurrentHashMap<>();
  private LocalStudySocketServer server;
  private MockEnvironment environment;

  @BeforeEach void setUp() throws Exception {
    server = new LocalStudySocketServer();
    environment = new MockEnvironment().withProperty("EXTERNAL_STUDIES_SOURCE_TIMEOUT_SECONDS", "5")
        .withProperty("EXTERNAL_STUDIES_MAX_DOCUMENT_BYTES", "4096");
    for (String source : List.of("fuesmen", "idm")) {
      String prefix = "EXTERNAL_STUDIES_" + source.toUpperCase(java.util.Locale.ROOT) + "_";
      environment.withProperty(prefix + "BASE_URL", server.httpUrl("/" + source + "/base"))
          .withProperty(prefix + "LOGIN_URL", server.httpUrl("/" + source + "/login"))
          .withProperty(prefix + "SEARCH_URL", server.httpUrl("/" + source + "/search"))
          .withProperty(prefix + "REPORT_URL", server.httpUrl("/" + source + "/reports/"))
          .withProperty(prefix + "USERNAME", "fixture-user-" + source)
          .withProperty(prefix + "PASSWORD", "fixture-password-" + source);
      searchReplies.put(source, row(STUDY, "<span>99.000.123</span>"));
      reportReplies.put(source, new LocalStudySocketServer.Reply(200, PDF,
          Map.of("Content-Type", source.equals("idm") ? "application/octet-stream" : "application/pdf")));
      server.onHttp("/" + source + "/base", request -> html("<form><input type='hidden' name='csrf_token_viewer' value='initial-" + source + "'></form>"));
      server.onHttp("/" + source + "/login", request -> {
        boolean valid = request.method().equals("POST") && request.body().contains("nombre=fixture-user-" + source)
            && request.body().contains("contrasena=fixture-password-" + source)
            && request.body().contains("csrf_token_viewer=initial-" + source);
        if (!valid) return new LocalStudySocketServer.Reply(403, "fixture authentication rejected");
        return new LocalStudySocketServer.Reply(200, "<html><input type='hidden' name='csrf_token_viewer' value='rotated-" + source + "'>Sesión activa</html>",
            Map.of("Set-Cookie", "provider_session=" + source + "; Path=/; HttpOnly", "Content-Type", "text/html; charset=UTF-8"));
      });
      server.onHttp("/" + source + "/search", request -> {
        boolean valid = request.method().equals("POST") && request.header("Cookie").equals("provider_session=" + source)
            && request.body().contains("pacienteId=" + DNI) && request.body().contains("csrf_token_viewer=rotated-" + source);
        return valid ? new LocalStudySocketServer.Reply(200, searchReplies.get(source), Map.of("Content-Type", "application/json"))
            : new LocalStudySocketServer.Reply(403, "fixture search rejected");
      });
      server.onHttp("/" + source + "/reports/" + STUDY, request -> request.header("Cookie").equals("provider_session=" + source)
          ? reportReplies.get(source) : new LocalStudySocketServer.Reply(403, "fixture document rejected"));
    }
  }

  @AfterEach void close() { server.close(); }

  @Test void informePdfUsaMismaSesionTrasVerificarPacienteYAdmiteAmbosIdentificadores() {
    for (String source : List.of("fuesmen", "idm")) {
      for (String id : List.of(STUDY, "external:" + source + ":" + STUDY)) {
        var result = service().resolve(DNI, source, id, "report");
        assertThat(result.contentType()).isEqualTo("application/pdf");
        assertThat(result.content()).isEqualTo(PDF.getBytes(StandardCharsets.UTF_8));
        assertThat(result.filename()).isEqualTo("informe-" + source + "-" + STUDY + ".pdf");
        assertThat(result.redirectUrl()).isBlank();
      }
      var ownRequests = server.requests().stream().filter(request -> request.path().startsWith("/" + source + "/")).toList();
      assertThat(ownRequests.stream().filter(request -> request.path().endsWith("/base")))
          .hasSize(2).allSatisfy(request -> assertThat(request.header("Cookie")).isEmpty());
      assertThat(ownRequests.stream().filter(request -> request.path().contains("/reports/")))
          .hasSize(2).allSatisfy(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.header("Cookie")).isEqualTo("provider_session=" + source);
            assertThat(request.query()).isNull();
          });
    }
    assertThat(server.failures()).isEmpty();
  }

  @Test void dniAjenoAusenteOEstudioNoEncontradoImpidenSolicitarDocumento() {
    for (String reply : List.of(row(STUDY, "99000124"), row(STUDY, ""), row("OTHER-STUDY", DNI))) {
      searchReplies.put("fuesmen", reply);
      var failure = failure(() -> service().resolve(DNI, "fuesmen", STUDY, "report"), HttpStatus.BAD_GATEWAY);
      assertThat(failure.getMessage()).contains("paciente activo").doesNotContain(DNI, "99000124", "OTHER-STUDY");
    }
    assertThat(server.requests()).noneMatch(request -> request.path().contains("/reports/"));
  }

  @Test void visorInstitucionalSeEntregaSoloTrasVerificarDniSinDescargarEstudio() {
    for (String source : List.of("fuesmen", "idm")) {
      var result = service().resolve(DNI, source, STUDY, "study");
      assertThat(result.content()).isEmpty();
      assertThat(result.contentType()).isBlank();
      assertThat(result.redirectUrl()).isEqualTo(ExternalStudyViewerLink.url(server.httpUrl("/" + source + "/base"), STUDY));
      assertThat(result.redirectUrl()).doesNotContain("fuesmen.com", "link_estudio.php", "fixture-password", "provider_session");
    }
    assertThat(server.requests()).noneMatch(request -> request.path().contains("/reports/") || request.path().endsWith("/pacientes"));
  }

  @Test void parametrosInvalidosSeRechazanAntesDeTodaPeticion() {
    record Arguments(String dni, String source, String id, String kind) {}
    for (var arguments : List.of(
        new Arguments(null, "fuesmen", STUDY, "report"), new Arguments("123' OR 1=1", "fuesmen", STUDY, "report"),
        new Arguments(DNI, null, STUDY, "report"), new Arguments(DNI, "unknown", STUDY, "report"),
        new Arguments(DNI, "fuesmen", null, "report"), new Arguments(DNI, "fuesmen", " ", "report"),
        new Arguments(DNI, "fuesmen", "external:idm:" + STUDY, "report"), new Arguments(DNI, "fuesmen", "external:fuesmen:", "report"),
        new Arguments(DNI, "fuesmen", "x".repeat(201), "report"), new Arguments(DNI, "fuesmen", "secret\nvalue", "report"),
        new Arguments(DNI, "fuesmen", "secret\\value", "report"), new Arguments(DNI, "fuesmen", STUDY, null),
        new Arguments(DNI, "fuesmen", STUDY, "delete"))) {
      failure(() -> service().resolve(arguments.dni(), arguments.source(), arguments.id(), arguments.kind()), HttpStatus.BAD_REQUEST);
    }
    assertThat(server.requests()).isEmpty();
  }

  @Test void limiteDeDocumentoPermiteTamanoExactoYRechazaElByteAdicional() {
    String exact = "%PDF-" + "x".repeat(4091);
    reportReplies.put("fuesmen", new LocalStudySocketServer.Reply(200, exact, Map.of("Content-Type", "application/pdf")));
    assertThat(service().resolve(DNI, "fuesmen", STUDY, "report").content()).hasSize(4096);
    reportReplies.put("fuesmen", new LocalStudySocketServer.Reply(200, exact + "x", Map.of("Content-Type", "application/pdf")));
    assertThat(failure(() -> service().resolve(DNI, "fuesmen", STUDY, "report"), HttpStatus.BAD_GATEWAY).getMessage()).contains("tamaño");
  }

  @Test void htmlAunqueDeclarePdfYContenidoNoPdfSeRechazanSinPublicarDatos() {
    for (String mime : List.of("application/pdf", "text/html", "application/json")) {
      reportReplies.put("fuesmen", new LocalStudySocketServer.Reply(200,
          "<html>fixture-password fixture-session " + DNI + " <script>unsafe()</script></html>", Map.of("Content-Type", mime)));
      assertThat(failure(() -> service().resolve(DNI, "fuesmen", STUDY, "report"), HttpStatus.BAD_GATEWAY).getMessage())
          .contains("informe válido").doesNotContain("fixture-password", "fixture-session", DNI, "<html>", "unsafe");
    }
  }

  @Test void pantallaDeLoginEnLugarDelPdfNoSeDevuelveAlNavegador() {
    reportReplies.put("idm", html("<form><input type='password' name='password'></form>fixture-password " + DNI));
    assertThat(failure(() -> service().resolve(DNI, "idm", STUDY, "report"), HttpStatus.BAD_GATEWAY).getMessage())
        .contains("inicio de sesión").doesNotContain("fixture-password", DNI, "<form>");
  }

  @Test void redireccionDeDocumentoAOtraAutoridadSeRechazaSinEnviarCookie() throws Exception {
    try (var other = new LocalStudySocketServer()) {
      other.onHttp("/private-report", request -> html("should not be contacted"));
      reportReplies.put("fuesmen", new LocalStudySocketServer.Reply(302, "",
          Map.of("Location", other.httpUrl("/private-report").replace("127.0.0.1", "localhost"))));
      assertThat(failure(() -> service().resolve(DNI, "fuesmen", STUDY, "report"), HttpStatus.BAD_GATEWAY).getMessage())
          .contains("destino no autorizado");
      assertThat(other.requests()).isEmpty();
    }
  }

  @Test void errorHttpDeDocumentoNoExponeRespuestaUrlNiCredenciales() {
    reportReplies.put("fuesmen", new LocalStudySocketServer.Reply(500, "fixture-password fixture-session " + DNI + " " + server.origin()));
    assertThat(failure(() -> service().resolve(DNI, "fuesmen", STUDY, "report"), HttpStatus.BAD_GATEWAY).getMessage())
        .doesNotContain("fixture-password", "fixture-session", DNI, server.origin());
  }

  @Test void laboratorioUsaHandlerNativoConOrdenYCookiesSinIntermediario() {
    configureLaboratory();
    for (String kind : List.of("report", "study")) {
      var result = service().resolve(DNI, "labo", "external:labo:12345", kind);
      assertThat(result.contentType()).isEqualTo("application/pdf");
      assertThat(result.content()).isEqualTo(PDF.getBytes(StandardCharsets.UTF_8));
    }
    assertThat(server.requests().stream().filter(request -> request.path().equals("/labo/report")))
        .hasSize(2).allSatisfy(request -> {
          assertThat(request.query()).contains("Action=go", "mostrarPDF=S", "orden=12345");
          assertThat(request.header("Cookie")).isEqualTo("provider_session=labo");
          assertThat(request.method()).isEqualTo("GET");
        });
    assertThat(server.requests()).noneMatch(request -> request.target().contains("ver_lab_curl") || request.target().contains("fuesmen.com"));
  }

  @Test void laboratorioPuedeEntregarHtmlParaElSandboxDelControladorSinConfundirLogin() {
    configureLaboratory();
    reportReplies.put("labo", html("<html><table><tr><td>Resultado ficticio</td></tr></table></html>"));
    var result = service().resolve(DNI, "labo", "12345", "report");
    assertThat(result.contentType()).isEqualTo("text/html");
    assertThat(result.filename()).endsWith(".html");
    assertThat(new String(result.content(), StandardCharsets.UTF_8)).contains("Resultado ficticio");
    reportReplies.put("labo", html("<form><input type=password name=password></form><table><tr><td>Login</td></tr></table>"));
    assertThat(failure(() -> service().resolve(DNI, "labo", "12345", "report"), HttpStatus.BAD_GATEWAY).getMessage()).contains("inicio de sesión");
  }

  @Test void laboratorioNoDescargaOrdenAusenteDeLaBusquedaDelPaciente() {
    configureLaboratory();
    failure(() -> service().resolve(DNI, "labo", "99999", "report"), HttpStatus.BAD_GATEWAY);
    assertThat(server.requests()).noneMatch(request -> request.path().equals("/labo/report"));
  }

  private void configureLaboratory() {
    environment.withProperty("EXTERNAL_STUDIES_LABO_BASE_URL", server.httpUrl("/labo/base"))
        .withProperty("EXTERNAL_STUDIES_LABO_LOGIN_URL", server.httpUrl("/labo/login"))
        .withProperty("EXTERNAL_STUDIES_LABO_SEARCH_URL", server.httpUrl("/labo/search"))
        .withProperty("EXTERNAL_STUDIES_LABO_REPORT_URL", server.httpUrl("/labo/report"))
        .withProperty("EXTERNAL_STUDIES_LABO_USERNAME", "fixture-lab")
        .withProperty("EXTERNAL_STUDIES_LABO_PASSWORD", "fixture-lab-password");
    server.onHttp("/labo/base", request -> html("<form><input type=password name=password></form>"));
    server.onHttp("/labo/login", request -> request.body().contains("login=fixture-lab") && request.body().contains("password=fixture-lab-password")
        ? new LocalStudySocketServer.Reply(200, "Sesión activa", Map.of("Set-Cookie", "provider_session=labo; Path=/; HttpOnly"))
        : new LocalStudySocketServer.Reply(403, "fixture denied"));
    server.onHttp("/labo/search", request -> {
      if (!request.body().contains("nrodoc=" + DNI) || !request.header("Cookie").equals("provider_session=labo")) return new LocalStudySocketServer.Reply(403, "fixture denied");
      return html("<table><tr><th>Orden</th><th>Fecha</th></tr>" + (request.query().contains("pag=1")
          ? "<tr><td class='orden'>12345</td><td>16/09/2026</td></tr>" : "") + "</table>");
    });
    reportReplies.put("labo", new LocalStudySocketServer.Reply(200, PDF, Map.of("Content-Type", "application/pdf")));
    server.onHttp("/labo/report", request -> request.header("Cookie").equals("provider_session=labo")
        ? reportReplies.get("labo") : new LocalStudySocketServer.Reply(403, "fixture denied"));
  }

  private ExternalStudyResourceService service() { return new ExternalStudyResourceService(mapper, environment); }
  private static ApiException failure(Runnable action, HttpStatus status) {
    var failure = assertThrows(ApiException.class, action::run);
    assertThat(failure.status()).isEqualTo(status);
    return failure;
  }
  private static LocalStudySocketServer.Reply html(String body) {
    return new LocalStudySocketServer.Reply(200, body, Map.of("Content-Type", "text/html; charset=UTF-8"));
  }
  private String row(String id, String dni) {
    var payload = mapper.createObjectNode();
    var row = payload.putArray("data").addArray();
    row.add("<input type='checkbox' name='id_study[]' value='" + id + "'>");
    row.add("").add("16/09/2026 08:00").add("TC").add(dni);
    for (int column = 5; column < 10; column++) row.add("");
    row.add("Estudio sintético");
    return payload.toString();
  }
}
