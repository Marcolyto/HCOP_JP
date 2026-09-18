package ar.com.hexium.hcop.integration.studies;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.integration.SecretBox;
import ar.com.hexium.hcop.integration.SystemSettingsRepository;
import java.net.URI;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Server-owned repository configuration. Secret values never enter its public representation. */
@Service
public class StudyRepositorySettings {
  private final SystemSettingsRepository settings;
  private final SecretBox secrets;
  private final ObjectMapper mapper;
  private final Environment environment;

  public StudyRepositorySettings(SystemSettingsRepository settings, SecretBox secrets,
      ObjectMapper mapper, Environment environment) {
    this.settings = settings; this.secrets = secrets; this.mapper = mapper; this.environment = environment;
  }

  public Map<String, Object> publicView() {
    var items = new ArrayList<Map<String, Object>>();
    for (var source : ExternalStudySource.values()) {
      var current = load(source);
      var endpoints = new ArrayList<Map<String, String>>();
      for (var field : fields(source)) endpoints.add(Map.of("key", field.key(), "label", field.label(), "url", current.endpoints().get(field.key())));
      items.add(Map.of("id", source.id, "name", source.displayName,
          "requiresCredentials", source != ExternalStudySource.PATOLOGIA,
          "username", current.username(), "hasPassword", !current.password().isBlank(), "endpoints", endpoints));
    }
    return Map.of("ok", true, "items", items);
  }

  @Transactional
  public Map<String, Object> update(String sourceId, JsonNode input, long actorId) {
    var source = Arrays.stream(ExternalStudySource.values()).filter(item -> item.id.equals(sourceId)).findFirst()
        .orElseThrow(() -> invalid("El repositorio no existe."));
    if (input == null || !input.isObject()) throw invalid("La configuración no es válida.");
    var current = load(source);
    var endpoints = new LinkedHashMap<>(current.endpoints());
    if (input.has("endpoints")) {
      if (!input.path("endpoints").isArray()) throw invalid("Los endpoints deben ser una lista.");
      var seen = new HashSet<String>();
      for (var entry : input.path("endpoints")) {
        String key = entry.path("key").asText("");
        if (!endpoints.containsKey(key) || !seen.add(key)) throw invalid("Hay un endpoint desconocido o repetido.");
        String url = entry.path("url").asText("").trim();
        validateEndpoint(url, key.equals("SOCKET_URL"));
        String scheme = URI.create(url).getScheme();
        url = scheme.toLowerCase(Locale.ROOT) + url.substring(scheme.length());
        endpoints.put(key, url);
      }
    }
    String username = input.path("username").asText(current.username()).trim();
    if (username.length() > 200 || username.chars().anyMatch(Character::isISOControl)) throw invalid("El usuario no es válido.");
    String action = input.path("passwordAction").asText("keep");
    String password = switch (action) {
      case "keep" -> current.password();
      case "remove" -> "";
      case "replace" -> input.path("password").asText("");
      default -> throw invalid("La acción de contraseña no es válida.");
    };
    if (password.length() > 4096 || (action.equals("replace") && password.isBlank())) throw invalid("Ingrese una contraseña válida.");
    if (source == ExternalStudySource.PATOLOGIA && (!username.isBlank() || !password.isBlank())) {
      throw invalid("El listado público de Patología no requiere usuario ni contraseña.");
    }
    // A changed host must not silently receive a saved credential.
    boolean changedAuthority = endpoints.entrySet().stream().anyMatch(entry -> !authority(entry.getValue()).equals(authority(current.endpoints().get(entry.getKey()))));
    if (changedAuthority && !password.isBlank() && action.equals("keep")) {
      throw invalid("Al cambiar el servidor, vuelva a ingresar la contraseña para ese destino.");
    }
    var value = mapper.createObjectNode().put("username", username);
    value.set("endpoints", mapper.valueToTree(endpoints));
    settings.upsert(key(source), value, secrets.encrypt(password), false, actorId);
    return publicView();
  }

  ExternalStudyConfiguration configuration() {
    var overrides = new LinkedHashMap<String, String>();
    for (var source : ExternalStudySource.values()) {
      // An immutable snapshot keeps a single search consistent while settings are edited.
      var current = load(source);
      current.endpoints().forEach((key, url) -> overrides.put(envKey(source, key), url));
      overrides.put(envKey(source, "USERNAME"), current.username());
      overrides.put(envKey(source, "PASSWORD"), current.password());
    }
    return new ExternalStudyConfiguration(environment, overrides);
  }

  private Configuration load(ExternalStudySource source) {
    var stored = settings.find(key(source)).orElse(null);
    var endpoints = new LinkedHashMap<String, String>();
    for (var field : fields(source)) {
      String fallback = new ExternalStudyConfiguration(environment).setting(envKey(source, field.key()), field.fallback());
      if (stored != null) fallback = stored.value().path("endpoints").path(field.key()).asText(fallback);
      endpoints.put(field.key(), fallback);
    }
    String username = stored == null ? environment.getProperty(envKey(source, "USERNAME"), "") : stored.value().path("username").asText("");
    String password = stored == null ? environment.getProperty(envKey(source, "PASSWORD"), "") : secrets.decrypt(stored.secret());
    return new Configuration(endpoints, username, password);
  }

  private List<Field> fields(ExternalStudySource source) {
    String id = source.id;
    var config = new ExternalStudyConfiguration(environment);
    if (source == ExternalStudySource.FUESMEN || source == ExternalStudySource.IDM) {
      String origin = source == ExternalStudySource.FUESMEN ? "https://host01.fuesmen.edu.ar" : "https://www.estudiosidm.com:8443";
      String base = config.provider(id, "BASE_URL", origin + "/viewer/index.php");
      return List.of(new Field("BASE_URL", "Portal", base),
          new Field("LOGIN_URL", "Inicio de sesión", base + "/usuarios/control"),
          new Field("SEARCH_URL", "Búsqueda de estudios", base + "/vm_ajax/getParametrosLista"),
          new Field("REPORT_URL", "Informes", origin + "/editor/index.php/informe_escrito/openFileFromServer/"),
          new Field("VIEWER_BASE_URL", "Visor", base));
    }
    if (source == ExternalStudySource.LABO) return List.of(
        new Field("BASE_URL", "Portal", "https://schestakow.biolatinasrl.com/default.asp"),
        new Field("LOGIN_URL", "Inicio de sesión", "https://schestakow.biolatinasrl.com/default.asp?Action=Login"),
        new Field("SEARCH_URL", "Búsqueda de estudios", "https://schestakow.biolatinasrl.com/areas/servicio/labListapac.asp"),
        new Field("REPORT_URL", "Informes PDF", "https://schestakow.biolatinasrl.com/areas/servicio/labResPdfinit.asp"));
    if (source == ExternalStudySource.PATOLOGIA) return List.of(
        new Field("FALLBACK_URL", "Listado de Patología", "http://fuesmen.com/buscar_estudios_externos/lista_estudios_rr.php"),
        new Field("FILES_BASE_URL", "Archivos de Patología", "http://pangeasystem.com/registro_tumores/"));
    String base = config.provider(id, "BASE_URL", config.setting("EXTERNAL_STUDIES_INFORMEMEDICO_BASE_URL", "https://portal.informemedico.com.ar/"));
    URI portal = URI.create(base.endsWith("/") ? base : base + "/");
    return List.of(new Field("BASE_URL", "Portal", base),
        new Field("LOGIN_URL", "Inicio de sesión", portal.resolve("portal/log_in").toString()),
        new Field("SOCKET_URL", "Consulta del portal", portal.resolve("live/websocket").toString().replaceFirst("^http", "ws")),
        new Field("VIEWER_BASE_URL", "Visor e informes", "https://estudio.informemedico.com.ar"),
        new Field("DOCUMENT_BASE_URL", "Servidor de documentos", source == ExternalStudySource.ESPANOL
            ? "https://imagensalud.informemedico.com.ar:8093" : "https://centrodeldiagnostico.informemedico.com.ar:8017"));
  }

  private static void validateEndpoint(String value, boolean socket) {
    try {
      if (value == null || value.isBlank() || value.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException();
      URI uri = URI.create(value);
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      if (value.length() > 2048 || !(socket ? Set.of("ws", "wss") : Set.of("http", "https")).contains(scheme)
          || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
          || uri.getPort() > 65535
          || uri.getHost().equals("169.254.169.254")) throw new IllegalArgumentException();
    } catch (IllegalArgumentException invalid) { throw invalid("El endpoint debe ser una dirección válida sin usuario ni contraseña incluidos."); }
  }
  private static String authority(String url) {
    URI uri = URI.create(url);
    String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
    int port = uri.getPort() >= 0 ? uri.getPort() : Set.of("https", "wss").contains(scheme) ? 443 : 80;
    return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + ":" + port;
  }
  private static String key(ExternalStudySource source) { return "study-repository." + source.id; }
  private static String envKey(ExternalStudySource source, String key) {
    return "EXTERNAL_STUDIES_" + (source == ExternalStudySource.PATOLOGIA ? "PATHOLOGY" : source.id.toUpperCase(Locale.ROOT)) + "_" + key;
  }
  private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
  private record Field(String key, String label, String fallback) {}
  private record Configuration(Map<String, String> endpoints, String username, String password) {}
}
