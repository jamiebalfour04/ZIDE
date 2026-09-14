package jamiebalfour.zide.editor;

import jamiebalfour.zpe.core.IAST;
import jamiebalfour.zpe.core.ZPE;
import jamiebalfour.zpe.core.ZPEKit;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class ZIDEByteCodePanel extends VBox {
  private final Button showButton = new Button("Show Byte Code");
  private final Label message = new Label();
  private final TextArea output = new TextArea();
  private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
    Thread thread = new Thread(task, "zide-bytecode");
    thread.setDaemon(true);
    return thread;
  });
  private final javafx.beans.value.ChangeListener<String> edits = (observable, before, after) -> {
    if (!Objects.equals(before, after)) markStale();
  };
  private EditorTab tab;
  private long request;
  private boolean opened;

  ZIDEByteCodePanel() {
    getStyleClass().add("bytecode-panel");
    showButton.getStyleClass().add("unfold-action-button");
    showButton.setMinWidth(Region.USE_PREF_SIZE);
    showButton.setOnAction(event -> compile());
    message.getStyleClass().add("unfold-availability-message");
    message.setMinWidth(0);
    message.setMaxWidth(Double.MAX_VALUE);
    message.setWrapText(true);
    VBox toolbar = new VBox(7, showButton, message);
    toolbar.setAlignment(Pos.CENTER_LEFT);
    toolbar.getStyleClass().add("unfold-panel-toolbar");
    output.getStyleClass().addAll("code-area", "bytecode-output");
    output.setEditable(false);
    output.setWrapText(false);
    output.setFont(javafx.scene.text.Font.font("Menlo", 12));
    VBox.setVgrow(output, Priority.ALWAYS);
    getChildren().addAll(toolbar, output);
    updateAvailability();
  }

  static boolean supports(EditorTab tab) {
    return tab != null && ("yass".equals(tab.getLanguageId()) || "zpeedy".equals(tab.getLanguageId()));
  }

  boolean isOpen() { return opened; }

  void follow(EditorTab next) {
    if (!opened) return;
    if (tab == next) {
      updateAvailability();
      return;
    }
    detach();
    request++;
    tab = next;
    output.clear();
    showButton.setDisable(false);
    updateAvailability();
    if (supports(tab)) tab.getEditor().getEditor().textProperty().addListener(edits);
  }

  void open(EditorTab selected) {
    opened = true;
    setVisible(true);
    setManaged(true);
    follow(selected);
  }

  void close() {
    if (!opened) return;
    opened = false;
    request++;
    detach();
    tab = null;
    setVisible(false);
    setManaged(false);
  }

  private void detach() {
    if (tab != null && supports(tab)) tab.getEditor().getEditor().textProperty().removeListener(edits);
  }

  private void updateAvailability() {
    boolean supported = supports(tab);
    showButton.setVisible(supported);
    showButton.setManaged(supported);
    message.setText(supported
            ? "Compile the current source to view its ZPE byte code."
            : tab == null
            ? "Open a YASS or Zpeedy file to view its byte code."
            : "Byte code view is available for YASS and Zpeedy files only.");
  }

  private void markStale() {
    request++;
    output.clear();
    showButton.setDisable(false);
    message.setText("Source changed. Show Byte Code to compile the latest version.");
  }

  private void compile() {
    if (!supports(tab)) return;
    EditorTab target = tab;
    String source = target.getEditor().getText();
    String language = target.getLanguageId();
    long version = ++request;
    showButton.setDisable(true);
    message.setText("Compiling byte code…");
    worker.submit(() -> {
      try {
        String byteCode = "yass".equals(language) ? ZPEKit.getCodeTree(source) : compileZpeedy(source);
        Platform.runLater(() -> {
          if (version != request || target != tab || !opened) return;
          output.setText(byteCode.isBlank() ? "No byte code generated." : byteCode);
          message.setText(target.getDisplayTitle());
          showButton.setDisable(false);
        });
      } catch (Exception | LinkageError failure) {
        Platform.runLater(() -> {
          if (version != request || target != tab || !opened) return;
          output.clear();
          message.setText("Byte code could not be generated.");
          output.setText(failure.getMessage() == null ? failure.toString() : failure.getMessage());
          showButton.setDisable(false);
        });
      }
    });
  }

  private static String compileZpeedy(String source) throws Exception {
    Path directory = Files.createTempDirectory("zide-bytecode-");
    Path input = directory.resolve("source.zps");
    Path compiled = directory.resolve("source.iast");
    Path log = directory.resolve("compiler.log");
    Process process = null;
    try {
      Files.writeString(input, source);
      List<String> command = new ArrayList<>();
      if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) command.addAll(List.of("cmd.exe", "/c"));
      command.addAll(List.of("zpeedy", "--iast", input.toString(), compiled.toString()));
      process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
      if (!process.waitFor(20, TimeUnit.SECONDS)) throw new IOException("Zpeedy compilation timed out.");
      if (process.exitValue() != 0) throw new IOException(Files.readString(log));
      return ZPE.ASTtoString(IAST.fromBytes(Files.readAllBytes(compiled)));
    } finally {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
        process.waitFor();
      }
      Files.deleteIfExists(input);
      Files.deleteIfExists(compiled);
      Files.deleteIfExists(log);
      Files.deleteIfExists(directory);
    }
  }
}
