package org.aeroplanechess.client;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;

public class Piece extends Equality {
  
  public enum Zone {
    HANGAR, LAUNCH, TRACK, FINAL_STRETCH;
    
    private static final Zone[] VALUES = values();
    
    public static Zone fromFirstLetter(String firstLetter) {
      for (Zone zone : VALUES) {
        if (zone.getFirstLetter().equals(firstLetter)) {
          return zone;
        }
      }
      throw new IllegalArgumentException("Did not find Zone=" + firstLetter);
    }
    
    public String getFirstLetter() {
      return name().substring(0, 1);
    }
  }
  
  private Zone zone;
  private int space;  // Index of location on the board. H: [0-3], L: [0], T: [0-51], F: [0-5]
  private final int pieceId;  // 0-3, since each player holds 4 pieces. For equality testing
  private final Color color;  // For equality testing
  private boolean isStacked;
  private boolean isFaceDown;
  
  public Piece(Zone zone, int pieceId, int space, Color color, 
      boolean isStacked, boolean isFaceDown) {
    checkArgument(pieceId >= 0 && pieceId < 4);
    checkArgument((space >= 0)
        && ((zone == Zone.HANGAR && space < 4) 
            || (zone == Zone.LAUNCH && space == 0) 
            || (zone == Zone.TRACK && space < 52) 
            || (zone == Zone.FINAL_STRETCH && space < 6)));
    
    this.zone = zone;
    this.pieceId = pieceId;
    this.space = space;
    this.color = color;
    this.isStacked = isStacked;
    this.isFaceDown = isFaceDown;
  }
  
  public Zone getZone() {
    return zone;
  }
  
  public int getSpace() {
    return space;
  }
  
  public String getLocation() {
    return zone.name().substring(0, 1) + AeroplaneChessLogic.format(space);
  }
  
  public int getPieceId() {
    return pieceId;
  }
  
  public Color getColor() {
    return color;
  }
  
  public boolean isStacked() {
    return isStacked;
  }
  
  public boolean isFaceDown() {
    return isFaceDown;
  }
  
  public String toString() {
    return "Color: " + color 
        + " ID: " + pieceId
        + " Location: " + getLocation() 
        + " Stacked: " + isStacked 
        + " Facedown: " + isFaceDown;
  }
  
  @Override
  public Object getId() {
    return Arrays.asList(getColor(), getPieceId());
  }
  
}
