package jamiebalfour.zide.git;

import jamiebalfour.parsers.json.ZenithJSONParser;
import jamiebalfour.zpe.core.types.ZPEMap;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/** Small authenticated GitHub REST client shared by repository, issue and pull-request views. */
public final class GitHubApi {
  private static final URI API = URI.create("https://api.github.com/");
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
  private final String token;

  public GitHubApi(String token) { this.token = token; }

  public ZPEMap currentUser() throws IOException, InterruptedException {
    HttpRequest request = request("user").GET().build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) throw new IOException("GitHub account lookup failed (HTTP "
            + response.statusCode() + ").");
    try { return (ZPEMap) new ZenithJSONParser().jsonDecode(response.body(), false); }
    catch (Exception exception) { throw new IOException("GitHub returned an unreadable response.", exception); }
  }

  public Repository createRepository(String name, String description, boolean isPrivate)
          throws IOException, InterruptedException {
    String body = "{\"name\":" + jsonString(name) + ",\"description\":" + jsonString(description)
            + ",\"private\":" + isPrivate + "}";
    HttpRequest request = request("user/repos").header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 201) {
      String reason = "";
      try {
        ZPEMap error = (ZPEMap) new ZenithJSONParser().jsonDecode(response.body(), false);
        reason = Objects.toString(error.get("message"), "");
      } catch (Exception ignored) { }
      throw new IOException("GitHub could not create the repository"
              + (reason.isBlank() ? " (HTTP " + response.statusCode() + ")." : ": " + reason));
    }
    try {
      ZPEMap result = (ZPEMap) new ZenithJSONParser().jsonDecode(response.body(), false);
      String cloneUrl = Objects.toString(result.get("clone_url"), "");
      String htmlUrl = Objects.toString(result.get("html_url"), "");
      if (cloneUrl.isBlank() || htmlUrl.isBlank()) throw new IOException("GitHub omitted the repository URLs.");
      return new Repository(cloneUrl, htmlUrl);
    } catch (Exception exception) {
      throw new IOException("GitHub created the repository but returned an unreadable response.", exception);
    }
  }

  private static String jsonString(String value) {
    StringBuilder result = new StringBuilder("\"");
    for (int i = 0; i < value.length(); i++) {
      char character = value.charAt(i);
      switch (character) {
        case '"' -> result.append("\\\"");
        case '\\' -> result.append("\\\\");
        case '\b' -> result.append("\\b");
        case '\f' -> result.append("\\f");
        case '\n' -> result.append("\\n");
        case '\r' -> result.append("\\r");
        case '\t' -> result.append("\\t");
        default -> {
          if (character < 0x20) result.append(String.format("\\u%04x", (int) character));
          else result.append(character);
        }
      }
    }
    return result.append('"').toString();
  }

  public record Repository(String cloneUrl, String htmlUrl) { }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(API.resolve(path)).timeout(Duration.ofSeconds(30))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer " + token)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "ZIDE");
  }
}
