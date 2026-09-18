package ar.com.hexium.hcop.integration.studies;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Read-only aggregation. Neither documents nor patient identifiers are logged or persisted. */
@Service
public class ExternalStudySearchService {
  private final ObjectMapper mapper;
  private final Environment environment;
  private final StudyRepositorySettings repositories;

  public ExternalStudySearchService(ObjectMapper mapper, Environment environment) {
    this(mapper, environment, null);
  }
  @org.springframework.beans.factory.annotation.Autowired
  public ExternalStudySearchService(ObjectMapper mapper, Environment environment, StudyRepositorySettings repositories) {
    this.mapper = mapper;
    this.environment = environment;
    this.repositories = repositories;
  }

  public JsonNode search(String dni) {
    if (dni == null || !dni.matches("[0-9]{5,10}")) {
      throw new IllegalArgumentException("El documento debe contener entre 5 y 10 dígitos.");
    }
    var config = repositories == null ? new ExternalStudyConfiguration(environment) : repositories.configuration();
    var started = Instant.now();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(
        Math.min(config.sourceTimeoutSeconds(), config.globalTimeoutSeconds()));
    var executor = Executors.newVirtualThreadPerTaskExecutor();
    Map<ExternalStudySource, Future<ExternalStudyResult>> futures = new LinkedHashMap<>();
    Map<ExternalStudySource, ExternalStudyResult> results = new LinkedHashMap<>();
    try {
      // Every source is always attempted. Each owns an independent in-memory cookie jar.
      for (var source : ExternalStudySource.values()) {
        futures.put(source, executor.submit(() -> {
          // Reserve a short interval to publish already received pages after a timeout.
          try (var transport = new ExternalStudyHttp(config, deadline - TimeUnit.MILLISECONDS.toNanos(100))) {
            return new ExternalStudyProviders(mapper, config, transport).search(source, dni);
          }
        }));
      }
      for (var item : futures.entrySet()) {
        try {
          // Completed successes are retained even when another source consumed its deadline.
          long remaining = Math.max(1, deadline - System.nanoTime());
          results.put(item.getKey(), item.getValue().get(remaining, TimeUnit.NANOSECONDS));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          results.put(item.getKey(), ExternalStudyResult.failure("La búsqueda fue interrumpida."));
        } catch (Exception failure) {
          item.getValue().cancel(true);
          Throwable cause = failure instanceof java.util.concurrent.ExecutionException
              ? failure.getCause() : failure;
          String message = cause instanceof ExternalStudyFailure safe ? safe.getMessage()
              : cause instanceof java.util.concurrent.TimeoutException
                  ? "La fuente excedió el tiempo de espera."
                  : "No se pudo consultar esta fuente. Intente nuevamente.";
          results.put(item.getKey(), ExternalStudyResult.failure(message));
        }
      }
    } finally {
      futures.values().forEach(future -> { if (!future.isDone()) future.cancel(true); });
      // close() waits for tasks; shutdownNow() preserves the request's hard deadline.
      executor.shutdownNow();
    }
    return response(started, results, config.maxResults());
  }

  private ObjectNode response(Instant started, Map<ExternalStudySource, ExternalStudyResult> results, int maximum) {
    var unique = new LinkedHashMap<String, ExternalStudy>();
    results.values().forEach(result -> result.studies().forEach(study -> unique.putIfAbsent(study.id(), study)));
    var studies = new ArrayList<>(unique.values());
    studies.sort(Comparator.comparing(ExternalStudy::date).reversed().thenComparing(ExternalStudy::id));
    boolean truncated = studies.size() > maximum;
    List<ExternalStudy> selected = studies.subList(0, Math.min(maximum, studies.size()));
    var response = mapper.createObjectNode();
    response.put("searchedAt", started.toString());
    var data = response.putArray("studies");
    selected.forEach(study -> data.add(mapper.valueToTree(study)));
    var sources = response.putArray("sources");
    boolean partial = truncated;
    for (var source : ExternalStudySource.values()) {
      var result = results.getOrDefault(source, ExternalStudyResult.failure("No se pudo consultar esta fuente."));
      int count = (int) selected.stream().filter(study -> study.id().startsWith("external:" + source.id + ":")).count();
      var status = sources.addObject().put("id", source.id).put("name", source.displayName)
          .put("status", result.partial() ? "error" : count == 0 ? "empty" : "ok").put("count", count);
      if (!result.message().isBlank()) status.put("message", result.message());
      if (truncated && count < result.studies().size()) status.put("message", "Se alcanzó el límite de resultados. Puede haber más estudios.");
      partial |= result.partial();
    }
    response.put("partial", partial).put("total", selected.size());
    return response;
  }
}

enum ExternalStudySource {
  FUESMEN("fuesmen", "FUESMEN"), IDM("idm", "IDM"),
  CENTRO("centro", "Centro del Diagnóstico"), ESPANOL("espanol", "Hospital Español"),
  MALARGUE("malargue", "Malargüe"),
  LABO("labo", "Laboratorio Hospital Schestakow"), PATOLOGIA("patologia", "Patología");

  final String id;
  final String displayName;
  ExternalStudySource(String id, String displayName) { this.id = id; this.displayName = displayName; }
}

record ExternalStudy(String id, String date, String type, String title, String source,
    String reportUrl, String studyUrl, String sourceId) {}

record ExternalStudyResult(List<ExternalStudy> studies, boolean partial, String message) {
  static ExternalStudyResult success(List<ExternalStudy> studies) { return new ExternalStudyResult(List.copyOf(studies), false, ""); }
  static ExternalStudyResult failure(String message) { return new ExternalStudyResult(List.of(), true, message); }
}

final class ExternalStudyFailure extends RuntimeException {
  ExternalStudyFailure(String safeMessage) { super(safeMessage); }
}
