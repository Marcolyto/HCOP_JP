package ar.com.hexium.hcop.integration.studies;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loopback-only HTTP and RFC6455 fixture; no provider or account is contacted. */
final class LocalStudySocketServer implements AutoCloseable {
  @FunctionalInterface interface HttpHandler { Reply handle(Request request) throws Exception; }
  @FunctionalInterface interface SocketHandler { void handle(Request request, Connection connection) throws Exception; }
  record Reply(int status, String body, Map<String, String> headers) {
    Reply(int status, String body) { this(status, body, Map.of()); }
  }
  record Request(String method, String target, String path, String query, Map<String, List<String>> headers, String body) {
    String header(String name) { return String.join("; ", headers.getOrDefault(name.toLowerCase(Locale.ROOT), List.of())); }
  }

  private final ServerSocket server;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
  private final Map<String, HttpHandler> httpRoutes = new ConcurrentHashMap<>();
  private final Map<String, SocketHandler> socketRoutes = new ConcurrentHashMap<>();
  private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
  private final List<Request> requests = new CopyOnWriteArrayList<>();
  private final List<Throwable> failures = new CopyOnWriteArrayList<>();
  private volatile boolean closed;

  LocalStudySocketServer() throws IOException {
    server = new ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"));
    executor.submit(() -> {
      while (!closed) {
        try {
          Socket socket = server.accept();
          socket.setSoTimeout(10_000);
          connections.add(socket);
          executor.submit(() -> serve(socket));
        } catch (IOException failure) {
          if (!closed) failures.add(failure);
          return;
        }
      }
    });
  }

  String origin() { return "http://127.0.0.1:" + server.getLocalPort(); }
  String httpUrl(String path) { return origin() + path; }
  String wsUrl(String path) { return "ws://127.0.0.1:" + server.getLocalPort() + path; }
  void onHttp(String path, HttpHandler handler) { httpRoutes.put(path, handler); }
  void onWebSocket(String path, SocketHandler handler) { socketRoutes.put(path, handler); }
  List<Request> requests() { return List.copyOf(requests); }
  List<Throwable> failures() { return List.copyOf(failures); }

  private void serve(Socket socket) {
    try (socket) {
      InputStream input = socket.getInputStream();
      OutputStream output = socket.getOutputStream();
      Request request = readRequest(input);
      requests.add(request);
      SocketHandler ws = socketRoutes.get(request.path());
      if (ws != null && request.header("Upgrade").equalsIgnoreCase("websocket")) {
        String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
            .digest((request.header("Sec-WebSocket-Key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
        output.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n")
            .getBytes(StandardCharsets.US_ASCII));
        output.flush();
        ws.handle(request, new Connection(input, output));
      } else {
        HttpHandler handler = httpRoutes.get(request.path());
        Reply reply = handler == null ? new Reply(404, "Fixture route not found") : handler.handle(request);
        byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        StringBuilder headers = new StringBuilder("HTTP/1.1 ").append(reply.status()).append(" Fixture\r\n")
            .append("Content-Length: ").append(body.length).append("\r\nConnection: close\r\n");
        reply.headers().forEach((name, value) -> headers.append(name).append(": ").append(value).append("\r\n"));
        headers.append("\r\n");
        output.write(headers.toString().getBytes(StandardCharsets.ISO_8859_1));
        output.write(body);
        output.flush();
      }
    } catch (EOFException | SocketException expectedDisconnect) {
      // Cancellation tests deliberately abort sockets, including during writes.
    } catch (Throwable failure) {
      if (!closed) failures.add(failure);
    } finally { connections.remove(socket); }
  }

  private static Request readRequest(InputStream input) throws IOException {
    String requestLine = line(input);
    String[] parts = requestLine.split(" ", 3);
    if (parts.length != 3) throw new IOException("Invalid fixture request");
    Map<String, List<String>> headers = new LinkedHashMap<>();
    for (String line = line(input); !line.isEmpty(); line = line(input)) {
      int colon = line.indexOf(':');
      if (colon <= 0) throw new IOException("Invalid fixture header");
      headers.computeIfAbsent(line.substring(0, colon).toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(line.substring(colon + 1).trim());
    }
    int length = Integer.parseInt(headers.getOrDefault("content-length", List.of("0")).getFirst());
    if (length < 0 || length > 1_048_576) throw new IOException("Fixture request too large");
    byte[] body = input.readNBytes(length);
    if (body.length != length) throw new EOFException();
    URI target = URI.create(parts[1]);
    return new Request(parts[0], parts[1], target.getPath(), target.getRawQuery(), headers, new String(body, StandardCharsets.UTF_8));
  }

  private static String line(InputStream input) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    for (int value; (value = input.read()) >= 0;) {
      if (value == '\n') return bytes.toString(StandardCharsets.ISO_8859_1).replaceFirst("\r$", "");
      if (bytes.size() >= 65_536) throw new IOException("Fixture header too large");
      bytes.write(value);
    }
    throw new EOFException();
  }

  @Override public void close() {
    closed = true;
    try { server.close(); } catch (IOException ignored) { }
    for (Socket socket : connections) { try { socket.close(); } catch (IOException ignored) { } }
    executor.shutdownNow();
  }

  static final class Connection {
    private final InputStream input;
    private final OutputStream output;
    Connection(InputStream input, OutputStream output) { this.input = input; this.output = output; }
    void sendText(String value) throws IOException { sendFrame(1, true, value.getBytes(StandardCharsets.UTF_8)); }
    void sendBinary(byte[] value) throws IOException { sendFrame(2, true, value); }
    void sendClose(int code, String reason) throws IOException {
      byte[] text = reason.getBytes(StandardCharsets.UTF_8);
      byte[] payload = new byte[text.length + 2];
      payload[0] = (byte) (code >>> 8); payload[1] = (byte) code;
      System.arraycopy(text, 0, payload, 2, text.length);
      sendFrame(8, true, payload);
    }
    synchronized void sendFrame(int opcode, boolean last, byte[] payload) throws IOException {
      output.write((last ? 0x80 : 0) | opcode);
      if (payload.length < 126) output.write(payload.length);
      else if (payload.length <= 65535) { output.write(126); output.write(payload.length >>> 8); output.write(payload.length); }
      else {
        output.write(127);
        for (int shift = 56; shift >= 0; shift -= 8) output.write((int) (((long) payload.length) >>> shift));
      }
      output.write(payload); output.flush();
    }
    String receiveText() throws IOException {
      ByteArrayOutputStream text = new ByteArrayOutputStream();
      while (true) {
        int first = input.read();
        if (first < 0) return null;
        int second = required(input);
        int opcode = first & 15;
        long length = second & 127;
        if (length == 126) length = ((long) required(input) << 8) | required(input);
        else if (length == 127) {
          length = 0;
          for (int index = 0; index < 8; index++) length = (length << 8) | required(input);
        }
        if (length < 0 || length > 1_048_576) throw new IOException("Fixture frame too large");
        if ((second & 128) == 0) throw new IOException("Client frames must be masked");
        byte[] mask = input.readNBytes(4);
        byte[] payload = input.readNBytes((int) length);
        if (mask.length != 4 || payload.length != length) throw new EOFException();
        for (int index = 0; index < payload.length; index++) payload[index] ^= mask[index % 4];
        if (opcode == 8) { sendFrame(8, true, payload); return null; }
        if (opcode == 9) { sendFrame(10, true, payload); continue; }
        if (opcode == 10) continue;
        if (opcode != 1 && opcode != 0) throw new IOException("Expected text frame");
        if (text.size() + payload.length > 1_048_576) throw new IOException("Fixture message too large");
        text.writeBytes(payload);
        if ((first & 128) != 0) return text.toString(StandardCharsets.UTF_8);
      }
    }
    private static int required(InputStream input) throws IOException {
      int value = input.read();
      if (value < 0) throw new EOFException();
      return value;
    }
  }
}
