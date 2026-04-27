package jamiebalfour.zide.core;
import jamiebalfour.zide.editor.ZIDEEditor;

public class ZIDE {

  final static String MAJOR_VERSION = "0.1";
  final static String MINOR_VERSION = "1";

  public static void main(String[] args) {

    if(args.length > 0) {
      if(args[0].equals("-h")){
        System.out.println("ZIDE Help coming soon...");
      } else if (args[0].equals("-g")) {
        ZIDEEditor.begin(args);

      } else if (args[0].equals("--version")) {
        System.out.println("ZIDE version " + getMajorVersion() + "." + getMinorVersion() + "." + getBuildNumber());
      }
    } else{
      ZIDEEditor.begin(args);
    }

  }

  public static String getVersion(){
    return MAJOR_VERSION + "." + MINOR_VERSION;
  }

  public static String getMajorVersion(){
    return MAJOR_VERSION;
  }

  public static String getMinorVersion(){
    return MINOR_VERSION;
  }

  public static String getBuildNumber(){
    return ZIDEBuildInformation.build;
  }
}
