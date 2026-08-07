package jamiebalfour.zide.editor;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A small JavaFX wrapper around the user's local command shell.  It deliberately
 * keeps a working directory between commands, while each command is executed in
 * its own process so the IDE cannot be held hostage by an interactive shell.
 */
final class ZIDESystemTerminal extends BorderPane {

  private final TextArea transcript = new TextArea();
  private final TextField commandInput = new TextField();
  private final Label prompt = new Label();
  private final List<String> history = new ArrayList<>();
  private int historyPosition = 0;
  private Path workingDirectory;
  private Process activeProcess;

  ZIDESystemTerminal(Path initialDirectory) {
    workingDirectory = normaliseDirectory(initialDirectory);

    getStyleClass().add("terminal-pane");

    transcript.setEditable(false);
    transcript.setWrapText(true);
    transcript.getStyleClass().add("terminal-output");
    transcript.setFocusTraversable(false);

    prompt.getStyleClass().add("terminal-prompt");
    updatePrompt();

    commandInput.getStyleClass().add("terminal-command-input");
    commandInput.setPromptText("Enter a command");
    commandInput.setOnAction(e -> submit());
    commandInput.setOnKeyPressed(e -> {
      if (e.getCode() == KeyCode.UP) {
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

    Button stop = new Button("Stop");
    stop.getStyleClass().add("terminal-stop-button");
    stop.setOnAction(e -> stopActiveCommand());

    HBox inputBar = new HBox(8, prompt, commandInput, stop);
    inputBar.getStyleClass().add("terminal-input-bar");
    inputBar.setPadding(new Insets(7, 10, 7, 10));
    HBox.setHgrow(commandInput, Priority.ALWAYS);

    VBox content = new VBox(transcript, inputBar);
    VBox.setVgrow(transcript, Priority.ALWAYS);
    setCenter(content);

    append("ZIDE system terminal\n");
    append("Working directory: " + workingDirectory + "\n");
    append("Commands are run using your local " + shellName() + " shell. Use cd, ls, git, zpe, etc.\n\n");
  }

  void setWorkingDirectory(Path directory) {
    workingDirectory = normaliseDirectory(directory);
    updatePrompt();
  }

  void focusCommandInput() {
    Platform.runLater(commandInput::requestFocus);
  }

  private void submit() {
    String command = commandInput.getText().trim();
    commandInput.clear();
    if (command.isEmpty()) return;

    append(prompt.getText() + " " + command + "\n");
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
      return;
    }
    if (command.equalsIgnoreCase("help") || command.equalsIgnoreCase("zide-help")) {
      append("This terminal runs local system commands. Built-ins: cd [directory], pwd, clear, help.\n");
      append("Examples: ls, git status, zpe --version, java --version.\n");
      return;
    }
    if (isChangeDirectory(command)) {
      changeDirectory(command);
      return;
    }

    runSystemCommand(command);
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
    updatePrompt();
  }

  private void runSystemCommand(String command) {
    commandInput.setDisable(true);

    Task<Integer> task = new Task<>() {
      @Override
      protected Integer call() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(shellCommand(command));
        builder.directory(workingDirectory.toFile());
        builder.redirectErrorStream(true);
        activeProcess = builder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                activeProcess.getInputStream(), StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            String outputLine = line;
            Platform.runLater(() -> append(outputLine + "\n"));
          }
        }

        return activeProcess.waitFor();
      }
    };

    task.setOnSucceeded(e -> {
      Integer exitCode = task.getValue();
      if (exitCode != null && exitCode != 0) append("[Process exited with code " + exitCode + "]\n");
      activeProcess = null;
      commandInput.setDisable(false);
      commandInput.requestFocus();
    });
    task.setOnFailed(e -> {
      Throwable error = task.getException();
      append("Unable to run command: " + (error == null ? "unknown error" : error.getMessage()) + "\n");
      activeProcess = null;
      commandInput.setDisable(false);
      commandInput.requestFocus();
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

  private void stopActiveCommand() {
    Process running = activeProcess;
    if (running != null && running.isAlive()) {
      running.destroyForcibly();
      append("[Process stopped]\n");
    }
  }

  private void clear() {
    transcript.clear();
  }

  private void showPreviousHistory() {
    if (history.isEmpty()) return;
    historyPosition = Math.max(0, historyPosition - 1);
    commandInput.setText(history.get(historyPosition));
    commandInput.positionCaret(commandInput.getText().length());
  }

  private void showNextHistory() {
    if (history.isEmpty()) return;
    historyPosition = Math.min(history.size(), historyPosition + 1);
    commandInput.setText(historyPosition == history.size() ? "" : history.get(historyPosition));
    commandInput.positionCaret(commandInput.getText().length());
  }

  private void append(String text) {
    transcript.appendText(text);
    transcript.positionCaret(transcript.getLength());
  }

  private void updatePrompt() {
    Path name = workingDirectory.getFileName();
    prompt.setText((name == null ? workingDirectory : name) + " $");
  }

  private Path normaliseDirectory(Path path) {
    if (path != null && Files.isDirectory(path)) return path.toAbsolutePath().normalize();
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
