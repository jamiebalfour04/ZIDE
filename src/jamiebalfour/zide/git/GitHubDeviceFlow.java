package jamiebalfour.zide.git;

import jamiebalfour.parsers.json.ZenithJSONParser;
import jamiebalfour.zpe.core.types.ZPEMap;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Implements GitHub's client-secret-free OAuth device authorization flow. */
public final class GitHubDeviceFlow {
  public static final String ZIDE_CLIENT_ID = "Ov23liw43fjIFZwLy9G3";
  private static final URI DEVICE_CODE = URI.create("https://github.com/login/device/code");
  private static final URI ACCESS_TOKEN = URI.create("https://github.com/login/oauth/access_token");
  private final HttpClient client;
  private final String clientId;

  public GitHubDeviceFlow(String clientId) {
    this.clientId = Objects.requireNonNull(clientId, "clientId").trim();
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
  }

  public DeviceCode requestCode() throws IOException, InterruptedException {
    if (clientId.isEmpty()) throw new IOException("ZIDE's GitHub client ID has not been configured.");
    String body = "client_id=" + encode(clientId) + "&scope=" + encode("repo read:user user:email");
    ZPEMap json = post(DEVICE_CODE, body);
    return new DeviceCode(value(json, "device_code"), value(json, "user_code"),
            URI.create(value(json, "verification_uri")), number(json, "expires_in"),
            Math.max(1, number(json, "interval")));
  }

  /** Polls at GitHub's supplied interval and completes only after approval or expiry. */
  public CompletableFuture<Token> awaitToken(DeviceCode code) {
    return CompletableFuture.supplyAsync(() -> {
      Instant expires = Instant.now().plusSeconds(code.expiresInSeconds());
      int interval = code.intervalSeconds();
      while (Instant.now().isBefore(expires)) {
        try {
          Thread.sleep(interval * 1000L);
          String body = "client_id=" + encode(clientId) + "&device_code=" + encode(code.deviceCode())
                  + "&grant_type=" + encode("urn:ietf:params:oauth:grant-type:device_code");
          ZPEMap json = post(ACCESS_TOKEN, body);
          String token = optionalValue(json, "access_token");
          if (token != null && !token.isBlank()) return tokenFrom(json);
          String error = optionalValue(json, "error");
          if ("authorization_pending".equals(error)) continue;
          if ("slow_down".equals(error)) { interval += 5; continue; }
          if (error != null) throw new IOException(optionalValue(json, "error_description"));
        } catch (IOException | InterruptedException exception) {
          if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
          throw new CompletionException(exception);
        }
      }
      throw new CompletionException(new IOException("The GitHub sign-in code expired."));
    });
  }

  public Token refresh(String refreshToken) throws IOException, InterruptedException {
    String body = "client_id=" + encode(clientId) + "&grant_type=" + encode("refresh_token")
            + "&refresh_token=" + encode(refreshToken);
    ZPEMap json = post(ACCESS_TOKEN, body);
    String error = optionalValue(json, "error");
    if (error != null) throw new IOException(optionalValue(json, "error_description"));
    return tokenFrom(json);
  }

  private static Token tokenFrom(ZPEMap json) throws IOException {
    long expiresIn = optionalNumber(json, "expires_in", 28_800);
    return new Token(value(json, "access_token"), optionalValue(json, "refresh_token"),
            Instant.now().plusSeconds(expiresIn), optionalValue(json, "scope"));
  }

  private ZPEMap post(URI uri, String body) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("User-Agent", "ZIDE")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IOException("GitHub returned HTTP " + response.statusCode() + ".");
    }
    try {
      return (ZPEMap) new ZenithJSONParser().jsonDecode(response.body(), false);
    } catch (Exception exception) {
      throw new IOException("GitHub returned an unreadable response.", exception);
    }
  }

  private static String value(ZPEMap map, String key) throws IOException {
    String value = optionalValue(map, key);
    if (value == null || value.isBlank()) throw new IOException("GitHub did not return " + key + ".");
    return value;
  }

  private static String optionalValue(ZPEMap map, String key) {
    Object value = map.get(key);
    return value == null ? null : value.toString();
  }

  private static int number(ZPEMap map, String key) throws IOException {
    try { return Integer.parseInt(value(map, key)); }
    catch (NumberFormatException exception) { throw new IOException("GitHub returned an invalid " + key + "."); }
  }

  private static long optionalNumber(ZPEMap map, String key, long fallback) {
    try {
      String value = optionalValue(map, key);
      return value == null ? fallback : Long.parseLong(value);
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  public record DeviceCode(String deviceCode, String userCode, URI verificationUri,
                           int expiresInSeconds, int intervalSeconds) { }
  public record Token(String accessToken, String refreshToken, Instant expiresAt, String scope) {
    public boolean needsRefresh() { return expiresAt != null && Instant.now().plusSeconds(60).isAfter(expiresAt); }
  }
}
