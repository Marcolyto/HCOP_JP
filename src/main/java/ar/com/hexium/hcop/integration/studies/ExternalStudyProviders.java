package ar.com.hexium.hcop.integration.studies;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

final class ExternalStudyProviders {
  private final ObjectMapper mapper;
  private final ExternalStudyConfiguration config;
  private final ExternalStudyHttp http;
  ExternalStudyProviders(ObjectMapper mapper, ExternalStudyConfiguration config, ExternalStudyHttp http) {
    this.mapper = mapper; this.config = config; this.http = http;
  }

  ExternalStudyResult search(ExternalStudySource source, String dni) {
    return switch (source) {
      case FUESMEN, IDM -> viewer(source, dni);
      case CENTRO, ESPANOL, MALARGUE -> new InformeMedicoPortal(mapper, config, http).search(source, dni);
      case LABO -> laboratory(dni);
      case PATOLOGIA -> pathology(dni);
    };
  }

  ExternalStudyResource resource(ExternalStudySource source, String dni, String studyId, String kind) {
    if (source == ExternalStudySource.CENTRO || source == ExternalStudySource.ESPANOL || source == ExternalStudySource.MALARGUE) {
      var study = new InformeMedicoPortal(mapper, config, http).study(source, dni, studyId);
      return new InformeMedicoReports(mapper, config, http).download(source, study.studyUrl(), studyId);
    }
    var found = search(source, dni).studies().stream()
        .filter(study -> study.id().equals("external:" + source.id + ":" + studyId)).findFirst()
        .orElseThrow(() -> new ExternalStudyFailure("El estudio no pudo verificarse para el paciente activo. Actualice la búsqueda."));
    if (kind.equals("study") && source != ExternalStudySource.LABO) return viewerResource(source, studyId);
    String report = found.reportUrl();
    String allowed = source == ExternalStudySource.LABO
        ? config.provider("labo", "BASE_URL", "https://schestakow.biolatinasrl.com/default.asp")
        : config.provider(source.id, "REPORT_URL", viewerOrigin(source) + "/editor/index.php/informe_escrito/openFileFromServer/");
    if (!sameOrigin(report, allowed)) throw new ExternalStudyFailure("La fuente devolvió una dirección de informe no autorizada.");
    var download = http.download(report);
    String name = "informe-" + source.id + "-" + studyId.replaceAll("[^a-zA-Z0-9_-]", "-");
    if (download.content().length >= 5 && new String(download.content(), 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
      return new ExternalStudyResource(download.content(), "application/pdf", name + ".pdf", "");
    }
    String html = download.text();
    var document = ExternalStudyHtml.parse(html);
    requireLogin(document);
    if (source == ExternalStudySource.LABO && download.contentType().toLowerCase(Locale.ROOT).contains("text/html") && !document.rows.isEmpty()) {
      // The controller must serve this with a sandbox CSP, never as an HCOP page.
      return new ExternalStudyResource(html.getBytes(StandardCharsets.UTF_8), "text/html", name + ".html", "");
    }
    throw new ExternalStudyFailure("La fuente no entregó un informe válido para este estudio.");
  }

  private ExternalStudyResource viewerResource(ExternalStudySource source, String studyId) {
    String base = config.provider(source.id, "BASE_URL", viewerOrigin(source) + "/viewer/index.php");
    String viewer = config.provider(source.id, "VIEWER_BASE_URL", base);
    return new ExternalStudyResource(new byte[0], "", "", ExternalStudyViewerLink.url(viewer, studyId));
  }

  private static String viewerOrigin(ExternalStudySource source) {
    return source == ExternalStudySource.FUESMEN ? "https://host01.fuesmen.edu.ar" : "https://www.estudiosidm.com:8443";
  }

  private static boolean sameOrigin(String first, String second) {
    URI a = ExternalStudyHttp.safeUri(first); URI b = ExternalStudyHttp.safeUri(second);
    int aPort = a.getPort() >= 0 ? a.getPort() : a.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    int bPort = b.getPort() >= 0 ? b.getPort() : b.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    return a.getScheme().equalsIgnoreCase(b.getScheme()) && a.getHost().equalsIgnoreCase(b.getHost()) && aPort == bPort;
  }

  private ExternalStudyResult viewer(ExternalStudySource source, String dni) {
    String id = source.id;
    String username = config.credential(id, "USERNAME");
    String password = config.credential(id, "PASSWORD");
    String origin = viewerOrigin(source);
    String base = config.provider(id, "BASE_URL", origin + "/viewer/index.php");
    var landing = ExternalStudyHtml.parse(http.get(base));
    var tokens = new LinkedHashMap<>(landing.tokens);
    if (source == ExternalStudySource.FUESMEN && !tokens.containsKey("csrf_token_viewer")) {
      throw new ExternalStudyFailure("La fuente no entregó el código de seguridad del inicio de sesión.");
    }
    var login = new LinkedHashMap<>(tokens);
    login.put("nombre", username); login.put("contrasena", password);
    login.put("tipo_validacion", ""); login.put("accept_terms_conditions", "on");
    String loggedIn = http.post(config.provider(id, "LOGIN_URL", base + "/usuarios/control"), login);
    var loginPage = ExternalStudyHtml.parse(loggedIn);
    requireLogin(loginPage);
    tokens.putAll(loginPage.tokens);
    var result = new Batch(source);
    for (int page = 0; page < config.maxPages(); page++) {
      var fields = new LinkedHashMap<>(tokens);
      for (String key : List.of("usuario", "estudioId", "descripcionEstudio", "accessNumber", "desde", "hasta", "birth",
          "medinformante", "medinformante2", "medsugerido", "studyIUIds", "participacion_informe", "asignado", "sin_asignar",
          "usuario_asignado", "medicosReferentes")) fields.put(key, "");
      fields.put("pacienteId", dni); fields.put("limit", "200"); fields.put("offset", Integer.toString(page * 200));
      fields.put("filtrocomentario", "sin_comentario=false&con_comentario=false");
      fields.put("filtroinforme", "sin_informar=true&preliminar=true&finalizado=true&audio=true");
      JsonNode payload;
      JsonNode rows;
      try {
        payload = json(http.post(config.provider(id, "SEARCH_URL", base + "/vm_ajax/getParametrosLista"), fields));
        rows = dataRows(payload);
      } catch (ExternalStudyFailure failure) { return result.failed(failure.getMessage()); }
      for (var row : rows) {
        String rowDni = row.isArray() ? row.path(4).asText("") : first(row, "pacienteId", "patientId", "dni", "nrodocumento");
        if (!dni.equals(ExternalStudyHtml.plain(rowDni).replaceAll("[.\\s-]", ""))) {
          result.unverified();
          continue;
        }
        String studyId = row.isArray() ? ExternalStudyHtml.parse(row.path(0).asText("")).inputs.getOrDefault("id_study[]", "")
            : first(row, "id_study", "studyId", "id");
        result.add(studyId, row.isArray() ? row.path(2).asText("") : first(row, "studydate", "date", "fecha"),
            row.isArray() ? row.path(3).asText("") : first(row, "modality", "type"),
            row.isArray() ? row.path(10).asText("") : first(row, "description", "title"),
            config.provider(id, "REPORT_URL", origin + "/editor/index.php/informe_escrito/openFileFromServer/") + ExternalStudyHttp.encode(studyId),
            ExternalStudyViewerLink.url(config.provider(id, "VIEWER_BASE_URL", base), studyId));
      }
      int total = payload.path("recordsFiltered").asInt(payload.path("recordsTotal").asInt(-1));
      if (rows.size() < 200 || (total >= 0 && (page + 1) * 200 >= total)) return result.finish();
      if (result.size() >= config.maxResults()) { result.limit(); return result.finish(); }
      // Some installations rotate their token in the data response.
      String refreshed = payload.path("csrf_token_viewer").asText("");
      if (!refreshed.isBlank()) tokens.put("csrf_token_viewer", refreshed);
    }
    result.limit(); return result.finish();
  }

  private ExternalStudyResult laboratory(String dni) {
    String username = config.credential("labo", "USERNAME");
    String password = config.credential("labo", "PASSWORD");
    String loginUrl = config.provider("labo", "LOGIN_URL", "https://schestakow.biolatinasrl.com/default.asp?Action=Login");
    String loginPageUrl = config.provider("labo", "BASE_URL", "https://schestakow.biolatinasrl.com/default.asp");
    var landing = ExternalStudyHtml.parse(http.get(loginPageUrl));
    var login = new LinkedHashMap<>(landing.tokens);
    login.put("login", username); login.put("password", password);
    requireLogin(ExternalStudyHtml.parse(http.post(loginUrl, login)));
    var fields = new LinkedHashMap<String, String>();
    fields.put("sts", "go"); fields.put("tipodoc", "DNI"); fields.put("nrodoc", dni);
    fields.put("estado", "Todos"); fields.put("sServicios", "Todos"); fields.put("DatosInt", "N"); fields.put("Filtro", "0");
    fields.put("cod_ser", config.provider("labo", "SERVICE_CODE", "GIN")); fields.put("servicioSel", config.provider("labo", "SERVICE_CODE", "GIN"));
    for (String key : List.of("frm_no", "frm_cp", "frm_me", "frm_st", "frm_fe", "frm_pd", "nro_ord", "apellido", "ref_externa", "sucursalSel", "origenSel", "estadoSel")) fields.put(key, "");
    var result = new Batch(ExternalStudySource.LABO);
    for (int page = 1; page <= config.maxPages(); page++) {
      String endpoint = ExternalStudyHttp.query(config.provider("labo", "SEARCH_URL", "https://schestakow.biolatinasrl.com/areas/servicio/labListapac.asp"), Map.of("Action", "search", "pag", Integer.toString(page)));
      String html;
      ExternalStudyHtml document;
      try {
        html = http.post(endpoint, fields);
        document = ExternalStudyHtml.parse(html); requireLogin(document);
      } catch (ExternalStudyFailure failure) { return result.failed(failure.getMessage()); }
      int before = result.size();
      boolean recognized = false;
      for (var row : document.rows) {
        for (int index = 0; index + 1 < row.size(); index++) {
          String order = row.get(index).text();
          if (!order.matches("[0-9]{1,30}") || date(row.get(index + 1).text()).isBlank()) continue;
          recognized = true;
          String link = ExternalStudyHttp.query(config.provider("labo", "REPORT_URL", "https://schestakow.biolatinasrl.com/areas/servicio/labResPdfinit.asp"),
              Map.of("Action", "go", "mostrarPDF", "S", "orden", order));
          result.add(order, row.get(index + 1).text(), "Laboratorio", "Laboratorio", link, link);
          break;
        }
      }
      String lower = document.text.toLowerCase(Locale.ROOT);
      if (!recognized && !(lower.contains("sin resultados") || lower.contains("no se encontr") || lower.contains("no hay")
          || lower.contains("orden") || lower.contains("pacientes"))) return result.failed("El laboratorio devolvió una respuesta no reconocida.");
      // The original portal uses at least two pages. Follow further explicit pagination only.
      boolean more = Pattern.compile("(?i)(?:[?&]|&amp;)pag=" + (page + 1) + "(?:[&\\\"'\\s>]|$)").matcher(html).find();
      if (result.size() == before || (page >= 2 && !more)) return result.finish();
      if (result.size() >= config.maxResults()) { result.limit(); return result.finish(); }
    }
    result.limit(); return result.finish();
  }

  private ExternalStudyResult pathology(String dni) {
    String jdbc = config.setting("PATHOLOGY_DB_JDBC_URL", "");
    if (!jdbc.isBlank()) return pathologyDatabase(dni, jdbc);
    // Patología is the explicitly approved exception: its existing source
    // remains available while the main search and other handlers run in Java.
    String endpoint = config.setting("EXTERNAL_STUDIES_PATHOLOGY_FALLBACK_URL", "http://fuesmen.com/buscar_estudios_externos/lista_estudios_rr.php");
    String html = http.get(ExternalStudyHttp.query(endpoint, Map.of("dni", dni, "fuesmen", "no", "idm", "no", "centro", "no", "espanol", "no", "labo", "no")));
    var document = ExternalStudyHtml.parse(html); requireLogin(document);
    var result = new Batch(ExternalStudySource.PATOLOGIA);
    for (var row : document.rows) {
      if (row.size() < 6 || !fold(row.get(3).text()).equals("PATOLOGIA")) continue;
      String report = row.get(4).links().stream().findFirst().orElse("");
      String study = row.get(5).links().stream().findFirst().orElse(report);
      // The legacy pathology source emits literal spaces in PDF filenames.
      // Encode those path characters here without relaxing URL validation.
      String safeReport = resolve(endpoint, report.replace(" ", "%20"));
      String safeStudy = resolve(endpoint, study.replace(" ", "%20"));
      result.add(digest(safeReport.isBlank() ? safeStudy : safeReport), row.get(0).text(), "Patología", "Anatomía patológica", safeReport, safeStudy);
    }
    if (result.size() == 0 && !fold(document.text).contains("0 ESTUDIOS ENCONTRADOS")) {
      throw new ExternalStudyFailure("No se pudo reconocer la respuesta de Patología; configure su conexión de base de datos o revise la fuente.");
    }
    return result.finish();
  }

  private ExternalStudyResult pathologyDatabase(String dni, String jdbc) {
    if (!(jdbc.startsWith("jdbc:postgresql:") || jdbc.startsWith("jdbc:mysql:"))) {
      throw new ExternalStudyFailure("La conexión de Patología debe usar PostgreSQL o MySQL.");
    }
    String username = config.setting("PATHOLOGY_DB_USERNAME", "");
    String password = config.setting("PATHOLOGY_DB_PASSWORD", "");
    if (username.isBlank() || password.isBlank()) throw new ExternalStudyFailure("Faltan las credenciales de la base de Patología.");
    var properties = new Properties();
    properties.setProperty("user", username); properties.setProperty("password", password);
    int timeout = (int) Math.max(1, (http.remainingMillis() + 999) / 1000);
    boolean postgres = jdbc.startsWith("jdbc:postgresql:");
    properties.setProperty("connectTimeout", Integer.toString(postgres ? timeout : timeout * 1000));
    properties.setProperty("socketTimeout", Integer.toString(postgres ? timeout : timeout * 1000));
    properties.setProperty("sslmode", "verify-full"); properties.setProperty("sslMode", "VERIFY_IDENTITY");
    var result = new Batch(ExternalStudySource.PATOLOGIA);
    try (var connection = DriverManager.getConnection(jdbc, properties);
        var statement = connection.prepareStatement("SELECT archivo, fecha_carga FROM registro_tumores_admisiones WHERE dni = ? ORDER BY fecha_carga DESC LIMIT ?")) {
      statement.setQueryTimeout((int) Math.max(1, (http.remainingMillis() + 999) / 1000));
      statement.setString(1, dni); statement.setInt(2, config.maxResults());
      try (var rows = statement.executeQuery()) {
        while (rows.next()) {
          http.remainingMillis();
          String file = rows.getString("archivo");
          String base = config.setting("EXTERNAL_STUDIES_PATHOLOGY_FILES_BASE_URL", "http://pangeasystem.com/registro_tumores/");
          String link = file == null || file.contains("..") || file.contains(":") || file.contains("\\") ? "" :
              resolve(base, java.util.Arrays.stream(file.split("/")).map(segment -> ExternalStudyHttp.encode(segment).replace("+", "%20")).collect(java.util.stream.Collectors.joining("/")));
          result.add(digest(link), rows.getString("fecha_carga"), "Patología", "Anatomía patológica", link, link);
        }
      }
      if (result.size() >= config.maxResults()) result.limit();
      return result.finish();
    } catch (ExternalStudyFailure failure) { return result.failed(failure.getMessage()); }
    catch (Exception failure) { return result.failed("No se pudo consultar la base de Patología. Revise conexión, certificado y controlador JDBC."); }
  }

  private JsonNode json(String body) {
    if (body.trim().startsWith("<")) {
      requireLogin(ExternalStudyHtml.parse(body));
      throw new ExternalStudyFailure("La fuente devolvió HTML en lugar de los resultados esperados.");
    }
    try {
      JsonNode result = mapper.readTree(body);
      if (result == null || !result.isObject()) throw new IllegalArgumentException();
      if (result.hasNonNull("error") && !result.path("error").asText("").isBlank()) throw new IllegalArgumentException();
      return result;
    } catch (Exception invalid) { throw new ExternalStudyFailure("La fuente devolvió una respuesta JSON inválida."); }
  }
  private static JsonNode dataRows(JsonNode payload) {
    var rows = payload.path("data");
    if (!rows.isArray()) throw new ExternalStudyFailure("La fuente no entregó una lista de estudios válida.");
    return rows;
  }
  private static void requireLogin(ExternalStudyHtml document) {
    if (document.loginRejected()) throw new ExternalStudyFailure("La fuente no confirmó el inicio de sesión. Revise sus credenciales.");
  }
  private static String first(JsonNode value, String... fields) {
    for (String field : fields) { String text = value.path(field).asText(""); if (!text.isBlank()) return text; }
    return "";
  }
  private static String resolve(String base, String reference) {
    if (reference.isBlank()) return "";
    try { return ExternalStudyHttp.safeLink(ExternalStudyHttp.safeUri(base).resolve(reference).toString()); }
    catch (Exception invalid) { return ""; }
  }
  static String date(String value) {
    String text = ExternalStudyHtml.plain(value == null ? "" : value);
    var iso = Pattern.compile("(?<![0-9])([0-9]{4})[-/]([0-9]{2})[-/]([0-9]{2})(?![0-9])").matcher(text);
    var latin = Pattern.compile("(?<![0-9])([0-9]{2})[-/]([0-9]{2})[-/]([0-9]{4})(?![0-9])").matcher(text);
    var compact = Pattern.compile("(?<![0-9])([0-9]{4})([0-9]{2})([0-9]{2})(?![0-9])").matcher(text);
    try {
      if (iso.find()) return LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3))).toString();
      if (latin.find()) return LocalDate.of(Integer.parseInt(latin.group(3)), Integer.parseInt(latin.group(2)), Integer.parseInt(latin.group(1))).toString();
      if (compact.find()) return LocalDate.of(Integer.parseInt(compact.group(1)), Integer.parseInt(compact.group(2)), Integer.parseInt(compact.group(3))).toString();
    } catch (java.time.DateTimeException invalid) { return ""; }
    return "";
  }
  private static String digest(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24); }
    catch (Exception impossible) { throw new IllegalStateException(); }
  }
  private static String fold(String value) { return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT).trim(); }

  private final class Batch {
    private final ExternalStudySource source;
    private final Map<String, ExternalStudy> studies = new LinkedHashMap<>();
    private boolean partial;
    private String message = "";
    Batch(ExternalStudySource source) { this.source = source; }
    int size() { return studies.size(); }
    void limit() { partial = true; message = "Se alcanzó el límite de resultados de esta fuente. Puede haber más estudios."; }
    void unverified() { partial = true; message = "Algunos registros no pudieron verificarse para el documento consultado y no se muestran."; }
    void add(String sourceId, String rawDate, String type, String title, String report, String study) {
      String cleanId = ExternalStudyHtml.plain(sourceId).trim();
      String cleanDate = date(rawDate);
      String reportUrl = ExternalStudyHttp.safeLink(report); String studyUrl = ExternalStudyHttp.safeLink(study);
      if (cleanId.isBlank() || cleanDate.isBlank() || (reportUrl.isBlank() && studyUrl.isBlank())) {
        partial = true; message = "Algunos registros de esta fuente no tienen fecha o enlace válidos y no se muestran."; return;
      }
      if (studies.size() >= config.maxResults()) { limit(); return; }
      if (cleanId.length() > 160) cleanId = digest(cleanId);
      String id = "external:" + source.id + ":" + cleanId;
      String cleanTitle = text(title, 320); String cleanType = text(type, 100);
      studies.putIfAbsent(id, new ExternalStudy(id, cleanDate, cleanType.isBlank() ? "Estudio" : cleanType,
          cleanTitle.isBlank() ? "Estudio externo" : cleanTitle, source.displayName, reportUrl, studyUrl, source.id));
    }
    ExternalStudyResult finish() { return new ExternalStudyResult(List.copyOf(studies.values()), partial, message); }
    ExternalStudyResult failed(String failure) { return new ExternalStudyResult(List.copyOf(studies.values()), true, failure); }
    private String text(String value, int maximum) { String clean = ExternalStudyHtml.plain(value == null ? "" : value); return clean.substring(0, Math.min(clean.length(), maximum)); }
  }
}
