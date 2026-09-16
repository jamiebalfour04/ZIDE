package jamiebalfour.zide.core;
import jamiebalfour.zide.editor.ZIDEEditor;

public class ZIDE {

  static {
    if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
      System.setProperty("apple.awt.application.name", "ZIDE");
      System.setProperty("com.apple.mrj.application.apple.menu.about.name", "ZIDE");
    }
  }

  final static String MAJOR_VERSION = "1.26";
  final static String MINOR_VERSION = "10";

  public static void main(String[] args) {

    if(args.length > 0) {
      if(args[0].equals("-h")){
        printHelp();
      } else if (args[0].equals("-s")) {
        if (args.length < 2) {
          System.err.println("Missing port number. Usage: zide.jar -s <port>");
          System.exit(2);
          return;
        }
        try {
          int port = Integer.parseInt(args[1]);
          if (port < 1 || port > 65535) {
            throw new NumberFormatException();
          }
          ZIDECollaborationServer server = new ZIDECollaborationServer(port);
          Runtime.getRuntime().addShutdownHook(new Thread(server::close, "zide-collaboration-shutdown"));
          server.start();
        } catch (NumberFormatException exception) {
          System.err.println("Invalid port number. Usage: zide.jar -s <port>");
          System.exit(2);
        } catch (Exception exception) {
          System.err.println("Could not start the collaboration server: " + exception.getMessage());
          System.exit(1);
        }
      } else if (args[0].equals("-g")) {
        try{
          ZIDEEditor.begin(args);
        } catch (java.lang.NoClassDefFoundError e){
          System.err.println("ZIDE requires JavaFX libraries. Please install JavaFX and try again.");
        }

      } else if (args[0].equals("--version")) {
        System.out.println("ZIDE version " + getMajorVersion() + "." + getMinorVersion() + "." + getBuildNumber());
      }
    } else{
      try {
        ZIDEEditor.begin(args);
      } catch (java.lang.NoClassDefFoundError e){
        System.err.println("ZIDE requires JavaFX libraries. Please install JavaFX and try again.");
      }
    }

  }

  private static void printHelp() {
    System.out.println("ZIDE " + getVersion());
    System.out.println("Usage:");
    System.out.println("  zide.jar                 Start the ZIDE editor");
    System.out.println("  zide.jar -s <port>       Start the collaboration server");
    System.out.println("  zide.jar --version       Show the version");
    System.out.println("  zide.jar -h              Show this help");
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
