/*
 * Modeled after Prof. Zibin's CardImage.java
 * https://github.com/yoav-zibin/cheat-game/blob/master/eclipse/src/org/cheat/graphics/CardImage.java
 */
package org.aeroplanechess.graphics;

/**
 * The image representation of the die.
 */
public final class DieImage {

  public static class Factory {
    public static DieImage getDie(int value) {
      return new DieImage(value);
    }
  }
  
  final int value; 

  private DieImage(int value) {
    this.value = value;
  }
  
  @Override
  public String toString() {
    return "die/" + value + ".png"; 
  }
  
}