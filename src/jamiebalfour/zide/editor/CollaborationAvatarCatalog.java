package jamiebalfour.zide.editor;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.List;

/** Shared catalogue and sprite-sheet rendering for ZIDE's built-in avatars. */
final class CollaborationAvatarCatalog {
  static final String PREFIX = "builtin:";
  private static final Image SPRITE_SHEET = new Image(
      CollaborationAvatarCatalog.class.getResourceAsStream("/files/collaboration-avatars.png"));
  private static final List<Avatar> AVATARS = List.of(
      new Avatar("raccoon", "Raccoon", 0, "#5b6470"), new Avatar("rabbit", "Rabbit", 1, "#e5a7a8"),
      new Avatar("dog", "Dog", 2, "#d9984e"), new Avatar("cat", "Cat", 3, "#8ba1b5"),
      new Avatar("fox", "Fox", 4, "#e8783d"), new Avatar("panda", "Panda", 5, "#aeb8c4"),
      new Avatar("penguin", "Penguin", 6, "#68758f"), new Avatar("frog", "Frog", 7, "#83b85a"),
      new Avatar("koala", "Koala", 8, "#a9a7b0"), new Avatar("owl", "Owl", 9, "#98714c"),
      new Avatar("bear", "Bear", 10, "#b77a51"), new Avatar("lion", "Lion", 11, "#d79a48"),
      new Avatar("tiger", "Tiger", 12, "#e68a3d"), new Avatar("monkey", "Monkey", 13, "#a66b45"),
      new Avatar("hedgehog", "Hedgehog", 14, "#c59d72"), new Avatar("otter", "Otter", 15, "#9a765c"));

  private CollaborationAvatarCatalog() { }

  static List<Avatar> avatars() { return AVATARS; }

  static boolean isBuiltIn(String value) {
    return find(value) != null;
  }

  static boolean is(String value, Avatar avatar) {
    return avatar != null && avatar.id().equals(id(value));
  }

  static String background(String value) {
    Avatar avatar = find(value);
    return avatar == null ? "" : avatar.background();
  }

  static String label(String value) {
    Avatar avatar = find(value);
    return avatar == null ? "" : avatar.name();
  }

  static ImageView view(String value, double size) {
    Avatar avatar = find(value);
    if (avatar == null || SPRITE_SHEET.isError()) return null;
    double tileWidth = SPRITE_SHEET.getWidth() / 4.0;
    double tileHeight = SPRITE_SHEET.getHeight() / 4.0;
    ImageView view = new ImageView(SPRITE_SHEET);
    view.setViewport(new Rectangle2D((avatar.index() % 4) * tileWidth,
        (avatar.index() / 4) * tileHeight, tileWidth, tileHeight));
    view.setFitWidth(size);
    view.setFitHeight(size);
    view.setPreserveRatio(true);
    return view;
  }

  private static Avatar find(String value) {
    String id = id(value);
    if (id == null) return null;
    return AVATARS.stream().filter(avatar -> avatar.id().equals(id)).findFirst().orElse(null);
  }

  private static String id(String value) {
    if (value == null || !value.startsWith(PREFIX)) return null;
    return value.substring(PREFIX.length());
  }

  record Avatar(String id, String name, int index, String background) {
    String value() { return PREFIX + id; }
  }
}
