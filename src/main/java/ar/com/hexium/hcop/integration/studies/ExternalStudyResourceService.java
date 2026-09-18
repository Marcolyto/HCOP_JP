package ar.com.hexium.hcop.integration.studies;

import ar.com.hexium.hcop.common.ApiException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Rechecks ownership at click time and never accepts a document URL from a client. */
@Service
public class ExternalStudyResourceService {
  private final ObjectMapper mapper;
  private final Environment environment;
  private final StudyRepositorySettings repositories;

  public ExternalStudyResourceService(ObjectMapper mapper, Environment environment) {
    this(mapper, environment, null);
  }
  @org.springframework.beans.factory.annotation.Autowired
  public ExternalStudyResourceService(ObjectMapper mapper, Environment environment, StudyRepositorySettings repositories) {
    this.mapper = mapper; this.environment = environment;
    this.repositories = repositories;
  }

  public ExternalStudyResource resolve(String dni, String sourceId, String studyId, String kind) {
    if (dni == null || !dni.matches("[0-9]{5,10}") || sourceId == null || !List.of("labo", "fuesmen", "idm", "centro", "espanol", "malargue").contains(sourceId)
        || kind == null || !List.of("report", "study").contains(kind) || studyId == null || studyId.isBlank() || studyId.length() > 200
        || List.of("centro", "espanol", "malargue").contains(sourceId) && !kind.equals("report")
        || studyId.matches("(?s).*[\\p{Cntrl}<>\\\"'\\\\].*")) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "El estudio solicitado no es válido.");
    }
    String prefix = "external:" + sourceId + ":";
    String localId = studyId.startsWith(prefix) ? studyId.substring(prefix.length()) : studyId;
    if (localId.isBlank() || localId.startsWith("external:")) throw new ApiException(HttpStatus.BAD_REQUEST, "El estudio solicitado no es válido.");
    ExternalStudySource source = ExternalStudySource.valueOf(sourceId.toUpperCase(java.util.Locale.ROOT));
    var config = repositories == null ? new ExternalStudyConfiguration(environment) : repositories.configuration();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(config.sourceTimeoutSeconds());
    try (var transport = new ExternalStudyHttp(config, deadline)) {
      return new ExternalStudyProviders(mapper, config, transport).resource(source, dni, localId, kind);
    } catch (ExternalStudyFailure safe) {
      throw new ApiException(HttpStatus.BAD_GATEWAY, safe.getMessage());
    }
  }
}
