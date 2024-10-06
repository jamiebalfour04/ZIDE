import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.*;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.Timer;
import jamiebalfour.HelperFunctions;
import jamiebalfour.zpe.core.*;
import jamiebalfour.zpe.editor.CodeEditorView;
import jamiebalfour.zpe.editor.CodeEditorView.ATTR_TYPE;
import jamiebalfour.zpe.exceptions.CompileException;
import jamiebalfour.zpe.exceptions.InternalException;
import jamiebalfour.zpe.exceptions.TranspilerNotFoundException;
import jamiebalfour.zpe.exceptions.ZPERuntimeException;
import jamiebalfour.zpe.interfaces.GenericEditor;
import jamiebalfour.zpe.interfaces.ZPEException;
import jamiebalfour.zpe.interfaces.ZPEPropertyWrapper;
import jamiebalfour.zpe.interfaces.ZPEType;
import jamiebalfour.zpe.os.macos.macOS;
import jamiebalfour.zpe.types.ZPEBoolean;
import jamiebalfour.zpe.types.ZPEList;
import jamiebalfour.zpe.types.ZPEMap;
import jamiebalfour.zpe.types.ZPEString;
import jamiebalfour.zpe.zen.ZENServer;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.text.*;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.Dialog.ModalityType;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.print.PrinterException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.*;
import java.util.List;
import java.util.Timer;
import java.util.*;

  class ZIDEEditorMain extends JFrame implements GenericEditor {

    private static final long serialVersionUID = -8251993208396962207L;
    static FileNameExtensionFilter filter1 = new FileNameExtensionFilter("YASS files (*.yas)", "yas");
    static FileNameExtensionFilter filter2 = new FileNameExtensionFilter("Text files (*.txt)", "txt");
    static FileNameExtensionFilter filter3 = new FileNameExtensionFilter("YASS executable files (*.yex)", "yex");
    private static JWindow tooltipWindow;
    private final Map<String, String> autoCompleteSuggestionTypes = new HashMap<>();
    protected UndoHandler undoHandler = new UndoHandler();
    protected UndoManager undoManager = new UndoManager();
    ZIDEEditorMain _this = this;
    JEditorPane contentEditor;
    Properties mainProperties;
    boolean propertiesChanged = false;
    String login_username = "";
    String login_password = "";
    String lastCloudFileOpened = "";
    String lastFileOpened = "";
    JMenuItem mainLoginMenuItem;
    JMenu mnRecentOnlineFilesMenu;
    JSeparator zpeOnlineSeparator;
    JMenuItem mntmLoadFromZPEOnlineMenuItem;
    JMenuItem mntmSaveToZPEOnlineMenuItem;
    JMenuItem mntmLoginToZPEOnlineMenuItem;
    JMenu mnFontFamilyMenu;
    JCheckBoxMenuItem mnDarkModeMenuItem;
    JPanel mainPanel;
    JCheckBoxMenuItem mnUseMacMenuBarMenuItem;
    JMenuItem mntmUncommentLinesMenuItem;
    JMenuItem mntmCommentOutLineMenuItem;
    JScrollPane scrollPane;
    CodeEditorView mainSyntax;
    String fontsAllowed = "Arial,Calibri,Comic Sans MS,Consolas,Courier New,Garamond,Lucida Sans,Menlo,Monospaced,Quicksand Light,Times New Roman";
    String defaultFontFamily;
    boolean dontUndo = true;
    boolean darkMode = false;
    ZPEString[] runtimeArgs = new ZPEString[0];
    JCheckBoxMenuItem chckbxmntmFontSizeSmallCheckItem;
    JCheckBoxMenuItem chckbxmntmFontSizeNormalCheckItem;
    JCheckBoxMenuItem chckbxmntmFontSizeLargeCheckItem;
    JCheckBoxMenuItem chckbxmntmFontSizeExtraLargeCheckItem;
    JCheckBoxMenuItem chckbxmntmCaseSensitiveCompileCheckItem;
    JCheckBoxMenuItem mntmMaximiseMenuItem;
    JMenuItem mntmRecentMenuItem;
    Process currentProcess;
    JMenuItem mntmStopCodeMenuItem;
    ImageIcon lighterLogo;
    ImageIcon lighterLogoFull;
    int permission_level = 3;
    ArrayList<String> recents = ZPEEditor.getRecentFiles("");
    String lastHoveredWord = "";
    Point lastPosition = new Point(0, 0);
    JWindow autoCompletePopup = new JWindow();
    private JFrame editor;
    private UndoAction undoAction = null;
    private RedoAction redoAction = null;
    private int currentAutoCompletePosition = 0;
    private java.util.List<String> autoCompleteSuggestionWords;
    private String previousAutoCompleteWord = "";  // Store the last word to compare with the current word
    private java.util.Timer autoCompleteDebounceTimer;
    //private Color autoCompleteItemForeColor = new Color(31, 31, 31, 255);
    private Color autoCompleteItemBackgroundColor = new Color(241, 241, 241, 255);
    private java.util.Timer showTooltipTimer;
    private java.util.Timer hideTooltipTimer;
    private final ArrayList<JFrame> currentWindows = new ArrayList<>();

    public ZIDEEditorMain() {
      this.permission_level = RunningInstance.getUserDefinedPermissionLevelProperty();
      URL imagePath;
      if (HelperFunctions.isMac()) {
        imagePath = ZPEEditorMain.class.getResource("/files/ZPELogomacOS.png");
      } else {
        imagePath = ZPEEditorMain.class.getResource("/files/ZPELogoLighter.png");
      }
      assert imagePath != null;
      lighterLogoFull = new ImageIcon(imagePath);
      Image newimg = lighterLogoFull.getImage().getScaledInstance(60, 60, java.awt.Image.SCALE_SMOOTH); // scale it the smooth way
      lighterLogo = new ImageIcon(newimg);
    }

    public ZIDEEditorMain(int permission_level) {
      this.permission_level = permission_level;
      URL imagePath;
      if (HelperFunctions.isMac()) {
        imagePath = ZPEEditorMain.class.getResource("/files/ZPELogomacOS.png");
      } else {
        imagePath = ZPEEditorMain.class.getResource("/files/ZPELogoLighter.png");
      }
      assert imagePath != null;
      lighterLogoFull = new ImageIcon(imagePath);
      Image newimg = lighterLogoFull.getImage().getScaledInstance(60, 60, java.awt.Image.SCALE_SMOOTH); // scale it the smooth way
      lighterLogo = new ImageIcon(newimg);
    }

    public ZIDEEditorMain(String name, String file) throws IOException {
      super(name);
      URL imagePath;
      if (HelperFunctions.isMac()) {
        imagePath = ZIDEEditorMain.class.getResource("/files/ZPELogomacOS.png");
      } else {
        imagePath = ZIDEEditorMain.class.getResource("/files/ZPELogoLighter.png");
      }
      assert imagePath != null;
      lighterLogoFull = new ImageIcon(imagePath);
      Image newimg = lighterLogoFull.getImage().getScaledInstance(60, 60, java.awt.Image.SCALE_SMOOTH); // scale it the smooth way
      lighterLogo = new ImageIcon(newimg);

      final HashMap<String, SimpleAttributeSet> YASSKeywords = new HashMap<>(16);

      for (String type : ZPEKit.getTypeKeywords()) {
        YASSKeywords.put(type, CodeEditorView.DEFAULT_TYPE);
      }

      for (String keyword : ZPEKit.getKeywords()) {
        YASSKeywords.put(keyword, CodeEditorView.DEFAULT_KEYWORD);
      }

      YASSKeywords.put("null", CodeEditorView.DEFAULT_NULL);
      YASSKeywords.put("NULL", CodeEditorView.DEFAULT_NULL);

        /*for(String keySymb : ZPEKit.getKeySymbols()){
            YASSKeywords.put(keySymb, CodeEditorView.DEFAULT_KEYWORD);
        }*/


      YASSKeywords.put("+", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put("-", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put("^", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put("=", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put("==", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put("!", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put("<", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put(">", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put("<=", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put(">=", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put("!=", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put("+=", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put("-=", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put("*=", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put("/=", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put("^=", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put("&", CodeEditorView.DEFAULT_KEYWORD);
      YASSKeywords.put("|", CodeEditorView.DEFAULT_KEYWORD);

      YASSKeywords.put("#breakpoint#", CodeEditorView.DEFAULT_SPECIAL);

      YASSKeywords.put("true", CodeEditorView.DEFAULT_BOOLEAN);
      YASSKeywords.put("false", CodeEditorView.DEFAULT_BOOLEAN);

      YASSKeywords.put("this", CodeEditorView.DEFAULT_VAR);

      for (String directive : ZPEKit.getDirectiveKeywords()) {
        YASSKeywords.put(directive, CodeEditorView.DEFAULT_DOC);
      }


      for (String cmd : jamiebalfour.zpe.core.ZPEKit.getAllCommands()) {
        YASSKeywords.put(cmd, CodeEditorView.DEFAULT_PREDEFINED_FUNCTION);
      }
      for (String cmd : jamiebalfour.zpe.core.ZPEKit.getAllAliases()) {
        YASSKeywords.put(cmd, CodeEditorView.DEFAULT_PREDEFINED_FUNCTION);
      }

      this.mainSyntax = new CodeEditorView(YASSKeywords, "\"'", "$");


      StringBuilder supportedFontString = new StringBuilder();
      ArrayList<String> supportedFontsList = new ArrayList<>();
      for (String s : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
        if (fontsAllowed.contains(s)) {
          supportedFontString.append(s).append(",");
          supportedFontsList.add(s);
        }
      }

      if (supportedFontString.toString().contains("Menlo")) {
        defaultFontFamily = "Menlo";
      } else {
        defaultFontFamily = "Courier New";
      }

      if (getVersion() < 9) {
        System.err.println("You must be running Java 9 or higher to open the GUI.");
        return;
      }

      mainSyntax.setFontSize(18);
      // System.setProperty("com.apple.mrj.application.apple.menu.about.name", name);

      addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent e) {

          int confirmed = JOptionPane.showConfirmDialog(editor, "Are you sure you want to exit the program?", "Exit Program Message Box", JOptionPane.YES_NO_OPTION);
          if (confirmed == JOptionPane.YES_OPTION) {
            dispose();
          }

          closeUp();

          System.exit(0);
        }
      });

      //Set the icon for the window

      try {
        setIconImage(lighterLogoFull.getImage());
      } catch (Exception ignored) {

      }


      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception e2) {
        //Ignore
      }

      if (HelperFunctions.isMac()) {
        //Mac supports a special About dialong in the ZPE menu item
        try {
          macOS a = new macOS();
          a.addAboutDialog(this::showAbout);
        } catch (Exception e) {
          //Ignore
        }

        try {
          //Attempts to set the icon in the Dock/taskbar
          if (java.awt.Taskbar.isTaskbarSupported()) {
            Toolkit.getDefaultToolkit();
            final java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
            final Image image = lighterLogoFull.getImage();

            taskbar.setIconImage(image);
          }
        } catch (Exception e) {
          //Ignore
        }

      }

      this.editor = this;

      this.setMinimumSize(new Dimension(400, 400));

      //Reads the properties for the GUI, and sets new ones if they have not already been created
      String path = System.getProperty("user.home") + "/zpe/" + "gui.properties";

      File f = new File(path);
      if (!f.exists()) {
        saveGUISettings(new Properties());
      }

      mainProperties = HelperFunctions.ReadProperties(path);
      boolean useMacMenuBar = true;

      if (mainProperties.containsKey("USE_MAC_SIMPLEBAR")) {
        useMacMenuBar = !mainProperties.get("USE_MAC_SIMPLEBAR").toString().equals("true");
      }

      //macOS has a different setup because of the way apps are displayed on it.
      if (HelperFunctions.isMac() && useMacMenuBar) {
        System.setProperty("apple.laf.useScreenMenuBar", "true");
      }

      setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
      setTitle("ZPE Editor");
      getContentPane().setLayout(new GridLayout(1, 0, 0, 0));

      mainPanel = new JPanel();
      getContentPane().add(mainPanel);
      mainPanel.setLayout(new BorderLayout(0, 0));

      scrollPane = new JScrollPane();
      scrollPane.setBackground(Color.white);
      scrollPane.setEnabled(false);
      scrollPane.setBorder(BorderFactory.createEmptyBorder());
      scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

      scrollPane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
        @Override
        public void adjustmentValueChanged(AdjustmentEvent e) {
          hideTooltip();
        }
      });

      setUpScrollBar(scrollPane, "#dddddd", "#aaaaaa", 7);

      JEditorPane mainEditor = (JEditorPane) mainSyntax.getEditPane();
      mainEditor.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));

      mainEditor.setFont(new Font(defaultFontFamily, Font.PLAIN, 18));
      scrollPane.setViewportView(mainEditor);


      //if(ZPEHelperFunctions.isWindows()) {
      scrollPane.setRowHeaderView(mainSyntax.getEditor());
      //} else {
      //	scrollPane.setRowHeaderView(mainEditor);
      //}

      mainPanel.add(scrollPane, BorderLayout.CENTER);

      this.contentEditor = mainEditor;

      JMenuBar menuBar = new JMenuBar();
      /*menuBar.setBackground(new Color(218, 20, 76));*/
      setJMenuBar(menuBar);

      JMenu mnFileMenu = new JMenu("File");
      mnFileMenu.setMnemonic('F');
      menuBar.add(mnFileMenu);

      JMenuItem mntmNewMenuItem = new JMenuItem("New");
      mntmNewMenuItem.setAccelerator(KeyStroke.getKeyStroke('N', HelperFunctions.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK));
      mntmNewMenuItem.addActionListener(e -> {
        clearUndoRedoManagers();
        setTextProperly("");
        lastCloudFileOpened = "";
      });
      mnFileMenu.add(mntmNewMenuItem);


      JMenuItem mntmSaveMenuItem = new JMenuItem("Save");
      mntmSaveMenuItem.setAccelerator(KeyStroke.getKeyStroke('S', HelperFunctions.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK));
      mntmSaveMenuItem.addActionListener(e -> {
        if (lastFileOpened.isEmpty()) {
          saveAsDialog();
        } else {
          try {
            HelperFunctions.WriteFile(lastFileOpened, contentEditor.getText(), false);
          } catch (IOException ex) {
            ZPE.Log(ex.getMessage());
          }
        }
      });
      mnFileMenu.add(mntmSaveMenuItem);

      JMenuItem mntmSaveAsMenuItem = new JMenuItem("Save As");
      mntmSaveAsMenuItem.addActionListener(e -> saveAsDialog());
      mnFileMenu.add(mntmSaveAsMenuItem);

      mnFileMenu.add(new JSeparator());

      JMenuItem mntmOpenMenuItem = new JMenuItem("Open");
      mntmOpenMenuItem.setAccelerator(KeyStroke.getKeyStroke('O', HelperFunctions.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK));
      mntmOpenMenuItem.addActionListener(e -> open());
      mnFileMenu.add(mntmOpenMenuItem);

      mntmRecentMenuItem = new JMenu("Recent files");

      updateRecentFiles();

      if (!recents.isEmpty()) {
        mnFileMenu.add(mntmRecentMenuItem);
      }

      mnFileMenu.add(new JSeparator());

      JMenuItem mntmPrintMenuItem = new JMenuItem("Print");
      mntmPrintMenuItem.setAccelerator(KeyStroke.getKeyStroke('P', HelperFunctions.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK));
      mntmPrintMenuItem.addActionListener(e -> {
        try {
          contentEditor.print();
        } catch (PrinterException e1) {
          JOptionPane.showMessageDialog(editor, "An error was encountered whilst trying to print.", "Error",
                  JOptionPane.ERROR_MESSAGE);
        }
      });
      mnFileMenu.add(mntmPrintMenuItem);

      mnFileMenu.add(new JSeparator());

      JMenuItem mntmExitMenuItem = new JMenuItem("Exit");
      mntmExitMenuItem.addActionListener(e -> {
        closeUp();
        System.exit(0);

      });
      mnFileMenu.add(mntmExitMenuItem);

      JMenu mnEditMenu = new JMenu("Edit");
      mnEditMenu.addMenuListener(new MenuListener() {
        public void menuCanceled(MenuEvent e) {
        }

        public void menuDeselected(MenuEvent e) {
        }

        public void menuSelected(MenuEvent e) {
          if (contentEditor.getSelectedText() != null && !contentEditor.getSelectedText().isEmpty()) {
            mntmCommentOutLineMenuItem.setEnabled(true);
            mntmUncommentLinesMenuItem.setEnabled(contentEditor.getSelectedText().startsWith("//"));
          } else {
            mntmCommentOutLineMenuItem.setEnabled(false);
            mntmUncommentLinesMenuItem.setEnabled(false);
          }
        }
      });
      mnEditMenu.setMnemonic('E');
      menuBar.add(mnEditMenu);

      // Undo and redo functionality

      mainEditor.getDocument().addUndoableEditListener(undoHandler);

      KeyStroke undoKeystroke = KeyStroke.getKeyStroke(KeyEvent.VK_Z, HelperFunctions.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK);
      KeyStroke redoKeystroke = KeyStroke.getKeyStroke(KeyEvent.VK_Y, HelperFunctions.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK);

      undoAction = new UndoAction();
      contentEditor.getInputMap().put(undoKeystroke, "undoKeystroke");
      contentEditor.getActionMap().put("undoKeystroke", undoAction);

      redoAction = new RedoAction();
      contentEditor.getInputMap().put(redoKeystroke, "redoKeystroke");
      contentEditor.getActionMap().put("redoKeystroke", redoAction);

      JMenuItem mntmUndoMenuItem = new JMenuItem(undoAction);
      //mntmUndoMenuItem.setAccelerator(KeyStroke.getKeyStroke('Z', HelperFunctions.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK));

      mnEditMenu.add(mntmUndoMenuItem);

      JMenuItem mntmRedoMenuItem = new JMenuItem(redoAction);
      //mntmRedoMenuItem.setAccelerator(KeyStroke.getKeyStroke('Y', HelperFunctions.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK));
      mnEditMenu.add(mntmRedoMenuItem);

      mnEditMenu.add(new JSeparator());

      JMenuItem mntmCutMenuItem = new JMenuItem("Cut");
      mntmCutMenuItem.setAccelerator(KeyStroke.getKeyStroke('X', HelperFunctions.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK));
      mntmCutMenuItem.addActionListener(e -> contentEditor.cut());
      mnEditMenu.add(mntmCutMenuItem);

      JMenuItem mntmCopyMenuItem = new JMenuItem("Copy");
      mntmCopyMenuItem.setAccelerator(KeyStroke.getKeyStroke('C', HelperFunctions.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK));
      mntmCopyMenuItem.addActionListener(e -> contentEditor.copy());
      mnEditMenu.add(mntmCopyMenuItem);

      JMenuItem mntmPasteMenuItem = new JMenuItem("Paste");
      mntmPasteMenuItem.setAccelerator(KeyStroke.getKeyStroke('V', HelperFunctions.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK));
      mntmPasteMenuItem.addActionListener(e -> contentEditor.paste());
      mnEditMenu.add(mntmPasteMenuItem);

      JMenuItem mntmDeleteMenuItem = new JMenuItem("Delete");
      mntmDeleteMenuItem.addActionListener(e -> {
        int start = contentEditor.getSelectionStart();
        int end = contentEditor.getSelectionEnd();

        String current = contentEditor.getText();

        String newText = current.substring(0, start) + current.substring(end);
        contentEditor.setText(newText);

      });
      mnEditMenu.add(mntmDeleteMenuItem);

      mnEditMenu.add(new JSeparator());

      JMenuItem mntmSelectAllMenuItem = new JMenuItem("Select All");
      mntmSelectAllMenuItem.setAccelerator(KeyStroke.getKeyStroke('A', HelperFunctions.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK));
      mntmSelectAllMenuItem.addActionListener(e -> contentEditor.selectAll());

      mnEditMenu.add(mntmSelectAllMenuItem);


      mnEditMenu.add(new JSeparator());

      JMenuItem mntmAddBreakpointMenuItem = new JMenuItem("Add breakpoint");
      mntmAddBreakpointMenuItem.setAccelerator(KeyStroke.getKeyStroke('B', HelperFunctions.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK));
      mntmAddBreakpointMenuItem.addActionListener(e -> {

      });


      mntmCommentOutLineMenuItem = new JMenuItem("Comment selected lines");
      mntmCommentOutLineMenuItem.setAccelerator(KeyStroke.getKeyStroke('/', HelperFunctions.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK));
      mntmCommentOutLineMenuItem.addActionListener(e -> {
        int start = contentEditor.getSelectionStart();
        int end = contentEditor.getSelectionEnd();


        String content = contentEditor.getText();
        StringBuilder out = new StringBuilder();

        while (start > 0 && content.charAt(start) != '\n') {
          start--;
        }

        if (start == 0) {
          out.append("//");
        }
        for (int i = start; i < end; i++) {

          out.append(content.charAt(i));
          if (content.charAt(i) == '\n') {
            out.append("//");
          }
        }

        setTextProperly(content.substring(0, start) + out + content.substring(end));
      });

      mnEditMenu.add(mntmCommentOutLineMenuItem);

      mntmUncommentLinesMenuItem = new JMenuItem("Uncomment selected lines");
      mntmUncommentLinesMenuItem.setAccelerator(KeyStroke.getKeyStroke('\\', HelperFunctions.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK));
      mntmUncommentLinesMenuItem.addActionListener(e -> {
        int start = contentEditor.getSelectionStart();
        int end = contentEditor.getSelectionEnd();

        String content = contentEditor.getText();
        String code = content.substring(start, end);

        while (start > 0 && content.charAt(start) != '\n') {
          start--;
        }

        StringBuilder out = new StringBuilder();

        Scanner s = new Scanner(code);

        while (s.hasNextLine()) {
          String line = s.nextLine();
          while (line.startsWith("//")) {
            line = line.substring(2);
          }
          out.append(line);

          if (s.hasNextLine()) {
            out.append(System.lineSeparator());
          }

        }

        s.close();


        setTextProperly(content.substring(0, start) + System.lineSeparator() + out + content.substring(end));
      });

      mnEditMenu.add(mntmUncommentLinesMenuItem);

      JMenu mnViewMenu = new JMenu("View");
      mnViewMenu.setMnemonic('V');
      menuBar.add(mnViewMenu);


      mnViewMenu.add(new JSeparator());

      JMenu mnFontSizeMenu = new JMenu("Font size");
      mnViewMenu.add(mnFontSizeMenu);

      chckbxmntmFontSizeSmallCheckItem = new JCheckBoxMenuItem("Small");
      chckbxmntmFontSizeSmallCheckItem.addActionListener(e -> {
        changeFontSize(-1);
        setProperty("FONT_SIZE", "" + -1);
        saveGUISettings(mainProperties);
      });

      mnFontSizeMenu.add(chckbxmntmFontSizeSmallCheckItem);

      chckbxmntmFontSizeNormalCheckItem = new JCheckBoxMenuItem("Normal");
      chckbxmntmFontSizeNormalCheckItem.addActionListener(e -> {
        changeFontSize(0);
        setProperty("FONT_SIZE", "" + 0);
        saveGUISettings(mainProperties);
      });
      chckbxmntmFontSizeNormalCheckItem.setSelected(true);
      mnFontSizeMenu.add(chckbxmntmFontSizeNormalCheckItem);

      chckbxmntmFontSizeLargeCheckItem = new JCheckBoxMenuItem("Large");
      chckbxmntmFontSizeLargeCheckItem.addActionListener(e -> {
        changeFontSize(1);
        setProperty("FONT_SIZE", "" + 1);
        saveGUISettings(mainProperties);
      });
      mnFontSizeMenu.add(chckbxmntmFontSizeLargeCheckItem);

      chckbxmntmFontSizeExtraLargeCheckItem = new JCheckBoxMenuItem("Extra Large");
      chckbxmntmFontSizeExtraLargeCheckItem.addActionListener(e -> {
        changeFontSize(2);
        setProperty("FONT_SIZE", "" + 2);
        saveGUISettings(mainProperties);
      });
      mnFontSizeMenu.add(chckbxmntmFontSizeExtraLargeCheckItem);

      mnFontFamilyMenu = new JMenu("Font family");
      mnViewMenu.add(mnFontFamilyMenu);


      JCheckBoxMenuItem defaultFont = new JCheckBoxMenuItem("Default font");
      defaultFont.addActionListener(e -> {
        changeFontFamily(defaultFontFamily);

        setProperty("FONT_FAMILY", defaultFontFamily);
        saveGUISettings(mainProperties);
      });

      mnFontFamilyMenu.add(defaultFont);

      mnFontFamilyMenu.add(new JSeparator());

      for (String s : supportedFontsList) {
        createFontDropdown(s);
      }

      final JCheckBoxMenuItem mntmMakeTextBoldMenuItem = new JCheckBoxMenuItem("Make text bold");
      mntmMakeTextBoldMenuItem.addActionListener(e -> {
        if (mntmMakeTextBoldMenuItem.isSelected()) {
          mainSyntax.makeBold(true);
          setProperty("USE_BOLD_TEXT", "true");
          resetScroll();
        } else {
          mainSyntax.makeBold(false);
          setProperty("USE_BOLD_TEXT", "false");
          resetScroll();
        }


        saveGUISettings(mainProperties);
        updateEditor();
      });

      mnViewMenu.add(mntmMakeTextBoldMenuItem);


      mnViewMenu.add(new JSeparator());

      mnDarkModeMenuItem = new JCheckBoxMenuItem("Dark Mode");
      mnViewMenu.add(mnDarkModeMenuItem);
      mnDarkModeMenuItem.addActionListener(e -> {
        if (!darkMode) {
          switchOnDarkMode();
        } else {
          switchOffDarkMode();
        }

        setProperty("DARK_MODE", "" + darkMode);
        saveGUISettings(mainProperties);

        updateEditor();
      });

      if (HelperFunctions.isMac()) {
        mnUseMacMenuBarMenuItem = new JCheckBoxMenuItem("Use Mac Menubar");


        mnViewMenu.add(mnUseMacMenuBarMenuItem);

        final boolean macMenuBarEnabled = useMacMenuBar;

        mnUseMacMenuBarMenuItem.addActionListener(e -> {
          if (macMenuBarEnabled) {
            setProperty("USE_MAC_SIMPLEBAR", "true");
          } else {
            setProperty("USE_MAC_SIMPLEBAR", "false");
          }
          mnUseMacMenuBarMenuItem.setEnabled(false);
          saveGUISettings(mainProperties);
          JOptionPane.showMessageDialog(editor, "This will happen on the next start.", "macOS Menubar",
                  JOptionPane.WARNING_MESSAGE);
        });

        if (useMacMenuBar) {
          mnUseMacMenuBarMenuItem.setSelected(true);
        }
      }


      mnViewMenu.add(new JSeparator());

      JMenu mnWindowMenu = new JMenu("Window");
      mnWindowMenu.setMnemonic('W');
      mnViewMenu.add(mnWindowMenu);

      mntmMaximiseMenuItem = new JCheckBoxMenuItem("Maximise");

      if (HelperFunctions.isMac()) {
        mntmMaximiseMenuItem.setText("Zoom");
      }

      mntmMaximiseMenuItem.addActionListener(e -> {
        if (editor.getExtendedState() != JFrame.MAXIMIZED_BOTH) {
          editor.setExtendedState(JFrame.MAXIMIZED_BOTH);
          mntmMaximiseMenuItem.setSelected(true);
        } else {
          editor.setExtendedState(JFrame.NORMAL);
          editor.setSize(new Dimension(400, 400));
          mntmMaximiseMenuItem.setSelected(false);
        }

      });
      mnWindowMenu.add(mntmMaximiseMenuItem);

      JMenuItem mntmFullScreenMenuItem = new JMenuItem("Full screen");
      mntmFullScreenMenuItem.addActionListener(e -> {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        if (gd.isFullScreenSupported()) {

          try {
            // _this.setUndecorated(false);
            gd.setFullScreenWindow(_this);
          } catch (Exception e1) {
            // gd.setFullScreenWindow(null);
          }
        } else {
          System.err.println("Full screen not supported");
        }

      });
      //mnWindowMenu.add(mntmFullScreenMenuItem);

      JMenu mnToolsMenu = new JMenu("Tools");
      mnToolsMenu.setMnemonic('T');
      menuBar.add(mnToolsMenu);

      JMenuItem mntmSetDefaultFileMenuItem = new JMenuItem("Set default file");
      mntmSetDefaultFileMenuItem.addActionListener(e -> {
        final JFileChooser fc = new JFileChooser();

        fc.addChoosableFileFilter(filter1);
        fc.addChoosableFileFilter(filter2);

        int returnVal = fc.showOpenDialog(editor.getContentPane());

        if (returnVal == JFileChooser.APPROVE_OPTION) {
          File file13 = fc.getSelectedFile();
          // This is where a real application would open the file.
          if (file13.exists()) {
            setProperty("DEFAULT_FILE", file13.getAbsolutePath());
            saveGUISettings(mainProperties);
          } else {
            JOptionPane.showMessageDialog(editor, "The file could not be opened.", "Error",
                    JOptionPane.ERROR_MESSAGE);
          }
        }

      });

      mnToolsMenu.add(mntmSetDefaultFileMenuItem);

      JMenuItem mntmSetRuntimeArgumentsMenuItem = new JMenuItem("Set runtime arguments");
      mntmSetRuntimeArgumentsMenuItem.addActionListener(e -> {
        StringBuilder joined = new StringBuilder();
        for (Object s : runtimeArgs) {
          joined.append(" ").append(s.toString());
        }
        String args = JOptionPane.showInputDialog(editor,
                "Please insert your runtime arguments, separated by spaces.", joined.toString());
        ZPEString[] new_args = new ZPEString[args.split(" ").length];
        int i = 0;
        for (String s : args.split((" "))) {
          new_args[i] = new ZPEString(s);
          i++;
        }
        runtimeArgs = new_args;
      });
      mnToolsMenu.add(mntmSetRuntimeArgumentsMenuItem);

      mnToolsMenu.add(new JSeparator());

      JMenuItem mntmOpenMacroEditor = new JMenuItem("Open ZPE Macro Interface Editor");


      mntmOpenMacroEditor.addActionListener(e -> {
        ZPERuntimeEnvironment z = new ZPERuntimeEnvironment();
        ZPEMacroInterface i = new ZPEMacroInterface(z, new EditorObject(z, ZPEKit.getGlobalFunction(z), mainEditor));
        i.setVisible(true);
        i.setLocationRelativeTo(_this);
        currentWindows.add(i);
      });

      mnToolsMenu.add(mntmOpenMacroEditor);

      mnToolsMenu.add(new JSeparator());

      JMenuItem mntmStartZENServerMenuItem = new JMenuItem("Start ZEN Server");
      mntmStartZENServerMenuItem.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {

          ZPEEditorZENServerUI z = new ZPEEditorZENServerUI(editor);
          Map<String, String> output = z.display();
          final int port = HelperFunctions.stringToInteger(output.get("port"));
          final int permission = HelperFunctions.stringToInteger(output.get("permission"));
          final String password = output.get("password");

          String additional = "";
          try {
            additional = " on " + InetAddress.getLocalHost();

          } catch (UnknownHostException e1) {
            //Ignore
          }

          JOptionPane.showMessageDialog(editor,
                  "Opening ZENServer on port " + port + additional,
                  "ZENServer", JOptionPane.INFORMATION_MESSAGE);
          _this.setVisible(false);

          class backgroundServer implements Runnable {

            int port;
            int permission;
            String password;

            Thread thread;

            @Override
            public void run() {
              thread = new Thread(this, "ZENServer");
              thread.setDaemon(true);
              thread.start();


            }

            public void start() {
              //Finally, open the server
              ZENServer s = new ZENServer();
              s.Start(port, permission, password, null);
            }
          }


          SwingUtilities.invokeLater(() -> {
            backgroundServer s = new backgroundServer();
            s.port = port;
            s.permission = permission;
            s.password = password;
            s.start();
          });


        }
      });

      mnToolsMenu.add(mntmStartZENServerMenuItem);

      JMenu currentMenu = null;
      String lastCategory = "";

      JMenu mnCommandsMenu = new JMenu("Functions");
      mnCommandsMenu.setMnemonic('F');


      menuBar.add(mnCommandsMenu);


      for (String s : ZPEKit.getAllCommands()) {
        if (!(lastCategory.equals(ZPEKit.getFunctionCategory(s)))) {

          if (currentMenu != null) {
            mnCommandsMenu.add(currentMenu);
          }

          lastCategory = ZPEKit.getFunctionCategory(s);

          currentMenu = new JMenu();
          currentMenu.setText(lastCategory);
        }

        JMenuItem commandMenuItem = new JMenuItem();
        // So that underscores work
        commandMenuItem.setText(s);

        final String replacement = ZPEKit.getFunctionManualHeader(s);// .replaceAll("\\{[^\\}]+\\} ", "");
        final String group = lastCategory;
        final String helpName = s;
        commandMenuItem.addActionListener(e -> {
          // contentEditor.replaceSelection(replacement);


          JOptionPane op = new JOptionPane(
                  new ZPEFunctionGenerator(helpName, group, replacement, _this).getContentPane(),
                  JOptionPane.INFORMATION_MESSAGE, JOptionPane.DEFAULT_OPTION, lighterLogo,
                  new String[]{});

          JDialog dlg = op.createDialog(editor, "ZPE Function Machine");

          dlg.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);

          dlg.setVisible(true);

        });

        assert currentMenu != null;
        currentMenu.add(commandMenuItem);


      }

      JMenu mnScriptMenu = new JMenu("Script");
      mnScriptMenu.setMnemonic('S');
      menuBar.add(mnScriptMenu);

      chckbxmntmCaseSensitiveCompileCheckItem = new JCheckBoxMenuItem("Case sensitive compile");
      chckbxmntmCaseSensitiveCompileCheckItem.setSelected(true);
      mnScriptMenu.add(chckbxmntmCaseSensitiveCompileCheckItem);

      mnScriptMenu.add(new JSeparator());

      JMenuItem mntmRunCodeMenuItem = new JMenuItem("Run code");
      mntmRunCodeMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
      mntmRunCodeMenuItem.addActionListener(e -> {
        ZPEKit.setDebugging(false);
        String t = "ZPE";
        if (!lastFileOpened.isEmpty()) {
          t = "ZPE : " + lastFileOpened;
        } else if (!lastCloudFileOpened.isEmpty()) {
          t = "ZPE : " + lastCloudFileOpened;
        }
        try {
          String extras = "";
          if (chckbxmntmCaseSensitiveCompileCheckItem.isSelected()) {
            extras += " --case_insensitive";
          }
          if (currentProcess != null) {
            currentProcess.destroy();
          }
          HelperFunctions.WriteFile(RunningInstance.getInstallPath() + "/tmp.yas", contentEditor.getText(), false);
          if (!RunningInstance.getJarExecPath().isEmpty()) {
            if (new File(RunningInstance.getJarExecPath()).exists()) {
              currentProcess = Runtime.getRuntime().exec("java -jar " + RunningInstance.getJarExecPath() + " -g " + RunningInstance.getInstallPath() + "/tmp.yas --console" + extras);
              mntmStopCodeMenuItem.setEnabled(true);
              mntmStopCodeMenuItem.setVisible(true);
            }
          }


        } catch (Exception ex) {
          //Ignore
        }

      });
      mnScriptMenu.add(mntmRunCodeMenuItem);

      JMenuItem mntmRunCodeWithDebuggingMenuItem = new JMenuItem("Debug code");
      mntmRunCodeWithDebuggingMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, HelperFunctions.isMac() ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK));
      mntmRunCodeWithDebuggingMenuItem.addActionListener(e -> {
        ZPEKit.setDebugging(true);
        String t = "ZPE";
        if (!lastFileOpened.isEmpty()) {
          t = "ZPE : " + lastFileOpened;
        } else if (!lastCloudFileOpened.isEmpty()) {
          t = "ZPE : " + lastCloudFileOpened;
        }

        this.setTitle(t);
        try {
          String extras = " --debugging";
          if (chckbxmntmCaseSensitiveCompileCheckItem.isSelected()) {
            extras += " --case_insensitive";
          }
          if (currentProcess != null) {
            currentProcess.destroy();
          }
          HelperFunctions.WriteFile(RunningInstance.getInstallPath() + "/tmp.yas", contentEditor.getText(), false);
          if (RunningInstance.getJarExecPath().isEmpty()) {
            if (new File(RunningInstance.getExecutablePath() + "/zpe.jar").exists()) {
              currentProcess = Runtime.getRuntime().exec("java -jar " + RunningInstance.getExecutablePath() + "/zpe.jar -g " + RunningInstance.getInstallPath() + "/tmp.yas --console" + extras);
              mntmStopCodeMenuItem.setEnabled(true);
            }
          } else {
            currentProcess = Runtime.getRuntime().exec("java -jar " + RunningInstance.getJarExecPath() + " -g " + RunningInstance.getInstallPath() + "/tmp.yas --console" + extras);
            mntmStopCodeMenuItem.setEnabled(true);
          }
        } catch (Exception ex) {
          //Ignore
        }
      });

      mnScriptMenu.add(mntmRunCodeWithDebuggingMenuItem);

      mntmStopCodeMenuItem = new JMenuItem("Stop code execution");
      mntmStopCodeMenuItem.setEnabled(false);
      mntmStopCodeMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0));
      mntmStopCodeMenuItem.addActionListener(e -> {
        currentProcess.destroy();
        currentProcess = null;
        mntmStopCodeMenuItem.setEnabled(false);
      });

      mnScriptMenu.add(mntmStopCodeMenuItem);

      mnScriptMenu.add(new JSeparator());

      JMenuItem mntmCompileCodeMenuItem = new JMenuItem("Compile code");
      mntmCompileCodeMenuItem.addActionListener(e -> {
        String name1 = JOptionPane.showInputDialog(editor,
                "Please insert the name of the compiled application.");
        CompileDetails details = new CompileDetails();

        details.name = name1;
        File file12;
        String extension;

        final JFileChooser fc = new JFileChooser();

        fc.addChoosableFileFilter(filter3);
        fc.setAcceptAllFileFilterUsed(false);

        int returnVal = fc.showSaveDialog(editor.getContentPane());

        if (returnVal == JFileChooser.APPROVE_OPTION) {
          file12 = fc.getSelectedFile();
          extension = getSaveExtension(fc.getFileFilter());
        } else {
          return;
        }

        try {

          try {
            // null for no password
            ZPEKit.compile(contentEditor.getText(), file12.toString() + "." + extension, details,
                    !chckbxmntmCaseSensitiveCompileCheckItem.isSelected(), false, null, null);

            JOptionPane.showMessageDialog(editor,
                    "YASS compile success. The file has been successfully compiled to " + file12 + ".",
                    "YASS compiler", JOptionPane.WARNING_MESSAGE);
          } catch (CompileException ex) {
            JOptionPane.showMessageDialog(editor,
                    "YASS compile failure. " + ex.getMessage() + ".",
                    "YASS compiler", JOptionPane.ERROR_MESSAGE);
          }


        } catch (IOException ex) {
          JOptionPane.showMessageDialog(editor,
                  "YASS compile failure. The YASS compiler could not compile the code given. The error was -1.",
                  "YASS compiler", JOptionPane.ERROR_MESSAGE);
        }
      });
      mnScriptMenu.add(mntmCompileCodeMenuItem);

      JMenuItem mntmTranspileCodeMenuItem = new JMenu("Transpile code");

      if (ZPEKit.listTranspilerNames().isEmpty()) {
        mntmTranspileCodeMenuItem.setVisible(false);
      } else {
        for (String transpiler : ZPEKit.listTranspilerNames()) {

          //Add all transpilers to the menu
          final String lang = transpiler;
          String transpilerName = ZPEKit.getTranspilerByName(transpiler).transpilerName();
          JMenuItem mntmTranspileCodeToMenuItem = new JMenuItem("To " + lang + " (" + transpilerName + ")");
          mntmTranspileCodeToMenuItem.addActionListener(e -> {
            final JFileChooser fc = new JFileChooser();

            //Read the extension of the transpiler
            fc.addChoosableFileFilter(new FileNameExtensionFilter(lang + " files (*." + ZPEKit.getTranspilerByName(lang).getFileExtension() + ")", "txt"));
            fc.setAcceptAllFileFilterUsed(false);

            int returnVal = fc.showSaveDialog(editor.getContentPane());

            if (returnVal == JFileChooser.APPROVE_OPTION) {
              try {
                String fpath = fc.getSelectedFile().getPath();
                //Add the file extension if it's not already on the file name
                if (!fpath.endsWith("." + ZPEKit.getTranspilerByName(lang).getFileExtension())) {
                  fpath = fpath + "." + ZPEKit.getTranspilerByName(lang).getFileExtension();
                }
                HelperFunctions.WriteFile(fpath, ZPEKit.convertCode(contentEditor.getText(), "", ZPEKit.getTranspilerByName(transpiler)), false);
                JOptionPane.showMessageDialog(editor, "The code has been transpiled to " + lang + " successfully. The output can be found at " + fpath, "Successful transpile",
                        JOptionPane.INFORMATION_MESSAGE);
              } catch (CompileException ex) {
                ZPE.Log(ex.getMessage());
                JOptionPane.showMessageDialog(editor, "The code could not be transpiled.", "Error",
                        JOptionPane.ERROR_MESSAGE);
              } catch (IOException ex) {
                JOptionPane.showMessageDialog(editor, "The transpiled code could not be saved.", "Error",
                        JOptionPane.ERROR_MESSAGE);
              } catch (InternalException ex) {
                JOptionPane.showMessageDialog(editor, "There was an error in the code that prevented transpiling.", "Error", JOptionPane.ERROR_MESSAGE);
                throw new RuntimeException(ex);
              } catch (ZPERuntimeException ex) {
                JOptionPane.showMessageDialog(editor, "There was an error in the code that prevented transpiling.", "Error",
                        JOptionPane.ERROR_MESSAGE);
              } catch (TranspilerNotFoundException ex) {
                JOptionPane.showMessageDialog(editor, "The transpiler does not exist or failed to start.", "Error",
                        JOptionPane.ERROR_MESSAGE);
              } catch (ZPEException ex) {
                JOptionPane.showMessageDialog(editor, ex.getMessage(), "Error",
                        JOptionPane.ERROR_MESSAGE);
              }
            }
          });

          mntmTranspileCodeMenuItem.add(mntmTranspileCodeToMenuItem);
        }
      }

      mnScriptMenu.add(mntmTranspileCodeMenuItem);

      mnScriptMenu.add(new JSeparator());

      JMenuItem mntmAnalyseCodeMenuItem = new JMenuItem("Analyse code");
      mntmAnalyseCodeMenuItem.addActionListener(e -> {

        try {
          if (ZPEKit.validateCode(contentEditor.getText())) {
            JOptionPane.showMessageDialog(editor, "Code is valid", "Code analysis",
                    JOptionPane.INFORMATION_MESSAGE, lighterLogo);
          } else {
            JOptionPane.showMessageDialog(editor, "Code is invalid", "Code analysis",
                    JOptionPane.INFORMATION_MESSAGE, lighterLogo);
          }
        } catch (CompileException ex) {
          JOptionPane.showMessageDialog(editor, "Code is invalid", "Code analysis",
                  JOptionPane.INFORMATION_MESSAGE, lighterLogo);
        }

      });

      mnScriptMenu.add(mntmAnalyseCodeMenuItem);


      JMenuItem mntmToByteCodeFileMenuItem = new JMenuItem("Compile to byte codes");
      mntmToByteCodeFileMenuItem.addActionListener(e -> {

        final JFileChooser fc = new JFileChooser();

        fc.addChoosableFileFilter(filter2);
        fc.setAcceptAllFileFilterUsed(false);

        int returnVal = fc.showSaveDialog(editor.getContentPane());

        if (returnVal == JFileChooser.APPROVE_OPTION) {
          File file1 = fc.getSelectedFile();
          String extension = getSaveExtension(fc.getFileFilter());
          // This is where a real application would open the file.
          try {
            StringBuilder text = new StringBuilder();
            for (byte s : ZPEKit.parseToBytes(contentEditor.getText())) {
              text.append(s).append(" ");
            }
            HelperFunctions.WriteFile(file1.getAbsolutePath() + "." + extension, text.toString(), false);
          } catch (IOException ex) {
            JOptionPane.showMessageDialog(editor, "The file could not be saved.", "Error",
                    JOptionPane.ERROR_MESSAGE);
          }
        }

      });

      mnScriptMenu.add(mntmToByteCodeFileMenuItem);

      JMenuItem mntmCreateOutlineMenuItem = new JMenuItem("Create code outline");
      mntmCreateOutlineMenuItem.addActionListener(e -> {
        try {
          StringBuilder output = new StringBuilder();
          String code = contentEditor.getText();
          for (String s : ZPEKit.getFunctionNames(code)) {
            output.append(s).append(System.lineSeparator());
          }
          JOptionPane.showMessageDialog(editor, "Functions of this program:\n\n" + output, "Create code outline",
                  JOptionPane.INFORMATION_MESSAGE, lighterLogo);
        } catch (CompileException e1) {
          JOptionPane.showMessageDialog(editor, "Code is invalid and an outline cannot be made", "Create code outline",
                  JOptionPane.ERROR_MESSAGE, lighterLogo);
        }

      });

      mnScriptMenu.add(mntmCreateOutlineMenuItem);
      mnScriptMenu.add(new JSeparator());

      JMenuItem mntmUnfoldCodeMenuItem = new JMenuItem("Unfold code");
      mntmUnfoldCodeMenuItem.addActionListener(e -> {


        try {

          String result = ZPEKit.unfold(contentEditor.getText(), false);
          JOptionPane op = new JOptionPane(new YASSUnfoldDialog(result).getContentPane(), JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, lighterLogo, new String[]{});
          JDialog dlg = op.createDialog(_this, "YASS Unfold code explainer");

          dlg.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);

          dlg.setVisible(true);
        } catch (CompileException ex) {
          JOptionPane.showMessageDialog(editor, "There was an error when using YASS Unfold.", "YASS Unfold",
                  JOptionPane.INFORMATION_MESSAGE, lighterLogo);
        }
      });

      mnScriptMenu.add(mntmUnfoldCodeMenuItem);


      JMenu mnZPEOnlineMenu = new JMenu("ZPE Online");
      mnZPEOnlineMenu.setMnemonic('O');
      menuBar.add(mnZPEOnlineMenu);

      mntmLoginToZPEOnlineMenuItem = new JMenuItem("Login to ZPE Online");
      mntmLoginToZPEOnlineMenuItem.addActionListener(e -> {
        if (mntmLoginToZPEOnlineMenuItem.getText().equals("Login to ZPE Online")) {
          login();
        } else {
          login_username = "";
          login_password = "";
          zpeOnlineSeparator.setVisible(false);
          mnRecentOnlineFilesMenu.setVisible(false);
          mnRecentOnlineFilesMenu.removeAll();
          mntmLoadFromZPEOnlineMenuItem.setVisible(false);
          mntmSaveToZPEOnlineMenuItem.setVisible(false);
          mntmLoginToZPEOnlineMenuItem.setText("Login to ZPE Online");
          JOptionPane.showMessageDialog(editor.getContentPane(), "You have been logged out successfully.",
                  "Logout successful", JOptionPane.INFORMATION_MESSAGE);
        }
      });

      mainLoginMenuItem = mntmLoginToZPEOnlineMenuItem;
      mnZPEOnlineMenu.add(mntmLoginToZPEOnlineMenuItem);

      JMenuItem mntmRegisterForZPEOnlineMenuItem = new JMenuItem("Register for ZPE Online");
      mntmRegisterForZPEOnlineMenuItem.addActionListener(e -> {
        try {
          HelperFunctions.openWebsite("https://www.jamiebalfour.scot/projects/zpe/online/register/");
        } catch (URISyntaxException | IOException ex) {
          JOptionPane.showMessageDialog(editor, "Could not connect to ZPE Online", "Failure", JOptionPane.ERROR_MESSAGE);
        }
      });

      mnZPEOnlineMenu.add(mntmRegisterForZPEOnlineMenuItem);

      mnZPEOnlineMenu.add(new JSeparator());

      JMenuItem mntmVisitZPEOnlineWebsiteMenuItem = new JMenuItem("Visit ZPE Online website");
      mntmVisitZPEOnlineWebsiteMenuItem.addActionListener(e -> {
        try {
          HelperFunctions.openWebsite("https://www.jamiebalfour.scot/projects/zpe/online/");
        } catch (Exception ex) {
          JOptionPane.showMessageDialog(editor, "Could not open ZPE Online", "Failure", JOptionPane.ERROR_MESSAGE);
        }
      });
      mnZPEOnlineMenu.add(mntmVisitZPEOnlineWebsiteMenuItem);

      JMenuItem mntmViewPublicUploadsMenuItem = new JMenuItem("View public uploads");
      mntmViewPublicUploadsMenuItem.addActionListener(e -> {
        try {
          HelperFunctions.openWebsite("https://www.jamiebalfour.scot/projects/zpe/online/code/");
        } catch (URISyntaxException | IOException ex) {
          JOptionPane.showMessageDialog(editor, "Could not connect to ZPE Online", "Failure", JOptionPane.ERROR_MESSAGE);
        }
      });
      mnZPEOnlineMenu.add(mntmViewPublicUploadsMenuItem);

      mnZPEOnlineMenu.add(new JSeparator());


      JMenuItem mntmLoadFromPublicZPEOnlineMenuItem = new JMenuItem("Load from public ZPE Online repository");
      mntmLoadFromPublicZPEOnlineMenuItem.addActionListener(e -> loadFromPublicCloud());
      mnZPEOnlineMenu.add(mntmLoadFromPublicZPEOnlineMenuItem);

      zpeOnlineSeparator = new JSeparator();
      zpeOnlineSeparator.setVisible(false);
      mnZPEOnlineMenu.add(zpeOnlineSeparator);

      mnRecentOnlineFilesMenu = new JMenu("Recent online files");
      mnRecentOnlineFilesMenu.setVisible(false);
      mnZPEOnlineMenu.add(mnRecentOnlineFilesMenu);

      mntmLoadFromZPEOnlineMenuItem = new JMenuItem("Load from ZPE Online");
      mntmLoadFromZPEOnlineMenuItem.addActionListener(e -> loadFromUsersCloud());
      mntmLoadFromZPEOnlineMenuItem.setVisible(false);
      mnZPEOnlineMenu.add(mntmLoadFromZPEOnlineMenuItem);

      mntmSaveToZPEOnlineMenuItem = new JMenuItem("Save to ZPE Online");
      mntmSaveToZPEOnlineMenuItem.addActionListener(e -> saveToTheCloud());
      mntmSaveToZPEOnlineMenuItem.setVisible(false);
      mnZPEOnlineMenu.add(mntmSaveToZPEOnlineMenuItem);

      JMenu mnHelpMenu = new JMenu("Help");
      mnHelpMenu.setMnemonic('H');
      menuBar.add(mnHelpMenu);

      if (!HelperFunctions.isMac() || !useMacMenuBar) {
        JMenuItem mntmAboutMenuItem = new JMenuItem("About ZPE");
        mntmAboutMenuItem.addActionListener(e -> showAbout());
        mnHelpMenu.add(mntmAboutMenuItem);
        mnHelpMenu.add(new JSeparator());
      }

      JMenuItem mntmLearnZPEYASSMenuItem = new JMenuItem("Learn ZPE/YASS");
      mntmLearnZPEYASSMenuItem.addActionListener(e -> {
        try {
          HelperFunctions.openWebsite("https://www.jamiebalfour.scot/courses/software/yass/");
        } catch (Exception ex) {
          JOptionPane.showMessageDialog(editor, "Could not open jamiebalfour.scot", "Failure", JOptionPane.ERROR_MESSAGE);
        }
      });
      mnHelpMenu.add(mntmLearnZPEYASSMenuItem);
      mnHelpMenu.add(new JSeparator());


      JMenuItem mntmOpenZPEFolderMenuItem = new JMenuItem("Open ZPE folder");
      mntmOpenZPEFolderMenuItem.addActionListener(e -> {
        try {
          Desktop.getDesktop().open(new File(ZPEKit.getInstallPath()));
        } catch (Exception ex) {
          //Ignore
        }
      });
      mnHelpMenu.add(mntmOpenZPEFolderMenuItem);
      mnHelpMenu.add(new JSeparator());


      JMenuItem mntmJamieBalfourItem = new JMenuItem("Contact Jamie Balfour");
      mntmJamieBalfourItem.addActionListener(e -> {
        try {
          HelperFunctions.openWebsite("https://www.jamiebalfour.scot/contact/");
        } catch (Exception ex) {
          //Ignore
        }
      });
      mnHelpMenu.add(mntmJamieBalfourItem);

      JMenuItem mntmProvideFeedbackItem = new JMenuItem("Provide Feedback");
      mntmProvideFeedbackItem.addActionListener(e -> {
        String message = JOptionPane.showInputDialog(editor,
                "Please insert your feedback below.");

        try {
          if (ZPEHelperFunctions.sendFeedback(message, null)) {
            JOptionPane.showMessageDialog(editor, "Feedback sent successfully.", "Success",
                    JOptionPane.INFORMATION_MESSAGE);
          } else {
            JOptionPane.showMessageDialog(editor, "Feedback could not be sent at this time.", "Error",
                    JOptionPane.ERROR_MESSAGE);
          }
        } catch (ZPERuntimeException ex) {
          JOptionPane.showMessageDialog(editor, "Feedback could not be sent at this time.", "Error",
                  JOptionPane.ERROR_MESSAGE);
        }
      });
      mnHelpMenu.add(mntmProvideFeedbackItem);
      mnHelpMenu.add(new JSeparator());


      JMenuItem mntmChangelogMenuItem = new JMenuItem("Read changelog");
      mntmChangelogMenuItem.addActionListener(e -> {

        JOptionPane op = new JOptionPane(new ZPEChangelogDialog().getContentPane(), JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION, lighterLogo, new String[]{});

        JDialog dlg = op.createDialog(_this, "ZPE changelog");

        dlg.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);

        dlg.setVisible(true);
      });
      mnHelpMenu.add(mntmChangelogMenuItem);

      JMenuItem mntmVisitOfficialDocumentationMenuItem = new JMenuItem("Visit the official documentation");
      mntmVisitOfficialDocumentationMenuItem.addActionListener(e -> {
        try {
          HelperFunctions.openWebsite("https://www.jamiebalfour.scot/projects/zpe/documentation/");
        } catch (Exception ex) {
          JOptionPane.showMessageDialog(editor, "Could not open jamiebalfour.scot", "Failure", JOptionPane.ERROR_MESSAGE);
        }
      });

      mnHelpMenu.add(mntmVisitOfficialDocumentationMenuItem);

      //mnHelpMenu.add(mnCommandsHelpMenu);


      addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          mntmMaximiseMenuItem.setSelected(editor.getExtendedState() == JFrame.MAXIMIZED_BOTH);
        }
      });

      String defaultFile = "/files/editor_program.txt";

      if (mainProperties.containsKey("LOGIN_USERNAME") || mainProperties.containsKey("LOGIN_PASSCODE")) {
        login_username = mainProperties.getProperty("LOGIN_USERNAME");
        login_password = mainProperties.getProperty("LOGIN_PASSCODE");
      }
      if (mainProperties.containsKey("DEFAULT_FILE")) {
        try {
          contentEditor.setText(HelperFunctions.readFileAsString(mainProperties.getProperty("DEFAULT_FILE"), "utf-8"));
        } catch (IOException e1) {

          try {
            contentEditor.setText(HelperFunctions.getResource(defaultFile));
          } catch (Exception e2) {
            //Ignore
          }
        }
      } else {
        try {
          setTextProperly(HelperFunctions.getResource(defaultFile));
        } catch (Exception e2) {
          //Ignore
        }
      }
      if (mainProperties.containsKey("USE_BOLD_TEXT")) {
        if (mainProperties.get("USE_BOLD_TEXT").equals("true")) {
          mainSyntax.makeBold(true);
          mntmMakeTextBoldMenuItem.setSelected(true);
        }
      }
      if (mainProperties.containsKey("DARK_MODE")) {
        if (mainProperties.get("DARK_MODE").equals("true")) {
          switchOnDarkMode();
        } else {
          switchOffDarkMode();
        }
      }
      if (mainProperties.containsKey("HEIGHT")) {
        editor.setSize(editor.getWidth(), HelperFunctions.stringToInteger(mainProperties.get("HEIGHT").toString()));
      }
      if (mainProperties.containsKey("WIDTH")) {
        editor.setSize(HelperFunctions.stringToInteger(mainProperties.get("WIDTH").toString()), editor.getHeight());
      }
      if (mainProperties.containsKey("XPOS")) {
        editor.setLocation(
                new Point(HelperFunctions.stringToInteger(mainProperties.get("XPOS").toString()), editor.getY()));
      }
      if (mainProperties.containsKey("YPOS")) {
        editor.setLocation(
                new Point(editor.getX(), HelperFunctions.stringToInteger(mainProperties.get("YPOS").toString())));
      }
      if (mainProperties.containsKey("FONT_SIZE")) {
        changeFontSize(HelperFunctions.stringToInteger(mainProperties.get("FONT_SIZE").toString()));
      }
      if (mainProperties.containsKey("FONT_FAMILY")) {
        changeFontFamily(mainProperties.get("FONT_FAMILY").toString());
      }
      if (mainProperties.containsKey("MAXIMISED")) {
        if (mainProperties.get("MAXIMISED").toString().equals("true")) {
          editor.setExtendedState(JFrame.MAXIMIZED_BOTH);
          mntmMaximiseMenuItem.setSelected(true);
        }
      }

      _this.addWindowFocusListener(new WindowAdapter() {
        @Override
        public void windowGainedFocus(WindowEvent e) {
          for (JFrame j : currentWindows) {
            if (j.isVisible() && !j.isAlwaysOnTop()) {
              j.setAlwaysOnTop(true);
            }

          }
        }

        public void windowLostFocus(WindowEvent e) {
          for (JFrame j : currentWindows) {
            if (!j.isFocused()) {
              j.setAlwaysOnTop(false);
            }

          }
        }
      });

      editor.setJMenuBar(menuBar);
      this.setVisible(true);


      //Starts by opening a file when the -g ZAC is given a value
      if (file != null) {
        try {
          File fil = new File(file);
          setTextProperly(HelperFunctions.readFileAsString(fil.getAbsolutePath()));
          editor.setTitle("ZPE Editor " + fil.getAbsolutePath());
        } catch (IOException e) {
          JOptionPane.showMessageDialog(editor, "The file could not be opened.", "Error",
                  JOptionPane.ERROR_MESSAGE);
        }
      }


      autoCompleteDebounceTimer = new java.util.Timer();

      addKeyBindings();


    }

    static String getSaveExtension(FileFilter f) {
      if (f.equals(filter1)) {
        return "yas";
      } else if (f.equals(filter2)) {
        return "txt";
      } else if (f.equals(filter3)) {
        return "yex";
      }

      return null;
    }

    public static void StartEditor() throws IOException {
      new ZIDEEditorMain("ZPE Editor", null);

    }

    public static void StartEditor(String file) throws IOException {
      new ZIDEEditorMain("ZPE Editor", file);

    }

    private static int getVersion() {
      String version = System.getProperty("java.version");
      if (version.startsWith("1.")) {
        version = version.substring(2, 3);
      } else {
        int dot = version.indexOf(".");
        if (dot != -1) {
          version = version.substring(0, dot);
        }
      }
      return Integer.parseInt(version);
    }

    // Get the word at the caret (mouse hover) position
    private static String getWordAtCaret(JEditorPane editorPane, int pos) throws BadLocationException {
      Document doc = editorPane.getDocument();
      int start = Utilities.getWordStart(editorPane, pos);
      int end = Utilities.getWordEnd(editorPane, pos);
      return doc.getText(start, end - start);
    }

    // Hide the tooltip
    private void hideTooltip() {
      if (tooltipWindow != null) {
        tooltipWindow.setVisible(false);
        lastHoveredWord = "";
        lastPosition = new Point(0, 0);
      }
    }

    void createFontDropdown(String s) {
      JCheckBoxMenuItem i = new JCheckBoxMenuItem(s);
      final String thisFont = s;
      i.addActionListener(e -> {
        changeFontFamily(thisFont);

        setProperty("FONT_FAMILY", thisFont);
        saveGUISettings(mainProperties);

      });
      mnFontFamilyMenu.add(i);
    }

    private void showAbout() {

      JOptionPane op = new JOptionPane(new ZPEEditorAboutDialog().getContentPane(), JOptionPane.PLAIN_MESSAGE,
              JOptionPane.DEFAULT_OPTION, lighterLogo, new String[]{});

      JDialog dlg = op.createDialog(editor, "About ZPE");

      dlg.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);

      dlg.setVisible(true);
    }

    private void addRecentsFromCloud(ZPEList recents) {

      if (!recents.isEmpty()) {
        zpeOnlineSeparator.setVisible(true);
        mnRecentOnlineFilesMenu.setVisible(true);

        mnRecentOnlineFilesMenu.removeAll();

        for (Object s : recents) {
          JMenuItem recentItemMenuItem = new JMenuItem();
          // So that underscores work
          // recentItemMenuItem.setMnemonicParsing(false);

          ZPEMap file = (ZPEMap) s;
          final String filename = file.get(new ZPEString("name")).toString();
          recentItemMenuItem.setText(filename);
          mnRecentOnlineFilesMenu.add(recentItemMenuItem);

          final String id = file.get(new ZPEString("id")).toString();

          recentItemMenuItem.addActionListener(e -> {
            lastCloudFileOpened = filename;
            lastFileOpened = "";
            Map<String, String> arguments = new HashMap<>();
            arguments.put("username", login_username);
            arguments.put("password", login_password);
            arguments.put("id", id);
            jamiebalfour.parsers.json.ZenithJSONParser p = new jamiebalfour.parsers.json.ZenithJSONParser();

            String s1;
            try {
              s1 = HelperFunctions.makePOSTRequest(
                      RunningInstance.getOnlinePathProperty() + "/get.php?type=file&version=10", arguments);
              ZPEMap results = (ZPEMap) p.jsonDecode(s1, false);

              // Turn JSON to results
              String code = URLDecoder.decode(results.get(new ZPEString("string")).toString(), "UTF-8");
              code = code.replace("\\n", System.lineSeparator());

              clearUndoRedoManagers();
              setTextProperly(code);
            } catch (Exception exc) {
              ZPE.Log(exc.getMessage());
            }
          });

        }
      }

    }

    public boolean login() {

      boolean autoLogin = false;

      if (login_username.isEmpty() && login_password.isEmpty()) {
        ZPEOnlineLoginPanel z = new ZPEOnlineLoginPanel(editor);
        Map<String, String> output = z.display();
        login_username = output.get("username");
        login_password = output.get("password");
      } else {
        autoLogin = true;
      }
      try {
        ZPEMap login = goLogin();

        if (login != null) {
          if (login.get(new ZPEString("result")).toString().equals("1")) {

            JOptionPane.showMessageDialog(editor.getContentPane(), "You have been logged in successfully.",
                    "Login successful", JOptionPane.INFORMATION_MESSAGE);

            if (!autoLogin) {
              // Ask to save details
              int res = JOptionPane.showConfirmDialog(editor.getContentPane(),
                      "Do you want to save your login details?", "Save log in details?",
                      JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE);
              if (res == 0) {
                // Add details to properties
                setProperty("LOGIN_USERNAME", login_username);
                setProperty("LOGIN_PASSCODE", login.get(new ZPEString("login_token")).toString());
                saveGUISettings(mainProperties);
              }
            }

            mntmLoadFromZPEOnlineMenuItem.setVisible(true);
            mntmSaveToZPEOnlineMenuItem.setVisible(true);
            mntmLoginToZPEOnlineMenuItem.setText("Logout of ZPE Online");
            addRecentsFromCloud((ZPEList) login.get(new ZPEString("list")));
            return true;
          } else {
            mntmLoadFromZPEOnlineMenuItem.setVisible(true);
            mntmSaveToZPEOnlineMenuItem.setVisible(true);
            JOptionPane.showMessageDialog(editor.getContentPane(), "You could not be logged in.",
                    "Login unsuccessful", JOptionPane.ERROR_MESSAGE);
            return false;
          }
        } else {
          mntmLoadFromZPEOnlineMenuItem.setVisible(true);
          mntmSaveToZPEOnlineMenuItem.setVisible(true);
          JOptionPane.showMessageDialog(editor.getContentPane(), "You could not be logged in.",
                  "Login unsuccessful", JOptionPane.ERROR_MESSAGE);
          return false;
        }
      } catch (Exception ex1) {
        ZPE.Log(ex1.getMessage());
        mntmLoadFromZPEOnlineMenuItem.setVisible(true);
        mntmSaveToZPEOnlineMenuItem.setVisible(true);
        JOptionPane.showMessageDialog(editor, "Cannot login.", "Error with ZPE Online", JOptionPane.ERROR_MESSAGE);
        return false;
      }


    }

    public void closeUp() {
      setProperty("HEIGHT", "" + editor.getHeight());
      setProperty("WIDTH", "" + editor.getWidth());
      setProperty("XPOS", "" + editor.getX());
      setProperty("YPOS", "" + editor.getY());
      if (editor.getExtendedState() == JFrame.MAXIMIZED_BOTH) {
        setProperty("MAXIMISED", "true");
      } else {
        setProperty("MAXIMISED", "false");
      }
      if (currentProcess != null) {
        currentProcess.destroy();
      }
      saveGUISettings(mainProperties);
    }

    public ZPEMap goLogin() throws Exception {
      Map<String, String> arguments = new HashMap<>();
      arguments.put("username", login_username);
      arguments.put("password", login_password);

      String s = HelperFunctions.makePOSTRequest(RunningInstance.getOnlinePathProperty() + "/get.php?type=login&version=10",
              arguments);

      Object o = new jamiebalfour.parsers.json.ZenithJSONParser().jsonDecode(s, false);

      ZPEMap a = (ZPEMap) o;

      if (HelperFunctions.stringToInteger(a.get(new ZPEString("result")).toString()) != 1) {
        login_username = "";
        login_password = "";
      } else {
        mainLoginMenuItem.setText("Logout");
      }
      return a;
    }

    public void saveGUISettings(Properties props) {
      if (propertiesChanged) {
        OutputStream output;
        String path = System.getProperty("user.home") + "/zpe/" + "gui.properties";

        try {
          output = new FileOutputStream(path);
          // save properties to project root folder
          props.store(output, null);
        } catch (Exception e) {
          ZPE.Log("Error saving GUI settings. " + e.getMessage());
        }
      }
    }

    public void setProperty(String name, String value) {
      this.mainProperties.setProperty(name, value);
      this.propertiesChanged = true;
    }

    private void open() {
      final JFileChooser fc = new JFileChooser();

      fc.addChoosableFileFilter(filter1);
      fc.addChoosableFileFilter(filter2);

      int returnVal = fc.showOpenDialog(this.getContentPane());

      if (returnVal == JFileChooser.APPROVE_OPTION) {
        lastCloudFileOpened = "";
        File file = fc.getSelectedFile();
        // This is where a real application would open the file.
        try {
          clearUndoRedoManagers();
          setTextProperly(HelperFunctions.readFileAsString(file.getAbsolutePath()));
          SwingUtilities.invokeLater(() -> {
            contentEditor.setCaretPosition(0);
            scrollPane.getVerticalScrollBar().setValue(0);
          });
          recents.add(file.getAbsolutePath());
          //Store recent files
          try {
            ZPEEditor.storeRecentFiles(recents, "");
            recents = ZPEEditor.getRecentFiles("");
            updateRecentFiles();
          } catch (IOException ex) {
            ZPE.Log(ex.getMessage());
          }

          editor.setTitle("ZPE Editor " + file.getAbsolutePath());
        } catch (IOException e) {
          JOptionPane.showMessageDialog(editor, "The file could not be opened.", "Error",
                  JOptionPane.ERROR_MESSAGE);
        }
      }
    }

    private void saveAsDialog() {
      final JFileChooser fc = new JFileChooser();

      fc.addChoosableFileFilter(filter1);
      fc.setAcceptAllFileFilterUsed(false);

      int returnVal = fc.showSaveDialog(this.getContentPane());

      if (returnVal == JFileChooser.APPROVE_OPTION) {
        File file = fc.getSelectedFile();
        String extension = getSaveExtension(fc.getFileFilter());
        // This is where a real application would open the file.
        try {
          HelperFunctions.WriteFile(file.getAbsolutePath() + "." + extension, contentEditor.getText(), false);
          lastFileOpened = file.getAbsolutePath();
          lastCloudFileOpened = "";
        } catch (IOException e) {
          JOptionPane.showMessageDialog(editor, "The file could not be saved.", "Error",
                  JOptionPane.ERROR_MESSAGE);
        }
      }
    }

    void setTextProperly(String text) {
      dontUndo = true;
      contentEditor.setText(text);
      dontUndo = false;
      //contentEditor.setCaretPosition(0);
    }

    private void updateEditor() {

      contentEditor = (JEditorPane) mainSyntax.getEditPane();
      setTextProperly(contentEditor.getText());

    }

    private void clearUndoRedoManagers() {
      undoManager.die();
      undoAction.update();
      redoAction.update();
    }

    private void changeFontFamily(String fam) {
      mainSyntax.setFontName(fam);
      if (mainSyntax != null) {
        mainSyntax.updateFont(new Font(fam, contentEditor.getFont().getStyle(), contentEditor.getFont().getSize()));
        mainSyntax.repaint();
      } else {
        contentEditor.setFont(new Font(fam, contentEditor.getFont().getStyle(), contentEditor.getFont().getSize()));
      }

      //updateEditor();
      if (fam.equals("Monospaced")) {
        fam = "Default font";
      }
      for (int i = 0; i < mnFontFamilyMenu.getItemCount(); i++) {
        JMenuItem current = mnFontFamilyMenu.getItem(i);
        if (current != null) {
          current.setSelected(current.getText().equals(fam));
        }

      }
      resetScroll();
    }

    private void setUpScrollBar(JScrollPane scrollPane, String trackColour, String thumbColour, int width) {
      scrollPane.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
        @Override
        protected void configureScrollBarColors() {
          this.thumbColor = Color.decode(thumbColour);
          this.trackColor = Color.decode(trackColour);
          this.scrollBarWidth = width;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
          return createZeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
          return createZeroButton();
        }

        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
          // your code
          Graphics2D g2 = (Graphics2D) g.create();

          // Enable anti-aliasing for smooth edges
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

          // Set the color of the thumb
          g2.setColor(thumbColor);

          // Set the thumb width and height
          int arc = 10; // This value sets the roundness of the corners. Increase or decrease it as needed.

          // Draw a rounded rectangle
          g2.fillRoundRect(thumbBounds.x, thumbBounds.y, thumbBounds.width, thumbBounds.height, arc, arc);

          // Dispose of the graphics context
          g2.dispose();
        }
      });
    }

    private JButton createZeroButton() {
      JButton button = new JButton();
      Dimension zeroDim = new Dimension(0, 0);
      button.setPreferredSize(zeroDim);
      button.setMinimumSize(zeroDim);
      button.setMaximumSize(zeroDim);
      return button;
    }

    private void resetScroll() {
      int caretPosition = contentEditor.getCaretPosition();
      int scrollPosition = scrollPane.getVerticalScrollBar().getValue();
      contentEditor.setText(contentEditor.getText());
      SwingUtilities.invokeLater(() -> {
        contentEditor.requestFocus();
        contentEditor.setCaretPosition(caretPosition);
        scrollPane.getVerticalScrollBar().setValue(scrollPosition);
      });
    }

    private void switchOnDarkMode() {

      mnDarkModeMenuItem.setSelected(true);

      Color dark = Color.decode("#282D37");
      this.getContentPane().setBackground(dark);


      scrollPane.setBackground(dark);
      editor.setBackground(dark);
      editor.getContentPane().setBackground(dark);
      mainPanel.setBackground(dark);
      contentEditor.setBackground(dark);
      contentEditor.setForeground(Color.white);
      mainSyntax.setAttributeColor(ATTR_TYPE.Normal, Color.white);
      mainSyntax.setAttributeColor(ATTR_TYPE.Quote, new Color(152, 195, 119));
      mainSyntax.setAttributeColor(ATTR_TYPE.Keyword, new Color(198, 120, 222));
      mainSyntax.setAttributeColor(ATTR_TYPE.Function, new Color(97, 172, 231));
      mainSyntax.setAttributeColor(ATTR_TYPE.Var, new Color(224, 108, 117));
      mainSyntax.setAttributeColor(ATTR_TYPE.Type, new Color(105, 143, 163));
      mainSyntax.setAttributeColor(ATTR_TYPE.Bool, new Color(208, 154, 102));
      contentEditor.setCaretColor(Color.white);
      //autoCompleteItemForeColor = new Color(255, 255, 255, 255);
      autoCompleteItemBackgroundColor = new Color(30, 39, 75, 255);
      resetScroll();
      setUpScrollBar(scrollPane, "#282D37", "#444444", 7);

      darkMode = true;
    }

    private void switchOffDarkMode() {

      mnDarkModeMenuItem.setSelected(false);

      Color light = new Color(255, 255, 255);
      editor.setBackground(light);


      editor.getContentPane().setBackground(light);
      mainPanel.setBackground(light);
      contentEditor.setBackground(light);
      contentEditor.setForeground(Color.black);
      mainSyntax.setAttributeColor(ATTR_TYPE.Normal, Color.black);
      mainSyntax.setAttributeColor(ATTR_TYPE.Quote, new Color(0, 128, 0));
      mainSyntax.setAttributeColor(ATTR_TYPE.Keyword, new Color(135, 16, 148));
      mainSyntax.setAttributeColor(ATTR_TYPE.Function, new Color(97, 172, 231));
      mainSyntax.setAttributeColor(ATTR_TYPE.Var, new Color(255, 138, 0));
      mainSyntax.setAttributeColor(ATTR_TYPE.Type, new Color(2, 87, 172));
      contentEditor.setCaretColor(Color.black);
      //autoCompleteItemForeColor = new Color(31, 31, 31, 255);
      autoCompleteItemBackgroundColor = new Color(241, 241, 241, 255);
      resetScroll();
      setUpScrollBar(scrollPane, "#dddddd", "#aaaaaa", 7);

      darkMode = false;
    }

    private void changeFontSize(int newSize) {
      // Clear all check boxes
      chckbxmntmFontSizeSmallCheckItem.setSelected(false);
      chckbxmntmFontSizeNormalCheckItem.setSelected(false);
      chckbxmntmFontSizeLargeCheckItem.setSelected(false);
      chckbxmntmFontSizeExtraLargeCheckItem.setSelected(false);

      if (newSize == -1) {
        chckbxmntmFontSizeSmallCheckItem.setSelected(true);
        mainSyntax.setFontSize(14);
        contentEditor.setFont(new Font(contentEditor.getFont().getName(), contentEditor.getFont().getStyle(), 14));
      }
      if (newSize == 0) {
        chckbxmntmFontSizeNormalCheckItem.setSelected(true);
        mainSyntax.setFontSize(17);
        contentEditor.setFont(new Font(contentEditor.getFont().getName(), contentEditor.getFont().getStyle(), 17));
      }
      if (newSize == 1) {
        chckbxmntmFontSizeLargeCheckItem.setSelected(true);
        mainSyntax.setFontSize(28);
        contentEditor.setFont(new Font(contentEditor.getFont().getName(), contentEditor.getFont().getStyle(), 28));
      }
      if (newSize == 2) {
        chckbxmntmFontSizeExtraLargeCheckItem.setSelected(true);
        mainSyntax.setFontSize(35);
        contentEditor.setFont(new Font(contentEditor.getFont().getName(), contentEditor.getFont().getStyle(), 35));
      }


      if (mainSyntax != null) {
        mainSyntax.updateFont(new Font(contentEditor.getFont().getFontName(), contentEditor.getFont().getStyle(), contentEditor.getFont().getSize()));
        mainSyntax.repaint();
      } else {
        contentEditor.setFont(new Font(contentEditor.getFont().getFontName(), contentEditor.getFont().getStyle(), contentEditor.getFont().getSize()));
      }

      resetScroll();


      // Add a mouse motion listener to detect hover events
      contentEditor.addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          try {
            if (autoCompletePopup.isVisible()) {
              return;
            }
            int pos = contentEditor.viewToModel2D(e.getPoint());
            String hoveredWord = getWordAtCaret(contentEditor, pos);

            //boolean isInComment = checkIfInComment(pos);

            // Check if hovered word is a known function and show popup
            if (ZPEKit.internalFunctionExists(hoveredWord)) {
              hideTooltipTimer.cancel();
              // Get the screen coordinates for the end of the hovered word
              int endOfWordPos = Utilities.getWordEnd(contentEditor, pos);
              Rectangle2D wordEndBounds = contentEditor.modelToView2D(endOfWordPos);

              // Display the tooltip at the end of the word
              if (wordEndBounds != null) {
                Point tooltipPosition = new Point((int) wordEndBounds.getX(), (int) wordEndBounds.getY() + (int) wordEndBounds.getHeight());
                if (!hoveredWord.equals(lastHoveredWord) || tooltipPosition.getX() != lastPosition.getX() || tooltipPosition.getY() != lastPosition.getY()) {
                  //Do not recreate the same tooltip
                  lastHoveredWord = hoveredWord;
                  lastPosition = tooltipPosition;

                  startShowTooltipTimer(tooltipPosition, hoveredWord);

                }
              }
            } else {
              startHideTooltipTimer();
            }
          } catch (BadLocationException ex) {
            //Ignore and do nothing
          }
        }
      });
    }

    private ArrayList<JLabel> getPopupItems(){
      ArrayList<JLabel> output = new ArrayList<>();
      JScrollPane pane = ((JScrollPane) autoCompletePopup.getContentPane().getComponent(0));
      JViewport v = (JViewport) pane.getComponent(0);
      JPanel p = (JPanel) v.getComponent(0);
      for(Component c : p.getComponents()){
        if(c instanceof JLabel){
          output.add((JLabel) c);
        }
      }
      return output;
    }

    private JLabel getPopupItem(int index){
      return getPopupItems().get(index);
    }

    private void resetItem() {
      getPopupItem(currentAutoCompletePosition).setBackground(autoCompleteItemBackgroundColor);

      if (autoCompleteSuggestionTypes.get(autoCompleteSuggestionWords.get(currentAutoCompletePosition)).equals("keyword")) {
        getPopupItem(currentAutoCompletePosition).setForeground(StyleConstants.getForeground(CodeEditorView.DEFAULT_KEYWORD));
      } else if (autoCompleteSuggestionTypes.get(autoCompleteSuggestionWords.get(currentAutoCompletePosition)).equals("type")) {
        getPopupItem(currentAutoCompletePosition).setForeground(StyleConstants.getForeground(CodeEditorView.DEFAULT_TYPE));
      } else if (autoCompleteSuggestionTypes.get(autoCompleteSuggestionWords.get(currentAutoCompletePosition)).equals("function")) {
        getPopupItem(currentAutoCompletePosition).setForeground(StyleConstants.getForeground(CodeEditorView.DEFAULT_PREDEFINED_FUNCTION));
      }
    }

    private void addKeyBindings() {
      contentEditor.addKeyListener(new KeyAdapter() {
        @Override
        public void keyReleased(KeyEvent e) {
          hideTooltip();

          if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            autoCompletePopup.setVisible(false);
            enableArrows();
          } else if ((e.getKeyCode() == KeyEvent.VK_BACK_SPACE || e.getKeyChar() == ' ') || e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_RIGHT) {
            // Trigger autocomplete when Ctrl + Space is pressed
            autoCompletePopup.setVisible(false);
            enableArrows();

          } else if ((e.getKeyCode() == KeyEvent.VK_TAB || e.getKeyCode() == KeyEvent.VK_ENTER) && autoCompletePopup.isVisible()) {
            e.consume();
            insertAutocomplete(autoCompleteSuggestionWords.get(currentAutoCompletePosition));
          } else if (e.getKeyCode() == KeyEvent.VK_DOWN && autoCompletePopup.isVisible()) {
            e.consume();
            ArrayList<JLabel> p = getPopupItems();
            if (!p.isEmpty() && currentAutoCompletePosition < p.size() - 1) {
              resetItem();
              getPopupItems().get(++currentAutoCompletePosition).setBackground(SystemColor.controlHighlight);
              getPopupItems().get(currentAutoCompletePosition).setForeground(Color.WHITE);
              getPopupItems().get(currentAutoCompletePosition).requestFocus();
            }
          } else if (e.getKeyCode() == KeyEvent.VK_UP && autoCompletePopup.isVisible()) {
            e.consume();
            ArrayList<JLabel> p = getPopupItems();
            if (!p.isEmpty() && currentAutoCompletePosition > 0) {
              resetItem();
              getPopupItems().get(--currentAutoCompletePosition).setBackground(SystemColor.controlHighlight);
              getPopupItems().get(currentAutoCompletePosition).setForeground(Color.WHITE);
            }
          } else {

            showAutocompletePopup();
            autoCompleteDebounceTimer.cancel();
            autoCompleteDebounceTimer = new java.util.Timer();
            autoCompleteDebounceTimer.schedule(new TimerTask() {
              @Override
              public void run() {
                SwingUtilities.invokeLater(new Runnable() {
                  @Override
                  public void run() {
                    hideTooltip();
                  }
                });
              }
            }, 100);

          }
        }
      });
    }

    private void enableArrows() {
      InputMap inputMap = contentEditor.getInputMap(JComponent.WHEN_FOCUSED);
      ActionMap actionMap = contentEditor.getActionMap();

      inputMap.put(KeyStroke.getKeyStroke("TAB"), null);
      inputMap.put(KeyStroke.getKeyStroke("UP"), null);
      inputMap.put(KeyStroke.getKeyStroke("DOWN"), null);
      inputMap.put(KeyStroke.getKeyStroke("ENTER"), null);
      // Define a condition that allows or blocks the arrows
      actionMap.put("doNothing", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
        }
      });
    }

    private void disableArrows() {
      InputMap inputMap = contentEditor.getInputMap(JComponent.WHEN_FOCUSED);
      ActionMap actionMap = contentEditor.getActionMap();

      inputMap.put(KeyStroke.getKeyStroke("TAB"), "doNothing");
      inputMap.put(KeyStroke.getKeyStroke("UP"), "doNothing");
      inputMap.put(KeyStroke.getKeyStroke("DOWN"), "doNothing");
      inputMap.put(KeyStroke.getKeyStroke("ENTER"), "doNothing");
      // Define a condition that allows or blocks the arrows
      actionMap.put("doNothing", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
        }
      });
    }

    private String getCurrentWord() {
      try {
        int caretPosition = contentEditor.getCaretPosition();
        int start = caretPosition - 1;

        // Check for letters, digits, underscore, and #
        while (start >= 0 && (Character.isLetterOrDigit(contentEditor.getText(start, 1).charAt(0)) || contentEditor.getText(start, 1).charAt(0) == '_' || contentEditor.getText(start, 1).charAt(0) == '#' || contentEditor.getText(start, 1).charAt(0) == '$')) {
          start--;
        }
        int next = 0;
        while (caretPosition + next < contentEditor.getText().length() && contentEditor.getText().charAt(caretPosition + next) != ' ' && contentEditor.getText().charAt(caretPosition + next) != '\t' && contentEditor.getText().charAt(caretPosition + next) != '\n') {

          next++;
        }
        return contentEditor.getText(start + 1, next + caretPosition - (start + 1));

      } catch (BadLocationException e) {
        // Ignore exception
      }
      return "";
    }

    private String getCurrentLine() {
      try {
        int caretPosition = contentEditor.getCaretPosition();
        int start = caretPosition - 1;

        // Check for letters, digits, underscore, and #
        while (start >= 0 && contentEditor.getText(start, 1).charAt(0) != '\n') {
          start--;
        }
        int next = 0;
        while (caretPosition + next < contentEditor.getText().length() && contentEditor.getText().charAt(caretPosition + next) != '\n') {

          next++;
        }
        return contentEditor.getText(start + 1, next + caretPosition - (start + 1));

      } catch (BadLocationException e) {
        // Ignore exception
      }
      return "";
    }

    private java.util.List<String> getAutocompleteSuggestions(String currentWord) {
      List<String> suggestions = new ArrayList<>();

      // Filter through keywords and add to suggestions if they start with the current word
      for (String keyword : ZPEKit.getKeywords()) {
        if (keyword.startsWith(currentWord)) {
          suggestions.add(keyword);
          autoCompleteSuggestionTypes.put(keyword, "keyword");
        }
      }

      // Filter through type keywords and add to suggestions if they start with the current word
      for (String keyword : ZPEKit.getTypeKeywords()) {
        if (keyword.startsWith(currentWord)) {
          suggestions.add(keyword);
          autoCompleteSuggestionTypes.put(keyword, "type");
        }
      }

      // Do the same for functions
      for (String function : ZPEKit.getBuiltInFunctions()) {
        if (function.startsWith(currentWord)) {
          suggestions.add(function);
          autoCompleteSuggestionTypes.put(function, "function");
        }
      }

      return suggestions;
    }

    private boolean isWithinQuotes() {
      try {
        int caretPosition = contentEditor.getCaretPosition();
        String text = contentEditor.getText(0, caretPosition);

        // Count the number of quotation marks (both single and double)
        int doubleQuotesCount = 0;
        int singleQuotesCount = 0;

        for (int i = 0; i < caretPosition; i++) {
          char c = text.charAt(i);
          if (c == '"') {
            doubleQuotesCount++;
          } else if (c == '\'') {
            singleQuotesCount++;
          }
        }

        // If the number of quotation marks is odd, it means the caret is inside quotes
        boolean insideDoubleQuotes = (doubleQuotesCount % 2 != 0);
        boolean insideSingleQuotes = (singleQuotesCount % 2 != 0);

        return insideDoubleQuotes || insideSingleQuotes;

      } catch (BadLocationException e) {
        //Ignore
      }
      return false;
    }

    // Method to show autocomplete popup
    private void showAutocompletePopup() {

      currentAutoCompletePosition = 0;

      String currentWord = getCurrentWord();
      String currentLine = getCurrentLine();

      // Prevent flashing by checking if the word hasn't changed
      if (currentWord.equals(previousAutoCompleteWord)) {
        return;  // Exit early if the current word hasn't changed
      }

      previousAutoCompleteWord = currentWord;  // Update the previous word

      autoCompletePopup.getContentPane().removeAll();  // Clear any existing items
      autoCompletePopup.getContentPane().setLayout(new BorderLayout());

      autoCompletePopup.setFocusableWindowState(false);

      autoCompletePopup.setBackground(new Color(255, 255, 255, 0));

      // Hide the popup if the word is empty or starts with "$"
      if (currentWord.isEmpty() || currentWord.startsWith("$") || isWithinQuotes() || currentLine.trim().startsWith("//")) {
        autoCompletePopup.setVisible(false);
        return;
      }

      autoCompleteSuggestionWords = getAutocompleteSuggestions(currentWord);

      autoCompletePopup.setFocusable(false);  // Ensure the popup doesn't steal focus
      if (!autoCompleteSuggestionWords.isEmpty()) {
        Map<String, Boolean> added = new HashMap<>();

        //RoundedPanel suggestionPanel = new RoundedPanel();

        autoCompletePopup.setLayout(new BorderLayout());


        JPanel suggestionPanel = new JPanel();
        suggestionPanel.setLayout(new GridBagLayout());
        suggestionPanel.setBorder(null);
        suggestionPanel.setOpaque(true);
        suggestionPanel.setBackground(autoCompleteItemBackgroundColor);  // Fully transparent background


        suggestionPanel.addMouseListener(new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            System.out.println("EEK");
          }
        });



        JScrollPane outerScroll = new JScrollPane(suggestionPanel);
        outerScroll.setBorder(null);
        outerScroll.setOpaque(true);
        outerScroll.getVerticalScrollBar().setOpaque(false);
        outerScroll.getVerticalScrollBar().setSize(new Dimension(0, 0));


        setUpScrollBar(outerScroll, "#dddddd", "#aaaaaa", 3);

        autoCompletePopup.getContentPane().add(outerScroll, BorderLayout.NORTH);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        // Add rounded panels for each suggestion
        for (String suggestion : autoCompleteSuggestionWords) {


          JLabel suggestionLabel = new JLabel(suggestion);
          suggestionLabel.setHorizontalAlignment(SwingConstants.LEFT);
          suggestionLabel.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 0));  // Add padding

          suggestionLabel.setFocusable(true);
          suggestionLabel.setRequestFocusEnabled(true);

          String type = autoCompleteSuggestionTypes.get(suggestion);
          if (type.equals("keyword") && !added.containsKey(suggestion)) {
            suggestionLabel.setForeground(StyleConstants.getForeground(CodeEditorView.DEFAULT_KEYWORD));
          } else if (type.equals("type")) {
            suggestionLabel.setForeground(StyleConstants.getForeground(CodeEditorView.DEFAULT_TYPE));
          } else if (type.equals("function")) {
            suggestionLabel.setForeground(StyleConstants.getForeground(CodeEditorView.DEFAULT_PREDEFINED_FUNCTION));
          }
          added.put(suggestion, true);
          suggestionLabel.setOpaque(true);

          // Add a mouse listener to the panel for click actions
          suggestionLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
              insertAutocomplete(suggestion);  // Set the text field to the clicked suggestion
              autoCompletePopup.setVisible(false);  // Hide the popup after a suggestion is clicked
              hideTooltip();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
              e.getComponent().setBackground(SystemColor.controlHighlight);
              if(ZPEKit.internalFunctionExists(suggestion)){
                Point p = new Point();
                p.x = p.x + e.getComponent().getWidth();
                p.y = (int) e.getComponent().getLocation().getY();
                showRoundedTooltip(contentEditor, p, getTooltipFunctionInfo(suggestion), suggestion);
              } else{
                hideTooltip();
              }

            }

            @Override
            public void mouseExited(MouseEvent e) {
              suggestionLabel.setBackground(autoCompleteItemBackgroundColor);  // Reset background when hover ends
              startHideTooltipTimer();
            }
          });

          suggestionLabel.setBackground(autoCompleteItemBackgroundColor);
          suggestionPanel.add(suggestionLabel, gbc);


        }

        outerScroll.setBackground(autoCompleteItemBackgroundColor);
        suggestionPanel.setBackground(autoCompleteItemBackgroundColor);
        autoCompletePopup.setBackground(autoCompleteItemBackgroundColor);




        autoCompletePopup.setPreferredSize(new Dimension(200, autoCompleteSuggestionWords.size() * 22));  // Set popup size dynamically

        autoCompletePopup.pack();

        JLabel first = getPopupItem(0);
        first.setBackground(SystemColor.controlHighlight);


        // Show the popup below the text field

        try {

          int caretPos = contentEditor.getCaretPosition();
        /*int i = 0;
        while(caretPos - i > 0 && (contentEditor.getText(caretPos - i, 1).charAt(0) != '\n' && contentEditor.getText(caretPos - i, 1).charAt(0) != ' ' && contentEditor.getText(caretPos - i, 1).charAt(0) != '\t' && contentEditor.getText(caretPos - i, 1).charAt(0) != ' ')){
          i--;
        }

        caretPos = caretPos - i + 1;*/

          int endOfWordPos = Utilities.getWordEnd(contentEditor, caretPos);
          Rectangle2D wordEndBounds = contentEditor.modelToView2D(endOfWordPos - 1).getBounds();

          // Get the location of the contentEditor on the screen
          Point locationOnScreen = contentEditor.getLocationOnScreen();

          // Calculate the popup location by adjusting based on the component's screen location
          int popupX = (int) wordEndBounds.getX() + locationOnScreen.x;
          int popupY = (int) wordEndBounds.getY() + locationOnScreen.y + (int) wordEndBounds.getHeight();


          // DEBUG: Print the x position for debugging
          System.out.println("Caret X Position: " + wordEndBounds.getX());
          System.out.println("Popup X Position: " + popupX);

          // Disable the arrow keys while showing the popup (assuming disableArrows() is implemented)
          disableArrows();

          // Set the location of the popup JWindow
          autoCompletePopup.setLocation(popupX, popupY);

          autoCompletePopup.setVisible(true);
          autoCompletePopup.toFront();
        } catch (BadLocationException e) {
          throw new RuntimeException(e);
        }
      } else {
        // Hide the popup if there are no suggestions
        autoCompletePopup.setVisible(false);
        enableArrows();
      }


    }

    private void insertAutocomplete(String suggestion) {
      try {
        int caretPosition = contentEditor.getCaretPosition();
        String currentWord = getCurrentWord();
        int wordStart = caretPosition - currentWord.length();

        if(currentWord.equals(suggestion)){
          autoCompletePopup.setVisible(false);  // Hide the autocomplete menu after insertion
          enableArrows();
          return;
        }

        if (autoCompleteSuggestionTypes.get(suggestion).equals("function")) {
          suggestion += "()";
        }

        // Remove the current incomplete word and insert the suggestion
        contentEditor.getDocument().remove(wordStart, currentWord.length());
        contentEditor.getDocument().insertString(wordStart, suggestion, null);
        autoCompletePopup.setVisible(false);  // Hide the autocomplete menu after insertion
        enableArrows();
      } catch (BadLocationException e) {
        //Ignore
      }
    }

    void doReplacement(String rep) {
      contentEditor.replaceSelection(rep);
    }

    // Method to start a timer to show the tooltip after a delay
    private void startShowTooltipTimer(Point tooltipPosition, String hoveredWord) {
      if (showTooltipTimer != null) {
        showTooltipTimer.cancel();  // Cancel any previous timer
      }

      showTooltipTimer = new java.util.Timer();
      showTooltipTimer.schedule(new TimerTask() {
        @Override
        public void run() {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              showRoundedTooltip(contentEditor, tooltipPosition, getTooltipFunctionInfo(hoveredWord), hoveredWord);
            }
          });  // Show the tooltip after the delay
        }
      }, 500);  // 500ms delay before showing the tooltip
    }

    // Method to start a timer to hide the tooltip after a delay
    private void startHideTooltipTimer() {
      if (hideTooltipTimer != null) {
        hideTooltipTimer.cancel();  // Cancel any previous timer
      }

      hideTooltipTimer = new Timer();
      hideTooltipTimer.schedule(new TimerTask() {
        @Override
        public void run() {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              hideTooltip();
            }
          });  // Hide the tooltip after the delay
        }
      }, 1000);  // 500ms delay before hiding the tooltip
    }

    // Method to create and show the tooltip with rounded corners
    private void showRoundedTooltip(JEditorPane editorPane, Point position, String info, String functionName) {
      if (tooltipWindow == null) {
        tooltipWindow = new JWindow();
        tooltipWindow.setAlwaysOnTop(true);
        tooltipWindow.setFocusableWindowState(false);
      }


      tooltipWindow.setBackground(new Color(255, 255, 255, 0));

      // Create the custom panel with rounded corners
      RoundedPanel tooltipPanel = new RoundedPanel();
      tooltipPanel.setLayout(new BorderLayout());
      tooltipPanel.setBorder(null);

      // Create the label to display inside the rounded panel
      JLabel tooltipLabel = new JLabel(info);
      tooltipLabel.setHorizontalAlignment(SwingConstants.CENTER);
      tooltipPanel.add(tooltipLabel, BorderLayout.CENTER);
      tooltipLabel.setOpaque(false);
      tooltipLabel.setBorder(null);
      tooltipLabel.setBackground(new Color(0, 0, 0, 0));  // Fully transparent background
      tooltipLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      final String path = (ZPEKit.getFunctionCategory(functionName).toLowerCase().replace(" ", "_").replace("/", "") + "/" + functionName.toLowerCase().replace(" ", "_").replace("/", ""));
      tooltipLabel.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          try {

            String url = "https://www.jamiebalfour.scot/projects/zpe/documentation/functions/" + path;
            HelperFunctions.openWebsite(url);
            hideTooltip();
          } catch (URISyntaxException | IOException ex) {
            throw new RuntimeException(ex);
          }
        }
      });
      // Set the tooltip content
      tooltipWindow.getContentPane().removeAll();
      tooltipWindow.getContentPane().add(tooltipPanel);
      tooltipWindow.pack();

      // Position the tooltip at the specified location
      Point editorPaneLocationOnScreen = editorPane.getLocationOnScreen();
      tooltipWindow.setLocation(editorPaneLocationOnScreen.x + position.x, editorPaneLocationOnScreen.y + position.y);
      tooltipWindow.setVisible(true);


      // Add a mouse listener to the link to detect clicks
      tooltipWindow.addMouseListener(new MouseAdapter() {


        @Override
        public void mouseEntered(MouseEvent e) {
          if (hideTooltipTimer != null) {
            hideTooltipTimer.cancel(); // Cancel the hide timer if hovering over the tooltip
          }
        }

        @Override
        public void mouseExited(MouseEvent e) {
          startHideTooltipTimer();  // Start the hide timer when leaving the tooltip
        }
      });
      tooltipLabel.addMouseListener(new MouseAdapter() {


        @Override
        public void mouseEntered(MouseEvent e) {
          if (hideTooltipTimer != null) {
            hideTooltipTimer.cancel(); // Cancel the hide timer if hovering over the tooltip
          }
        }

        @Override
        public void mouseExited(MouseEvent e) {
          startHideTooltipTimer();  // Start the hide timer when leaving the tooltip
        }
      });
    }


    // Return function information based on the hovered function name
    private String getTooltipFunctionInfo(String functionName) {
      String output = "";


      if (darkMode) {
        output += "<html><div style='padding:10px;width:300px;color:#ddd;'>";
      } else {
        output += "<html><div style='padding:10px;width:300px;color:#333;'>";
      }

      ArrayList<AbstractMap.SimpleEntry<String, String>> params = ZPEEditor.getParams(ZPEKit.getFunctionManualHeader(functionName));

      StringBuilder header = new StringBuilder();
      for (int i = 0; i < params.size(); i++) {
        AbstractMap.SimpleEntry<String, String> param = params.get(i);
        String name = param.getKey();
        String type = param.getValue();

        //new Color(105, 143, 163) new Color(150, 0, 150)

        if (darkMode) {
          header.append("<span style='color: rgb(105, 143, 163);font-style:italic;'>").append(type).append("</span>").append(" <span style='color:#f60'>").append(name).append("</span>");
        } else {
          header.append("<span style='color: rgb(2, 87, 172);font-style:italic;'>").append(type).append("</span>").append(" <span style='color:#f60'>").append(name).append("</span>");
        }

        if (i + 1 < params.size()) {
          header.append(", ");
        }

      }


      output += "<div style='font-weight:100;text-decoration:underline;margin-bottom:5px;'>Category: " + ZPEKit.getFunctionCategory(functionName) + "</div>";
      if (darkMode) {
        output += "<div style='margin-bottom:20px;'><span style='font-weight:bold;color:rgb(198, 120, 222)'>" + functionName + "</span> (" + header + ") : " + ZPEHelperFunctions.typeByteToString(ZPEKit.getFunctionReturnType(functionName)) + "</div>";
      } else {
        output += "<div style='margin-bottom:20px;'><span style='font-weight:bold;margin-bottom:20px;color:rgb(135, 16, 148)'>" + functionName + "</span> (" + header + ") : " + ZPEHelperFunctions.typeByteToString(ZPEKit.getFunctionReturnType(functionName)) + "</div>";
      }

      output += ZPEEditor.processDescriptionToHtml(ZPEKit.getFunctionManualEntry(functionName), darkMode);

      if (darkMode) {
        output += "<div style='margin:10px 0; color:#222;'>Function version " + ZPEKit.getFunctionVersion(functionName) + "</div>";
      } else {
        output += "<div style='margin:10px 0; color:#bbb;'>Function version " + ZPEKit.getFunctionVersion(functionName) + "</div>";
      }

      output += "<div style='color:#0af'>Click for more information online.</div>";

      output += "</div></html>";

      return output;
    }

    public void saveToTheCloud() {
      if (login_username.isEmpty() || login_password.isEmpty()) {
        if (!login()) {
          return;
        }
      }

      try {
        if (!ZPEKit.validateCode(contentEditor.getText())) {
          JOptionPane.showMessageDialog(editor.getContentPane(),
                  "Your code is not valid. Please validate your code before uploading.", "Save unsuccessful",
                  JOptionPane.INFORMATION_MESSAGE);
          return;
        }
      } catch (CompileException e1) {
        System.err.println(e1.getMessage());
        JOptionPane.showMessageDialog(editor.getContentPane(), "Your code could not validate. Make sure it is valid before uploading.", "Save unsuccessful", JOptionPane.INFORMATION_MESSAGE);
        return;
      }

      String path;

      if (!lastCloudFileOpened.isEmpty()) {
        path = JOptionPane.showInputDialog(editor, "Please insert the name for this file.", lastCloudFileOpened);
      } else {
        path = JOptionPane.showInputDialog(editor, "Please insert the name for this file.");
      }

      int public_or_private = 0;
      int res = JOptionPane.showConfirmDialog(editor.getContentPane(), "Do you want to make your code public?",
              "Make your code public", JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE);
      if (res == 0) {
        public_or_private = 1;
      }
      // Null if Cancel is selected
      if (path != null) {

        Map<String, String> arguments = new HashMap<>();
        arguments.put("username", login_username);
        arguments.put("password", login_password);
        arguments.put("content", contentEditor.getText());
        arguments.put("content_name", path);

        arguments.put("public", "" + public_or_private);
        try {
          String postRes = HelperFunctions.makePOSTRequest(RunningInstance.getOnlinePathProperty() + "/save.php?version=10",
                  arguments);
          if (postRes.isEmpty()) {
            JOptionPane.showMessageDialog(editor, "Your file could not be saved at this time.", "Save failed",
                    JOptionPane.ERROR_MESSAGE);
          } else {

            jamiebalfour.parsers.json.ZenithJSONParser p = new jamiebalfour.parsers.json.ZenithJSONParser();
            ZPEMap a = (ZPEMap) p.jsonDecode(postRes, false);
            if (a.get(new ZPEString("result")).toString().equals("1")) {
              lastCloudFileOpened = path;
              lastFileOpened = "";
              JOptionPane.showMessageDialog(editor.getContentPane(),
                      "Your file has been saved to your ZPE Online account.", "Save successful",
                      JOptionPane.INFORMATION_MESSAGE);
              if (a.containsKey(new ZPEString("list"))) {
                addRecentsFromCloud((ZPEList) a.get(new ZPEString("list")));
              }
            } else {
              JOptionPane.showMessageDialog(editor, "Your file could not be saved at this time.",
                      "Save failed", JOptionPane.ERROR_MESSAGE);
            }

          }
        } catch (Exception e) {
          JOptionPane.showMessageDialog(editor, "Your file could not be saved at this time.", "Save failed",
                  JOptionPane.ERROR_MESSAGE);
          System.err.println(e.getMessage());
        }
      }
    }

    private void loadFromUsersCloud() {

      if (login_username.isEmpty() || login_password.isEmpty()) {
        if (!login()) {
          return;
        }
      }

      try {
        // Get the list
        Map<String, String> arguments = new HashMap<>();
        arguments.put("username", login_username);
        arguments.put("password", login_password);
        String lst = HelperFunctions.makePOSTRequest(RunningInstance.getOnlinePathProperty() + "/get.php?type=list&version=10",
                arguments);
        jamiebalfour.parsers.json.ZenithJSONParser p = new jamiebalfour.parsers.json.ZenithJSONParser();
        ZPEMap a = (ZPEMap) p.jsonDecode(lst, false);

        if (a.containsKey(new ZPEString("result")) && HelperFunctions.stringToInteger(a.get(new ZPEString("result")).toString()) == 1) {
          Object l = a.get(new ZPEString("list"));
          ZPEList arr = (ZPEList) l;
          ArrayList<String> choices = new ArrayList<>();

          ZPEMap selections = new ZPEMap();

          String first = "";

          for (Object k : arr) {
            // A file
            ZPEMap file = (ZPEMap) k;
            choices.add(file.get(new ZPEString("name")).toString());
            selections.put(new ZPEString(file.get(new ZPEString("name")).toString()), new ZPEString(file.get(new ZPEString("id")).toString()));
            // Set the first option
            if (first.isEmpty()) {
              first = file.get(new ZPEString("name")).toString();
            }
          }

          new ZPEOnlineFileChooser(_this, arguments, selections, choices, false);


        }

      } catch (Exception e) {
        ZPE.Log(e.getMessage());
      }

    }

    private void loadFromPublicCloud() {

      try {
        // Get the list
        Map<String, String> arguments = new HashMap<>();
        String lst = HelperFunctions.makePOSTRequest(RunningInstance.getOnlinePathProperty() + "/public.php?type=list&version=10",
                arguments);
        jamiebalfour.parsers.json.ZenithJSONParser p = new jamiebalfour.parsers.json.ZenithJSONParser();
        ZPEMap a = (ZPEMap) p.jsonDecode(lst, false);

        if (a.containsKey(new ZPEString("result")) && HelperFunctions.stringToInteger(a.get(new ZPEString("result")).toString()) == 1) {
          Object l = a.get(new ZPEString("list"));
          ZPEList arr = (ZPEList) l;
          ArrayList<String> choices = new ArrayList<>();

          ZPEMap selections = new ZPEMap();

          String first = "";

          for (Object k : arr) {
            // A file
            ZPEMap file = (ZPEMap) k;
            choices.add(file.get(new ZPEString("name")).toString());
            selections.put(new ZPEString(file.get(new ZPEString("name")).toString()), new ZPEString(file.get(new ZPEString("id")).toString()));
            // Set the first option
            if (first.isEmpty()) {
              first = file.get(new ZPEString("name")).toString();
            }
          }

          new ZPEOnlineFileChooser(_this, arguments, selections, choices, true);


        }

      } catch (Exception e) {
        ZPE.Log(e.getMessage());
      }
    }

    void loadFromCloudFile(String file, Map<String, String> arguments, ZPEMap selections, boolean publicrepo) {
      jamiebalfour.parsers.json.ZenithJSONParser p = new jamiebalfour.parsers.json.ZenithJSONParser();


      arguments.put("id", selections.get(new ZPEString(file)).toString());
      String s;
      try {
        if (!publicrepo) {
          s = HelperFunctions
                  .makePOSTRequest(RunningInstance.getOnlinePathProperty() + "/get.php?type=file&version=10", arguments);
        } else {
          s = HelperFunctions
                  .makePOSTRequest(RunningInstance.getOnlinePathProperty() + "/public.php?type=file&version=10", arguments);
        }


        ZPEMap results = (ZPEMap) p.jsonDecode(s, false);

        // Turn JSON to results
        String code;
        code = URLDecoder.decode(results.get(new ZPEString("string")).toString(), "UTF-8");

        code = code.replace("\\n", System.lineSeparator());

        clearUndoRedoManagers();
        setTextProperly(code);

        lastCloudFileOpened = file;
        lastFileOpened = "";

        if (publicrepo) {
          lastCloudFileOpened = "";
        }
      } catch (Exception e) {
        JOptionPane.showMessageDialog(editor, "File cannot be opened.", "Error", JOptionPane.ERROR_MESSAGE);
      }

    }

    @Override
    public Properties getProperties() {
      return this.mainProperties;
    }

    @Override
    public void destroyConsole() {
      this.AttachedConsole = null;
    }

    private void updateRecentFiles() {
      mntmRecentMenuItem.removeAll();
      for (String fStr : recents) {
        JMenuItem item = new JMenuItem(new File(fStr).getName());
        item.addActionListener(e -> {
          try {
            clearUndoRedoManagers();
            setTextProperly(HelperFunctions.readFileAsString(new File(fStr).getAbsolutePath()));
            SwingUtilities.invokeLater(() -> {
              contentEditor.setCaretPosition(0);
              scrollPane.getVerticalScrollBar().setValue(0);
            });
            editor.setTitle("ZPE Editor " + new File(fStr).getAbsolutePath());
          } catch (IOException ex) {
            throw new RuntimeException(ex);
          }
        });
        mntmRecentMenuItem.add(item, 0);
      }
    }

    static class EditorObject extends ZPEStructure {

      JEditorPane editor;

      protected EditorObject(ZPERuntimeEnvironment z, ZPEPropertyWrapper parent, JEditorPane editor) {
        super(z, parent, "editor");
        this.editor = editor;
        addNativeMethod("write", new write_Command());
        addNativeMethod("write_line", new write_line_Command());
        addNativeMethod("replace", new replace_Command());
      }

      class write_Command implements jamiebalfour.zpe.interfaces.ZPEObjectNativeMethod {

        @Override
        public String[] getParameterNames() {
          String[] n = new String[1];
          n[0] = "text";
          return n;
        }

        @Override
        public ZPEType MainMethod(HashMap<String, ZPEType> parameters, ZPEObject parent) {

          SwingUtilities.invokeLater(() -> {
            String txt = parameters.get("text").toString();
            int caretPosition = editor.getCaretPosition();
            int currentWordLength = 0;
            if (editor.getSelectedText() != null) {
              currentWordLength = editor.getSelectedText().length();
            }
            int wordStart = caretPosition - currentWordLength;
            // Remove the current incomplete word and insert the suggestion

            try {
              editor.getDocument().remove(wordStart, currentWordLength);
              editor.getDocument().insertString(wordStart, txt, null);
            } catch (BadLocationException e) {
              throw new RuntimeException(e);
            }
          });
          return new ZPEBoolean(true);
        }

        @Override
        public int getRequiredPermissionLevel() {
          return 0;
        }

        public String getName() {
          return "write";
        }

      }

      class write_line_Command implements jamiebalfour.zpe.interfaces.ZPEObjectNativeMethod {

        @Override
        public String[] getParameterNames() {
          String[] n = new String[1];
          n[0] = "text";
          return n;
        }

        @Override
        public ZPEType MainMethod(HashMap<String, ZPEType> parameters, ZPEObject parent) {

          SwingUtilities.invokeLater(() -> {
            String txt = parameters.get("text").toString();
            int caretPosition = editor.getCaretPosition();
            int currentWordLength = 0;
            if (editor.getSelectedText() != null) {
              currentWordLength = editor.getSelectedText().length();
            }
            int wordStart = caretPosition - currentWordLength;
            // Remove the current incomplete word and insert the suggestion

            try {
              editor.getDocument().remove(wordStart, currentWordLength);
              editor.getDocument().insertString(wordStart, txt + System.lineSeparator(), null);
            } catch (BadLocationException e) {
              throw new RuntimeException(e);
            }
          });
          return new ZPEBoolean(true);
        }

        @Override
        public int getRequiredPermissionLevel() {
          return 0;
        }

        public String getName() {
          return "write_line";
        }

      }

      class replace_Command implements jamiebalfour.zpe.interfaces.ZPEObjectNativeMethod {

        @Override
        public String[] getParameterNames() {
          String[] n = new String[2];
          n[0] = "find";
          n[1] = "replace";
          return n;
        }

        // Method to replace text in the JEditorPane
        public void replaceText(JEditorPane editorPane, String target, String replacement) {
          try {
            String currentText = editorPane.getDocument().getText(0, editorPane.getDocument().getLength());
            String updatedText = currentText.replace(target, replacement);
            editorPane.getDocument().remove(0, editorPane.getDocument().getLength());
            editorPane.getDocument().insertString(0, updatedText, null);
          } catch (BadLocationException e) {
            //Ignore
          }
        }

        @Override
        public ZPEType MainMethod(HashMap<String, ZPEType> parameters, ZPEObject parent) {
          try {
            String find = parameters.get("find").toString();
            String replace = parameters.get("replace").toString();
            while (editor.getText().contains(find)) {
              replaceText(editor, find, replace);
            }
            return new ZPEBoolean(true);
          } catch (Exception e) {
            return new ZPEBoolean(false);
          }
        }

        @Override
        public int getRequiredPermissionLevel() {
          return 0;
        }

        public String getName() {
          return "replace";
        }

      }

    }

    // Custom JPanel with rounded corners
    class RoundedPanel extends JPanel {
      private static final int ARC_WIDTH = 20;
      private static final int ARC_HEIGHT = 20;

      public RoundedPanel() {
        setOpaque(false);  // Ensure the background is transparent
        setLayout(new BorderLayout()); // Ensure proper layout inside the panel
        setBackground(new Color(0, 0, 0, 0));  // Fully transparent background
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
      }

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Clear the background and draw only the rounded rectangle
        if (darkMode) {
          // Set the color for the tooltip background
          g2.setColor(new Color(33, 33, 33));  // Light yellow // Dark grey backgroun
        } else {
          g2.setColor(new Color(255, 255, 255));  // Light yellow // Dark grey backgroun
        }
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC_WIDTH, ARC_HEIGHT);


        //g2.setColor(new Color(66, 68, 71, 255));

        // Optionally, draw a border around the tooltip
        g2.setColor(new Color(0, 0, 0, 0));  // Fully transparent border
        g2.drawRoundRect(0, 0, 1, 1, ARC_WIDTH, ARC_HEIGHT);

        // Optionally, draw a border around the tooltip (comment this out if you want no border)

      }
    }

    class UndoHandler implements UndoableEditListener {

      /**
       * Messaged when the Document has created an edit, the edit is added to
       * <code>undoManager</code>, an instance of UndoManager.
       */
      public void undoableEditHappened(UndoableEditEvent e) {
        if (!e.getEdit().getPresentationName().equals("style change") && !dontUndo) {
          undoManager.addEdit(e.getEdit());
          undoAction.update();
          redoAction.update();
        }

      }
    }

    class UndoAction extends AbstractAction {

      private static final long serialVersionUID = -3804879849241500100L;

      public UndoAction() {
        super("Undo");
        setEnabled(false);
      }

      public void actionPerformed(ActionEvent e) {
        try {
          undoManager.undo();
          update();
          redoAction.update();
        } catch (CannotUndoException ex) {
          JOptionPane.showMessageDialog(editor, "Cannot undo.", "Error", JOptionPane.ERROR_MESSAGE);
        }

      }

      protected void update() {
        if (undoManager.canUndo()) {
          setEnabled(true);
          putValue(Action.NAME, undoManager.getUndoPresentationName());
        } else {
          setEnabled(false);
          putValue(Action.NAME, "Undo");
        }
      }
    }

    class RedoAction extends AbstractAction {

      private static final long serialVersionUID = -2308035050104867155L;

      public RedoAction() {
        super("Redo");
        setEnabled(false);
      }

      public void actionPerformed(ActionEvent e) {
        try {
          undoManager.redo();
        } catch (CannotRedoException ex) {
          JOptionPane.showMessageDialog(editor, "Cannot redo.", "Error", JOptionPane.ERROR_MESSAGE);
        }
        update();
        undoAction.update();
      }

      protected void update() {
        if (undoManager.canRedo()) {
          setEnabled(true);
          putValue(Action.NAME, undoManager.getRedoPresentationName());
        } else {
          setEnabled(false);
          putValue(Action.NAME, "Redo");
        }
      }
    }

  }


