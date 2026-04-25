package jamiebalfour.zide.editor;

import jamiebalfour.HelperFunctions;
import jamiebalfour.codeeditor.CodeEditorView;
import jamiebalfour.zpe.core.ZPEHelperFunctions;
import jamiebalfour.zpe.core.ZPEKit;
import javafx.application.Platform;

import javax.swing.Timer;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ZIDESyntaxEditor extends CodeEditorView {

  ZIDEEditor owner;
  private List<FunctionHint> customFunctions = new ArrayList<>();
  private Timer symbolTimer;
  private volatile int symbolVersion = 0;

  public ZIDESyntaxEditor(jamiebalfour.zide.editor.ZIDEEditor editor) {
    super();
    owner = editor;

    setInformationWindowClickClickListener((e, x) -> {
      int pos = findByFunctionName(e);
      if (pos != -1) {
        goToLine(customFunctions.get(pos).line);
      } else {
        String cat = ZPEKit.getFunctionCategory(e).toLowerCase().replace("/", "").replace(" ", "_");
        String url = "https://www.jamiebalfour.scot/projects/zpe/documentation/functions/" + cat + "/" + e;
        System.out.println("Opening URL: " + url);
        try {
          HelperFunctions.openWebsite(url);
        } catch (URISyntaxException ex) {
          throw new RuntimeException(ex);
        } catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }
    });
  }

  private int findByFunctionName(String functionName) {
    int i = 0;
    for (FunctionHint x : customFunctions) {
      if (x.getName().equals(functionName)) {
        return i;
      }
      i++;
    }

    return -1;
  }

  void loadAllCitizens() {
    symbolVersion++;

    int version = symbolVersion;

    if (symbolTimer != null) symbolTimer.stop();

    symbolTimer = new Timer(400, e -> {
      rebuildSymbols(version);
    });

    symbolTimer.setRepeats(false);
    symbolTimer.start();
  }


  String getBuiltInFunctionTooltip(String functionName) {
    String output = "";

    boolean dark = owner.toggleTheme != null && owner.toggleTheme.isSelected();

    if (dark) {
      output += "<html><div style='padding:10px;width:300px;color:#ddd;'>";
    } else {
      output += "<html><div style='padding:10px;width:300px;color:#333;'>";
    }

    ArrayList<AbstractMap.SimpleEntry<String, String>> params = ZIDEEditor.getParams(ZPEKit.getFunctionManualHeader(functionName));

    StringBuilder header = new StringBuilder();

    for (int i = 0; i < params.size(); i++) {
      AbstractMap.SimpleEntry<String, String> param = params.get(i);
      String name = param.getKey();
      String type = param.getValue();

      if (dark) {
        header.append("<span style='color: rgb(105, 143, 163);font-style:italic;'>").append(type).append("</span> <span style='color:#f60'>").append(name).append("</span>");
      } else {
        header.append("<span style='color: rgb(2, 87, 172);font-style:italic;'>").append(type).append("</span> <span style='color:#f60'>").append(name).append("</span>");
      }

      if (i + 1 < params.size()) {
        header.append(", ");
      }
    }

    if (dark) {
      output += "<div style='margin-bottom:5px;'><code style='font-size:10px;'><span style='font-weight:bold;color:rgb(198, 120, 222)'>" + functionName + "</span> (" + header + ") : " + ZPEHelperFunctions.typeByteToString(ZPEKit.getFunctionReturnType(functionName)) + "</code></div>";
    } else {
      output += "<div style='margin-bottom:5px;'><code style='font-size:10px;'><span style='font-weight:bold;margin-bottom:20px;color:rgb(135, 16, 148)'>" + functionName + "</span> (" + header + ") : " + ZPEHelperFunctions.typeByteToString(ZPEKit.getFunctionReturnType(functionName)) + "</code></div>";
    }

    output += ZPEKit.getFunctionManualEntryAsStyledHtml(functionName, dark);

    output += dark ? "<div style='margin:10px 0; color:#bbb;'>Function version " + ZPEKit.getFunctionVersion(functionName) + "</div>" : "<div style='margin:10px 0; color:#222;'>Function version " + ZPEKit.getFunctionVersion(functionName) + "</div>";

    output += "<div style='font-weight:100;margin-bottom:10px;'>Category: " + ZPEKit.getFunctionCategory(functionName) + "</div>";

    output += "<div style='color:#0af'>Click for more information online.</div>";
    output += "</div></html>";

    return output;
  }

  private String getUserDefinedFunctionTooltip(FunctionHint function) {

    boolean dark = owner.toggleTheme != null && owner.toggleTheme.isSelected();

    String textColour = dark ? "#ddd" : "#333";
    String nameColour = dark ? "rgb(198, 120, 222)" : "rgb(135, 16, 148)";
    String descColour = dark ? "#bbb" : "#222";
    String metaColour = dark ? "#aaa" : "#555";

    Map<String, String> ann = function.getAnnotations();

    String doc = ann.getOrDefault("doc", function.getDescription());
    String author = ann.getOrDefault("author", "");
    String date = ann.getOrDefault("date", "");

    String output = "";

    output += "<html><div style='padding:10px;width:300px;color:" + textColour + ";'>";

    // --- Module (NEW) ---
    if (function.getModuleName() != null && !function.getModuleName().isEmpty()) {
      output += "<div style='font-size:10px;color:" + metaColour + ";margin-bottom:4px;'>";
      output += "Module: <span style='font-weight:bold;'>" + escapeHtml(function.getModuleName()) + "</span>";
      output += "</div>";
    }

    // --- Signature ---
    output += "<div style='margin-bottom:5px;'><code style='font-size:10px;'>";
    output += "<span style='font-weight:bold;color:" + nameColour + "'>";
    output += escapeHtml(function.getName());
    output += "</span> ";
    output += escapeHtml(function.getSignature().substring(function.getName().length()));
    output += "</code></div>";

    // --- Description ---
    if (!doc.isEmpty()) {
      output += "<div style='margin:10px 0;color:" + descColour + ";'>";
      output += escapeHtml(doc);
      output += "</div>";
    }

    // --- Metadata block ---
    if (!author.isEmpty() || !date.isEmpty()) {
      output += "<div style='margin:8px 0 10px 0;font-size:10px;color:" + metaColour + ";'>";

      if (!author.isEmpty()) {
        output += "<div>Author: <span style='font-weight:bold;'>" + escapeHtml(author) + "</span></div>";
      }

      if (!date.isEmpty()) {
        output += "<div>Date: <span style='font-weight:bold;'>" + escapeHtml(date) + "</span></div>";
      }

      output += "</div>";
    }

    // --- Category ---
    output += "<div style='font-weight:100;margin-bottom:10px;'>Category: User-defined function</div>";

    output += "</div></html>";

    return output;
  }

  private String escapeHtml(String s) {
    if (s == null) return "";

    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
  }

  private void rebuildSymbols(int version) {

    String code = getText();

    new Thread(() -> {
      List<FunctionHint> functions = extractCustomFunctions(code);

      if (version != symbolVersion) return;

      Platform.runLater(() -> {
        for (FunctionHint f : functions) {
          addAutoCompleteItem(f.getName(), CodeEditorView.AutoCompleteItemType.Function);
          setTooltipInfo(f.getName(), getUserDefinedFunctionTooltip(f));

          String qualified = f.getQualifiedName();

          if (!qualified.equals(f.getName())) {
            addAutoCompleteItem(qualified, CodeEditorView.AutoCompleteItemType.Function);
            setTooltipInfo(qualified, getUserDefinedFunctionTooltip(f));
          }
        }
      });
      customFunctions = functions;
    }, "ZIDE Symbol Rebuilder").start();
  }

  private String stripComments(String code) {
    StringBuilder out = new StringBuilder(code);

    Matcher block = Pattern.compile("(?s)/\\*.*?\\*/").matcher(code);
    while (block.find()) {
      for (int i = block.start(); i < block.end(); i++) {
        out.setCharAt(i, code.charAt(i) == '\n' ? '\n' : ' ');
      }
    }

    Matcher line = Pattern.compile("(?m)//.*$").matcher(out.toString());
    while (line.find()) {
      for (int i = line.start(); i < line.end(); i++) {
        out.setCharAt(i, ' ');
      }
    }

    return out.toString();
  }

  private List<FunctionHint> extractCustomFunctions(String code) {
    String cleanCode = stripComments(code);

    List<FunctionHint> functions = new ArrayList<>();

    Pattern pattern = Pattern.compile("(?m)^\\s*" + "(?:" + "(?:module\\s+([A-Za-z_][A-Za-z0-9_]*))" + "|(?:structure\\s+([A-Za-z_][A-Za-z0-9_]*))" + "|(?:end\\s+(module|structure))" + "|(?:(?:public|private)?\\s*function\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\))" + ")");

    Matcher matcher = pattern.matcher(cleanCode);

    String currentModule = null;
    String currentStructure = null;

    while (matcher.find()) {
      String moduleName = matcher.group(1);
      String structureName = matcher.group(2);
      String endType = matcher.group(3);
      String functionName = matcher.group(4);
      String params = matcher.group(5);

      if (moduleName != null) {
        currentModule = moduleName;
        continue;
      }

      if (structureName != null) {
        currentStructure = structureName;
        continue;
      }

      if (endType != null) {
        if ("structure".equals(endType)) {
          currentStructure = null;
        } else if ("module".equals(endType)) {
          currentModule = null;
        }
        continue;
      }

      if (functionName != null) {
        Map<String, String> annotations = findAnnotationsBefore(code, matcher.start());
        String description = annotations.getOrDefault("doc", "User-defined function");

        int offset = matcher.start();
        int line = getLineNumber(code, offset);

        functions.add(new FunctionHint(functionName, functionName + "(" + params.trim() + ")", description, annotations, currentModule, currentStructure, line, offset));
      }
    }

    return functions;
  }

  private Map<String, String> findAnnotationsBefore(String code, int functionStart) {
    Map<String, String> data = new LinkedHashMap<>();

    String before = code.substring(0, functionStart);
    String[] lines = before.split("\\R");

    for (int i = lines.length - 1; i >= 0; i--) {
      String line = lines[i].trim();

      if (line.isEmpty()) {
        continue;
      }

      if (!line.startsWith("@")) {
        break;
      }

      int space = line.indexOf(' ');
      String key;
      String value;

      if (space > 0) {
        key = line.substring(1, space).trim();
        value = line.substring(space + 1).trim();
      } else {
        key = line.substring(1).trim();
        value = "";
      }

      value = stripQuotes(value);

      data.put(key, value);
    }

    return data;
  }

  private String stripQuotes(String value) {
    if (value == null) return "";

    value = value.trim();

    if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
      return value.substring(1, value.length() - 1);
    }

    return value;
  }

  private int getLineNumber(String code, int offset) {
    int line = 1;

    for (int i = 0; i < offset && i < code.length(); i++) {
      if (code.charAt(i) == '\n') {
        line++;
      }
    }

    return line;
  }

  private FunctionHint findCustomFunction(String name) {
    for (FunctionHint f : customFunctions) {
      if (f.getName().equals(name) || f.getQualifiedName().equals(name)) {
        return f;
      }
    }

    return null;
  }

  public static class FunctionHint {

    private final int line;
    private final int offset;
    private final String name;
    private final String signature;
    private final String description;

    private final Map<String, String> annotations;

    private final String moduleName;
    private final String structureName;

    public FunctionHint(String name, String signature, String description,
                        Map<String, String> annotations,
                        String moduleName,
                        String structureName,
                        int line,
                        int offset) {

      this.name = name;
      this.signature = signature;
      this.description = description;
      this.annotations = annotations;
      this.moduleName = moduleName;
      this.structureName = structureName;

      this.line = line;
      this.offset = offset;

    }

    public int getLine() {
      return line;
    }

    public int getOffset() {
      return offset;
    }

    public String getModuleName() {
      return moduleName;
    }

    public String getStructureName() {
      return structureName;
    }

    public String getQualifiedName() {
      StringBuilder sb = new StringBuilder();

      if (moduleName != null && !moduleName.isEmpty()) {
        sb.append(moduleName).append("::");
      }

      if (structureName != null && !structureName.isEmpty()) {
        sb.append(structureName).append("->");
      }

      sb.append(name);

      return sb.toString();
    }

    public Map<String, String> getAnnotations() {
      return annotations;
    }

    public String getName() {
      return name;
    }

    public String getSignature() {
      return signature;
    }

    public String getDescription() {
      return description;
    }
  }




}
