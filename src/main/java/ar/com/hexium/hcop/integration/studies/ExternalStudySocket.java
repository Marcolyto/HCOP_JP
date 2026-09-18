package ar.com.hexium.hcop.integration.studies;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/** Short-lived, bounded channel sharing only its provider's HTTP session. */
final class ExternalStudySocket implements AutoCloseable {
  private final ArrayBlockingQueue<Object> incoming = new ArrayBlockingQueue<>(32);
  private final AtomicInteger queuedCharacters = new AtomicInteger();
  private final LongSupplier remainingMillis;
  private final int maximumBytes;
  private volatile WebSocket socket;
  private volatile boolean closed;

  ExternalStudySocket(HttpClient client, URI uri, String origin, int maximumBytes, LongSupplier remainingMillis) {
    this.remainingMillis = remainingMillis;
    this.maximumBytes = maximumBytes;
    CompletableFuture<WebSocket> opening = null;
    try {
      opening = client.newWebSocketBuilder()
          .connectTimeout(Duration.ofMillis(Math.min(5000, remainingMillis.getAsLong())))
          .header("Origin", origin)
          .buildAsync(uri, new Receiver());
      socket = opening.get(remainingMillis.getAsLong(), TimeUnit.MILLISECONDS);
    } catch (Exception failure) {
      if (opening != null) opening.cancel(true);
      close();
      throw safeFailure(failure);
    }
  }

  void send(String value) {
    if (value == null || value.length() > maximumBytes || value.getBytes(StandardCharsets.UTF_8).length > maximumBytes) {
      throw new ExternalStudyFailure("La consulta de la fuente supera el tamaño permitido.");
    }
    if (closed || socket == null) throw new ExternalStudyFailure("Se cerró el canal de consulta de esta fuente.");
    CompletableFuture<WebSocket> sending = null;
    try {
      sending = socket.sendText(value, true);
      sending.get(remainingMillis.getAsLong(), TimeUnit.MILLISECONDS);
    } catch (Exception failure) {
      if (sending != null) sending.cancel(true);
      close();
      throw safeFailure(failure);
    }
  }

  String receive() {
    try {
      if (closed && incoming.isEmpty()) throw new ExternalStudyFailure("Se cerró el canal de consulta de esta fuente.");
      Object value = incoming.poll(remainingMillis.getAsLong(), TimeUnit.MILLISECONDS);
      if (value == null) throw new java.util.concurrent.TimeoutException();
      if (value instanceof ExternalStudyFailure failure) throw failure;
      String message = (String) value;
      queuedCharacters.updateAndGet(count -> Math.max(0, count - message.length()));
      return message;
    } catch (Exception failure) {
      close();
      throw safeFailure(failure);
    }
  }

  @Override public void close() {
    closed = true;
    WebSocket current = socket;
    if (current != null) current.abort();
    incoming.clear();
    queuedCharacters.set(0);
    incoming.offer(new ExternalStudyFailure("Se cerró el canal de consulta de esta fuente."));
  }

  private void fail(String message, WebSocket current) {
    closed = true;
    incoming.clear();
    queuedCharacters.set(0);
    incoming.offer(new ExternalStudyFailure(message));
    current.abort();
  }

  private ExternalStudyFailure safeFailure(Exception failure) {
    if (failure instanceof ExternalStudyFailure safe) return safe;
    if (failure instanceof InterruptedException) {
      Thread.currentThread().interrupt();
      return new ExternalStudyFailure("La consulta de la fuente fue interrumpida.");
    }
    if (failure instanceof java.util.concurrent.TimeoutException) return new ExternalStudyFailure("La fuente excedió el tiempo de espera.");
    return new ExternalStudyFailure("No se pudo establecer el canal de consulta de esta fuente.");
  }

  private final class Receiver implements WebSocket.Listener {
    private final StringBuilder text = new StringBuilder();
    private long messageBytes;
    @Override public void onOpen(WebSocket current) {
      socket = current;
      if (closed) current.abort(); else current.request(1);
    }
    @Override public CompletionStage<?> onText(WebSocket current, CharSequence data, boolean last) {
      if (closed) return null;
      // Count UTF-8 bytes conservatively, including surrogate pairs split across fragments.
      for (int index = 0; index < data.length(); index++) {
        char value = data.charAt(index);
        messageBytes += value <= 0x7f ? 1 : value <= 0x7ff ? 2 : 3;
      }
      if (messageBytes > maximumBytes) {
        fail("La respuesta de la fuente supera el tamaño permitido.", current);
        return null;
      }
      text.append(data);
      if (last) {
        String message = text.toString();
        text.setLength(0); messageBytes = 0;
        if (queuedCharacters.addAndGet(message.length()) > maximumBytes || !incoming.offer(message)) {
          fail("La fuente envió demasiadas respuestas pendientes.", current);
          return null;
        }
      }
      current.request(1);
      return null;
    }
    @Override public CompletionStage<?> onBinary(WebSocket current, ByteBuffer data, boolean last) {
      fail("La fuente devolvió un mensaje de consulta inválido.", current);
      return null;
    }
    @Override public CompletionStage<?> onClose(WebSocket current, int statusCode, String reason) {
      if (!closed) {
        closed = true;
        incoming.offer(new ExternalStudyFailure("Se cerró el canal de consulta de esta fuente."));
      }
      return null;
    }
    @Override public void onError(WebSocket current, Throwable error) {
      if (!closed) fail("Se interrumpió el canal de consulta de esta fuente.", current);
    }
  }
}
