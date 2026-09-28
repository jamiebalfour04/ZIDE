package jamiebalfour.zide.plugins;

/** Small host surface exposed to plugins so menu actions do not depend on ZIDE internals. */
public interface ZIDEPluginContext {
  String activeLanguageId();
  String selectedText();
  void insertText(String text);
  void showMessage(String title, String message);
}
