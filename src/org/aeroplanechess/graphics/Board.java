package org.aeroplanechess.graphics;

import org.aeroplanechess.client.Piece.Zone;
import org.aeroplanechess.client.Color;

/**
 * Representation of Aeroplane Chess board and the integer x, y coordinates (top, left) 
 * of where to place the pieces on the board.
 */
public final class Board {
  // Scale to set board coordinates properly
  private static double scale = 1.0;
  public static final int GAME_WIDTH = 830;
  public static final int GAME_HEIGHT = 730;
  
  public static class Point {
    private int x;
    private int y;
    
    Point(int x, int y) {
      this.x = x;
      this.y = y;
    }
    
    public int getX() {
      return x;
    }
    
    public int getY() {
      return y;
    }
    
    public int getScaledX() {
      return (int) (x * scale);
    }
    
    public int getScaledY() {
      return (int) (y * scale);
    }
    
    @Override
    public String toString() {
      return "X: " + x + " Y: " + y;
    }
  }
  
  private static final Point[][] HANGAR_MAPPING = {
    { // Red pieces
      new Point(550, 560),  // H00
      new Point(630, 560),  // H01
      new Point(550, 635),  // H02
      new Point(630, 635)   // H03
    },
    { // Blue pieces
      new Point(17, 565),   // H00
      new Point(100, 565),  // H01
      new Point(17, 635),   // H02
      new Point(100, 635)   // H03
    },
    { // Yellow pieces
      new Point(17, 26),    // H00
      new Point(100, 26),   // H01
      new Point(17, 105),   // H02
      new Point(100, 105)   // H03
    },
    { // Green pieces
      new Point(550, 26),   // H00
      new Point(630, 26),   // H01
      new Point(550, 105),  // H02
      new Point(630, 105)   // H03
    }
  };
  
  private static final Point[][] FINAL_STRETCH_MAPPING = {
    { // Red pieces
      new Point(556, 330),  // F00
      new Point(518, 330),  // F01
      new Point(480, 330),  // F02
      new Point(442, 330),  // F03
      new Point(404, 330),  // F04
      new Point(366, 330)   // F05
    },
    { // Blue pieces
      new Point(324, 564),  // F00,
      new Point(324, 526),  // F01
      new Point(324, 488),  // F02
      new Point(324, 450),  // F03
      new Point(324, 412),  // F04
      new Point(324, 374)   // F05
    },
    { // Yellow pieces
      new Point(89, 330),   // F00
      new Point(127, 330),  // F01
      new Point(165, 330),  // F02
      new Point(203, 330),  // F03
      new Point(241, 330),  // F04
      new Point(279, 330)   // F05
    },
    { // Green pieces
      new Point(324, 97),   // F00
      new Point(324, 135),  // F01
      new Point(324, 173),  // F02
      new Point(324, 211),  // F03
      new Point(324, 249),  // F04
      new Point(324, 287)   // F05
    }
  }; 
  
  private static final Point[] TRACK_MAPPING = {
    new Point(195, 36),   // T00
    new Point(241, 22),   // T01
    new Point(282, 22),   // T02
    new Point(323, 22),   // T03
    new Point(364, 22),   // T04
    new Point(405, 22),   // T05
    new Point(448, 36),   // T06
    new Point(467, 81),   // T07
    new Point(467, 122),  // T08
    new Point(452, 168),  // T09
    new Point(481, 203),  // T10
    new Point(526, 185),  // T11
    new Point(567, 185),  // T12
    new Point(609, 203),  // T13
    new Point(623, 247),  // T14
    new Point(623, 288),  // T15
    new Point(623, 329),  // T16
    new Point(623, 370),  // T17
    new Point(623, 411),  // T18
    new Point(609, 458),  // T19
    new Point(567, 477),  // T20
    new Point(526, 477),  // T21
    new Point(481, 458),  // T22
    new Point(452, 490),  // T23        
    new Point(467, 540),  // T24
    new Point(467, 582),  // T25
    new Point(452, 624),  // T26  
    new Point(405, 641),  // T27
    new Point(364, 641),  // T28
    new Point(323, 641),  // T29
    new Point(282, 641),  // T30
    new Point(241, 641),  // T31
    new Point(195, 624),  // T32                         
    new Point(180, 582),  // T33
    new Point(180, 540),  // T34
    new Point(195, 490),  // T35
    new Point(161, 458),  // T36
    new Point(117, 477),  // T37
    new Point(77, 477),   // T38
    new Point(33, 458),   // T39
    new Point(23, 411),   // T40
    new Point(23, 370),   // T41
    new Point(23, 329),   // T42
    new Point(23, 288),   // T43
    new Point(23, 247),   // T44
    new Point(33, 203),   // T45
    new Point(77, 185),   // T46
    new Point(117, 185),  // T47
    new Point(161, 203),  // T48
    new Point(195, 168),  // T49  
    new Point(180, 122),  // T50
    new Point(180, 81)    // T51
  };
  
  private static final Point[] LAUNCH_MAPPING = {
    new Point(645, 493), // Red
    new Point(163, 657), // Blue
    new Point(0, 170),   // Yellow
    new Point(482, 3)    // Green
  };
      
  public static Point getCoordinates(Color color, Zone zone, int space) {
    switch (zone) {
      case HANGAR:
        return HANGAR_MAPPING[color.ordinal()][space];
    case FINAL_STRETCH:
        return FINAL_STRETCH_MAPPING[color.ordinal()][space];
      case TRACK:
        return TRACK_MAPPING[space];
      case LAUNCH:
        return LAUNCH_MAPPING[color.ordinal()];
      default:
        throw new IllegalArgumentException("Did not find Zone=" + zone);
    }
  }
  
  public static void setScale(double scale) {
    Board.scale = scale;
  }
  
  public static double getScale() {
    return Board.scale;
  }
}
