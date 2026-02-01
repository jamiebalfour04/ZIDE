package jamiebalfour.zide;

import jamiebalfour.zpe.core.ZPECore;

public class ZIDEMain {
  public static void main(String[] args) {

    if(args.length > 0) {
      if(args[0].equals("-h")){

      } else if (args[0].equals("-g")) {
        ZIDEEditor.begin(args);
      } else if (args[0].equals("--version")) {
        System.out.println("ZIDE " + ZPECore.getVersionNumber());
      }
    } else{
      ZIDEEditor.begin(args);
    }

  }
}
