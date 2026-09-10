package jamiebalfour.zide.git;

import jamiebalfour.parsers.json.ZenithJSONParser;
import jamiebalfour.zpe.core.types.ZPEMap;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

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

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(API.resolve(path)).timeout(Duration.ofSeconds(30))
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer " + token)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "ZIDE");
  }
}
