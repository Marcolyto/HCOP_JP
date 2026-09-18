package ar.com.hexium.hcop.patient;

import ar.com.hexium.hcop.auth.AuthContext;
import ar.com.hexium.hcop.auth.AuthService;
import ar.com.hexium.hcop.auth.SessionPrincipal;
import ar.com.hexium.hcop.common.ApiException;
import ar.com.hexium.hcop.integration.studies.ExternalStudySearchService;
import ar.com.hexium.hcop.integration.studies.ExternalStudyResourceService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.Set;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Read-only federated search: the patient identity always comes from HCOP. */
@RestController
public class ExternalStudiesController {
  private static final Set<String> LOCAL_REPORT_SOURCES = Set.of("fuesmen", "idm", "labo", "centro", "espanol", "malargue");
  private static final Set<String> LOCAL_STUDY_SOURCES = Set.of("fuesmen", "idm", "labo");
  private final PatientService patients;
  private final ExternalStudySearchService studies;
  private final ExternalStudyResourceService resources;
  private final AuthContext auth;
  private final AuthService sessions;

  public ExternalStudiesController(
      PatientService patients, ExternalStudySearchService studies,
      AuthContext auth, AuthService sessions, ExternalStudyResourceService resources) {
    this.patients = patients;
    this.studies = studies;
    this.auth = auth;
    this.sessions = sessions;
    this.resources = resources;
  }

  @GetMapping("/api/patients/{patientId}/external-studies")
  @Operation(summary = "Buscar estudios externos del paciente activo",
      description = "Consulta todas las instituciones por el DNI guardado en HCOP. Devuelve JSON con estudios y estado por fuente; un fallo no interrumpe las demás. Requiere section.studies.view y no modifica la historia clínica.")
  ResponseEntity<JsonNode> search(@PathVariable long patientId, HttpServletRequest request) {
    auth.requirePermission(request, "section.studies.view");
    SessionPrincipal principal = auth.require(request);
    requirePatient(principal, patientId);
    String dni = normalizeDni(patients.require(patientId).dni());
    ObjectNode result = (ObjectNode) studies.search(dni).deepCopy();

    revalidate(request, principal, patientId, dni);
    for (JsonNode node : result.path("studies")) {
      if (!(node instanceof ObjectNode study)) continue;
      String source = study.path("sourceId").asText("");
      String prefix = "external:" + source + ":";
      String id = study.path("id").asText("");
      if (!LOCAL_REPORT_SOURCES.contains(source) || !id.startsWith(prefix)) continue;
      String local = "/api/patients/" + patientId + "/external-studies/" + source + "/"
          + UriUtils.encodePathSegment(id.substring(prefix.length()), StandardCharsets.UTF_8);
      if (!study.path("reportUrl").asText("").isBlank()) study.put("reportUrl", local + "/report");
      if (LOCAL_STUDY_SOURCES.contains(source) && !study.path("studyUrl").asText("").isBlank()) {
        study.put("studyUrl", local + "/study");
      }
    }
    result.put("patientId", Long.toString(patientId));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.PRAGMA, "no-cache")
        .body(result);
  }

  @GetMapping("/api/patients/{patientId}/external-studies/{sourceId}/{studyId}/{kind}")
  @Operation(summary = "Abrir un estudio externo verificado del paciente activo")
  ResponseEntity<byte[]> resource(@PathVariable long patientId, @PathVariable String sourceId,
      @PathVariable String studyId, @PathVariable String kind, HttpServletRequest request) {
    auth.requirePermission(request, "section.studies.view");
    SessionPrincipal principal = auth.require(request);
    requirePatient(principal, patientId);
    String dni = normalizeDni(patients.require(patientId).dni());
    var resource = resources.resolve(dni, sourceId, studyId, kind);
    revalidate(request, principal, patientId, dni);
    if (resource.redirectUrl() != null && !resource.redirectUrl().isBlank()) {
      URI target;
      try { target = URI.create(resource.redirectUrl()); }
      catch (IllegalArgumentException invalid) { throw invalidResource(); }
      if (!("http".equals(target.getScheme()) || "https".equals(target.getScheme())) || target.getHost() == null
          || target.getUserInfo() != null) throw invalidResource();
      return ResponseEntity.status(HttpStatus.FOUND).location(target)
          .cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache")
          .header("Referrer-Policy", "no-referrer").build();
    }
    if (resource.content() == null || resource.content().length == 0) throw invalidResource();
    // Institutional HTML is untrusted even after an authenticated download.
    String mime = Objects.requireNonNullElse(resource.contentType(), "").split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
    boolean html = "text/html".equals(mime);
    boolean pdf = "application/pdf".equals(mime);
    if (!html && !pdf) throw invalidResource();
    String filename = Objects.requireNonNullElse(resource.filename(), pdf ? "informe.pdf" : "informe.html")
        .replaceAll("[\\p{Cntrl}\\\\/\"]", "_");
    return ResponseEntity.ok()
        .contentType(html ? new MediaType("text", "html", StandardCharsets.UTF_8) : MediaType.APPLICATION_PDF)
        .cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache")
        .header("X-Content-Type-Options", "nosniff")
        .header("Referrer-Policy", "no-referrer")
        .header("Content-Security-Policy", html
            ? "sandbox; default-src 'none'; style-src 'unsafe-inline'; img-src data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"
            : "default-src 'none'; frame-ancestors 'none'")
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(filename, StandardCharsets.UTF_8).build().toString())
        .body(resource.content());
  }

  private static ApiException invalidResource() {
    return new ApiException(HttpStatus.BAD_GATEWAY, "La institución no devolvió un informe o enlace válido.");
  }

  private void revalidate(HttpServletRequest request, SessionPrincipal principal, long patientId, String dni) {
    // A request can outlive the active patient or the authenticated session.
    SessionPrincipal current = sessions.authenticate(auth.token(request))
        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Debe iniciar sesión."));
    if (current.userId() != principal.userId() || !current.hasPermission("section.studies.view")) {
      throw new ApiException(HttpStatus.FORBIDDEN, "No tiene permiso para consultar estudios.");
    }
    requirePatient(current, patientId);
    if (!dni.equals(normalizeDni(patients.require(patientId).dni()))) {
      throw new ApiException(HttpStatus.CONFLICT,
          "El DNI del paciente cambió durante la búsqueda. Vuelva a consultar.", "STUDY_PATIENT_CHANGED");
    }
  }

  private static void requirePatient(SessionPrincipal principal, long patientId) {
    if (!Objects.equals(principal.activePatientId(), patientId)) {
      throw new ApiException(HttpStatus.CONFLICT,
          "Abra este paciente antes de consultar sus estudios.", "ACTIVE_PATIENT_REQUIRED");
    }
  }

  static String normalizeDni(String value) {
    String dni = value == null ? "" : value.replaceAll("[.\\s-]", "");
    if (!dni.matches("[0-9]{5,10}")) {
      throw new ApiException(HttpStatus.BAD_REQUEST,
          "El paciente necesita un DNI válido para buscar estudios externos.", "STUDY_DNI_REQUIRED");
    }
    return dni;
  }
}
