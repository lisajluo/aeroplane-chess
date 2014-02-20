package org.aeroplanechess.client;

import static com.google.common.base.Preconditions.checkNotNull;
import com.google.common.collect.ImmutableList;

/* 
 * The GameApi state entries used in Aeroplane Chess are:
 * die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves
 * These are mapped to the following fields:
 * die, action, rPieces, yPieces, lastTwoRolls, lastTwoMoves
 * We also store turn and playerIds.
 */
public class AeroplaneChessState {
  
  public enum Action {
    INITIALIZE, TAXI, MOVE, STACK, JUMP, TAKE_SHORTCUT;
    
    private static final Action[] VALUES = values();
    
    public static Action fromLowerString(String actionString) {
      for (Action action : VALUES) {
        if (action.name().equals(actionString.toUpperCase())) {
          return action;
        }
      }
      throw new IllegalArgumentException("Did not find Action=" + actionString);
    }
  }
  
  private final Color turn;
  private final ImmutableList<Integer> playerIds;
  private final int die;  // Die roll 1-6
  private final Action action;
  private final ImmutableList<Piece> rPieces;
  private final ImmutableList<Piece> yPieces;
  
  /* 
   * lastTwoRolls is [-1, -1] if there are no rolls yet by the current player
   * Previous roll is stored in lastTwoRolls[0], the roll 2 turns ago in lastTwoRolls[1] 
   */
  private final ImmutableList<Integer> lastTwoRolls; 
  
  /* 
   * lastTwoMoves is composed of Strings, each char of which is a piece that was moved in
   * the last two moves.  For example, if I moved piece 3 and then the stacked pieces 1 and 2,
   * lastTwoMoves would store ["12", "3"]. Reset to ["", ""] if no moves made yet by the player.
   */
  private final ImmutableList<String> lastTwoMoves;  
  
  public AeroplaneChessState(Color turn, ImmutableList<Integer> playerIds, int die, Action action, 
      ImmutableList<Piece> rPieces, ImmutableList<Piece> yPieces, 
      ImmutableList<Integer> lastTwoRolls, ImmutableList<String> lastTwoMoves) {
    this.turn = checkNotNull(turn);
    this.playerIds = checkNotNull(playerIds);
    this.die = die;
    this.action = checkNotNull(action);
    this.rPieces = checkNotNull(rPieces);
    this.yPieces = checkNotNull(yPieces);
    this.lastTwoRolls = checkNotNull(lastTwoRolls);
    this.lastTwoMoves = checkNotNull(lastTwoMoves);
  }
  
  public Color getTurn() {
    return turn;
  }

  public ImmutableList<Integer> getPlayerIds() {
    return playerIds;
  }
  
  public int getDie() {
    return die;
  }

  public Action getAction() {
    return action;
  }
  
  public ImmutableList<Piece> getPieces(Color color) {
    return color.isRed() ? rPieces : yPieces;
  }
  
  public ImmutableList<Integer> getLastTwoRolls() {
    return lastTwoRolls;
  }
  
  public ImmutableList<String> getLastTwoMoves() {
    return lastTwoMoves;
  }
}