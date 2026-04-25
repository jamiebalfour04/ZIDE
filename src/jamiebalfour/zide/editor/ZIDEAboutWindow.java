package jamiebalfour.zide.editor;

import jamiebalfour.balflaf_fx.BalfTitleBar;
import jamiebalfour.zpe.core.ZPECore;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Objects;

public final class ZIDEAboutWindow {

  private ZIDEAboutWindow() {}

  public static void show(Stage owner) {

    Stage stage = new Stage();
    stage.initOwner(owner);
    stage.initModality(Modality.WINDOW_MODAL);
    stage.initStyle(StageStyle.UNDECORATED);
    stage.setResizable(false);

    // ---- Titlebar ----
    BalfTitleBar titleBar = new BalfTitleBar(stage, "About ZIDE", null, false);

    titleBar.setMinHeight(32);
    // ---- Content ----
    VBox content = new VBox(14);
    content.setPadding(new javafx.geometry.Insets(18, 18, 18, 18));
    content.getStyleClass().add("about-content");

    ImageView icon = new ImageView(
            new Image(Objects.requireNonNull(ZIDEAboutWindow.class.getResourceAsStream("/files/balflaf_fx/icons/jb.png")))
    );
    icon.setFitWidth(64);
    icon.setFitHeight(64);
    icon.setPreserveRatio(true);

    Label name = new Label("ZIDE");
    name.getStyleClass().add("about-title");
    name.setFont(new javafx.scene.text.Font(20));

    Label version = new Label("ZIDE Version " + getVersion());
    version.getStyleClass().add("about-version");

    Label zpe_version = new Label("ZPE Version " + ZPECore.getVersionNumber() + " [" + ZPECore.getVersionName() + "]");
    zpe_version.getStyleClass().add("about-version");


    Label tagline = new Label("A modern IDE for ZPE and YASS.");
    tagline.getStyleClass().add("about-tagline");

    VBox headerText = new VBox(2, name, version, zpe_version, tagline);

    HBox header = new HBox(14, icon, headerText);
    header.setAlignment(Pos.CENTER_LEFT);

    GridPane info = new GridPane();
    info.setHgap(10);
    info.setVgap(6);

    int r = 0;
    addInfo(info, r++, "Java", System.getProperty("java.version"));
    addInfo(info, r++, "JavaFX", System.getProperty("javafx.runtime.version"));
    addInfo(info, r++, "OS",
            System.getProperty("os.name") + " " +
                    System.getProperty("os.version")
    );

    Button ok = new Button("OK");
    ok.getStyleClass().add("accent");
    ok.setOnAction(e -> stage.close());

    HBox buttons = new HBox(ok);
    buttons.setAlignment(Pos.CENTER_RIGHT);

    content.getChildren().addAll(
            header,
            new Separator(),
            info,
            buttons
    );

    // ---- Root ----
    VBox root = new VBox(titleBar, content);
    root.getStyleClass().add("about-root");

    Scene scene = new Scene(root, 420, 280);

    stage.setScene(scene);
    stage.showAndWait();
  }

  private static void addInfo(GridPane gp, int row, String key, String value) {
    Label k = new Label(key);
    k.getStyleClass().add("about-key");

    Label v = new Label(value);
    v.getStyleClass().add("about-val");

    gp.add(k, 0, row);
    gp.add(v, 1, row);
  }

  private static String getVersion() {
    return System.getProperty("zide.version", "Dev");
  }
}
