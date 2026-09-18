package ar.com.hexium.hcop.integration.studies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.mock.env.MockEnvironment;

@Timeout(10)
class ExternalStudySocketTest {
  @Test
  void websocketReusesItsHttpSessionCookieAndSendsOnlyTheOriginAuthority() throws Exception {
    try (var server = new LocalStudySocketServer(); var http = transport(Duration.ofSeconds(5))) {
      server.onHttp("/login", request -> new LocalStudySocketServer.Reply(200, "ready", Map.of("Set-Cookie", "fixture_session=account-a; Path=/; HttpOnly")));
      server.onWebSocket("/live/websocket", (request, connection) -> {
        connection.sendText("ready");
        connection.sendText("echo:" + connection.receiveText());
        connection.receiveText();
      });
      assertThat(http.post(server.httpUrl("/login"), Map.of("username", "synthetic-a"))).isEqualTo("ready");
      try (var socket = http.openSocket(server.wsUrl("/live/websocket?vsn=2.0.0"), server.httpUrl("/portal/page?synthetic=1"))) {
        assertThat(socket.receive()).isEqualTo("ready");
        socket.send("phoenix-fixture-message");
        assertThat(socket.receive()).isEqualTo("echo:phoenix-fixture-message");
        var handshake = server.requests().stream().filter(request -> request.path().equals("/live/websocket")).findFirst().orElseThrow();
        assertThat(handshake.header("Cookie")).contains("fixture_session=account-a");
        assertThat(handshake.header("Origin")).isEqualTo(server.origin());
        assertThat(handshake.query()).isEqualTo("vsn=2.0.0");
        assertThat(server.failures()).isEmpty();
      }
    }
  }

  @Test
  void separateTransportsDoNotShareCookiesEvenForTheSameHostAndSocketPath() throws Exception {
    try (var server = new LocalStudySocketServer(); var first = transport(Duration.ofSeconds(5)); var second = transport(Duration.ofSeconds(5)); var anonymous = transport(Duration.ofSeconds(5))) {
      server.onHttp("/login", request -> new LocalStudySocketServer.Reply(200, "ready",
          Map.of("Set-Cookie", "fixture_session=" + (request.body().contains("account-a") ? "account-a" : "account-b") + "; Path=/; HttpOnly")));
      server.onWebSocket("/live", (request, connection) -> { connection.sendText(request.header("Cookie")); connection.receiveText(); });
      first.post(server.httpUrl("/login"), Map.of("username", "account-a"));
      second.post(server.httpUrl("/login"), Map.of("username", "account-b"));
      try (var firstSocket = first.openSocket(server.wsUrl("/live"), server.origin());
           var secondSocket = second.openSocket(server.wsUrl("/live"), server.origin());
           var anonymousSocket = anonymous.openSocket(server.wsUrl("/live"), server.origin())) {
        assertThat(firstSocket.receive()).contains("fixture_session=account-a").doesNotContain("account-b");
        assertThat(secondSocket.receive()).contains("fixture_session=account-b").doesNotContain("account-a");
        assertThat(anonymousSocket.receive()).isEmpty();
      }
    }
  }

  @Test
  void rejectsOtherOriginsFragmentsCredentialsAndNonWebsocketSchemesBeforeConnecting() throws Exception {
    try (var source = new LocalStudySocketServer(); var other = new LocalStudySocketServer(); var http = transport(Duration.ofSeconds(5))) {
      for (String url : List.of(other.wsUrl("/live"), source.wsUrl("/live#fragment"), source.httpUrl("/live"),
          source.wsUrl("/live").replace("ws://", "ws://synthetic:password@"), source.wsUrl("/live").replace("127.0.0.1", "localhost"))) {
        assertThatThrownBy(() -> http.openSocket(url, source.origin())).isInstanceOf(ExternalStudyFailure.class)
            .hasMessage("La fuente contiene un canal de consulta no autorizado.");
      }
      assertThat(source.requests()).isEmpty();
      assertThat(other.requests()).isEmpty();
    }
  }

  @Test
  void assemblesUtf8FragmentsAndResetsTheirSizeForTheNextMessage() throws Exception {
    try (var server = new LocalStudySocketServer(); var http = transport(Duration.ofSeconds(5))) {
      String expected = "{\"text\":\"Malargüe · estudio 😀\"}";
      byte[] bytes = expected.getBytes(StandardCharsets.UTF_8);
      int split = expected.substring(0, expected.indexOf("😀")).getBytes(StandardCharsets.UTF_8).length + 2;
      server.onWebSocket("/live", (request, connection) -> {
        connection.sendFrame(1, false, Arrays.copyOfRange(bytes, 0, split));
        connection.sendFrame(0, true, Arrays.copyOfRange(bytes, split, bytes.length));
        connection.sendText("next-message");
        connection.receiveText();
      });
      try (var socket = http.openSocket(server.wsUrl("/live"), server.origin())) {
        assertThat(socket.receive()).isEqualTo(expected);
        assertThat(socket.receive()).isEqualTo("next-message");
      }
    }
  }

  @Test
  void rejectsIncomingUtf8PayloadAboveTheByteLimitWithoutExposingItsContent() throws Exception {
    try (var server = new LocalStudySocketServer(); var http = transport(Duration.ofSeconds(5))) {
      server.onWebSocket("/live", (request, connection) -> { connection.sendText("synthetic-private-marker" + "é".repeat(3000)); connection.receiveText(); });
      try (var socket = http.openSocket(server.wsUrl("/live"), server.origin())) {
        assertThatThrownBy(socket::receive).isInstanceOf(ExternalStudyFailure.class)
            .hasMessage("La respuesta de la fuente supera el tamaño permitido.");
      }
    }
  }

  @Test
  void appliesTheSizeLimitAcrossFragmentsRatherThanIndividually() throws Exception {
    try (var server = new LocalStudySocketServer(); var http = transport(Duration.ofSeconds(5))) {
      server.onWebSocket("/live", (request, connection) -> {
        connection.sendFrame(1, false, "a".repeat(3000).getBytes(StandardCharsets.UTF_8));
        connection.sendFrame(0, true, "b".repeat(2000).getBytes(StandardCharsets.UTF_8));
        connection.receiveText();
      });
      try (var socket = http.openSocket(server.wsUrl("/live"), server.origin())) {
        assertThatThrownBy(socket::receive).isInstanceOf(ExternalStudyFailure.class)
            .hasMessage("La respuesta de la fuente supera el tamaño permitido.");
      }
    }
  }

  @Test
  void rejectsOutgoingAsciiAndMultibytePayloadsAboveTheByteLimit() throws Exception {
    try (var server = new LocalStudySocketServer(); var http = transport(Duration.ofSeconds(5))) {
      server.onWebSocket("/live", (request, connection) -> { connection.sendText("ready"); connection.receiveText(); });
      try (var socket = http.openSocket(server.wsUrl("/live"), server.origin())) {
        assertThat(socket.receive()).isEqualTo("ready");
        for (String oversized : List.of("a".repeat(4097), "é".repeat(3000))) {
          assertThatThrownBy(() -> socket.send(oversized)).isInstanceOf(ExternalStudyFailure.class)
              .hasMessage("La consulta de la fuente supera el tamaño permitido.");
        }
      }
    }
  }

  @Test
  void rejectsBinaryFramesAndHidesRemoteCloseReasons() throws Exception {
    try (var server = new LocalStudySocketServer(); var http = transport(Duration.ofSeconds(5))) {
      server.onWebSocket("/binary", (request, connection) -> { connection.sendBinary("synthetic-private-marker".getBytes(StandardCharsets.UTF_8)); connection.receiveText(); });
      server.onWebSocket("/close", (request, connection) -> { connection.sendClose(1011, "synthetic-private-marker"); connection.receiveText(); });
      try (var binary = http.openSocket(server.wsUrl("/binary"), server.origin())) {
        assertThatThrownBy(binary::receive).isInstanceOf(ExternalStudyFailure.class).hasMessage("La fuente devolvió un mensaje de consulta inválido.");
      }
      try (var closed = http.openSocket(server.wsUrl("/close"), server.origin())) {
        assertThatThrownBy(closed::receive).isInstanceOf(ExternalStudyFailure.class).hasMessage("Se cerró el canal de consulta de esta fuente.");
      }
    }
  }

  @Test
  void receiveHonorsTheRemainingRequestDeadline() throws Exception {
    try (var server = new LocalStudySocketServer(); var http = transport(Duration.ofMillis(700))) {
      server.onWebSocket("/live", (request, connection) -> { connection.sendText("ready"); connection.receiveText(); });
      try (var socket = http.openSocket(server.wsUrl("/live"), server.origin())) {
        assertThat(socket.receive()).isEqualTo("ready");
        long started = System.nanoTime();
        assertThatThrownBy(socket::receive).isInstanceOf(ExternalStudyFailure.class).hasMessage("La fuente excedió el tiempo de espera.");
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
      }
    }
  }

  @Test
  void closeUnblocksAnAlreadyWaitingReceiveWithoutWaitingForTheSourceDeadline() throws Exception {
    try (var server = new LocalStudySocketServer(); var http = transport(Duration.ofSeconds(5))) {
      server.onWebSocket("/live", (request, connection) -> { connection.sendText("ready"); connection.receiveText(); });
      try (var socket = http.openSocket(server.wsUrl("/live"), server.origin())) {
        assertThat(socket.receive()).isEqualTo("ready");
        CompletableFuture<Throwable> outcome = new CompletableFuture<>();
        Thread receiver = Thread.ofVirtual().start(() -> {
          try { socket.receive(); outcome.complete(new AssertionError("Expected channel cancellation")); }
          catch (Throwable failure) { outcome.complete(failure); }
        });
        try {
          awaitBlocked(receiver);
          socket.close();
          assertThat(outcome.get(1, TimeUnit.SECONDS)).isInstanceOf(ExternalStudyFailure.class);
        } finally { receiver.interrupt(); receiver.join(1000); }
      }
    }
  }

  @Test
  void interruptCancelsReceiveAndPreservesTheThreadInterruptFlag() throws Exception {
    try (var server = new LocalStudySocketServer(); var http = transport(Duration.ofSeconds(5))) {
      server.onWebSocket("/live", (request, connection) -> { connection.sendText("ready"); connection.receiveText(); });
      try (var socket = http.openSocket(server.wsUrl("/live"), server.origin())) {
        assertThat(socket.receive()).isEqualTo("ready");
        CompletableFuture<Throwable> outcome = new CompletableFuture<>();
        AtomicBoolean interrupted = new AtomicBoolean();
        Thread receiver = Thread.ofVirtual().start(() -> {
          try { socket.receive(); outcome.complete(new AssertionError("Expected channel cancellation")); }
          catch (Throwable failure) { interrupted.set(Thread.currentThread().isInterrupted()); outcome.complete(failure); }
        });
        try {
          awaitBlocked(receiver);
          receiver.interrupt();
          assertThat(outcome.get(1, TimeUnit.SECONDS)).isInstanceOf(ExternalStudyFailure.class).hasMessage("La consulta de la fuente fue interrumpida.");
          assertThat(interrupted).isTrue();
        } finally { receiver.interrupt(); receiver.join(1000); }
      }
    }
  }

  private static ExternalStudyHttp transport(Duration budget) {
    var environment = new MockEnvironment().withProperty("EXTERNAL_STUDIES_MAX_RESPONSE_BYTES", "4096")
        .withProperty("EXTERNAL_STUDIES_SOURCE_TIMEOUT_SECONDS", "5");
    return new ExternalStudyHttp(new ExternalStudyConfiguration(environment), System.nanoTime() + budget.toNanos());
  }

  private static void awaitBlocked(Thread thread) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
    while (thread.isAlive() && thread.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) Thread.sleep(5);
    assertThat(thread.getState()).isEqualTo(Thread.State.TIMED_WAITING);
  }
}
