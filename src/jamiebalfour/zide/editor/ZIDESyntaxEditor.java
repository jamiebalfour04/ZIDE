package jamiebalfour.zide.editor;

import jamiebalfour.HelperFunctions;
import jamiebalfour.codeeditor.CodeEditorView;
import jamiebalfour.zpe.core.ZPEHelperFunctions;
import jamiebalfour.zpe.core.ZPEKit;
import jamiebalfour.zpe.gui.YASSCodeEditor;
import javafx.application.Platform;

import javax.swing.Timer;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ZIDESyntaxEditor extends YASSCodeEditor {

  ZIDEEditor owner;
  private List<FunctionHint> customFunctions = new ArrayList<>();
  private Timer symbolTimer;
  private volatile int symbolVersion = 0;

  public ZIDESyntaxEditor(jamiebalfour.zide.editor.ZIDEEditor editor, boolean wordWrap) {
    super(wordWrap);
    owner = editor;

  }




}
