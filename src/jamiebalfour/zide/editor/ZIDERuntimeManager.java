package jamiebalfour.zide.editor;

import jamiebalfour.helpers.HelperFunctions;
import jamiebalfour.zpe.core.ZPEDebugger;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

/** Owns the optional ZPE and ZPEX packages installed specifically for ZIDE. */
final class ZIDERuntimeManager {
  enum RuntimeKind { ZPE, ZPEX }

  private static final String DOWNLOAD_ROOT = "https://www.jamiebalfour.scot/downloads/1-zpe/";
  private final Path installation;
  private final Path sharedInstallation = Path.of(System.getProperty("user.home", ""), "jb", "zpe");

  ZIDERuntimeManager(Path installation) {
    this.installation = installation;
  }

  Path path(RuntimeKind kind) {
    Path shared = sharedInstallation.resolve(runtimeFileName(kind));
    return Files.isRegularFile(shared) ? shared : installation.resolve(runtimeFileName(kind));
  }

  private static String runtimeFileName(RuntimeKind kind) {
    return kind == RuntimeKind.ZPE ? "zpe.jar" : HelperFunctions.isWindows() ? "zpex.exe" : "zpex";
  }

  boolean isInstalled(RuntimeKind kind) {
    return Files.isRegularFile(path(kind));
  }

  String downloadUrl(RuntimeKind kind) {
    if (kind == RuntimeKind.ZPE) return DOWNLOAD_ROOT + "zpe";
    if (HelperFunctions.isMac()) return DOWNLOAD_ROOT + "zpe-native-aarch64";
    if (HelperFunctions.isWindows()) return DOWNLOAD_ROOT + "zpe-native-win-x64";
    if (HelperFunctions.isUnix()) return DOWNLOAD_ROOT + "zpe-native-ubuntu-linux-x64";
    throw new IllegalStateException("ZPEX is not available for this operating system.");
  }

  Launch prepare(RuntimeKind kind, Path source, Path resourceRoot, boolean debug, String extras)
          throws IOException {
    if (source == null || !Files.isRegularFile(source)) throw new IOException("The YASS source file does not exist.");
    Path runtime = path(kind);
    if (!Files.isRegularFile(runtime)) throw new IOException(kind + " is not installed in ZIDE.");

    ArrayList<String> command = new ArrayList<>();
    if (kind == RuntimeKind.ZPEX) {
      command.add(runtime.toString());
    } else {
      command.add(javaCommand());
      if (HelperFunctions.isMac()) command.add("-XstartOnFirstThread");
      command.add("-jar");
      command.add(runtime.toString());
    }
    command.add(debug ? "-d" : "-r");
    command.add(source.toAbsolutePath().toString());
    if (resourceRoot != null && Files.isDirectory(resourceRoot)) {
      command.add("--resource-root");
      command.add(resourceRoot.toAbsolutePath().toString());
    }
    command.add("--silent");

    ServerSocket debugServer = null;
    try {
      if (debug) {
        debugServer = new ServerSocket(0);
        command.add("--use_zpe_events");
        command.add("-port");
        command.add(Integer.toString(debugServer.getLocalPort()));
      }
      if (extras != null && !extras.trim().isEmpty()) command.addAll(Arrays.asList(extras.trim().split("\\s+")));
      ProcessBuilder builder = new ProcessBuilder(command);
      if (resourceRoot != null && Files.isDirectory(resourceRoot)) builder.directory(resourceRoot.toFile());
      return new Launch(builder, debugServer,
              "Running with ZIDE's " + kind + " package (" + runtime + ").");
    } catch (RuntimeException exception) {
      if (debugServer != null) debugServer.close();
      throw exception;
    }
  }

  Launch prepareZenLanguageTraining(Path definition) throws IOException {
    if (definition == null || !Files.isRegularFile(definition)) {
      throw new IOException("The ZenLang definition does not exist.");
    }
    Path runtime = path(RuntimeKind.ZPE);
    if (!Files.isRegularFile(runtime)) throw new IOException("ZPE is not installed in ZIDE.");
    ArrayList<String> command = new ArrayList<>();
    command.add(javaCommand());
    if (HelperFunctions.isMac()) command.add("-XstartOnFirstThread");
    command.add("-jar");
    command.add(runtime.toString());
    command.add("-m");
    command.add(definition.toAbsolutePath().toString());
    command.add("--train");
    ProcessBuilder builder = new ProcessBuilder(command);
    Path parent = definition.toAbsolutePath().getParent();
    if (parent != null) builder.directory(parent.toFile());
    return new Launch(builder, null, "Training ZenLang syntax with ZIDE's ZPE package.");
  }

  Launch prepareZenLanguageTest(Path definition, Path source) throws IOException {
    if (definition == null || !Files.isRegularFile(definition)) {
      throw new IOException("The ZenLang definition does not exist.");
    }
    if (source == null || !Files.isRegularFile(source)) {
      throw new IOException("The test script does not exist.");
    }
    Path runtime = path(RuntimeKind.ZPE);
    if (!Files.isRegularFile(runtime)) throw new IOException("ZPE is not installed in ZIDE.");
    ArrayList<String> command = new ArrayList<>();
    command.add(javaCommand());
    if (HelperFunctions.isMac()) command.add("-XstartOnFirstThread");
    command.add("-jar");
    command.add(runtime.toString());
    command.add("-m");
    command.add(source.toAbsolutePath().toString());
    command.add(definition.toAbsolutePath().toString());
    ProcessBuilder builder = new ProcessBuilder(command);
    Path parent = source.toAbsolutePath().getParent();
    if (parent != null) builder.directory(parent.toFile());
    return new Launch(builder, null, "Testing script with the current ZenLang definition.");
  }

  private static String javaCommand() {
    String executable = HelperFunctions.isWindows() ? "java.exe" : "java";
    String javaHome = System.getProperty("java.home", "");
    if (!javaHome.isEmpty()) {
      Path bundled = Path.of(javaHome, "bin", executable);
      if (Files.isRegularFile(bundled)) return bundled.toString();
    }
    return executable;
  }

  /** A prepared child process plus the optional socket used by ZPE debugging. */
  static final class Launch implements AutoCloseable {
    private final ProcessBuilder builder;
    private final ServerSocket debugServer;
    private final String description;
    private boolean debuggerStarted;

    Launch(ProcessBuilder builder, ServerSocket debugServer, String description) {
      this.builder = builder;
      this.debugServer = debugServer;
      this.description = description;
    }

    ProcessBuilder processBuilder() { return builder; }
    String description() { return description; }

    synchronized void processStarted(Process process) {
      if (debugServer == null || debuggerStarted) return;
      debuggerStarted = true;
      ZPEDebugger.respond(debugServer);
      Thread cleanup = new Thread(() -> {
        try {
          process.waitFor();
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
        } finally {
          close();
        }
      }, "zide-debug-server-cleanup");
      cleanup.setDaemon(true);
      cleanup.start();
    }

    @Override
    public void close() {
      if (debugServer == null) return;
      try {
        debugServer.close();
      } catch (IOException ignored) {
        // The child may already have closed its debugging connection.
      }
    }
  }
}
