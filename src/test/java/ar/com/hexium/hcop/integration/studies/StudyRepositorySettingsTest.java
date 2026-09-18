package ar.com.hexium.hcop.integration.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.config.HcopProperties;
import ar.com.hexium.hcop.integration.SecretBox;
import ar.com.hexium.hcop.integration.SystemSettingsRepository;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class StudyRepositorySettingsTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final SystemSettingsRepository repository = mock(SystemSettingsRepository.class);
  private final SecretBox secrets = new SecretBox(new HcopProperties(null, null, null, "", "", 0, 0, 0, "", "fixture-encryption-key"));
  private final Map<String, SystemSettingsRepository.Setting> stored = new LinkedHashMap<>();
  private MockEnvironment environment;
  private StudyRepositorySettings service;
  private long savedActor;

  @BeforeEach void setUp() {
    environment = new MockEnvironment()
        .withProperty("EXTERNAL_STUDIES_FUESMEN_USERNAME", "fixture-user")
        .withProperty("EXTERNAL_STUDIES_FUESMEN_PASSWORD", "environment-secret");
    when(repository.find(anyString())).thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));
    when(repository.upsert(anyString(), any(JsonNode.class), any(), anyBoolean(), anyLong())).thenAnswer(call -> {
      String key = call.getArgument(0); JsonNode value = call.getArgument(1); byte[] secret = call.getArgument(2);
      assertThat(call.<Boolean>getArgument(3)).isFalse();
      savedActor = call.getArgument(4);
      var next = new SystemSettingsRepository.Setting(key, value.deepCopy(), secret == null ? null : secret.clone(), 1L);
      stored.put(key, next); return next;
    });
    service = new StudyRepositorySettings(repository, secrets, mapper, environment);
  }

  @Test void listadoDeSieteRepositoriosSoloExponePresenciaDeClave() {
    JsonNode view = mapper.valueToTree(service.publicView());
    assertThat(view.path("items").size()).isEqualTo(7);
    assertThat(item(view, "fuesmen").path("username").asText()).isEqualTo("fixture-user");
    assertThat(item(view, "fuesmen").path("hasPassword").asBoolean()).isTrue();
    assertThat(item(view, "patologia").path("requiresCredentials").asBoolean()).isFalse();
    assertThat(view.toString()).doesNotContain("environment-secret", "fixture-encryption-key", "\"password\":", "\"secret\":");
    assertThat(stored).isEmpty();
  }

  @Test void reemplazoSeGuardaCifradoFueraDelJsonYPreservaPasswordExacta() {
    String password = "  fixture-new-secret  ";
    var input = action("replace").put("password", password).put("username", " new-user ");
    var result = service.update("fuesmen", input, 71L);
    var saved = stored.get("study-repository.fuesmen");
    assertThat(saved.value().toString()).doesNotContain(password, "password", "passwordAction", "fixture-new-secret");
    assertThat(saved.secret()).isNotEmpty().isNotEqualTo(password.getBytes(StandardCharsets.UTF_8));
    assertThat(secrets.decrypt(saved.secret())).isEqualTo(password);
    assertThat(savedActor).isEqualTo(71L);
    assertThat(service.configuration().credential("fuesmen", "USERNAME")).isEqualTo("new-user");
    assertThat(service.configuration().credential("fuesmen", "PASSWORD")).isEqualTo(password);
    assertThat(mapper.writeValueAsString(result)).doesNotContain(password, "fixture-new-secret");
  }

  @Test void conservarImportaClaveDeEntornoYDespuesMantieneLaGuardada() {
    service.update("fuesmen", action("keep"), 7L);
    assertThat(secrets.decrypt(stored.get("study-repository.fuesmen").secret())).isEqualTo("environment-secret");
    environment.setProperty("EXTERNAL_STUDIES_FUESMEN_PASSWORD", "changed-environment-secret");
    service.update("fuesmen", action("keep").put("password", "ignored-password").put("username", "updated-user"), 8L);
    assertThat(service.configuration().credential("fuesmen", "PASSWORD")).isEqualTo("environment-secret");
    assertThat(service.configuration().credential("fuesmen", "USERNAME")).isEqualTo("updated-user");
    assertThat(mapper.writeValueAsString(service.publicView())).doesNotContain("environment-secret", "ignored-password");
  }

  @Test void quitarClaveNoReactivaLaVariableDeEntorno() {
    service.update("fuesmen", action("remove"), 7L);
    assertThat(stored.get("study-repository.fuesmen").secret()).isNull();
    assertThat(item(mapper.valueToTree(service.publicView()), "fuesmen").path("hasPassword").asBoolean()).isFalse();
    assertThatThrownBy(() -> service.configuration().credential("fuesmen", "PASSWORD"))
        .isInstanceOf(ExternalStudyFailure.class).hasMessageContaining("credenciales");
  }

  @Test void hostPuertoODegradacionHttpsNoRecibenClaveGuardadaImplicitamente() {
    environment.setProperty("EXTERNAL_STUDIES_FUESMEN_LOGIN_URL", "https://provider.fixture.invalid:8443/login");
    for (String url : List.of("https://other.fixture.invalid:8443/login", "https://provider.fixture.invalid:9443/login", "http://provider.fixture.invalid:8443/login")) {
      bad(() -> service.update("fuesmen", endpoint("LOGIN_URL", url), 1L));
      assertThat(stored).isEmpty();
    }
    // Changing only a route on the same secure server is not a credential transfer.
    service.update("fuesmen", endpoint("LOGIN_URL", "https://provider.fixture.invalid:8443/other-login"), 1L);
    assertThat(service.configuration().credential("fuesmen", "PASSWORD")).isEqualTo("environment-secret");
  }

  @Test void destinoNuevoRequiereReemplazoExplicitoOQuitarClave() {
    var replacement = endpoint("LOGIN_URL", "https://other.fixture.invalid/login");
    replacement.put("passwordAction", "replace").put("password", "new-destination-secret");
    service.update("fuesmen", replacement, 1L);
    assertThat(service.configuration().credential("fuesmen", "PASSWORD")).isEqualTo("new-destination-secret");
    var removal = endpoint("LOGIN_URL", "https://third.fixture.invalid/login");
    removal.put("passwordAction", "remove");
    service.update("fuesmen", removal, 1L);
    assertThat(stored.get("study-repository.fuesmen").secret()).isNull();
  }

  @Test void endpointsInvalidosSiempreDevuelven400SinPersistir() {
    for (String url : List.of("", "/relative", "https://", "ftp://provider.example/file", "https://user:password@provider.example/", "https://provider.example/#fragment",
        "http://169.254.169.254/latest", "https://provider.example:65536/", "https://provider.example/\nunsafe", "ws://provider.example/live")) {
      bad(() -> service.update("fuesmen", endpoint("LOGIN_URL", url), 1L));
    }
    bad(() -> service.update("espanol", endpoint("SOCKET_URL", "https://portal.example/live"), 1L));
    bad(() -> service.update("fuesmen", endpoint("UNKNOWN_URL", "https://provider.example/"), 1L));
    var duplicate = endpoint("LOGIN_URL", "https://provider.example/");
    duplicate.withArray("endpoints").addObject().put("key", "LOGIN_URL").put("url", "https://provider.example/");
    bad(() -> service.update("fuesmen", duplicate, 1L));
    assertThat(stored).isEmpty();
  }

  @Test void cuerpoAccionUsuarioYPasswordInvalidosNoSePersisten() {
    bad(() -> service.update("unknown", action("keep"), 1L));
    bad(() -> service.update("fuesmen", null, 1L));
    bad(() -> service.update("fuesmen", mapper.createArrayNode(), 1L));
    bad(() -> service.update("fuesmen", mapper.createObjectNode().put("endpoints", "invalid"), 1L));
    bad(() -> service.update("fuesmen", action("unknown"), 1L));
    bad(() -> service.update("fuesmen", action("replace").put("password", " "), 1L));
    bad(() -> service.update("fuesmen", action("replace").put("password", "x".repeat(4097)), 1L));
    bad(() -> service.update("fuesmen", action("keep").put("username", "line\nuser"), 1L));
    assertThat(stored).isEmpty();
  }

  @Test void patologiaUsaVariablesPathologyYNoAceptaCredencialesDelListadoPublico() {
    environment.setProperty("EXTERNAL_STUDIES_PATHOLOGY_FALLBACK_URL", "https://pathology.fixture.invalid/list");
    environment.setProperty("EXTERNAL_STUDIES_PATHOLOGY_FILES_BASE_URL", "https://pathology.fixture.invalid/files/");
    environment.setProperty("PATHOLOGY_DB_JDBC_URL", "jdbc:postgresql://db.fixture.invalid/pathology");
    var view = item(mapper.valueToTree(service.publicView()), "patologia");
    assertThat(endpointValue(view, "FALLBACK_URL")).isEqualTo("https://pathology.fixture.invalid/list");
    service.update("patologia", endpoint("FALLBACK_URL", "https://new-pathology.fixture.invalid/list"), 1L);
    var config = service.configuration();
    assertThat(config.setting("EXTERNAL_STUDIES_PATHOLOGY_FALLBACK_URL", "")).isEqualTo("https://new-pathology.fixture.invalid/list");
    assertThat(config.setting("EXTERNAL_STUDIES_PATHOLOGY_FILES_BASE_URL", "")).isEqualTo("https://pathology.fixture.invalid/files/");
    assertThat(config.setting("PATHOLOGY_DB_JDBC_URL", "")).isEqualTo("jdbc:postgresql://db.fixture.invalid/pathology");
    bad(() -> service.update("patologia", action("replace").put("password", "not-allowed"), 1L));
    bad(() -> service.update("patologia", action("keep").put("username", "not-allowed"), 1L));
  }

  @Test void portalDerivaLoginYSocketDeBasePorFuenteYRespetaOverrides() {
    environment.setProperty("EXTERNAL_STUDIES_INFORMEMEDICO_BASE_URL", "https://shared-portal.fixture.invalid/app/");
    environment.setProperty("EXTERNAL_STUDIES_ESPANOL_BASE_URL", "https://spanish-portal.fixture.invalid:8443/custom");
    environment.setProperty("EXTERNAL_STUDIES_MALARGUE_LOGIN_URL", "https://shared-portal.fixture.invalid/custom-login");
    environment.setProperty("EXTERNAL_STUDIES_CENTRO_DOCUMENT_BASE_URL", "https://documents.fixture.invalid/");
    var config = service.configuration();
    assertThat(config.provider("espanol", "LOGIN_URL", "")).isEqualTo("https://spanish-portal.fixture.invalid:8443/custom/portal/log_in");
    assertThat(config.provider("espanol", "SOCKET_URL", "")).isEqualTo("wss://spanish-portal.fixture.invalid:8443/custom/live/websocket");
    assertThat(config.provider("centro", "LOGIN_URL", "")).isEqualTo("https://shared-portal.fixture.invalid/app/portal/log_in");
    assertThat(config.provider("malargue", "LOGIN_URL", "")).isEqualTo("https://shared-portal.fixture.invalid/custom-login");
    assertThat(config.provider("centro", "DOCUMENT_BASE_URL", "")).isEqualTo("https://documents.fixture.invalid/");
    var view = mapper.valueToTree(service.publicView());
    assertThat(endpointValue(item(view, "centro"), "DOCUMENT_BASE_URL")).isEqualTo("https://documents.fixture.invalid/");
    assertThat(endpointValue(item(view, "espanol"), "DOCUMENT_BASE_URL")).isEqualTo("https://imagensalud.informemedico.com.ar:8093");
    assertThat(endpointValue(item(view, "malargue"), "DOCUMENT_BASE_URL")).isEqualTo("https://centrodeldiagnostico.informemedico.com.ar:8017");
    service.update("centro", endpoint("DOCUMENT_BASE_URL", "https://new-documents.fixture.invalid/"), 1L);
    assertThat(service.configuration().provider("centro", "DOCUMENT_BASE_URL", "")).isEqualTo("https://new-documents.fixture.invalid/");
  }

  @Test void snapshotNoCambiaEnConsultasEnCursoCuandoSeEditaRepositorio() {
    var before = service.configuration();
    service.update("fuesmen", action("replace").put("password", "new-snapshot-secret").put("username", "new-snapshot-user"), 1L);
    var after = service.configuration();
    assertThat(before.credential("fuesmen", "PASSWORD")).isEqualTo("environment-secret");
    assertThat(before.credential("fuesmen", "USERNAME")).isEqualTo("fixture-user");
    assertThat(after.credential("fuesmen", "PASSWORD")).isEqualTo("new-snapshot-secret");
    assertThat(after.credential("fuesmen", "USERNAME")).isEqualTo("new-snapshot-user");
  }

  @Test void endpointVacioEnEntornoConservaDefaultValido() {
    environment.setProperty("EXTERNAL_STUDIES_FUESMEN_LOGIN_URL", " ");
    service.update("fuesmen", action("keep"), 1L);
    assertThat(service.configuration().provider("fuesmen", "LOGIN_URL", "")).isEqualTo("https://host01.fuesmen.edu.ar/viewer/index.php/usuarios/control");
  }

  private ObjectNode action(String action) { return mapper.createObjectNode().put("passwordAction", action); }
  private ObjectNode endpoint(String key, String url) {
    var input = action("keep"); input.putArray("endpoints").addObject().put("key", key).put("url", url); return input;
  }
  private static JsonNode item(JsonNode view, String id) {
    for (var item : view.path("items")) if (item.path("id").asText().equals(id)) return item;
    throw new AssertionError("Missing fixture source");
  }
  private static String endpointValue(JsonNode source, String key) {
    for (var item : source.path("endpoints")) if (item.path("key").asText().equals(key)) return item.path("url").asText();
    throw new AssertionError("Missing fixture endpoint");
  }
  private static void bad(Runnable action) {
    assertThatThrownBy(action::run).isInstanceOf(ApiException.class)
        .satisfies(failure -> assertThat(((ApiException) failure).status()).isEqualTo(HttpStatus.BAD_REQUEST));
  }
}
