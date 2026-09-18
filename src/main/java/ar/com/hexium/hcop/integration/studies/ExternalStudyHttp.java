package ar.com.hexium.hcop.integration.studies;

import java.io.ByteArrayOutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

final class ExternalStudyHttp implements AutoCloseable {
  private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
  private final HttpClient client;
  private final int maximumBytes;
  private final int maximumDocumentBytes;
  private final long deadline;
  private String lastResponseUrl = "";

  ExternalStudyHttp(ExternalStudyConfiguration config, long deadline) {
    this.maximumBytes = config.maxBytes();
    this.maximumDocumentBytes = config.maxDocumentBytes();
    this.deadline = deadline;
    this.client = HttpClient.newBuilder().cookieHandler(cookies)
        .connectTimeout(Duration.ofSeconds(Math.min(5, config.sourceTimeoutSeconds())))
        .followRedirects(HttpClient.Redirect.NEVER).build();
  }

  long remainingMillis() {
    long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
    if (remaining < 1 || Thread.currentThread().isInterrupted()) throw new ExternalStudyFailure("La fuente excedió el tiempo de espera.");
    return remaining;
  }

  String get(String url) { return request("GET", url, Map.of(), maximumBytes).text(); }
  String post(String url, Map<String, String> fields) { return request("POST", url, fields, maximumBytes).text(); }
  Download download(String url) { return request("GET", url, Map.of(), maximumDocumentBytes); }
  Download download(String url, String viewerReferer) {
    return request("GET", url, Map.of(), maximumDocumentBytes, safeUri(viewerReferer).toASCIIString());
  }
  String lastResponseUrl() { return lastResponseUrl; }

  record Download(byte[] content, String contentType) {
    String text() {
      Charset charset = StandardCharsets.UTF_8;
      var matcher = Pattern.compile("(?i)charset=\"?([a-z0-9_-]+)").matcher(contentType);
      if (matcher.find()) { try { charset = Charset.forName(matcher.group(1)); } catch (Exception ignored) { /* UTF-8 fallback */ } }
      return new String(content, charset).replaceFirst("^\\uFEFF", "");
    }
  }

  ExternalStudySocket openSocket(String socketUrl, String httpOriginUrl) {
    URI origin = safeUri(httpOriginUrl);
    URI socket;
    try {
      socket = URI.create(socketUrl);
      String scheme = socket.getScheme();
      if (!("ws".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme)) || socket.getFragment() != null) {
        throw new IllegalArgumentException();
      }
      URI equivalent = safeUri(("wss".equalsIgnoreCase(scheme) ? "https" : "http") + socketUrl.substring(scheme.length()));
      if (!sameOrigin(origin, equivalent)) throw new IllegalArgumentException();
    } catch (Exception invalid) {
      throw new ExternalStudyFailure("La fuente contiene un canal de consulta no autorizado.");
    }
    String originHeader = origin.getScheme() + "://" + origin.getRawAuthority();
    return new ExternalStudySocket(client, socket, originHeader, maximumBytes, this::remainingMillis);
  }

  private Download request(String method, String url, Map<String, String> fields, int responseLimit) {
    return request(method, url, fields, responseLimit, "");
  }

  private Download request(String method, String url, Map<String, String> fields, int responseLimit, String viewerReferer) {
    URI uri = safeUri(url);
    for (int redirect = 0; redirect <= 4; redirect++) {
      var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(remainingMillis()))
          .header("Accept", "application/json,text/html;q=0.9,*/*;q=0.5")
          .header("User-Agent", "HCOP-External-Studies/1.0")
          .header("Accept-Encoding", "identity");
      if (!viewerReferer.isBlank()) builder.header("Referer", viewerReferer);
      if (method.equals("POST")) {
        builder.header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .header("X-Requested-With", "XMLHttpRequest")
            .POST(HttpRequest.BodyPublishers.ofString(form(fields), StandardCharsets.UTF_8));
      } else builder.GET();
      CompletableFuture<HttpResponse<byte[]>> pending = null;
      try {
        pending = client.sendAsync(builder.build(), info -> new BoundedBody(responseLimit));
        var response = pending.get(remainingMillis(), TimeUnit.MILLISECONDS);
        int status = response.statusCode();
        if (status >= 300 && status < 400) {
          String location = response.headers().firstValue("Location").orElse("");
          if (location.isBlank() || redirect == 4) throw new ExternalStudyFailure("La fuente devolvió una redirección inválida.");
          URI target = safeUri(uri.resolve(location).toString());
          if (status == 303 || ((status == 301 || status == 302) && method.equals("POST"))) method = "GET";
          boolean safeUpgrade = method.equals("GET") && uri.getScheme().equalsIgnoreCase("http")
              && target.getScheme().equalsIgnoreCase("https") && uri.getHost().equalsIgnoreCase(target.getHost())
              && port(uri) == 80 && port(target) == 443;
          if (!sameOrigin(uri, target) && !safeUpgrade) throw new ExternalStudyFailure("La fuente redirigió a un destino no autorizado.");
          uri = target;
          continue;
        }
        if (status == 401 || status == 403) throw new ExternalStudyFailure("La fuente rechazó el acceso. Revise sus credenciales.");
        if (status < 200 || status >= 300) throw new ExternalStudyFailure("La fuente respondió con un error HTTP (" + status + ").");
        lastResponseUrl = uri.toString();
        return new Download(response.body(), response.headers().firstValue("Content-Type").orElse("application/octet-stream"));
      } catch (ExternalStudyFailure failure) { throw failure; }
      catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new ExternalStudyFailure("La consulta de la fuente fue interrumpida.");
      } catch (Exception failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && !(cause instanceof ExternalStudyFailure)) cause = cause.getCause();
        if (cause instanceof ExternalStudyFailure safe) throw safe;
        if (failure instanceof java.util.concurrent.TimeoutException || cause instanceof java.net.http.HttpTimeoutException) {
          throw new ExternalStudyFailure("La fuente excedió el tiempo de espera.");
        }
        // Exception messages from HTTP clients may contain URLs, identifiers or credentials.
        throw new ExternalStudyFailure("No se pudo conectar con esta fuente de forma segura.");
      } finally { if (pending != null && !pending.isDone()) pending.cancel(true); }
    }
    throw new ExternalStudyFailure("La fuente devolvió demasiadas redirecciones.");
  }

  static String form(Map<String, String> fields) {
    return fields.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
        .collect(java.util.stream.Collectors.joining("&"));
  }
  static String query(String url, Map<String, String> fields) { return url + (url.contains("?") ? "&" : "?") + form(fields); }
  static String encode(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
  static URI safeUri(String value) {
    try {
      if (value == null || value.matches("(?s).*[\\p{Cntrl}<>\"'\\\\].*")) throw new IllegalArgumentException();
      URI uri = URI.create(value);
      if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
      return uri;
    } catch (IllegalArgumentException invalid) { throw new ExternalStudyFailure("La fuente contiene una dirección inválida."); }
  }
  static String safeLink(String value) {
    if (value == null || value.isBlank() || value.length() > 2048) return "";
    try { return safeUri(value).toASCIIString(); }
    catch (ExternalStudyFailure invalid) { return ""; }
  }
  private static boolean sameOrigin(URI left, URI right) {
    return left.getScheme().equalsIgnoreCase(right.getScheme()) && left.getHost().equalsIgnoreCase(right.getHost())
        && port(left) == port(right);
  }
  private static int port(URI uri) { return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80; }
  @Override public void close() { cookies.getCookieStore().removeAll(); client.shutdownNow(); }

  private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final int maximum;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> completed = new CompletableFuture<>();
    private Flow.Subscription subscription;
    BoundedBody(int maximum) { this.maximum = maximum; }
    @Override public CompletionStage<byte[]> getBody() { return completed; }
    @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
    @Override public void onNext(List<ByteBuffer> items) {
      for (ByteBuffer item : items) {
        if (item.remaining() > maximum - bytes.size()) {
          subscription.cancel(); completed.completeExceptionally(new ExternalStudyFailure("La respuesta de la fuente supera el tamaño permitido.")); return;
        }
        byte[] part = new byte[item.remaining()]; item.get(part); bytes.writeBytes(part);
      }
      subscription.request(1);
    }
    @Override public void onError(Throwable failure) { completed.completeExceptionally(failure); }
    @Override public void onComplete() { completed.complete(bytes.toByteArray()); }
  }
}
