package jamiebalfour.zide.editor;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

/** Updates the standard Cocoa item names which JavaFX derives from its launcher class. */
final class ZIDEMacApplicationMenu {
  private ZIDEMacApplicationMenu() { }

  static void setApplicationName(String name) {
    if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) return;
    try {
      Pointer app = message(objectiveCClass("NSApplication"), "sharedApplication");
      Pointer mainMenu = message(app, "mainMenu");
      Pointer applicationItem = message(mainMenu, "itemAtIndex:", 0L);
      setTitle(applicationItem, name);
      Pointer applicationMenu = message(applicationItem, "submenu");
      setTitle(applicationMenu, name);
      long itemCount = messageLong(applicationMenu, "numberOfItems");
      for (long index = 0; index < itemCount; index++) {
        Pointer item = message(applicationMenu, "itemAtIndex:", index);
        String title = stringValue(message(item, "title"));
        if (title.startsWith("Hide ") && !"Hide Others".equals(title)) {
          setTitle(item, "Hide " + name);
        } else if (title.startsWith("Quit ")) {
          setTitle(item, "Quit " + name);
        }
      }
    } catch (Throwable ignored) {
      // A menu-title failure must never prevent the editor from starting.
    }
  }

  private static void setTitle(Pointer item, String title) {
    if (isNull(item)) return;
    message(item, "performSelectorOnMainThread:withObject:waitUntilDone:",
            selector("setTitle:"), nsString(title), true);
  }

  private static String stringValue(Pointer string) {
    Pointer bytes = message(string, "UTF8String");
    return isNull(bytes) ? "" : bytes.getString(0);
  }

  private static Pointer objectiveCClass(String name) {
    return (Pointer) objectiveC("objc_getClass").invoke(Pointer.class, new Object[]{name});
  }

  private static Pointer selector(String name) {
    return (Pointer) objectiveC("sel_registerName").invoke(Pointer.class, new Object[]{name});
  }

  private static Pointer nsString(String value) {
    return message(objectiveCClass("NSString"), "stringWithUTF8String:", value);
  }

  private static Pointer message(Pointer receiver, String selector, Object... arguments) {
    if (isNull(receiver)) return null;
    Object[] invocation = invocation(receiver, selector, arguments);
    return (Pointer) objectiveC("objc_msgSend").invoke(Pointer.class, invocation);
  }

  private static long messageLong(Pointer receiver, String selector, Object... arguments) {
    if (isNull(receiver)) return 0;
    return (Long) objectiveC("objc_msgSend").invoke(long.class,
            invocation(receiver, selector, arguments));
  }

  private static Object[] invocation(Pointer receiver, String selector, Object[] arguments) {
    Object[] invocation = new Object[arguments.length + 2];
    invocation[0] = receiver;
    invocation[1] = selector(selector);
    System.arraycopy(arguments, 0, invocation, 2, arguments.length);
    return invocation;
  }

  private static boolean isNull(Pointer pointer) {
    return pointer == null || Pointer.NULL.equals(pointer);
  }

  private static Function objectiveC(String function) {
    return NativeLibrary.getInstance("objc").getFunction(function);
  }
}
