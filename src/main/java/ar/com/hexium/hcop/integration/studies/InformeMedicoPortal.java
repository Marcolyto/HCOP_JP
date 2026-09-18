package ar.com.hexium.hcop.integration.studies;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** A fresh authenticated LiveView session for exactly one institution and DNI. */
final class InformeMedicoPortal {
  private static final int PAGE_SIZE = 10;
  private final ObjectMapper mapper;
  private final ExternalStudyConfiguration config;
  private final ExternalStudyHttp http;

  InformeMedicoPortal(ObjectMapper mapper, ExternalStudyConfiguration config, ExternalStudyHttp http) {
    this.mapper = mapper; this.config = config; this.http = http;
  }

  ExternalStudyResult search(ExternalStudySource source, String dni) {
    // Obtain every verified viewer first: optional report metadata must not
    // consume the deadline before the remaining studies can be collected.
    return withReports(source, studyLinks(source, dni, null));
  }

  ExternalStudy study(ExternalStudySource source, String dni, String accession) {
    return studyLinks(source, dni, accession).studies().stream()
        .filter(study -> study.id().equals("external:" + source.id + ":" + accession)).findFirst()
        .orElseThrow(() -> new ExternalStudyFailure("El estudio no pudo verificarse para el paciente activo. Actualice la búsqueda."));
  }

  private ExternalStudyResult studyLinks(ExternalStudySource source, String dni, String wantedAccession) {
    String username = config.credential(source.id, "USERNAME");
    String password = config.credential(source.id, "PASSWORD");
    URI base = baseUri(config.provider(source.id, "BASE_URL",
        config.setting("EXTERNAL_STUDIES_INFORMEMEDICO_BASE_URL", "https://portal.informemedico.com.ar/")));
    String loginUrl = config.provider(source.id, "LOGIN_URL", base.resolve("portal/log_in").toString());
    var login = ExternalStudyHtml.parse(http.get(loginUrl));
    String loginToken = login.tokens.getOrDefault("_csrf_token", "");
    if (loginToken.isBlank()) throw new ExternalStudyFailure("La fuente no entregó el código de seguridad del inicio de sesión.");
    var fields = new LinkedHashMap<String, String>();
    fields.put("_csrf_token", loginToken);
    fields.put("user[username]", username); fields.put("user[password]", password);
    var authenticated = ExternalStudyHtml.parse(http.post(loginUrl, fields));
    String pageUrl = http.lastResponseUrl();
    String csrf = authenticated.tokens.getOrDefault("csrf-token", "");
    if (authenticated.loginRejected() || csrf.isBlank() || authenticated.liveViews.size() != 1) {
      throw new ExternalStudyFailure("La fuente no confirmó el inicio de sesión. Revise sus credenciales.");
    }
    var liveView = authenticated.liveViews.get(0);
    String socketUrl = config.provider(source.id, "SOCKET_URL", base.resolve("live/websocket").toString().replaceFirst("^http", "ws"));
    socketUrl = ExternalStudyHttp.query(socketUrl, Map.of("_csrf_token", csrf, "_mounts", "0", "vsn", "2.0.0"));
    var studies = new LinkedHashMap<String, ExternalStudy>();
    boolean partial = false;
    String message = "";
    String previousPage = "";
    try (var socket = http.openSocket(socketUrl, base.toString())) {
      // LiveView validates the route that rendered these signed tokens. The
      // portal can redirect / to /sitios after login, so use the final URL.
      var channel = new Channel(socket, liveView, pageUrl, csrf);
      for (int page = 1; page <= config.maxPages(); page++) {
        ObjectNode request = mapper.createObjectNode().put("id", "studies").put("page", page).put("size", PAGE_SIZE);
        request.putArray("filter").addObject().put("field", "nrodocumento").put("type", "=").put("value", dni);
        request.putArray("sort");
        JsonNode reply = channel.event("fetch_data", request);
        if (reply.path("status").asInt() != 200 || !reply.path("data").isArray()) {
          throw new ExternalStudyFailure("La fuente no entregó una lista de estudios válida.");
        }
        JsonNode rows = reply.path("data");
        String signature = rows.toString();
        if (page > 1 && signature.equals(previousPage) && !rows.isEmpty()) return limited(studies);
        previousPage = signature;
        for (var row : rows) {
          http.remainingMillis();
          // Do not trust a remote filter to enforce patient identity.
          if (!row.isObject() || !dni.equals(normalizeDni(row.path("nrodocumento").asText("")))) {
            partial = true; message = "Algunos registros no pudieron verificarse para el documento consultado y no se muestran.";
            continue;
          }
          String accession = ExternalStudyHtml.plain(row.path("accessionnumber").asText("")).trim();
          String date = ExternalStudyProviders.date(row.path("studydate").asText(""));
          if (accession.isBlank() || accession.length() > 160 || date.isBlank()) {
            partial = true; message = "Algunos registros de esta fuente no tienen identificador o fecha válidos y no se muestran.";
            continue;
          }
          // A report click needs only its verified viewer. Do not spend the
          // shared deadline resolving other studies or their report metadata.
          if (wantedAccession != null && !wantedAccession.equals(accession)) continue;
          String id = "external:" + source.id + ":" + accession;
          if (studies.containsKey(id)) continue;
          if (studies.size() >= config.maxResults()) return limited(studies);
          StudyLinks links;
          try { links = resolveStudyLinks(channel, row, reply.path("apiVersion").asInt(0)); }
          catch (ExternalStudyFailure failure) {
            partial = true; message = failure.getMessage();
            if (!channel.isUsable()) return new ExternalStudyResult(List.copyOf(studies.values()), true, message);
            continue;
          }
          String studyUrl = links.studyUrl();
          String reportUrl = links.reportUrl();
          if (studyUrl.isBlank() && reportUrl.isBlank()) {
            partial = true; message = "Algunos registros de esta fuente no tienen enlaces válidos y no se muestran.";
            continue;
          }
          String title = clean(row.path("proceduredescription").asText(""), 320);
          String type = clean(row.path("modality").asText(""), 100);
          studies.put(id, new ExternalStudy(id, date, type.isBlank() ? "Estudio" : type,
              title.isBlank() ? "Estudio externo" : title, source.displayName, reportUrl, studyUrl, source.id));
          if (wantedAccession != null) return new ExternalStudyResult(List.copyOf(studies.values()), partial, message);
        }
        int lastPage = reply.path("last_page").asInt(-1);
        if (rows.isEmpty() || lastPage >= 0 && page >= lastPage || lastPage < 0 && rows.size() < PAGE_SIZE) {
          return new ExternalStudyResult(List.copyOf(studies.values()), partial, message);
        }
      }
      return limited(studies);
    } catch (ExternalStudyFailure failure) {
      return new ExternalStudyResult(List.copyOf(studies.values()), true, failure.getMessage());
    }
  }

  private ExternalStudyResult withReports(ExternalStudySource source, ExternalStudyResult result) {
    var studies = new ArrayList<>(result.studies());
    var reports = new InformeMedicoReports(mapper, config, http);
    boolean partial = result.partial();
    String message = result.message();
    for (int index = 0; index < studies.size(); index++) {
      var study = studies.get(index);
      try {
        http.remainingMillis();
        String report = reports.reportUrl(source, study.studyUrl());
        if (!report.equals(study.reportUrl())) studies.set(index, new ExternalStudy(study.id(), study.date(), study.type(),
            study.title(), study.source(), report, study.studyUrl(), study.sourceId()));
      } catch (ExternalStudyFailure unavailable) {
        partial = true;
        if (message.isBlank()) message = "No se pudo comprobar el informe de algunos estudios; puede abrir su visor.";
        // Stop promptly after the shared deadline, retaining every viewer and
        // the reports that were already resolved. Other metadata errors allow
        // the remaining reports to be attempted while time remains.
        try { http.remainingMillis(); }
        catch (ExternalStudyFailure exhausted) { break; }
      }
    }
    return new ExternalStudyResult(List.copyOf(studies), partial, message);
  }

  private record StudyLinks(String reportUrl, String studyUrl) {}

  private StudyLinks resolveStudyLinks(Channel channel, JsonNode row, int apiVersion) {
    channel.event("tabulator_rowselected_studies", row);
    // The two generations expose different study openers, not separate report
    // buttons. Invoke only the hook rendered after selecting this exact row.
    String studyUrl = ExternalStudyHttp.safeLink(channel.event(channel.studyOpenEvent(apiVersion), mapper.createObjectNode()).path("url").asText(""));
    return new StudyLinks("", studyUrl);
  }

  private final class Channel {
    private final ExternalStudySocket socket;
    private final String topic;
    private int reference = 1;
    private boolean usable = true;
    private JsonNode lastDiff;
    private String selectedOpenEvent = "";
    Channel(ExternalStudySocket socket, ExternalStudyHtml.LiveView liveView, String url, String csrf) {
      this.socket = socket; this.topic = "lv:" + liveView.id();
      ObjectNode join = mapper.createObjectNode().put("url", url).put("session", liveView.session()).put("static", liveView.staticToken()).putNull("flash");
      join.putObject("params").put("_csrf_token", csrf).put("_mounts", 0);
      call("phx_join", join);
    }
    JsonNode event(String name, JsonNode value) {
      ObjectNode event = mapper.createObjectNode().put("type", "hook").put("event", name).putNull("cid");
      event.set("value", value);
      return call("event", event);
    }
    boolean isUsable() { return usable; }
    String studyOpenEvent(int apiVersion) {
      String rendered = lastDiff == null ? "" : lastDiff.toString();
      boolean first = rendered.contains("open-link-1");
      boolean second = rendered.contains("open-link-2");
      if (first && second) throw new ExternalStudyFailure("La fuente devolvió opciones de estudio ambiguas.");
      if (first) selectedOpenEvent = "abrir_sitio";
      else if (second) selectedOpenEvent = "encriptar";
      else if (selectedOpenEvent.isBlank() && apiVersion == 1) selectedOpenEvent = "abrir_sitio";
      else if (selectedOpenEvent.isBlank() && apiVersion == 2) selectedOpenEvent = "encriptar";
      if (selectedOpenEvent.isBlank()) throw new ExternalStudyFailure("La fuente no entregó una opción válida para abrir este estudio.");
      return selectedOpenEvent;
    }
    private JsonNode call(String event, JsonNode payload) {
      String ref = Integer.toString(reference++);
      var frame = mapper.createArrayNode().add("1").add(ref).add(topic).add(event).add(payload);
      try { socket.send(frame.toString()); }
      catch (ExternalStudyFailure failure) { usable = false; throw failure; }
      for (int count = 0; count < 128; count++) {
        JsonNode response;
        try { response = mapper.readTree(socket.receive()); }
        catch (ExternalStudyFailure failure) { usable = false; throw failure; }
        catch (Exception invalid) { throw unusable("La fuente devolvió una respuesta de consulta inválida."); }
        if (response == null || !response.isArray() || response.size() != 5) throw unusable("La fuente devolvió un mensaje de consulta inválido.");
        if (!topic.equals(response.path(2).asText(""))) continue;
        String responseEvent = response.path(3).asText("");
        if (responseEvent.equals("phx_error") || responseEvent.equals("phx_close")) throw unusable("La fuente cerró la sesión de consulta.");
        if (!responseEvent.equals("phx_reply") || !ref.equals(response.path(1).asText(""))) continue;
        JsonNode envelope = response.path(4);
        if (!envelope.path("status").asText("").equals("ok")) throw new ExternalStudyFailure("La fuente rechazó la consulta. Revise la sesión y sus permisos.");
        JsonNode body = envelope.path("response");
        if (!body.isObject()) throw unusable("La fuente devolvió una respuesta de consulta inválida.");
        lastDiff = body.path("diff");
        // LiveView 0.20 serializes hook replies in the reserved diff.r slot.
        return body.path("diff").has("r") ? body.path("diff").path("r") : body.has("reply") ? body.path("reply") : body;
      }
      throw unusable("La fuente envió demasiados mensajes sin responder la consulta.");
    }
    private ExternalStudyFailure unusable(String message) { usable = false; return new ExternalStudyFailure(message); }
  }

  private static URI baseUri(String value) {
    URI uri = ExternalStudyHttp.safeUri(value);
    if (uri.getQuery() != null || uri.getFragment() != null) throw new ExternalStudyFailure("La fuente contiene una dirección inválida.");
    return value.endsWith("/") ? uri : ExternalStudyHttp.safeUri(value + "/");
  }
  private static String normalizeDni(String value) { return value.replaceAll("[.\\s-]", ""); }
  private static String clean(String value, int maximum) { String text = ExternalStudyHtml.plain(value); return text.substring(0, Math.min(maximum, text.length())); }
  private static ExternalStudyResult limited(Map<String, ExternalStudy> studies) {
    return new ExternalStudyResult(List.copyOf(studies.values()), true, "Se alcanzó el límite de resultados de esta fuente. Puede haber más estudios.");
  }
}
