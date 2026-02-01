package jamiebalfour.zide;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

final class DownloadDialog {

  private final Stage stage;
  private final ProgressBar bar;
  private final Label label;

  public DownloadDialog(Window owner, String title, String message) {
    stage = new Stage();
    stage.initOwner(owner);
    stage.initModality(Modality.WINDOW_MODAL);
    stage.setTitle(title);
    stage.setResizable(false);

    label = new Label(message);
    bar = new ProgressBar();
    bar.setPrefWidth(360);
    bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

    VBox root = new VBox(10, label, bar);
    root.setPadding(new Insets(14));

    stage.setScene(new Scene(root));
  }

  public void show() { stage.show(); }
  public void close() { stage.close(); }

  public void setMessage(String msg) { label.setText(msg); }
  public void setProgress(double p) { bar.setProgress(p); } // 0..1
}
