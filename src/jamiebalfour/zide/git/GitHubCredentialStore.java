package jamiebalfour.zide.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Optional;

/** Persists GitHub credentials outside repositories with owner-only file permissions. */
public final class GitHubCredentialStore {
  private final Path file;

  public GitHubCredentialStore(Path applicationDirectory) {
    file = applicationDirectory.resolve("github.credentials");
  }

  public void save(GitHubDeviceFlow.Token token) throws IOException {
    Files.createDirectories(file.getParent());
    String value = encode(token.accessToken()) + "\n" + encode(token.refreshToken()) + "\n"
            + (token.expiresAt() == null ? "" : token.expiresAt()) + "\n" + encode(token.scope());
    Files.writeString(file, value, StandardCharsets.UTF_8);
    try {
      Files.setPosixFilePermissions(file, EnumSet.of(PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE));
    } catch (UnsupportedOperationException ignored) {
      file.toFile().setReadable(false, false);
      file.toFile().setReadable(true, true);
      file.toFile().setWritable(false, false);
      file.toFile().setWritable(true, true);
    }
  }

  public Optional<GitHubDeviceFlow.Token> load() throws IOException {
    if (!Files.isRegularFile(file)) return Optional.empty();
    String[] values = Files.readString(file, StandardCharsets.UTF_8).split("\\R", -1);
    if (values.length < 4) return Optional.empty();
    Instant expiry = values[2].isBlank() ? null : Instant.parse(values[2]);
    return Optional.of(new GitHubDeviceFlow.Token(decode(values[0]), decode(values[1]), expiry,
            decode(values[3])));
  }

  public void clear() throws IOException { Files.deleteIfExists(file); }

  private static String encode(String value) {
    return value == null ? "" : Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String decode(String value) {
    return value.isBlank() ? null : new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
  }
}
