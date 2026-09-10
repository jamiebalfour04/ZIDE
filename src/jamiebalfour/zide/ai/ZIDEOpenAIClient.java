package jamiebalfour.zide.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** OpenAI Responses API client configured exclusively by ZIDE's properties. */
public final class ZIDEOpenAIClient {
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  private final OkHttpClient client = new OkHttpClient();
  private final ObjectMapper mapper = new ObjectMapper();
  private final String url;
  private final String key;
  private final String model;

  public ZIDEOpenAIClient(String url, String key, String model) {
    this.url = url.replaceFirst("/chat/completions/?$", "/responses");
    this.key = key;
    this.model = model;
  }

  public String sendMessage(String instructions, String input) throws IOException {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model);
    body.put("instructions", instructions);
    body.put("input", input);
    body.put("reasoning", Map.of("effort", "medium"));
    Request request = new Request.Builder().url(url)
            .header("Authorization", "Bearer " + key)
            .post(RequestBody.create(mapper.writeValueAsString(body), JSON)).build();
    try (Response response = client.newCall(request).execute()) {
      String payload = response.body() == null ? "" : response.body().string();
      if (!response.isSuccessful()) {
        throw new IOException("OpenAI error " + response.code() + ": " + payload);
      }
      JsonNode root = mapper.readTree(payload);
      for (JsonNode output : root.path("output")) {
        for (JsonNode content : output.path("content")) {
          if ("output_text".equals(content.path("type").asText())) {
            return content.path("text").asText();
          }
        }
      }
      throw new IOException("The OpenAI response contained no output text.");
    }
  }
}
