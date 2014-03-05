/*
 * Modeled after Prof. Zibin's CardImage.java
 * https://github.com/yoav-zibin/cheat-game/blob/master/eclipse/src/org/cheat/graphics/CardImage.java
 */
package org.aeroplanechess.graphics;

import org.aeroplanechess.client.Piece;

/**
 * The image representation of the piece.
 */
public final class PieceImage {

  public static class Factory {
    public static PieceImage getPiece(Piece piece, boolean highlighted) {
      return new PieceImage(piece, highlighted);
    }
  }

  final boolean highlighted;
  public final Piece piece;

  private PieceImage(Piece piece, boolean highlighted) {
    this.highlighted = highlighted;
    this.piece = piece;
  }
  
  @Override
  public String toString() {
    String lowerColor = piece.getColor().name().toLowerCase();
    return "pieces/" + lowerColor + (piece.isFaceDown() ? "_facedown" : "") 
        + (highlighted ? "_highlight" : "") + ".png"; 
  }
  
}
