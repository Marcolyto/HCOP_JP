package ar.com.hexium.hcop.integration.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ExternalStudySearchServiceTest {
  private static final String DNI = "99000123";
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<String, Reply> fixtures = new ConcurrentHashMap<>();
  private final List<Request> requests = new CopyOnWriteArrayList<>();
  private final List<HttpServer> servers = new ArrayList<>();
  private ExecutorService executor;
  private HttpServer server;
  private InformeMedicoPortalFixture portal;
  private MockEnvironment environment;
  private String base;

  @BeforeEach
  void setUp() throws Exception {
    executor = Executors.newVirtualThreadPerTaskExecutor();
    server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
    servers.add(server); server.setExecutor(executor);
    server.createContext("/", exchange -> {
      String path = exchange.getRequestURI().getPath();
      requests.add(new Request(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(),
          new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), exchange.getRequestHeaders().getFirst("Cookie")));
      Reply reply = fixtures.getOrDefault(path, new Reply(404, "fixture not found", Map.of(), 0));
      if (exchange.getRequestURI().getRawQuery() != null && exchange.getRequestURI().getRawQuery().contains("pag=2")) {
        reply = fixtures.getOrDefault(path + "/page2", reply);
      }
      respond(exchange, reply);
    });
    server.start(); base = "http://127.0.0.1:" + server.getAddress().getPort();
    environment = new MockEnvironment().withProperty("EXTERNAL_STUDIES_SOURCE_TIMEOUT_SECONDS", "4");
    portal = new InformeMedicoPortalFixture();
    portal.configure(environment);
    for (String id : List.of("fuesmen", "idm", "labo")) {
      environment.withProperty("EXTERNAL_STUDIES_" + id.toUpperCase() + "_USERNAME", "fixture-user")
          .withProperty("EXTERNAL_STUDIES_" + id.toUpperCase() + "_PASSWORD", "fixture-password")
          .withProperty("EXTERNAL_STUDIES_" + id.toUpperCase() + "_BASE_URL", base + "/" + id + "/base")
          .withProperty("EXTERNAL_STUDIES_" + id.toUpperCase() + "_LOGIN_URL", base + "/" + id + "/login");
      fixtures.put("/" + id + "/base", ok("<form><input value='initial-" + id + "' type='hidden' name='csrf_token_viewer'><input name='password' type='password'></form>"));
      fixtures.put("/" + id + "/login", new Reply(200,
          "<html><input value='rotated-" + id + "' name='csrf_token_viewer' type='hidden'><p>Sesión activa</p></html>",
          Map.of("Set-Cookie", "provider_session=" + id + "; Path=/; HttpOnly"), 0));
    }
    for (String id : List.of("fuesmen", "idm", "centro", "espanol", "labo")) {
      environment.withProperty("EXTERNAL_STUDIES_" + id.toUpperCase() + "_SEARCH_URL", base + "/" + id + "/search")
          .withProperty("EXTERNAL_STUDIES_" + id.toUpperCase() + "_REPORT_URL", base + "/" + id + "/report/");
    }
    environment.withProperty("EXTERNAL_STUDIES_PATHOLOGY_FALLBACK_URL", base + "/patologia/search");
    fixtures.put("/fuesmen/search", ok(viewerRows("F-001", "15/09/2026", "<b>TC de tórax</b>", true)));
    fixtures.put("/idm/search", ok(viewerRows("I-001", "14/09/2026", "RM de abdomen", false)));
    portal.page("centro", 1, """
        [{"studydate":"20260913","proceduredescription":"Ecografía","modality":"US","accessionnumber":"C-001","nrodocumento":"99000123"}]
        """, 1);
    portal.page("espanol", 1, """
        [{"studydate":"2026-09-12T10:00:00","proceduredescription":"Radiografía","modality":"CR","accessionnumber":"E-001","nrodocumento":"99000123"}]
        """, 1);
    portal.page("malargue", 1, """
        [{"studydate":"20260909","proceduredescription":"Resonancia","modality":"MR","accessionnumber":"M-001","nrodocumento":"99000123"}]
        """, 1);
    fixtures.put("/labo/search", ok("""
        <html><table><tr><th>Orden</th><th>Fecha</th></tr>
        <tr><td class='orden'>12345</td><td>11/09/2026</td></tr></table></html>
        """));
    fixtures.put("/patologia/search", ok("""
        2 ESTUDIOS ENCONTRADOS <BR><table>
        <tr><td>2026-09-10</td><td>PATOLOGIA</td><td>PATOLOGIA</td><td> PATOLOGÍA </td>
        <td><a target='_blank' href='/reports/P-001.pdf'>Ver informe</a></td><td><a href='/reports/P-001.pdf'>Ver estudio</a></td></tr>
        <tr><td>2026-09-16</td><td>Otra fuente</td><td>TC</td><td>FUESMEN</td><td><a href='/other.pdf'>Informe</a></td><td><a href='/other.pdf'>Estudio</a></td></tr>
        </table>
        """));
  }

  @AfterEach
  void tearDown() { servers.forEach(item -> item.stop(0)); portal.close(); executor.shutdownNow(); }

  @Test
  void consultaLasSieteFuentesOrdenaDeduplicaYAislaCookiesYTokens() {
    var result = service().search(DNI);
    assertThat(result.path("partial").asBoolean()).isFalse();
    assertThat(result.path("total").asInt()).isEqualTo(7);
    assertThat(result.path("sources").size()).isEqualTo(7);
    assertThat(result.path("studies").get(0).path("id").asText()).isEqualTo("external:fuesmen:F-001");
    assertThat(result.path("studies").get(0).path("title").asText()).isEqualTo("TC de tórax");
    assertThat(result.path("studies").get(0).path("sourceId").asText()).isEqualTo("fuesmen");
    assertThat(result.path("studies").get(6).path("date").asText()).isEqualTo("2026-09-09");
    assertThat(result.toString()).doesNotContain("<b>", DNI, "fixture-password", "provider_session");
    for (String id : List.of("fuesmen", "idm", "labo")) {
      var query = requests.stream().filter(request -> request.path().equals("/" + id + "/search")).findFirst().orElseThrow();
      assertThat(query.cookie()).contains("provider_session=" + id);
      assertThat(requests.stream().filter(request -> request.path().equals("/" + id + "/base")).findFirst().orElseThrow().cookie()).isNull();
      if (!id.equals("labo")) assertThat(query.body()).contains("csrf_token_viewer=rotated-" + id);
    }
    var pathology = requests.stream().filter(request -> request.path().equals("/patologia/search")).findFirst().orElseThrow();
    assertThat(pathology.query()).contains("fuesmen=no", "idm=no", "centro=no", "espanol=no", "labo=no");
    assertThat(pathology.cookie()).isNull();
    assertThat(portal.joins).containsExactlyInAnyOrder("centro", "espanol", "malargue");
    assertThat(requests).noneMatch(request -> request.path().equals("/centro/search") || request.path().equals("/espanol/search"));
    var repeated = service().search(DNI);
    assertThat(repeated.path("studies")).isEqualTo(result.path("studies"));
  }

  @Test
  void diferenciaRespuestasVaciasDeFallosIncluidoPatologiaCeroSinPermiso() {
    for (String id : List.of("fuesmen", "idm")) fixtures.put("/" + id + "/search", ok("{\"data\":[]}"));
    portal.pages.clear();
    fixtures.put("/labo/search", ok("<table><tr><th>Orden</th><th>Fecha</th></tr></table>Sin resultados"));
    fixtures.put("/patologia/search", ok("12/31/2023 0 ESTUDIOS ENCONTRADOS <BR>SIN PERMISO"));
    var result = service().search(DNI);
    assertThat(result.path("partial").asBoolean()).isFalse();
    assertThat(result.path("total").asInt()).isZero();
    for (var source : result.path("sources")) assertThat(source.path("status").asText()).isEqualTo("empty");
  }

  @Test
  void fuenteJsonInvalidaNoOcultaLosOtrosSeisResultadosNiExponeRespuesta() {
    fixtures.put("/idm/search", ok("provider exception " + DNI + " fixture-password"));
    var result = service().search(DNI);
    assertThat(result.path("partial").asBoolean()).isTrue();
    assertThat(result.path("total").asInt()).isEqualTo(6);
    assertThat(source(result, "idm").path("status").asText()).isEqualTo("error");
    assertThat(result.toString()).doesNotContain(DNI, "fixture-password", "provider exception");
  }

  @Test
  void faltaCsrfOLoginRechazadoSeInformaSinContinuarConsulta() {
    fixtures.put("/fuesmen/base", ok("<form><input type=password name=password></form>"));
    fixtures.put("/idm/login", ok("<form><input type=password name=password></form>Usuario incorrecto"));
    var result = service().search(DNI);
    assertThat(source(result, "fuesmen").path("status").asText()).isEqualTo("error");
    assertThat(source(result, "idm").path("status").asText()).isEqualTo("error");
    assertThat(requests).noneMatch(request -> request.path().equals("/fuesmen/login") || request.path().equals("/idm/search"));
    assertThat(result.path("total").asInt()).isEqualTo(5);
  }

  @Test
  void sinCredencialesIgualConsultaFuentesSinLogin() {
    for (String id : List.of("FUESMEN", "IDM", "LABO")) environment.setProperty("EXTERNAL_STUDIES_" + id + "_PASSWORD", "");
    var result = service().search(DNI);
    assertThat(result.path("total").asInt()).isEqualTo(4);
    assertThat(result.path("sources").size()).isEqualTo(7);
    assertThat(source(result, "labo").path("message").asText()).contains("credenciales");
  }

  @Test
  void descartaFechasImposiblesYJavascriptSinRenderizarHtml() throws Exception {
    portal.page("centro", 1, "[{\"studydate\":\"31/02/2026\",\"accessionnumber\":\"C-001\",\"nrodocumento\":\"99000123\"}]", 1);
    fixtures.put("/patologia/search", ok("""
        1 ESTUDIOS ENCONTRADOS<table><tr><td>2026-09-10</td><td>PATOLOGIA</td><td>PATOLOGIA</td><td>PATOLOGIA</td>
        <td><a href='javascript:alert(1)'>Informe</a></td><td><a href='data:text/html,unsafe'>Estudio</a></td></tr></table>
        """));
    var result = service().search(DNI);
    assertThat(source(result, "centro").path("status").asText()).isEqualTo("error");
    assertThat(source(result, "patologia").path("status").asText()).isEqualTo("error");
    assertThat(result.toString()).doesNotContain("javascript:", "data:text", "31/02/2026");
    assertThat(result.path("total").asInt()).isEqualTo(5);
  }

  @Test
  void respuestaDemasiadoGrandeNoAbortaLasOtrasFuentes() {
    environment.setProperty("EXTERNAL_STUDIES_MAX_RESPONSE_BYTES", "4096");
    fixtures.put("/fuesmen/search", ok("{\"data\":[],\"noise\":\"" + "x".repeat(5000) + "\"}"));
    var result = service().search(DNI);
    assertThat(source(result, "fuesmen").path("status").asText()).isEqualTo("error");
    assertThat(source(result, "fuesmen").path("message").asText()).contains("tamaño");
    assertThat(result.path("total").asInt()).isEqualTo(6);
  }

  @Test
  void falloDePaginaDosConservaLaboratoriosDePaginaUno() {
    fixtures.put("/labo/search/page2", new Reply(502, "downstream failure", Map.of(), 0));
    var result = service().search(DNI);
    assertThat(source(result, "labo").path("status").asText()).isEqualTo("error");
    assertThat(source(result, "labo").path("count").asInt()).isEqualTo(1);
    assertThat(result.path("total").asInt()).isEqualTo(7);
    assertThat(result.path("partial").asBoolean()).isTrue();
    assertThat(ExternalStudyProviders.date("07-09-2026")).isEqualTo("2026-09-07");
  }

  @Test
  void noReenviaCredencialesEnRedireccion307AOtraAutoridad() throws Exception {
    AtomicInteger received = new AtomicInteger();
    var other = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
    other.setExecutor(executor); other.createContext("/", exchange -> { received.incrementAndGet(); respond(exchange, ok("")); });
    other.start(); servers.add(other);
    fixtures.put("/fuesmen/login", new Reply(307, "", Map.of("Location", "http://127.0.0.1:" + other.getAddress().getPort() + "/receive"), 0));
    var result = service().search(DNI);
    assertThat(received).hasValue(0);
    assertThat(source(result, "fuesmen").path("status").asText()).isEqualTo("error");
    assertThat(result.path("total").asInt()).isEqualTo(6);
  }

  @Test
  void plazosParalelosNoSumanSieteTimeouts() {
    environment.setProperty("EXTERNAL_STUDIES_SOURCE_TIMEOUT_SECONDS", "1");
    fixtures.replaceAll((path, reply) -> new Reply(reply.status(), reply.body(), reply.headers(), 1800));
    portal.delayMillis = 1800;
    long started = System.nanoTime();
    var result = service().search(DNI);
    Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
    assertThat(elapsed).isLessThan(Duration.ofSeconds(4));
    assertThat(result.path("total").asInt()).isZero();
    for (var source : result.path("sources")) assertThat(source.path("status").asText()).isEqualTo("error");
  }

  @Test
  void documentoInvalidoSeRechazaAntesDeConsultarProveedores() {
    assertThatThrownBy(() -> service().search("123' OR 1=1")).isInstanceOf(IllegalArgumentException.class);
    assertThat(requests).isEmpty();
  }

  private ExternalStudySearchService service() { return new ExternalStudySearchService(mapper, environment); }
  private static JsonNode source(JsonNode result, String id) {
    for (var source : result.path("sources")) if (source.path("id").asText().equals(id)) return source;
    throw new AssertionError("Missing source " + id);
  }
  private String viewerRows(String id, String date, String title, boolean duplicate) {
    var payload = mapper.createObjectNode();
    var rows = payload.putArray("data");
    for (int index = 0; index < (duplicate ? 2 : 1); index++) {
      var row = rows.addArray();
      row.add("<input class='selection' value='" + id + "' name='id_study[]' type='checkbox'>");
      row.add(""); row.add("<span class='calendar'>fecha</span> " + date + " 08:00"); row.add("TC");
      row.add(DNI);
      for (int field = 5; field < 10; field++) row.add("");
      row.add(title);
    }
    return payload.toString();
  }
  private static Reply ok(String body) { return new Reply(200, body, Map.of(), 0); }
  private static void respond(HttpExchange exchange, Reply reply) throws IOException {
    try {
      if (reply.delayMillis() > 0) Thread.sleep(reply.delayMillis());
      exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
      reply.headers().forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
      byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(reply.status(), bytes.length);
      exchange.getResponseBody().write(bytes);
    } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
    finally { exchange.close(); }
  }
  private record Reply(int status, String body, Map<String, String> headers, long delayMillis) {}
  private record Request(String method, String path, String query, String body, String cookie) {}
}
