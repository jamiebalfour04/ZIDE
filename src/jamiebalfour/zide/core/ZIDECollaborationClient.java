package jamiebalfour.zide.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** HTTP client for a ZIDE collaboration relay. */
public final class ZIDECollaborationClient {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> RESPONSE_TYPE = new TypeReference<>() { };
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final URI endpoint;
  private final String password;

  public ZIDECollaborationClient(String server, int port) {
    this(server, port, "");
  }

  public ZIDECollaborationClient(String server, int port, String password) {
    endpoint = endpoint(server, port);
    this.password = password == null ? "" : password;
  }

  public Map<String, Object> create(String name, String document, String fileName, String language)
          throws IOException, InterruptedException {
    return request("create", Map.of("name", name, "document", document, "fileName", fileName, "language", language, "authHash", passwordHash()));
  }

  public Map<String, Object> create(String name, String document, String fileName, String language, List<String> projectFiles)
          throws IOException, InterruptedException {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("name", name);
    fields.put("document", document);
    fields.put("fileName", fileName);
    fields.put("language", language);
    fields.put("projectFiles", projectFiles == null ? List.of() : projectFiles);
    fields.put("authHash", passwordHash());
    return request("create", fields);
  }

  public Map<String, Object> join(String code, String name) throws IOException, InterruptedException {
    return request("join", Map.of("code", code, "name", name, "authHash", passwordHash()));
  }

  private String passwordHash() {
    if (password.isEmpty()) return "";
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder(digest.length * 2);
      for (byte value : digest) result.append(String.format("%02x", value));
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public Map<String, Object> poll(String code, String token, long sinceRevision, long sinceParticipantRevision)
          throws IOException, InterruptedException {
    return request("poll", Map.of("code", code, "token", token, "sinceRevision", sinceRevision,
            "sinceParticipantRevision", sinceParticipantRevision, "waitSeconds", 15));
  }

  public Map<String, Object> edit(String code, String token, long baseRevision, int start, int deleteLength, String insertText)
          throws IOException, InterruptedException {
    return request("edit", Map.of("code", code, "token", token, "baseRevision", baseRevision,
            "start", start, "deleteLength", deleteLength, "insertText", insertText));
  }

  public Map<String, Object> presence(String code, String token, String file, int line)
          throws IOException, InterruptedException {
    return request("presence", Map.of("code", code, "token", token, "file", file, "line", line));
  }

  public Map<String, Object> chat(String code, String token, String message)
          throws IOException, InterruptedException {
    return request("chat", Map.of("code", code, "token", token, "message", message));
  }

  public Map<String, Object> heartbeat(String code, String token)
          throws IOException, InterruptedException {
    return request("heartbeat", Map.of("code", code, "token", token));
  }

  /** Requests a project-relative file. A pending result means the owner is loading it. */
  public Map<String, Object> requestProjectFile(String code, String token, String path)
          throws IOException, InterruptedException {
    return request("file-request", Map.of("code", code, "token", token, "path", path));
  }

  /** Publishes a project file into the collaboration server's temporary session cache. */
  public Map<String, Object> publishProjectFile(String code, String token, String path, String content)
          throws IOException, InterruptedException {
    return request("file-publish", Map.of("code", code, "token", token, "path", path, "content", content));
  }

  public void leave(String code, String token) throws IOException, InterruptedException {
    request("leave", Map.of("code", code, "token", token));
  }

  private Map<String, Object> request(String action, Map<String, ?> fields) throws IOException, InterruptedException {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("action", action);
    payload.putAll(fields);
    HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(25))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(payload))).build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    Map<String, Object> body;
    try {
      body = JSON.readValue(response.body(), RESPONSE_TYPE);
    } catch (Exception exception) {
      throw new IOException("The collaboration server returned an unreadable response.", exception);
    }
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      String message = body.get("error") instanceof String error ? error : "HTTP " + response.statusCode();
      throw new CollaborationException(message, response.statusCode(), body);
    }
    return body;
  }

  private static URI endpoint(String server, int port) {
    String value = server == null ? "" : server.trim();
    boolean hasScheme = value.contains("://");
    String scheme = hasScheme ? value.substring(0, value.indexOf("://")).toLowerCase(java.util.Locale.ROOT)
            : "http";
    URI supplied = URI.create(hasScheme ? value : scheme + "://" + value);
    if (supplied.getHost() == null) {
      throw new IllegalArgumentException("Enter a valid collaboration server name.");
    }
    int effectivePort = supplied.getPort() >= 0 ? supplied.getPort() : port;
    String path = supplied.getPath() == null ? "" : supplied.getPath().replaceAll("/+$", "");
    try {
      return new URI(supplied.getScheme(), null, supplied.getHost(), effectivePort,
              path + "/api/v1/collaboration", null, null);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Enter a valid collaboration server address.", exception);
    }
  }

  public static long revision(Map<String, Object> state) throws IOException {
    Object value = state.get("revision");
    if (!(value instanceof Number number)) {
      throw new IOException("The collaboration server omitted the revision.");
    }
    return number.longValue();
  }

  public static String string(Map<String, Object> state, String key) throws IOException {
    Object value = state.get(key);
    if (!(value instanceof String text)) {
      throw new IOException("The collaboration server omitted " + key + ".");
    }
    return text;
  }

  public static long participantRevision(Map<String, Object> state) {
    Object value = state.get("participantRevision");
    return value instanceof Number number ? number.longValue() : 0L;
  }

  public static List<String> participantNames(Map<String, Object> state) {
    Object value = state.get("participantNames");
    if (!(value instanceof Iterable<?> names)) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (Object name : names) if (name instanceof String text && !text.isBlank()) result.add(text);
    return List.copyOf(result);
  }

  public static List<Presence> presences(Map<String, Object> state) {
    Object value = state.get("presence");
    if (!(value instanceof Iterable<?> entries)) {
      return List.of();
    }
    List<Presence> result = new ArrayList<>();
    for (Object entry : entries) {
      if (entry instanceof Map<?, ?> presence
              && presence.get("name") instanceof String name
              && presence.get("file") instanceof String file
              && presence.get("line") instanceof Number line
              && !name.isBlank() && !file.isBlank() && line.intValue() > 0) {
        result.add(new Presence(name, file, line.intValue()));
      }
    }
    return List.copyOf(result);
  }

  public static List<String> projectFileRequests(Map<String, Object> state) {
    Object value = state.get("projectFileRequests");
    if (!(value instanceof Iterable<?> paths)) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (Object path : paths) {
      if (path instanceof String text && !text.isBlank()) result.add(text);
    }
    return List.copyOf(result);
  }

  public static boolean snapshotRequired(Map<String, Object> state) {
    return Boolean.TRUE.equals(state.get("snapshotRequired"));
  }

  public static String snapshot(Map<String, Object> state) throws IOException {
    return string(state, "document");
  }

  public static List<TextEdit> edits(Map<String, Object> state) throws IOException {
    Object value = state.get("changes");
    if (!(value instanceof Iterable<?> changes)) {
      return List.of();
    }
    List<TextEdit> result = new ArrayList<>();
    for (Object item : changes) {
      if (!(item instanceof Map<?, ?> change)
              || !(change.get("revision") instanceof Number revision)
              || !(change.get("start") instanceof Number start)
              || !(change.get("deleteLength") instanceof Number deleteLength)
              || !(change.get("insertText") instanceof String insertText)) {
        throw new IOException("The collaboration server sent an invalid edit.");
      }
      result.add(new TextEdit(revision.longValue(), start.intValue(), deleteLength.intValue(), insertText));
    }
    return List.copyOf(result);
  }

  public record TextEdit(long revision, int start, int deleteLength, String insertText) { }

  public record Presence(String name, String file, int line) { }

  public static final class CollaborationException extends IOException {
    private final int statusCode;
    private final Map<String, Object> state;
    private CollaborationException(String message, int statusCode, Map<String, Object> state) {
      super(message);
      this.statusCode = statusCode;
      this.state = state;
    }
    public int statusCode() { return statusCode; }
    public Map<String, Object> state() { return state; }
  }
}
