package jamiebalfour.zide.editor;

import jamiebalfour.balflaf_fx.BalfTitleBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.skin.TextFieldSkin;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Optional;

public final class ZIDELoginWindow {

  private ZIDELoginWindow() {}

  public static LoginResult show(Stage owner, String service) {

    service = service.trim();

    Stage stage = new Stage();
    stage.initOwner(owner);
    stage.initModality(Modality.WINDOW_MODAL);
    stage.initStyle(StageStyle.UNDECORATED);
    stage.setResizable(false);

    final LoginResult[] result = new LoginResult[1];

    // ---- Titlebar ----
    BalfTitleBar titleBar = new BalfTitleBar(stage, "Sign in to " + service, null, false, false, false, false);
    titleBar.styleProperty().set("-fx-background-color: #517;");
    titleBar.setMinHeight(32);

    // ---- Content ----
    VBox content = new VBox(14);
    content.setPadding(new Insets(18));
    content.getStyleClass().add("about-content");

    Label title = new Label("Login " + service);
    title.getStyleClass().add("about-title");

    Label subtitle = new Label("Enter your credentials");
    subtitle.getStyleClass().add("about-tagline");

    GridPane form = new GridPane();
    form.setHgap(10);
    form.setVgap(10);

    TextField username = new TextField();
    username.setPromptText("Username");
    PasswordField password = new PasswordField();
    password.setPromptText("Password");
    password.setSkin(new TextFieldSkin(password) {
      @Override
      protected String maskText(String text) {
        return "•".repeat(text.length());
      }
    });

    form.add(new Label("Username"), 0, 0);
    form.add(username, 1, 0);
    form.add(new Label("Password"), 0, 1);
    form.add(password, 1, 1);

    Label status = new Label();
    status.setVisible(false);

    Button login = new Button("Login");
    login.getStyleClass().add("accent");

    Button cancel = new Button("Cancel");

    login.setOnAction(e -> {
      if (username.getText().isEmpty() || password.getText().isEmpty()) {
        status.setText("Please enter username and password");
        status.setVisible(true);
        return;
      }

      result[0] = new LoginResult(
              username.getText(),
              password.getText()
      );

      stage.close();
    });

    cancel.setOnAction(e -> {
      result[0] = null;
      stage.close();
    });

    password.setOnAction(e -> login.fire());

    HBox buttons = new HBox(10, cancel, login);
    buttons.setAlignment(Pos.CENTER_RIGHT);

    content.getChildren().addAll(
            title,
            subtitle,
            form,
            status,
            buttons
    );

    VBox root = new VBox(titleBar, content);
    root.getStyleClass().add("about-root");

    Scene scene = new Scene(root, 400, 260);

    stage.setScene(scene);

    stage.setOnShown(e -> username.requestFocus());

    stage.showAndWait();

    return result[0];
  }

  public static class LoginResult {
    private final String username;
    private final String password;

    public LoginResult(String username, String password) {
      this.username = username;
      this.password = password;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
  }
}
