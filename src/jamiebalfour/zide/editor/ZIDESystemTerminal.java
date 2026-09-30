package jamiebalfour.zide.editor;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A small JavaFX wrapper around the user's local command shell.  It deliberately
 * keeps a working directory between commands, while each command is executed in
 * its own process so the IDE cannot be held hostage by an interactive shell.
 */
final class ZIDESystemTerminal extends BorderPane {

  private static final Font TERMINAL_FONT = loadTerminalFont();
  private final TextArea transcript = new TextArea();
  private final Button stop = new Button("Stop");
  private final List<String> history = new ArrayList<>();
  private int historyPosition = 0;
  private int inputStart;
  private boolean commandRunning;
  private boolean internalEdit;
  private Path workingDirectory;
  private Process activeProcess;
  private BufferedWriter activeInput;
  private final Function<String, String> zideCommandHandler;
  private static final java.util.regex.Pattern ANSI = java.util.regex.Pattern.compile(
          "\\u001B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\\u0007]*(?:\\u0007|\\u001B\\\\))");

  ZIDESystemTerminal(Path initialDirectory) {
    this(initialDirectory, null);
  }

  ZIDESystemTerminal(Path initialDirectory, Function<String, String> zideCommandHandler) {
    workingDirectory = normaliseDirectory(initialDirectory);
    this.zideCommandHandler = zideCommandHandler;

    getStyleClass().add("terminal-pane");

    transcript.setEditable(true);
    transcript.setWrapText(true);
    transcript.setFont(TERMINAL_FONT);
    transcript.getStyleClass().add("terminal-output");
    transcript.setTextFormatter(new TextFormatter<String>(change -> {
      if (internalEdit) return change;
      int boundary = Math.min(inputStart, transcript.getLength());
      return change.getRangeStart() < boundary || change.getRangeEnd() < boundary ? null : change;
    }));
    transcript.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
      if (transcript.getCaretPosition() < inputStart) {
        Platform.runLater(() -> transcript.positionCaret(transcript.getLength()));
      }
    });
    transcript.addEventFilter(MouseEvent.MOUSE_RELEASED, e ->
        Platform.runLater(() -> {
          if (transcript.getCaretPosition() < inputStart) {
            transcript.positionCaret(transcript.getLength());
          }
        }));
    transcript.setOnKeyPressed(e -> {
      if (e.isShortcutDown() && e.getCode() == KeyCode.A) {
        transcript.selectRange(inputStart, transcript.getLength());
        e.consume();
      } else if (e.getCode() == KeyCode.HOME) {
        transcript.positionCaret(inputStart);
        e.consume();
      } else if (e.getCode() == KeyCode.LEFT
              && transcript.getSelection().getStart() == transcript.getSelection().getEnd()
              && transcript.getCaretPosition() <= inputStart) {
        e.consume();
      } else if (commandRunning && e.isControlDown()) {
        if (e.getCode() == KeyCode.C) sendSignal("INT");
        else if (e.getCode() == KeyCode.Z) sendSignal("TSTP");
        else if (e.getCode() == KeyCode.BACK_SLASH) sendSignal("QUIT");
        else if (e.getCode() == KeyCode.D) closeProcessInput();
        e.consume();
      } else if (commandRunning && e.getCode() == KeyCode.ENTER) {
        submitProcessInput();
        e.consume();
      } else if (commandRunning && e.getCode() == KeyCode.BACK_SPACE
              && inputTouchesTranscriptBoundary()) {
        e.consume();
      } else if (commandRunning && e.getCode() == KeyCode.DELETE
              && inputTouchesTranscriptBoundary()) {
        e.consume();
      } else if (commandRunning && transcript.getCaretPosition() < inputStart) {
        transcript.positionCaret(transcript.getLength());
        e.consume();
      } else if (e.getCode() == KeyCode.ENTER) {
        submit();
        e.consume();
      } else if (e.getCode() == KeyCode.BACK_SPACE && transcript.getCaretPosition() <= inputStart) {
        e.consume();
      } else if (e.getCode() == KeyCode.BACK_SPACE && inputTouchesTranscriptBoundary()) {
        e.consume();
      } else if (e.getCode() == KeyCode.DELETE && inputTouchesTranscriptBoundary()) {
        e.consume();
      } else if (e.getCode() == KeyCode.UP) {
        showPreviousHistory();
        e.consume();
      } else if (e.getCode() == KeyCode.DOWN) {
        showNextHistory();
        e.consume();
      } else if (e.isControlDown() && e.getCode() == KeyCode.L) {
        clear();
        e.consume();
      }
    });
    transcript.setOnKeyTyped(e -> {
      if (transcript.getCaretPosition() < inputStart) {
        transcript.positionCaret(transcript.getLength());
        e.consume();
      }
    });

    stop.getStyleClass().add("terminal-stop-button");
    stop.setVisible(false);
    stop.setManaged(false);
    stop.setOnAction(e -> stopActiveCommand());

    StackPane content = new StackPane(transcript, stop);
    StackPane.setAlignment(stop, Pos.BOTTOM_RIGHT);
    StackPane.setMargin(stop, new Insets(0, 30, 16, 0));
    setCenter(content);

    append("ZIDE system terminal\n");
    append("Working directory: " + workingDirectory + "\n");
    append("Commands are run using your local " + shellName() + " shell. This terminal also supports the built-in zide command. Use cd, ls, git, zpe, etc.\n\n");
    appendPrompt();
  }

  private boolean inputTouchesTranscriptBoundary() {
    int selectionStart = transcript.getSelection().getStart();
    int selectionEnd = transcript.getSelection().getEnd();
    return selectionStart < inputStart
            || (selectionStart == selectionEnd && transcript.getCaretPosition() <= inputStart);
  }

  private static Font loadTerminalFont() {
    try (var stream = ZIDESystemTerminal.class.getResourceAsStream("/files/JetBrainsMono-Medium.ttf")) {
      Font bundled = stream == null ? null : Font.loadFont(stream, 12);
      if (bundled != null) {
        return bundled;
      }
    } catch (IOException ignored) { }
    return Font.font("Monospaced", FontWeight.MEDIUM, 12);
  }

  void setWorkingDirectory(Path directory) {
    workingDirectory = normaliseDirectory(directory);
    if (!commandRunning) replaceCurrentInput("");
  }

  void focusCommandInput() {
    Platform.runLater(() -> {
      transcript.requestFocus();
      transcript.positionCaret(transcript.getLength());
    });
  }

  private void submit() {
    if (commandRunning) return;
    String command = currentInput().trim();
    append("\n");
    if (command.isEmpty()) { appendPrompt(); return; }
    if (history.isEmpty() || !history.get(history.size() - 1).equals(command)) {
      history.add(command);
    }
    historyPosition = history.size();

    if ("clear".equalsIgnoreCase(command) || "cls".equalsIgnoreCase(command)) {
      clear();
      return;
    }
    if ("pwd".equalsIgnoreCase(command)) {
      append(workingDirectory + "\n");
      appendPrompt();
      return;
    }
    if (command.equalsIgnoreCase("help") || command.equalsIgnoreCase("zide-help")) {
      append("This terminal runs local system commands and the built-in zide command.\n");
      append("Built-ins: cd [directory], pwd, clear, help, zide help.\n");
      append("Examples: ls, git status, zpe --version, java --version.\n");
      appendPrompt();
      return;
    }
    if (isZideCommand(command)) {
      runZideCommand(command);
      return;
    }
    if (isChangeDirectory(command)) {
      changeDirectory(command);
      appendPrompt();
      return;
    }

    runSystemCommand(command);
  }

  private boolean isZideCommand(String command) {
    return command.equalsIgnoreCase("zide")
            || command.regionMatches(true, 0, "zide ", 0, 5);
  }

  private void runZideCommand(String command) {
    String arguments = command.length() <= 4 ? "" : command.substring(4).trim();
    if (arguments.isEmpty()) {
      append("Type 'zide help' for more information.\n");
      appendPrompt();
      return;
    }
    if ("help".equalsIgnoreCase(arguments)) {
      append("ZIDE terminal commands:\n");
      append("  zide help   Show this help\n");
      append("  zide pwd    Show the terminal working directory\n");
      append("  zide clear  Clear the terminal\n");
      append("  zide run    Run the current file\n");
      append("  zide stop   Stop the active process\n");
      append("  zide build  Build the current project\n");
      append("  zide open <folder>  Open or import a project\n");
      append("  zide project  Show the active project\n");
      append("  zide version  Show ZIDE and Java versions\n");
      append("  zide clear-cache  Clear the current project cache\n");
      appendPrompt();
      return;
    }
    if ("pwd".equalsIgnoreCase(arguments)) {
      append(workingDirectory + "\n");
      appendPrompt();
      return;
    }
    if ("clear".equalsIgnoreCase(arguments) || "cls".equalsIgnoreCase(arguments)) {
      clear();
      return;
    }
    if (zideCommandHandler != null) {
      String response = zideCommandHandler.apply(arguments);
      if (response != null && !response.isBlank()) append(response.endsWith("\n") ? response : response + "\n");
      appendPrompt();
      return;
    }
    append("zide: unknown command: " + arguments + "\n");
    append("Use 'zide help' to list ZIDE terminal commands.\n");
    appendPrompt();
  }

  private boolean isChangeDirectory(String command) {
    return command.equalsIgnoreCase("cd") || command.regionMatches(true, 0, "cd ", 0, 3);
  }

  private void changeDirectory(String command) {
    String suppliedPath = command.length() <= 2 ? System.getProperty("user.home") : command.substring(2).trim();
    suppliedPath = unquote(suppliedPath);
    if (suppliedPath.startsWith("~")) {
      suppliedPath = System.getProperty("user.home") + suppliedPath.substring(1);
    }

    Path candidate;
    try {
      candidate = Path.of(suppliedPath);
      if (!candidate.isAbsolute()) candidate = workingDirectory.resolve(candidate);
      candidate = candidate.toAbsolutePath().normalize();
    } catch (Exception ex) {
      append("cd: invalid directory: " + suppliedPath + "\n");
      return;
    }

    if (!Files.isDirectory(candidate)) {
      append("cd: no such directory: " + candidate + "\n");
      return;
    }

    workingDirectory = candidate;
  }

  private void runSystemCommand(String command) {
    commandRunning = true;
    stop.setManaged(true);
    stop.setVisible(true);

    Task<Integer> task = new Task<>() {
      @Override
      protected Integer call() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(shellCommand(terminalCommand(command)));
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("NO_COLOR", "1");
        builder.environment().put("TERM", "dumb");
        // Allows terminal-aware runtimes to distinguish this embedded shell from a GUI launch.
        builder.environment().put("ZPE_TERMINAL", "1");
        activeProcess = builder.start();
        activeInput = new BufferedWriter(new OutputStreamWriter(
                activeProcess.getOutputStream(), StandardCharsets.UTF_8));
        Platform.runLater(() -> inputStart = transcript.getLength());

        try (Reader reader = new InputStreamReader(
                activeProcess.getInputStream(), StandardCharsets.UTF_8)) {
          char[] buffer = new char[512];
          int count;
          while ((count = reader.read(buffer)) >= 0) {
            if (count == 0) {
              continue;
            }
            String output = new String(buffer, 0, count);
            Platform.runLater(() -> insertProcessOutput(output));
          }
        }

        return activeProcess.waitFor();
      }
    };

    task.setOnSucceeded(e -> {
      Integer exitCode = task.getValue();
      if (exitCode != null && exitCode != 0) append("[Process exited with code " + exitCode + "]\n");
      activeProcess = null;
      activeInput = null;
      finishCommand();
    });
    task.setOnFailed(e -> {
      Throwable error = task.getException();
      append("Unable to run command: " + (error == null ? "unknown error" : error.getMessage()) + "\n");
      activeProcess = null;
      activeInput = null;
      finishCommand();
    });

    Thread thread = new Thread(task, "ZIDE system terminal");
    thread.setDaemon(true);
    thread.start();
  }

  private List<String> shellCommand(String command) {
    if (isWindows()) {
      String translated = command.matches("(?i)^ls(\\s.*)?$")
              ? "dir" + command.substring(2)
              : command;
      return List.of("cmd.exe", "/c", translated);
    }

    String shell = System.getenv("SHELL");
    if (shell == null || shell.isBlank() || !Files.isExecutable(Path.of(shell))) shell = "/bin/sh";
    return List.of(shell, "-lc", command);
  }

  /** Bare ZPE prints its introduction here; -i remains the explicit interactive mode. */
  private String terminalCommand(String command) {
    return "zpe".equalsIgnoreCase(command.trim()) ? "zpe --help" : command;
  }

  private void stopActiveCommand() {
    Process running = activeProcess;
    if (running != null && running.isAlive()) {
      running.destroyForcibly();
      append("[Process stopped]\n");
    }
  }

  /** Sends terminal-style control signals to the shell and the command it launched. */
  private void sendSignal(String signal) {
    Process running = activeProcess;
    if (running == null || !running.isAlive()) return;
    if (isWindows()) {
      if ("INT".equals(signal)) running.destroy();
      return;
    }
    List<ProcessHandle> targets = new ArrayList<>(running.descendants().toList());
    targets.add(running.toHandle());
    for (ProcessHandle target : targets) {
      try {
        new ProcessBuilder("/bin/kill", "-" + signal, Long.toString(target.pid())).start();
      } catch (IOException ignored) { }
    }
  }

  private void closeProcessInput() {
    BufferedWriter input = activeInput;
    if (input == null) return;
    try {
      input.close();
      activeInput = null;
    } catch (IOException ignored) { }
  }

  private void submitProcessInput() {
    BufferedWriter input = activeInput;
    if (input == null) return;
    String line = currentInput();
    line = stripInteractivePrompt(line);
    append("\n");
    inputStart = transcript.getLength();
    try {
      input.write(line);
      input.newLine();
      input.flush();
    } catch (IOException exception) {
      append("[Could not write to process]\n");
      inputStart = transcript.getLength();
    }
  }

  void clearScreen() {
    internalEdit = true;
    try {
      transcript.clear();
    } finally {
      internalEdit = false;
    }
    appendPrompt();
  }

  private void clear() { clearScreen(); }

  private void showPreviousHistory() {
    if (history.isEmpty()) return;
    historyPosition = Math.max(0, historyPosition - 1);
    replaceCurrentInput(history.get(historyPosition));
  }

  private void showNextHistory() {
    if (history.isEmpty()) return;
    historyPosition = Math.min(history.size(), historyPosition + 1);
    replaceCurrentInput(historyPosition == history.size() ? "" : history.get(historyPosition));
  }

  private void append(String text) {
    internalEdit = true;
    try {
      transcript.appendText(ANSI.matcher(text).replaceAll(""));
      transcript.positionCaret(transcript.getLength());
    } finally {
      internalEdit = false;
    }
  }

  /** Keeps asynchronously arriving output ahead of text the user is currently composing. */
  private void insertProcessOutput(String text) {
    String clean = ANSI.matcher(text).replaceAll("");
    if (clean.isEmpty()) return;
    removeDuplicateInteractivePrompt(clean);
    int insertionPoint = Math.max(0, Math.min(inputStart, transcript.getLength()));
    internalEdit = true;
    try {
      transcript.insertText(insertionPoint, clean);
      inputStart = insertionPoint + clean.length();
    } finally {
      internalEdit = false;
    }
    transcript.positionCaret(transcript.getLength());
  }

  private void removeDuplicateInteractivePrompt(String output) {
    if (!output.contains("zpe > ")) return;
    String pending = currentInput();
    if (!pending.startsWith("zpe > ")) return;
    internalEdit = true;
    try {
      transcript.replaceText(inputStart, transcript.getLength(), pending.substring("zpe > ".length()));
    } finally {
      internalEdit = false;
    }
  }

  private String promptText() {
    Path name = workingDirectory.getFileName();
    return (name == null ? workingDirectory : name) + " $ ";
  }

  private void appendPrompt() {
    append(promptText());
    inputStart = transcript.getLength();
  }

  private void replaceCurrentInput(String value) {
    internalEdit = true;
    try {
      transcript.replaceText(Math.min(inputStart, transcript.getLength()), transcript.getLength(), value);
      transcript.positionCaret(transcript.getLength());
    } finally {
      internalEdit = false;
    }
  }

  private String currentInput() {
    int start = Math.max(0, Math.min(inputStart, transcript.getLength()));
    return transcript.getText(start, transcript.getLength());
  }

  private String stripInteractivePrompt(String value) {
    String result = value;
    while (result.startsWith("zpe > ")) {
      result = result.substring("zpe > ".length());
    }
    return result;
  }

  private void finishCommand() {
    activeProcess = null;
    commandRunning = false;
    stop.setVisible(false);
    stop.setManaged(false);
    appendPrompt();
    focusCommandInput();
  }

  private Path normaliseDirectory(Path path) {
    if (path != null && Files.isDirectory(path)) {
      return path.toAbsolutePath().normalize();
    }
    return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
  }

  private String shellName() {
    return isWindows() ? "Command Prompt" : "system";
  }

  private boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }

  private String unquote(String value) {
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1);
    }
    return value;
  }
}
