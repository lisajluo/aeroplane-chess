/* 
 * Modeled after Prof. Zibin's Color.java
 * https://github.com/yoav-zibin/cheat-game/blob/master/eclipse/src/org/cheat/client/Color.java
 */

package org.aeroplanechess.client;

public enum Color {
  
  /*
   * For now we implement 2-player (red vs. yellow) version of Aeroplane Chess; the other colors
   * will be useful for checking color of board spaces. This is the order of the colors going
   * clockwise around the board.
   */
  R, B, Y, G;  
  
  private static final Color[] VALUES = values();
  
  public static Color fromFirstLetter(String firstLetter) {
    for (Color color : VALUES) {
      if (color.name().equals(firstLetter)) {
        return color;
      }
    }
    throw new IllegalArgumentException("Did not find Color=" + firstLetter);
  }
  
  // Red is always the first player, and for 2-player Yellow must be the 2nd player
  public static Color fromPlayerOrder(int playerOrder) {
    return playerOrder == 0 ? R : Y;
  }

  public boolean isRed() {
    return this == R;
  }

  public boolean isYellow() {
    return this == Y;
  }

  public Color getOppositeColor() {
    return this.isRed() ? Y : R;
  }
}