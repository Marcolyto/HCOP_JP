package ar.com.hexium.hcop.integration.studies;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Resolves and retrieves documents through the portal's study viewer contract. */
final class InformeMedicoReports {
  private static final String VIEWER_BASE = "https://estudio.informemedico.com.ar/";
  private static final Set<String> DOCUMENT_ORIGINS = Set.of(
      "https://imagensalud.informemedico.com.ar:8093",
      "https://centrodeldiagnostico.informemedico.com.ar:8017");
  private final ObjectMapper mapper;
  private final ExternalStudyConfiguration config;
  private final ExternalStudyHttp http;
  private final Map<String, JsonNode> siteConfigs = new LinkedHashMap<>();

  InformeMedicoReports(ObjectMapper mapper, ExternalStudyConfiguration config, ExternalStudyHttp http) {
    this.mapper = mapper; this.config = config; this.http = http;
  }

  String reportUrl(ExternalStudySource source, String studyUrl) {
    Fragment study = fragment(studyUrl);
    URI viewer = base(config.provider(source.id, "VIEWER_BASE_URL", VIEWER_BASE));
    String siteConfigUrl = viewer.resolve("cgi-bin/displaystudy.bf/siteconfig/" + study.site()).toString();
    JsonNode site = siteConfigs.computeIfAbsent(siteConfigUrl, url -> json(http.get(url)));
    if (!site.isObject()) throw failure();
    String fromSite = site.path("getstudyfromsite").asText("");
    if (fromSite.equalsIgnoreCase("false")) return "";
    if (!fromSite.equalsIgnoreCase("true")) throw failure();
    String template = site.path("documentlisturl").asText("");
    if (!template.contains("{{accession}}")) throw failure();
    // The opaque token is already URL encoded in the viewer fragment. Decoding
    // and re-encoding it would change some valid encrypted API2 study keys.
    URI metadata = ExternalStudyHttp.safeUri(template.replace("{{accession}}", study.token()));
    if (metadata.getFragment() != null || !allowedDocument(source, metadata)) throw failure();
    JsonNode documents = json(http.get(ExternalStudyHttp.query(metadata.toString(), study.flags())));
    if (!documents.isArray()) throw failure();
    for (var document : documents) {
      if (!document.path("document_type").asText("").equalsIgnoreCase("pdf")) continue;
      String documentId = document.path("iddocument").asText("");
      String download = site.path("pdfdownloadsurl").asText("");
      if (documentId.isBlank() || documentId.length() > 512 || !download.contains("{{iddocument}}")) throw failure();
      String report = ExternalStudyHttp.safeLink(download.replace("{{iddocument}}", ExternalStudyHttp.encode(documentId).replace("+", "%20")));
      if (report.isBlank()) throw failure();
      URI target = ExternalStudyHttp.safeUri(report);
      if (target.getFragment() != null || !allowedDocument(source, target)) throw failure();
      return report;
    }
    return "";
  }

  ExternalStudyResource download(ExternalStudySource source, String studyUrl, String studyId) {
    String report = reportUrl(source, studyUrl);
    if (report.isBlank()) throw new ExternalStudyFailure("Este estudio todavía no tiene un informe disponible.");
    // The institutional PDF endpoint rejects bare requests with HTTP 403. Its
    // official viewer sends this origin as Referer. Never forward the HCOP
    // request's headers, credentials, DNI or encrypted study fragment.
    String referer = origin(base(config.provider(source.id, "VIEWER_BASE_URL", VIEWER_BASE))) + "/";
    var document = http.download(report, referer);
    byte[] content = document.content();
    if (content.length < 5 || !new String(content, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
      throw new ExternalStudyFailure("La fuente no entregó un informe PDF válido para este estudio.");
    }
    String filename = "informe-" + source.id + "-" + studyId.replaceAll("[^a-zA-Z0-9_-]", "-") + ".pdf";
    return new ExternalStudyResource(content, "application/pdf", filename, "");
  }

  private boolean allowedDocument(ExternalStudySource source, URI target) {
    String configured = config.provider(source.id, "DOCUMENT_BASE_URL", "");
    return DOCUMENT_ORIGINS.contains(origin(target)) || !configured.isBlank() && origin(base(configured)).equals(origin(target));
  }

  static Fragment fragment(String studyUrl) {
    try {
      URI viewer = ExternalStudyHttp.safeUri(studyUrl);
      if (viewer.getRawFragment() == null) throw failure();
      URI fragment = URI.create(viewer.getRawFragment());
      if (fragment.isAbsolute() || fragment.getRawAuthority() != null || fragment.getRawFragment() != null) throw failure();
      String[] segments = fragment.getRawPath().split("/", -1);
      if (segments.length != 3 || !segments[0].isEmpty() || !segments[1].matches("[0-9]{1,10}")
          || segments[2].isBlank() || segments[2].equals(".") || segments[2].equals("..")) throw failure();
      var query = new LinkedHashMap<String, String>();
      if (fragment.getRawQuery() != null) for (String pair : fragment.getRawQuery().split("&")) {
        String[] parts = pair.split("=", 2);
        String name = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
        query.putIfAbsent(name, parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
      }
      var flags = new LinkedHashMap<String, String>();
      for (String name : new String[] {"fromportal", "fromEmail", "withaccession"}) flags.put(name, query.getOrDefault(name, "undefined"));
      return new Fragment(segments[1], segments[2], flags);
    } catch (ExternalStudyFailure safe) { throw safe; }
    catch (Exception invalid) { throw failure(); }
  }

  record Fragment(String site, String token, Map<String, String> flags) {}
  private JsonNode json(String raw) {
    try { JsonNode value = mapper.readTree(raw); if (value == null) throw failure(); return value; }
    catch (Exception invalid) { throw failure(); }
  }
  private static URI base(String value) {
    URI result = ExternalStudyHttp.safeUri(value.endsWith("/") ? value : value + "/");
    if (result.getQuery() != null || result.getFragment() != null) throw failure();
    return result;
  }
  private static String origin(URI uri) {
    int port = uri.getPort() < 0 ? (uri.getScheme().equalsIgnoreCase("https") ? 443 : 80) : uri.getPort();
    String authority = uri.getScheme().toLowerCase(java.util.Locale.ROOT) + "://" + uri.getHost().toLowerCase(java.util.Locale.ROOT);
    return authority + ((uri.getScheme().equalsIgnoreCase("https") && port == 443 || uri.getScheme().equalsIgnoreCase("http") && port == 80) ? "" : ":" + port);
  }
  private static ExternalStudyFailure failure() {
    return new ExternalStudyFailure("No se pudo comprobar el informe de algunos estudios; puede abrir su visor.");
  }
}
