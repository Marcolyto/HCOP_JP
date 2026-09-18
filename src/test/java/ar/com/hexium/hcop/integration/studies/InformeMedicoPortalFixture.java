package ar.com.hexium.hcop.integration.studies;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Sanitized three-account LiveView fixture using the actual response.diff.r shape. */
final class InformeMedicoPortalFixture implements AutoCloseable {
  record Hook(String source, String event, JsonNode value) {}
  final LocalStudySocketServer server = new LocalStudySocketServer();
  final ObjectMapper mapper = new ObjectMapper();
  final Map<String, JsonNode> pages = new ConcurrentHashMap<>();
  final Map<String, LocalStudySocketServer.Reply> documents = new ConcurrentHashMap<>();
  final Set<String> rejectedLogins = ConcurrentHashMap.newKeySet();
  final Set<String> rejectedJoins = ConcurrentHashMap.newKeySet();
  final Set<String> malformedReplies = ConcurrentHashMap.newKeySet();
  final Set<String> rejectedLinks = ConcurrentHashMap.newKeySet();
  final Set<String> failedPages = ConcurrentHashMap.newKeySet();
  final List<Hook> hooks = new CopyOnWriteArrayList<>();
  final List<String> joins = new CopyOnWriteArrayList<>();
  volatile long delayMillis;
  volatile long documentDelayMillis;

  InformeMedicoPortalFixture() throws Exception {
    server.onHttp("/portal/log_in", request -> {
      if (delayMillis > 0) Thread.sleep(delayMillis);
      if (request.method().equals("GET")) return new LocalStudySocketServer.Reply(200,
          "<form><input type='hidden' name='_csrf_token' value='fixture-login-token'><input type='password' name='user[password]'></form>",
          Map.of("Set-Cookie", "login_fixture=ready; Path=/; HttpOnly"));
      Map<String, String> form = form(request.body());
      String source = form.getOrDefault("user[username]", "").replaceFirst("^fixture-", "");
      boolean valid = List.of("centro", "espanol", "malargue").contains(source)
          && form.getOrDefault("user[password]", "").equals("fixture-password-" + source)
          && form.getOrDefault("_csrf_token", "").equals("fixture-login-token")
          && request.header("Cookie").contains("login_fixture=ready") && !rejectedLogins.contains(source);
      if (!valid) return new LocalStudySocketServer.Reply(200, "<form><input type=password name=password></form>Credenciales incorrectas");
      return new LocalStudySocketServer.Reply(302, "", Map.of("Location", "/", "Set-Cookie", "portal_account=" + source + "; Path=/; HttpOnly"));
    });
    server.onHttp("/", request -> new LocalStudySocketServer.Reply(302, "", Map.of("Location", "/sitios")));
    server.onHttp("/sitios", request -> {
      String source = account(request.header("Cookie"));
      return new LocalStudySocketServer.Reply(200,
          "<html><head><meta name='csrf-token' content='fixture-csrf-" + source + "'></head><body>"
              + "<div id='root-" + source + "' data-phx-main data-phx-session='fixture-session-" + source
              + "' data-phx-static='fixture-static-" + source + "'><div id='studies'></div></div></body></html>");
    });
    for (String site : List.of("83", "124")) {
      server.onHttp("/cgi-bin/displaystudy.bf/siteconfig/" + site, request -> new LocalStudySocketServer.Reply(200,
          mapper.createObjectNode().put("getstudyfromsite", "True")
              .put("documentlisturl", server.origin() + "/documents/" + site + "/{{accession}}")
              .put("pdfdownloadsurl", server.origin() + "/reports/{{iddocument}}").toString()));
    }
    server.onWebSocket("/live/websocket", (request, connection) -> {
      String source = account(request.header("Cookie"));
      JsonNode join = mapper.readTree(connection.receiveText());
      joins.add(source);
      boolean valid = join.path(3).asText().equals("phx_join") && join.path(2).asText().equals("lv:root-" + source)
          && join.path(4).path("url").asText().equals(server.origin() + "/sitios")
          && join.path(4).path("flash").isNull()
          && join.path(4).path("params").path("_csrf_token").asText().equals("fixture-csrf-" + source)
          && join.path(4).path("session").asText().equals("fixture-session-" + source)
          && !rejectedJoins.contains(source);
      connection.sendText(reply(join, valid ? "ok" : "error", mapper.createObjectNode().putObject("rendered")).toString());
      if (!valid) return;
      JsonNode selected = mapper.createObjectNode();
      for (String raw; (raw = connection.receiveText()) != null;) {
        JsonNode frame = mapper.readTree(raw);
        String event = frame.path(4).path("event").asText();
        JsonNode value = frame.path(4).path("value");
        hooks.add(new Hook(source, event, value.deepCopy()));
        if (malformedReplies.contains(source)) { connection.sendText("not-json fixture-session-" + source); continue; }
        JsonNode response = mapper.createObjectNode();
        var payload = mapper.createObjectNode();
        var diff = payload.putObject("diff");
        if (event.equals("fetch_data")) {
          if (failedPages.contains(source + ":" + value.path("page").asInt())) {
            connection.sendText(reply(frame, "error", mapper.createObjectNode()).toString()); continue;
          }
          response = pages.getOrDefault(source + ":" + value.path("page").asInt(), emptyPage());
          diff.set("r", response);
        } else if (event.equals("tabulator_rowselected_studies")) {
          selected = value;
          diff.putObject("1").putObject("3").putArray("s").add("<button phx-hook=\"OpenLink\" id=\"open-link-" + apiVersion(source) + "\">Abrir</button>");
        } else if (event.equals(source.equals("espanol") ? "abrir_sitio" : "encriptar")) {
          if (rejectedLinks.contains(selected.path("accessionnumber").asText())) {
            connection.sendText(reply(frame, "error", mapper.createObjectNode()).toString()); continue;
          }
          response = mapper.createObjectNode().put("url", studyUrl(source, selected.path("accessionnumber").asText()));
          diff.set("r", response);
        } else {
          connection.sendText(reply(frame, "error", mapper.createObjectNode()).toString()); continue;
        }
        connection.sendText(reply(frame, "ok", payload).toString());
      }
    });
  }

  void configure(MockEnvironment environment) {
    for (String id : List.of("centro", "espanol", "malargue")) {
      environment.withProperty("EXTERNAL_STUDIES_" + id.toUpperCase() + "_BASE_URL", server.origin())
          .withProperty("EXTERNAL_STUDIES_" + id.toUpperCase() + "_VIEWER_BASE_URL", server.origin())
          .withProperty("EXTERNAL_STUDIES_" + id.toUpperCase() + "_DOCUMENT_BASE_URL", server.origin())
          .withProperty("EXTERNAL_STUDIES_" + id.toUpperCase() + "_USERNAME", "fixture-" + id)
          .withProperty("EXTERNAL_STUDIES_" + id.toUpperCase() + "_PASSWORD", "fixture-password-" + id);
    }
  }

  static String studyUrl(String source, String accession) {
    return "https://study-fixture.example/#/" + (source.equals("espanol") ? "83" : "124") + "/" + source + "-" + accession + "?fromportal=true&fromEmail=fixture%2Bvalue";
  }

  JsonNode emptyPage() { var response = mapper.createObjectNode().put("status", 200).put("last_page", 0); response.putArray("data"); return response; }
  JsonNode page(String source, int number, String rows, int lastPage) throws Exception {
    var response = mapper.createObjectNode().put("status", 200).put("last_page", lastPage).put("apiVersion", apiVersion(source));
    JsonNode data = mapper.readTree(rows);
    for (var row : data) {
      String token = source + "-" + row.path("accessionnumber").asText();
      server.onHttp("/documents/" + (source.equals("espanol") ? "83" : "124") + "/" + token, request -> {
        if (documentDelayMillis > 0) Thread.sleep(documentDelayMillis);
        return documents.getOrDefault(token, new LocalStudySocketServer.Reply(200, "[]"));
      });
    }
    response.set("data", data); pages.put(source + ":" + number, response); return response;
  }
  private JsonNode reply(JsonNode request, String status, JsonNode response) {
    var payload = mapper.createObjectNode().put("status", status); payload.set("response", response);
    return mapper.createArrayNode().add(request.path(0)).add(request.path(1)).add(request.path(2)).add("phx_reply").add(payload);
  }
  private static Map<String, String> form(String body) {
    var values = new java.util.LinkedHashMap<String, String>();
    for (String pair : body.split("&")) { String[] parts = pair.split("=", 2); values.put(decode(parts[0]), parts.length == 2 ? decode(parts[1]) : ""); }
    return values;
  }
  private static String decode(String value) { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
  private static int apiVersion(String source) { return source.equals("espanol") ? 1 : 2; }
  private static String account(String cookie) {
    var matcher = java.util.regex.Pattern.compile("(?:^|;\\s*)portal_account=(centro|espanol|malargue)(?:;|$)").matcher(cookie);
    return matcher.find() ? matcher.group(1) : "unknown";
  }
  @Override public void close() { server.close(); }
}
