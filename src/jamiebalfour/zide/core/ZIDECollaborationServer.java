package jamiebalfour.zide.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Lightweight in-memory relay for ZIDE live-editing sessions. */
public final class ZIDECollaborationServer implements AutoCloseable {
  private static final int MAX_DOCUMENT_BYTES = 1_048_576;
  private static final int MAX_REQUEST_BYTES = MAX_DOCUMENT_BYTES + 65_536;
  private static final int MAX_PARTICIPANTS = 8;
  private static final long SESSION_TTL_MILLIS = TimeUnit.HOURS.toMillis(12);
  private static final long MAX_POLL_MILLIS = TimeUnit.SECONDS.toMillis(15);
  private static final int MAX_EDIT_HISTORY = 4096;
  private static final int MAX_EDIT_HISTORY_BYTES = 4 * 1024 * 1024;
  private static final String CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final ObjectMapper JSON = new ObjectMapper();

  private final HttpServer server;
  private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "zide-collaboration-cleanup");
    thread.setDaemon(true);
    return thread;
  });
  private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

  public ZIDECollaborationServer(int port) throws IOException {
    this(new InetSocketAddress("0.0.0.0", port));
  }

  public ZIDECollaborationServer(InetSocketAddress address) throws IOException {
    server = HttpServer.create(address, 64);
    server.createContext("/health", this::handleHealth);
    server.createContext("/api/v1/collaboration", this::handleApi);
    server.setExecutor(Executors.newCachedThreadPool(r -> {
      Thread thread = new Thread(r, "zide-collaboration-request");
      thread.setDaemon(true);
      return thread;
    }));
  }

  public void start() {
    server.start();
    cleanup.scheduleAtFixedRate(this::removeExpiredSessions, 1, 1, TimeUnit.MINUTES);
    System.out.println("ZIDE collaboration server listening on " + server.getAddress());
    System.out.println("API: http://<server-host>:" + server.getAddress().getPort() + "/api/v1/collaboration");
    System.out.println("Use HTTPS in production by placing this service behind a TLS reverse proxy.");
  }

  @Override
  public void close() {
    cleanup.shutdownNow();
    server.stop(1);
    synchronized (sessions) {
      for (Session session : sessions.values()) {
        synchronized (session) {
          session.ended = true;
          session.notifyAll();
        }
      }
      sessions.clear();
    }
  }

  private void handleHealth(HttpExchange exchange) throws IOException {
    if (!"GET".equals(exchange.getRequestMethod())) {
      send(exchange, 405, Map.of("error", "Use GET."));
      return;
    }
    send(exchange, 200, Map.of("status", "ok", "service", "zide-collaboration", "time", Instant.now().toString()));
  }

  private void handleApi(HttpExchange exchange) throws IOException {
    if (!"POST".equals(exchange.getRequestMethod())) {
      exchange.getResponseHeaders().set("Allow", "POST");
      send(exchange, 405, Map.of("error", "Use POST with a JSON request body."));
      return;
    }
    byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
    if (body.length > MAX_REQUEST_BYTES) {
      send(exchange, 413, Map.of("error", "Request is too large."));
      return;
    }
    Map<String, Object> request;
    try {
      request = JSON.readValue(body, new TypeReference<>() { });
    } catch (Exception exception) {
      send(exchange, 400, Map.of("error", "Invalid JSON request."));
      return;
    }
    Object action = request.get("action");
    if (!(action instanceof String)) {
      send(exchange, 400, Map.of("error", "A JSON action is required."));
      return;
    }
    switch ((String) action) {
      case "create" -> create(exchange, request);
      case "join" -> join(exchange, request);
      case "poll" -> poll(exchange, request);
      case "edit" -> edit(exchange, request);
      case "update" -> update(exchange, request);
      case "leave" -> leave(exchange, request);
      default -> send(exchange, 400, Map.of("error", "Unknown action."));
    }
  }

  private void create(HttpExchange exchange, Map<String, Object> request) throws IOException {
    String document = string(request, "document", "");
    String fileName = string(request, "fileName", "Untitled");
    String language = string(request, "language", "text");
    String name = string(request, "name", "ZIDE User");
    if (document == null || fileName == null || language == null || name == null) {
      send(exchange, 400, Map.of("error", "Document metadata must be text."));
      return;
    }
    if (document.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) {
      send(exchange, 413, Map.of("error", "Document exceeds the 1 MiB session limit."));
      return;
    }
    if (fileName.length() > 255 || language.length() > 64 || name.isBlank() || name.length() > 80) {
      send(exchange, 400, Map.of("error", "Document metadata is too long."));
      return;
    }
    if (sessions.size() >= 10_000) {
      send(exchange, 503, Map.of("error", "The server is at session capacity."));
      return;
    }

    String code;
    Session session;
    do {
      code = makeCode();
      String token = makeToken();
      session = new Session(code, document, fileName, language, token, name);
      Session existing = sessions.putIfAbsent(code, session);
      if (existing == null) {
        send(exchange, 201, state(session, token));
        return;
      }
    } while (true);
  }

  private void join(HttpExchange exchange, Map<String, Object> request) throws IOException {
    String code = normalizedCode(request.get("code"));
    if (code == null) {
      send(exchange, 400, Map.of("error", "Session code must be eight characters."));
      return;
    }
    Session session = sessions.get(code);
    if (session == null) {
      send(exchange, 404, Map.of("error", "Session code was not found or has expired."));
      return;
    }
    String name = string(request, "name", "ZIDE User");
    if (name == null || name.isBlank() || name.length() > 80) {
      send(exchange, 400, Map.of("error", "Your name must be between 1 and 80 characters."));
      return;
    }
    String token = makeToken();
    synchronized (session) {
      if (session.ended || isExpired(session)) {
        sessions.remove(code, session);
        send(exchange, 404, Map.of("error", "Session code was not found or has expired."));
        return;
      }
      if (session.participants.size() >= MAX_PARTICIPANTS) {
        send(exchange, 409, Map.of("error", "This session is full."));
        return;
      }
      String tokenId = tokenHash(token);
      session.participants.put(tokenId, "guest");
      session.participantNames.put(tokenId, name);
      session.participantRevision++;
      session.lastActivity = System.currentTimeMillis();
      session.notifyAll();
      Map<String, Object> response = state(session, token);
      send(exchange, 200, response);
    }
  }

  private void poll(HttpExchange exchange, Map<String, Object> request) throws IOException {
    Session session = findAuthorized(exchange, request);
    if (session == null) return;
    Long sinceRevision = number(request.get("sinceRevision"));
    Long sinceParticipantRevision = number(request.get("sinceParticipantRevision"));
    Long waitSeconds = number(request.get("waitSeconds"));
    if (sinceRevision == null || sinceRevision < 0) {
      send(exchange, 400, Map.of("error", "sinceRevision must be a non-negative integer."));
      return;
    }
    if (sinceParticipantRevision == null || sinceParticipantRevision < 0) sinceParticipantRevision = 0L;
    long waitMillis = waitSeconds == null ? 10_000 : Math.max(0, Math.min(MAX_POLL_MILLIS, waitSeconds * 1000));
    long deadline = System.currentTimeMillis() + waitMillis;
    synchronized (session) {
      while (!session.ended && session.revision <= sinceRevision
              && session.participantRevision <= sinceParticipantRevision && System.currentTimeMillis() < deadline) {
        long remaining = deadline - System.currentTimeMillis();
        try {
          session.wait(Math.max(1, remaining));
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          break;
        }
      }
      if (session.ended) {
        send(exchange, 410, Map.of("error", "This collaboration session has ended."));
        return;
      }
      session.lastActivity = System.currentTimeMillis();
      send(exchange, 200, pollState(session, sinceRevision));
    }
  }

  private void edit(HttpExchange exchange, Map<String, Object> request) throws IOException {
    Session session = findAuthorized(exchange, request);
    if (session == null) return;
    Long baseRevision = number(request.get("baseRevision"));
    Long start = number(request.get("start"));
    Long deleteLength = number(request.get("deleteLength"));
    String insertText = string(request, "insertText", null);
    if (baseRevision == null || start == null || deleteLength == null || baseRevision < 0 || start < 0
            || deleteLength < 0 || insertText == null || start > Integer.MAX_VALUE || deleteLength > Integer.MAX_VALUE) {
      send(exchange, 400, Map.of("error", "Edit requires baseRevision, start, deleteLength and insertText."));
      return;
    }
    synchronized (session) {
      if (session.ended) {
        send(exchange, 410, Map.of("error", "This collaboration session has ended."));
        return;
      }
      if (baseRevision != session.revision) {
        send(exchange, 409, Map.of("revision", session.revision));
        return;
      }
      if (start + deleteLength > session.document.length()) {
        send(exchange, 400, Map.of("error", "Edit range is outside the current document."));
        return;
      }
      String updated = session.document.substring(0, start.intValue()) + insertText
              + session.document.substring((int)(start + deleteLength));
      if (updated.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) {
        send(exchange, 413, Map.of("error", "Document exceeds the 1 MiB session limit."));
        return;
      }
      session.document = updated;
      session.revision++;
      Edit accepted = new Edit(session.revision, start.intValue(), deleteLength.intValue(), insertText);
      session.edits.addLast(accepted);
      session.editHistoryBytes += accepted.byteSize();
      while (session.edits.size() > MAX_EDIT_HISTORY || session.editHistoryBytes > MAX_EDIT_HISTORY_BYTES) {
        session.editHistoryBytes -= session.edits.removeFirst().byteSize();
      }
      session.lastActivity = System.currentTimeMillis();
      session.notifyAll();
      Map<String, Object> response = new LinkedHashMap<>();
      response.put("revision", session.revision);
      response.put("participantRevision", session.participantRevision);
      response.put("participantNames", List.copyOf(session.participantNames.values()));
      send(exchange, 200, response);
    }
  }

  private void update(HttpExchange exchange, Map<String, Object> request) throws IOException {
    Session session = findAuthorized(exchange, request);
    if (session == null) return;
    Long baseRevision = number(request.get("baseRevision"));
    String document = string(request, "document", null);
    if (baseRevision == null || baseRevision < 0 || document == null) {
      send(exchange, 400, Map.of("error", "Update requires baseRevision and document."));
      return;
    }
    if (document.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) {
      send(exchange, 413, Map.of("error", "Document exceeds the 1 MiB session limit."));
      return;
    }
    synchronized (session) {
      if (session.ended) {
        send(exchange, 410, Map.of("error", "This collaboration session has ended."));
        return;
      }
      if (baseRevision != session.revision) {
        send(exchange, 409, state(session, null));
        return;
      }
      Edit accepted = new Edit(session.revision + 1, 0, session.document.length(), document);
      session.document = document;
      session.revision++;
      session.edits.addLast(accepted);
      session.editHistoryBytes += accepted.byteSize();
      while (session.edits.size() > MAX_EDIT_HISTORY || session.editHistoryBytes > MAX_EDIT_HISTORY_BYTES) {
        session.editHistoryBytes -= session.edits.removeFirst().byteSize();
      }
      session.lastActivity = System.currentTimeMillis();
      session.notifyAll();
      send(exchange, 200, state(session, null));
    }
  }

  private void leave(HttpExchange exchange, Map<String, Object> request) throws IOException {
    Session session = findAuthorized(exchange, request);
    if (session == null) return;
    String token = string(request, "token", "");
    synchronized (session) {
      String role = session.participants.get(tokenHash(token));
      if ("host".equals(role)) {
        session.ended = true;
        sessions.remove(session.code, session);
        session.notifyAll();
        send(exchange, 200, Map.of("ended", true));
        return;
      }
      session.participants.remove(tokenHash(token));
      session.participantNames.remove(tokenHash(token));
      session.participantRevision++;
      session.lastActivity = System.currentTimeMillis();
      session.notifyAll();
      send(exchange, 200, Map.of("left", true));
    }
  }

  private Session findAuthorized(HttpExchange exchange, Map<String, Object> request) throws IOException {
    String code = normalizedCode(request.get("code"));
    String token = string(request, "token", "");
    if (code == null || token == null || token.isBlank()) {
      send(exchange, 400, Map.of("error", "Session code and token are required."));
      return null;
    }
    Session session = sessions.get(code);
    if (session == null) {
      send(exchange, 404, Map.of("error", "Session was not found or has expired."));
      return null;
    }
    synchronized (session) {
      if (session.ended || isExpired(session)) {
        sessions.remove(code, session);
        session.ended = true;
        session.notifyAll();
        send(exchange, 410, Map.of("error", "This collaboration session has ended."));
        return null;
      }
      if (!session.participants.containsKey(tokenHash(token))) {
        send(exchange, 403, Map.of("error", "Invalid session token."));
        return null;
      }
      session.lastActivity = System.currentTimeMillis();
    }
    return session;
  }

  private static Map<String, Object> state(Session session, String token) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("revision", session.revision);
    response.put("document", session.document);
    response.put("fileName", session.fileName);
    response.put("language", session.language);
    response.put("participants", session.participants.size());
    response.put("participantNames", List.copyOf(session.participantNames.values()));
    response.put("participantRevision", session.participantRevision);
    response.put("expiresAt", session.lastActivity + SESSION_TTL_MILLIS);
    if (token != null) {
      response.put("code", session.code);
      response.put("token", token);
    }
    return response;
  }

  private static Map<String, Object> pollState(Session session, long sinceRevision) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("revision", session.revision);
    response.put("participantRevision", session.participantRevision);
    response.put("participantNames", List.copyOf(session.participantNames.values()));
    response.put("participants", session.participants.size());
    response.put("fileName", session.fileName);
    response.put("language", session.language);
    boolean snapshotRequired = sinceRevision < session.revision
            && (session.edits.isEmpty() || sinceRevision < session.edits.getFirst().revision - 1);
    response.put("snapshotRequired", snapshotRequired);
    if (snapshotRequired) {
      response.put("document", session.document);
      response.put("changes", List.of());
    } else {
      List<Map<String, Object>> changes = new ArrayList<>();
      for (Edit edit : session.edits) {
        if (edit.revision > sinceRevision) changes.add(edit.toMap());
      }
      response.put("changes", changes);
    }
    return response;
  }

  private static String string(Map<String, Object> request, String key, String fallback) {
    Object value = request.get(key);
    return value == null ? fallback : value instanceof String ? (String) value : null;
  }

  private static Long number(Object value) {
    return value instanceof Number ? ((Number)value).longValue() : null;
  }

  private static String normalizedCode(Object value) {
    if (!(value instanceof String)) return null;
    String code = ((String)value).trim().toUpperCase(java.util.Locale.ROOT);
    return code.matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}") ? code : null;
  }

  private static String makeCode() {
    StringBuilder code = new StringBuilder(8);
    for (int i = 0; i < 8; i++) code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
    return code.toString();
  }

  private static String makeToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String tokenHash(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(digest.length * 2);
      for (byte value : digest) result.append(String.format("%02x", value));
      return result.toString();
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is unavailable.", exception);
    }
  }

  private boolean isExpired(Session session) {
    return System.currentTimeMillis() - session.lastActivity > SESSION_TTL_MILLIS;
  }

  private void removeExpiredSessions() {
    long now = System.currentTimeMillis();
    sessions.forEach((code, session) -> {
      synchronized (session) {
        if (now - session.lastActivity > SESSION_TTL_MILLIS) {
          session.ended = true;
          session.notifyAll();
          sessions.remove(code, session);
        }
      }
    });
  }

  private static void send(HttpExchange exchange, int status, Object response) throws IOException {
    byte[] bytes = JSON.writeValueAsBytes(response);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var output = exchange.getResponseBody()) {
      output.write(bytes);
    }
  }

  private static final class Session {
    final String code;
    final String fileName;
    final String language;
    final Map<String, String> participants = new HashMap<>();
    final Map<String, String> participantNames = new LinkedHashMap<>();
    final ArrayDeque<Edit> edits = new ArrayDeque<>();
    String document;
    long revision;
    long participantRevision;
    int editHistoryBytes;
    long lastActivity = System.currentTimeMillis();
    boolean ended;

    Session(String code, String document, String fileName, String language, String hostToken, String hostName) {
      this.code = code;
      this.document = document;
      this.fileName = fileName;
      this.language = language;
      String tokenId = tokenHash(hostToken);
      participants.put(tokenId, "host");
      participantNames.put(tokenId, hostName);
    }
  }

  private record Edit(long revision, int start, int deleteLength, String insertText) {
    int byteSize() { return 32 + insertText.getBytes(StandardCharsets.UTF_8).length; }
    Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("revision", revision);
      result.put("start", start);
      result.put("deleteLength", deleteLength);
      result.put("insertText", insertText);
      return result;
    }
  }
}
