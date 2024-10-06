import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.SystemInfo;

import javax.swing.*;

public class ZIDEMain {
  public static void main(String[] args){
    //Not sure if this will do anything anyway
    if(System.console() != null){
      System.out.print("ZIDE is a GUI program and must be run from the executable file directly.");
    }

    if(args[0].equals("-g")){
      FlatLightLaf.setup();
      JFrame.setDefaultLookAndFeelDecorated( true );
      ZIDEEditor z = new ZIDEEditor();
      if( SystemInfo.isMacFullWindowContentSupported ) {
        frame.getRootPane().putClientProperty( "apple.awt.fullWindowContent", true );
        frame.getRootPane().putClientProperty( "apple.awt.transparentTitleBar", true );
      }
      JFrame f = new JFrame();
      f.setContentPane(z);
      f.pack();
      f.setVisible(true);
    }

    System.out.println("Welcome to ZIDE");
  }
}
