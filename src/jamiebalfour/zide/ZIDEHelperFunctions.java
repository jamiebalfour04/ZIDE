package jamiebalfour.zide;

import javafx.concurrent.Task;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;

public final class ZIDEHelperFunctions {

  private ZIDEHelperFunctions() {}

  public static Task<Path> downloadToFileTask(String url, Path target) {
    return new Task<>() {
      @Override
      protected Path call() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<InputStream> resp =
                client.send(req, HttpResponse.BodyHandlers.ofInputStream());

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
          throw new RuntimeException("HTTP " + resp.statusCode());
        }

        OptionalLong contentLen = resp.headers().firstValueAsLong("content-length");
        long total = contentLen.orElse(-1);

        // Ensure parent dir exists
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);

        try (InputStream in = resp.body();
             OutputStream out = Files.newOutputStream(target)) {

          byte[] buf = new byte[64 * 1024];
          long done = 0;

          if (total > 0) {
            updateProgress(0, total);
          } else {
            updateProgress(-1, -1); // indeterminate
          }

          int r;
          while ((r = in.read(buf)) != -1) {
            out.write(buf, 0, r);
            done += r;

            if (total > 0) {
              updateProgress(done, total);
              updateMessage("Downloading… " + (done / 1024) + " KB / " + (total / 1024) + " KB");
            } else {
              updateMessage("Downloading… " + (done / 1024) + " KB");
            }

            if (isCancelled()) {
              throw new InterruptedException("Download cancelled");
            }
          }
        }

        updateMessage("Finalising…");
        return target;
      }
    };
  }
}
