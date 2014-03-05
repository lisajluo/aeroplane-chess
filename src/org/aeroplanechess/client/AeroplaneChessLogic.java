/* 
 * Modeled after Prof. Zibin's CheatLogic.java
 * https://github.com/yoav-zibin/cheat-game/blob/master/eclipse/src/org/cheat/client/CheatLogic.java
 */

package org.aeroplanechess.client;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Arrays;
import java.util.Map;

import static org.aeroplanechess.client.Constants.*;
import org.aeroplanechess.client.AeroplaneChessState.Action;
import org.aeroplanechess.client.GameApi.EndGame;
import org.aeroplanechess.client.GameApi.Set;
import org.aeroplanechess.client.GameApi.SetRandomInteger;
import org.aeroplanechess.client.GameApi.SetTurn;
import org.aeroplanechess.client.GameApi.VerifyMove;
import org.aeroplanechess.client.GameApi.VerifyMoveDone;
import org.aeroplanechess.client.GameApi.Operation;

import org.aeroplanechess.client.Piece.Zone;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class AeroplaneChessLogic {
  
  // All other logic is to support verifying whether player's move is correct
  public VerifyMoveDone verify(VerifyMove verifyMove) {
    try {
      checkMoveIsLegal(verifyMove);
      return new VerifyMoveDone();
    } catch (Exception e) {
      return new VerifyMoveDone(verifyMove.getLastMovePlayerId(), e.getMessage());
    }
  }
  
  /** Track spaces run clockwise from (T00) Red, Blue, Yellow, Green, Red, ... Green (T51) */
  private Color getTrackSpaceColor(int space) {
    checkArgument(space >= 0 && space < TOTAL_SPACES);
    return Color.values()[space % PIECES_PER_PLAYER];
  }
  
  /**
   * A player can take a shortcut if his piece lands on the shortcut space of his color.
   * The shortcut spaces are: Y: T10, G: T23, R: T36, B: T49
   */
  boolean isShortcutAvailable(Zone zone, int space, Color turn) {
    int[] shortcuts = {36, 49, 10, 23};
    return (zone == Zone.TRACK) && space == shortcuts[turn.ordinal()];
  }
  
  /**
   * The ends of the shortcuts (you cannot jump from these spaces).
   * The shortcut end spaces are: B: T09, Y: T22, G: T35, R: T48
   */
  private boolean isShortcutEnd(Zone zone, int space, Color turn) {
    int[] ends = {48, 9, 22, 35};
    return (zone == Zone.TRACK) && space == ends[turn.ordinal()];
  }
  
  /** 
   * Returns true if jump is available: 
   * If a piece/pieces lands on a space of the same color, it will automatically jump 4 spaces to 
   * the next space of that color.
   * A jump is not available if you have just landed on the ending point of a shortcut. (Ie., you 
   * cannot take a shortcut followed by a jump.)
   * A jump is also not available at the shortcut or Final Stretch starts (otherwise you would miss 
   * the shortcut or Final Stretch entirely!)
   * Also, you can't jump multiple times (so if the last action on your turn was JUMP then 
   * you cannot make another jump).
   */
  boolean isJumpAvailable(Action action, Zone zone, int space, Color turn) {
    return zone == Zone.TRACK 
        && action != Action.JUMP
        && (turn == getTrackSpaceColor(space))
        && !isShortcutEnd(zone, space, turn)
        && !isShortcutAvailable(zone, space, turn)
        && (getStart(turn, Zone.FINAL_STRETCH) != space);
  }
  
  /** 
   * Players can stack (or unstack) on the next move only if any pieces are on the space where they 
   * attempt to move.
   */
  boolean isStackAvailable(Zone zone, int space, List<Piece> pieces) {
    for (Piece piece : pieces) {
      if (piece.getSpace() == space && piece.getZone() == zone) {
        return true;
      }
    }
    return false;
  }
  
  /**
   * Each player's final stretch/launch starts at a different track space. 
   * Final stretch start: R = T16, Y = T42, B = T29, G = T03
   * Launch start: R = T18, Y = T44, G = T05, B = T31
   */
  int getStart(Color turn, Zone zone) {
    int[] finalStart = {16, 29, 42, 3};
    int[] launchStart = {18, 31, 44, 5};
    
    return zone == Zone.FINAL_STRETCH ? finalStart[turn.ordinal()] : launchStart[turn.ordinal()];
  }
  
  /**
   *  Returns the pieces in the list of operations that were moved (so that we can check
   *  whether they were moved correctly, against the state).
   */
  @SuppressWarnings("unchecked")
  private List<Piece> getMovedPieces(List<Operation> lastMove, Color turn) {
    List<Piece> movedPieces = Lists.newArrayList();
    String turnString = turn.name();
    int pieceId;
    
    /* 
     * Operations on the pieces are:
     * new Set("R|Y#", [location, stacked|unstacked, faceup|facedown])
     * The other Set operations (ACTION, LAST_*) do not begin with the same letters,
     * so it is safe to check for the first letter of the key to see if we are 
     * moving a piece.
     */ 
    
    for (int i = 2; i < lastMove.size(); i++) {
      if (lastMove.get(i).getMessageName() == SET) {
        Set operation = (Set) lastMove.get(i);
        String key = operation.getKey();
        if (key.startsWith(turnString)) {
          pieceId = (int) (key.charAt(1) - '0');
          movedPieces.add(gameApiPieceToAeroplaneChessPiece((List<String>) operation.getValue(), 
              pieceId, turnString));
        }
      }
    }
    
    return movedPieces;
  }
  
  /** Get the Piece that was last moved, as it was *before* it was moved. */
  private Piece getStatePiece(AeroplaneChessState state, Color turn, int pieceId) {
    return state.getPieces(turn).get(pieceId);
  }
  
  /**
   * Takes a list of moved pieces and returns a String representation of which were moved
   * (to be used in lastTwoMoves).
   */
  private String getMovedString(List<Piece> playerPieces) {
    String movedString = "";
    
    for (Piece piece : playerPieces) {
      movedString += piece.getPieceId();
    }
    
    return movedString;
  }
  
  /** 
   * Takes a list of pieces (size > 1) that we are attempting to move and the previous state.  
   * If any one of them was unstacked, we can't move the whole pile.
   * Also checks that all the move pieces are set as stacked.
   */
  private boolean moveAllStacked(List<Piece> movedPieces, AeroplaneChessState state, Color turn) {
      for (Piece piece : movedPieces) {
        if (!getStatePiece(state, turn, piece.getPieceId()).isStacked() || !piece.isStacked()) {
          return false;
        }
      }
    return true;
  }
  
  /**
   * Returns a list of opponent's pieces that can be sent back to the Hangar.
   * Every time a player makes a move, if he lands on a space where an opponent's pieces 
   * reside, then those opponent's pieces are all sent back to the Hangar.
   */
  List<Piece> getOpponentPiecesOnSpace(List<Piece> opponentPieces, String location) {
    List<Piece> piecesToMove = Lists.newArrayList();
    
    for (Piece piece : opponentPieces) {
      if (piece.getLocation().equals(location)) {
        piecesToMove.add(piece);
      }
    }
    
    return piecesToMove;
  }
  
  /**
   * Returns a list of operations to return an opponent's pieces to the Hangar.
   */
  private List<Operation> getOperationsOpponentToHangar(Color turn, List<Piece> opponentPieces) {
    List<Operation> operations = Lists.newArrayList();
    
    for (Piece piece : opponentPieces) {
      int pieceId = piece.getPieceId();
      operations.add(
          new Set(turn.getOppositeColor().name() + pieceId, getNewPiece(H + format(pieceId))));
    }
    
    return operations;
  }
  
  /**
   * Returns a list of operations to move player pieces (after correctness has been checked
   * on the moves).
   */
  private List<Operation> getOperationsMovePlayerPieces(Color turn, List<Piece> movedPieces) {
    List<Operation> operations = Lists.newArrayList();
    
    for (Piece piece : movedPieces) {
      operations.add(
          new Set(turn.name() + piece.getPieceId(), pieceToGameApiPiece(piece)));
    }
    
    return operations;
  }
  
  /**
   * Returns an updated representation of lastTwoRolls: 
   * lastTwoRolls stores the die values of the last two rolls or -1 if there was no roll.
   * If the turn has just switched, the values are [-1, -1].  If you have rolled once 
   * (for example, a 6), then the values are [6, -1].  If you roll a second time, the values will
   * be [3, 6] -- shifted over one.  (Of course if you roll a third 6, the turn will go to
   * the other player and lastTwoRolls will be reset.)
   */
  private List<Integer> getNewLastTwoRolls(AeroplaneChessState state, int roll) {
    List<Integer> lastTwoRolls = state.getLastTwoRolls();
    
    return Lists.newArrayList(roll, lastTwoRolls.get(0));
  }
  
  /** Returns an updated representation of lastTwoMoves:
   * lastTwoMoves stores the strings containing pieces that were moved on the last two moves or
   * an empty string if there was no move. Values are shifted over as in lastTwoRolls.
   */
  private List<String> getNewLastTwoMoves(AeroplaneChessState state, String pieces) {
    List<String> lastTwoMoves = state.getLastTwoMoves();
    
    return Lists.newArrayList(pieces, lastTwoMoves.get(0));
  }

  /** Returns the playerId of the opposite player. */
  private int getOppositeId(List<Integer> playerIds, int playerId) {
    return playerIds.get(0) == playerId ? playerIds.get(1) : playerIds.get(0);
  }
  
  /** True if last three rolls were all 6's.  Must send affected pieces back to hangar. */
  boolean rolledThreeSixes(AeroplaneChessState state) {
    return (state.getDie() == 6 && state.getLastTwoRolls().equals(DOUBLE_SIX_ROLLS));
  }
  
  /** 
   * True if all the pieces are currently in the Hangar. (This, combined with an odd roll, 
   * means that you can't make a move on the board. 
   */
  private boolean allPiecesInHangar(List<Piece> pieces) { 
    for (Piece piece : pieces) {
      if (piece.getZone() != Zone.HANGAR) {
        return false;
      }
    }
    return true;
  }
  
  /**
   * Returns a new GameApi representation of a piece backtracked to a different 
   * location on the board.
   */
  private List<String> backtrackGameApiPiece(Piece piece, String newLocation) {
    String stacked = piece.isStacked() ? STACKED : UNSTACKED;
    String faceup = piece.isFaceDown() ? FACEDOWN : FACEUP;
    
    return ImmutableList.<String>of(newLocation, stacked, faceup);
  }
  
  /**
   * Takes an integer and returns a string padded to 2 digits (substitute for String.format()
   * which is not supported by GWT).  Not using GWT libraries (this way testing will work).
   */
  static String format(int number) {
    return number > 9 ? "" + number : "0" + number;
  }
  
  /**
   * Returns a list of operations for when ACTION == TAXI.
   */
  List<Operation> getOperationsTaxi(AeroplaneChessState state, List<Piece> playerMovedPieces, 
      List<Piece> opponentMovedPieces, int playerId) {
    List<Operation> expectedOperations;
    Color turn = state.getTurn();
    
    check(state.getDie() % 2 == 0, 
        "Illegal to TAXI on an odd roll.");
    check(opponentMovedPieces.isEmpty(), 
        "Illegal to move opponent's pieces on TAXI.");
    check(playerMovedPieces.size() == 1, 
        "You must TAXI only one piece at a time.=");
    
    // Check that the piece that was moved was previously in the hangar
    Piece movedPiece = playerMovedPieces.get(0);
    int pieceId = movedPiece.getPieceId();
    Piece statePiece = getStatePiece(state, turn, pieceId); 
    check(statePiece.getZone() == Zone.HANGAR, 
        "Illegal to taxi piece not in Hangar.");
    
    // Check that the piece was moved to the correct Launch spot
    check(movedPiece.getZone() == Zone.LAUNCH && movedPiece.getSpace() == 0,
        "Must taxi piece to Launch zone.");
    
    // Check that the piece is still faceup
    check(!statePiece.isFaceDown(), 
        "Illegal to taxi facedown piece.");
    
    /* 
     * On a taxi move, you should set turn to the other player (and roll die for them) unless
     * you previously rolled a 6.  
     * If you rolled a 6, you should set turn to yourself and roll the die again. 
     */
    int die = state.getDie();
    int oppositeId = getOppositeId(state.getPlayerIds(), playerId);
    
    if (die == 6) {
      expectedOperations = ImmutableList.<Operation>of(
          new SetTurn(playerId),
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, TAXI),
          new Set(turn.name() + pieceId, getNewPiece(movedPiece.getLocation())),
          new Set(LAST_TWO_ROLLS, getNewLastTwoRolls(state, die)),
          new Set(LAST_TWO_MOVES, getNewLastTwoMoves(state, Integer.toString(pieceId))));
    }
    else {  // die != 6
      expectedOperations = ImmutableList.<Operation>of(
          new SetTurn(oppositeId),
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, TAXI),
          new Set(turn.name() + pieceId, getNewPiece(movedPiece.getLocation())),
          new Set(LAST_TWO_ROLLS, EMPTY_ROLLS),
          new Set(LAST_TWO_MOVES, EMPTY_MOVES));
    }
    
    return expectedOperations;
  }
  
  /**
   * Returns a list of operations for when ACTION == MOVE.
   */
  List<Operation> getOperationsMove(AeroplaneChessState state, List<Piece> playerMovedPieces, 
      List<Piece> opponentMovedPieces, int playerId) {
    List<Operation> expectedOperations;
    Color turn = state.getTurn();
    int die = state.getDie();
    int oppositeId = getOppositeId(state.getPlayerIds(), playerId);
    /*
     * If you rolled a third 6: must send all pieces affected by the last two rolls back to the 
     * hangar and pass turn to the other player.
     */
    List<String> lastTwoMoves = state.getLastTwoMoves();
    if (rolledThreeSixes(state)) {
      List<Operation> operations = Lists.newArrayList(
          new SetTurn(oppositeId),  
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO), // Roll die for other player
          new Set(ACTION, MOVE));
      
      for (int i = 0; i < PIECES_PER_PLAYER; i++) {
        if (lastTwoMoves.get(0).contains(Integer.toString(i)) 
              || lastTwoMoves.get(1).contains(Integer.toString(i))) {
          operations.add(new Set(turn.name() + i, getNewPiece(H + format(i))));
        }
      }
      
      operations.add(new Set(LAST_TWO_ROLLS, EMPTY_ROLLS));
      operations.add(new Set(LAST_TWO_MOVES, EMPTY_MOVES));
      
      return operations;
    }
    
    if (playerMovedPieces.size() > 1) {
      //Check that if there are multiple pieces moved, they are all stacked
      check(moveAllStacked(playerMovedPieces, state, turn), 
            "Illegal to move multiple unstacked pieces.");
    }
    else if (playerMovedPieces.size() == 1) {
      // Check that if you moved one piece, it is not stacked with others
      check(!getStatePiece(state, turn, playerMovedPieces.get(0).getPieceId()).isStacked(),
          "Illegal to move single piece out of stacked pieces.");
    }
    else {
      // Check that if no pieces were moved, then it wasn't possible to make a move
      // because all the pieces were in the Hangar and you didn't roll an odd number
      check(die % 2 != 0 && allPiecesInHangar(state.getPieces(turn)), 
          "Move is possible but no pieces moved.");
      
      // Pass the turn if there were no moves possible
      List<Operation> operations = Lists.newArrayList(
          new SetTurn(oppositeId), 
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, MOVE),
          new Set(LAST_TWO_ROLLS, EMPTY_ROLLS),
          new Set(LAST_TWO_MOVES, EMPTY_MOVES));
      
      return operations;
    }
    
    // Check that the pieces are all still faceup *IF* you are not moving to the hangar
    for (Piece piece : playerMovedPieces) {
      check(piece.getZone() == Zone.HANGAR || !piece.isFaceDown(), 
          "Illegal to set to facedown unless in Hangar.");
    }
    
    Piece movedPiece = playerMovedPieces.get(0);
    Piece statePiece = getStatePiece(state, turn, movedPiece.getPieceId());
    Zone stateZone = statePiece.getZone();
    Zone movedZone = movedPiece.getZone();
    int originalSpace = statePiece.getSpace();
    int movedSpace = movedPiece.getSpace();
    
    /* If you rolled an inexact roll, then you must backtrack. This can only happen if you were
     * in the final stretch.
     */
    if (stateZone == Zone.FINAL_STRETCH && originalSpace + die > WIN_FINAL_SPACE) {
      List<Operation> operations = Lists.newArrayList(
          new SetTurn(oppositeId), 
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, MOVE));
      // Backtrack pieces
      for (Piece piece : playerMovedPieces) {
        if (originalSpace - die < 0) {  // Have to backtrack to Track zone
          int offset = die - originalSpace - 1;
          int finalStretchStart = getStart(turn, Zone.FINAL_STRETCH);
          operations.add(new Set(
              turn.name() + piece.getPieceId(), 
              backtrackGameApiPiece(
                  piece, 
                  T + format(finalStretchStart - offset))));
        }
        else {  // Can stay in Final Stretch
          operations.add(new Set(
              turn.name() + piece.getPieceId(), 
              backtrackGameApiPiece(piece, F + format(originalSpace - die))));
        }
      }
      
      operations.add(new Set(LAST_TWO_ROLLS, EMPTY_ROLLS));
      operations.add(new Set(LAST_TWO_MOVES, EMPTY_MOVES));
       
      return operations;
    }
    
    /* 
     * If you moved to the final stretch, make sure you are in the right one (and moved correct
     * amount of spaces)
     */
    if (movedZone == Zone.FINAL_STRETCH) {
      /* Final stretch to final stretch -- check amount of move */
      if (stateZone == Zone.FINAL_STRETCH) {
        check(movedSpace - originalSpace == die, 
            "Moved illegal amount of spaces.");
      }
      else if (stateZone == Zone.HANGAR || stateZone == Zone.LAUNCH) { 
        check(false, 
            "Illegal move to final stretch from non-track space.");
      }
      else {  // Track to Final Stretch
        int finalStretchStart = getStart(turn, Zone.FINAL_STRETCH);
        check(finalStretchStart == (((originalSpace + die) % TOTAL_SPACES) - movedSpace - 1),
            "Illegal number of spaces moved.");
      }
    }
    else if (movedZone == Zone.TRACK) {  // Checking moves to TRACK
      if (stateZone == Zone.LAUNCH) {
        int launchStart = getStart(turn, Zone.LAUNCH);
        check(launchStart + die == movedSpace, 
            "Moved incorrect spaces from launch.");
      }
      else if (stateZone == Zone.TRACK) {
        check((originalSpace + die) % TOTAL_SPACES == movedSpace,
            "Moved incorrect amount of spaces.", movedSpace, originalSpace, die);
      }
      else {  // You can only move to the TRACK from TRACK or LAUNCH
        check(false, "Illegal move to track.");
      }
    }
    else if (movedZone == Zone.HANGAR) {  // Checking moves to HANGAR
      /*
       * You can only MOVE (your own pieces) to the HANGAR in 2 scenarios:
       * 1) You rolled 6 sixes and have to return pieces moved in those rolls to the Hangar.
       *    In this case all the pieces will remain faceup.
       * 2) You rolled an exact roll to get your piece or stacked pieces from the Final Stretch
       *    (or space before the Final Stretch) to the Home Zone --> Hangar
       */
      if (!rolledThreeSixes(state)) {
        for (Piece piece : playerMovedPieces) {
          check(piece.isFaceDown(), 
              "Must set all pieces to facedown.", playerMovedPieces);
        }
        
        /*
         * If the die roll is exactly the same number as needed to reach F05 (automatically goes to
         * Hangar), and ALL other pieces are already facedown in the Hangar, then we win.
         * Otherwise if the roll is inexact then player must backtrack that many steps.
         */
        // You must start exactly on the start of the final stretch, or in it
        int finalStretchStart = getStart(turn, Zone.FINAL_STRETCH);
        if (originalSpace == finalStretchStart || stateZone == Zone.FINAL_STRETCH) {
          check((originalSpace == finalStretchStart && die == 6 && stateZone == Zone.TRACK)
              || (stateZone == Zone.FINAL_STRETCH && (originalSpace + die == WIN_FINAL_SPACE)), 
            "Can't win on inexact spaces.");
          
          List<Operation> operations = Lists.newArrayList(
              new SetTurn(playerId),
              new Set(ACTION, MOVE));
          // Add player pieces - no opponent pieces necessary (since we are ending the game)
          operations.addAll(getOperationsMovePlayerPieces(turn, playerMovedPieces));
          operations.add(new EndGame(playerId));

          return operations;
        }
        else { 
          check(false, "Moved illegal amount of spaces to Hangar.", playerMovedPieces, die);
        }
      }
    }
    else {  // You can only MOVE to FINAL_STRETCH, TRACK, or HANGAR
      check(false, "Cannot use action MOVE with this zone.");
    }
    
    // Get any opponent's pieces that should be moved on landing
    List<Piece> stateOpponentPieces = state.getPieces(turn.getOppositeColor());
    List<Piece> opponentPiecesToMove = getOpponentPiecesOnSpace(
        stateOpponentPieces, 
        movedZone.name().substring(0, 1) + movedSpace);
    
    /* If you rolled a (first or second) 6, or if a stack, shortcut, or jump is available after this
     * move, then set the turn back to yourself.  Don't roll the die if stack/shortcut/jump is 
     * available (because that will be taken care of after the sequence of moves).
     * If you did not roll a 6 and there is no stack/shortcut/jump available:
     * Set turn to the other player and roll for them.
     */
    boolean stackOrJumpOrShortcutAvailable = isShortcutAvailable(movedZone, movedSpace, turn)
        || isJumpAvailable(state.getAction(), movedZone, movedSpace, turn)
        || isStackAvailable(movedZone, movedSpace, state.getPieces(turn));
        
    if (die == 6 || stackOrJumpOrShortcutAvailable) {
      expectedOperations = Lists.newArrayList();
      expectedOperations.add(new SetTurn(playerId));
      if (!stackOrJumpOrShortcutAvailable) {
        expectedOperations.add(new SetRandomInteger(DIE, DIE_FROM, DIE_TO));
      }
      expectedOperations.add(new Set(ACTION, MOVE));
      
      // Add player piece movements
      expectedOperations.addAll(getOperationsMovePlayerPieces(turn, playerMovedPieces));
      // Add any opponent piece movements (if they were in player's way - send to Hangar)
      expectedOperations.addAll(getOperationsOpponentToHangar(turn, opponentPiecesToMove));
      expectedOperations.add(new Set(LAST_TWO_ROLLS, getNewLastTwoRolls(state, die)));
      expectedOperations.add(new Set(LAST_TWO_MOVES, 
          getNewLastTwoMoves(state, getMovedString(playerMovedPieces))));
    }
    else {  // die != 6 && !stackOrJumpOrShortcutAvailable
      expectedOperations = Lists.newArrayList(
          new SetTurn(oppositeId),
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, MOVE));
      // Add player piece movements
      expectedOperations.addAll(getOperationsMovePlayerPieces(turn, playerMovedPieces));
      // Add any opponent piece movements (if they were in player's way - send to Hangar)
      expectedOperations.addAll(getOperationsOpponentToHangar(turn, opponentPiecesToMove));
      expectedOperations.add(new Set(LAST_TWO_ROLLS, EMPTY_ROLLS));
      expectedOperations.add(new Set(LAST_TWO_MOVES, EMPTY_MOVES));
    }
    
    return expectedOperations;
  }
  
  /**
   * Returns a list of operations for when ACTION == STACK.
   */
  List<Operation> getOperationsStack(AeroplaneChessState state, List<Piece> playerMovedPieces, 
      List<Piece> opponentMovedPieces, int playerId) {
    List<Operation> expectedOperations;
    Color turn = state.getTurn();
    
    check(opponentMovedPieces.isEmpty(), 
        "Illegal to move opponent's pieces on STACK.");
    check(playerMovedPieces.size() > 0, 
        "You must stack more than one piece.");
    
    String stackLocation = playerMovedPieces.get(0).getLocation();
    String lastMovedIds = state.getLastTwoMoves().get(0);
    boolean found = false;
    
    for (Piece piece: playerMovedPieces) {
      String newLocation = piece.getLocation();
      // Check that stacking does not change location of the pieces
      check(
          newLocation.equals(getStatePiece(state, turn, piece.getPieceId()).getLocation()),
          "Illegal to change position of pieces while stacking.");
      
      // Check that all the pieces moved are on the same space
      check(newLocation.equals(stackLocation),
          "Illegal to attempt stack on pieces at different positions.");
      
      // Check that the pieces are all still faceup (since you can't STACK pieces in the Hangar)
      check(!piece.isFaceDown(), "You can only set facedown in the Hangar.");
      
      /*
       * Check that the one of the pieces stacked was moved last (you can't stack if you didn't just
       * land on that space)
       */
      if (lastMovedIds.contains(Integer.toString(piece.getPieceId()))) {
        found = true;
      }
    }
    
    check(found, "Attempting to stack pieces not moved last.");
    
    /*
     *  Check that there are no other pieces on the same space except the ones that you attempt to
     *  move (since you must stack all or none)
     */
    List<Piece> playerStatePieces = state.getPieces(turn);
    for (Piece statePiece : playerStatePieces) {
      if (statePiece.getLocation().equals(stackLocation)) {
        found = false;
        for (Piece movedPiece : playerMovedPieces) {
          if (movedPiece.getLocation().equals(stackLocation)) {
            found = true;
          }
        }
        check(found, "Did not stack all the pieces on the space.", playerMovedPieces, statePiece);
      }
    }
    
    /* 
     * On a stack move, you should set turn to the other player (and roll die for them) unless
     * you previously rolled a 6.  
     * If you rolled a 6, you should set turn to yourself and roll the die again. 
     */
    int die = state.getDie();
    int oppositeId = getOppositeId(state.getPlayerIds(), playerId);
    
    if (die == 6) {
      expectedOperations = Lists.newArrayList(
          new SetTurn(playerId),
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, STACK));
      // Add player piece movements
      expectedOperations.addAll(getOperationsMovePlayerPieces(turn, playerMovedPieces));
      expectedOperations.add(new Set(LAST_TWO_ROLLS, getNewLastTwoRolls(state, die)));
      /* 
       * A stack means that 2+ pieces are now being moved together. In lastTwoMoves,
       * the representation for the pieces moved on the last die roll should include all
       * the stacked pieces.  We can simply replace the first element of the list instead
       * of shifting as we normally do -- unless we are UNSTACKING, in which case we keep
       * the same lastTwoRolls.
       */
      if (!playerMovedPieces.get(0).isStacked()) {
        expectedOperations.add(new Set(LAST_TWO_MOVES, state.getLastTwoMoves()));
      }
      else {
        expectedOperations.add(new Set(LAST_TWO_MOVES, 
            ImmutableList.<String>of(
                getMovedString(playerMovedPieces), 
                state.getLastTwoMoves().get(1))));
      }
    }
    else {  // die != 6
      expectedOperations = Lists.newArrayList(
          new SetTurn(oppositeId),
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, STACK));
      // Add player piece movements
      expectedOperations.addAll(getOperationsMovePlayerPieces(turn, playerMovedPieces));
      expectedOperations.add(new Set(LAST_TWO_ROLLS, EMPTY_ROLLS));
      expectedOperations.add(new Set(LAST_TWO_MOVES, EMPTY_MOVES));
    }
    
    return expectedOperations;
  }
  
  /**
   * Returns a list of operations for when ACTION == JUMP.
   */
  List<Operation> getOperationsJump(AeroplaneChessState state, List<Piece> playerMovedPieces, 
      List<Piece> opponentMovedPieces, int playerId) {
    List<Operation> expectedOperations;
    Color turn = state.getTurn();
    
    if (playerMovedPieces.size() > 1) {
    // Check that if there are multiple pieces moved, they are all stacked
      check(moveAllStacked(playerMovedPieces, state, turn), 
            "Illegal to move multiple unstacked pieces.");
    }
    else if (playerMovedPieces.size() == 1) {
      // Check that if you moved one piece, it is not stacked with others
      check(!getStatePiece(state, turn, playerMovedPieces.get(0).getPieceId()).isStacked(),
          "Illegal to move single piece out of stacked pieces.");
    }
    else {
      // Unlike TAKE_SHORTCUT, there must be pieces set in a Jump action
      check(false, "No pieces moved in last move.");
    }
    
    // Check that the turn hasn't just switched (ie., a jump cannot be the first thing you do)
    check(!state.getLastTwoRolls().equals(EMPTY_ROLLS), "Can't jump first thing!");
    
    // Check that the pieces are all still faceup
    for (Piece piece : playerMovedPieces) {
      check(!piece.isFaceDown(), "Illegal to set to facedown on the track.");
    }
    
    /* Check that the pieces moved were previously on a valid jump space (if one is,
     * then they all are, since the stacked test was passed), and also that the pieces did
     * not previously jump.
     */
    Piece movedPiece = playerMovedPieces.get(0);
    Piece lastPiece = getStatePiece(state, turn, movedPiece.getPieceId());
    int lastSpace = lastPiece.getSpace();
    check(isJumpAvailable(state.getAction(), lastPiece.getZone(), lastSpace, turn),
        "Jump not available on that space.");
    
    // Check that pieces are being moved to the correct position
    check((lastSpace + JUMP_AMOUNT) % TOTAL_SPACES == movedPiece.getSpace(),
        "Jumped wrong amount of spaces.");

    // Get any opponent's pieces that should be moved on landing
    List<Piece> stateOpponentPieces = state.getPieces(turn.getOppositeColor());
    List<Piece> opponentPiecesToMove = getOpponentPiecesOnSpace(
        stateOpponentPieces, 
        T + format((lastPiece.getSpace() + JUMP_AMOUNT) % TOTAL_SPACES));
    
    /* 
     * On a jump move, if you rolled a (first or second) 6, or if a stack/shortcut is available 
     * after this move, then set the turn back to yourself.  Don't roll the die if stack/shortcut
     * is available (because that will be taken care of after the sequence of moves).
     * If you did not roll a 6 and there is no stack/shortcut available:
     * Set turn to the other player and roll for them.
     */
    int die = state.getDie();
    int oppositeId = getOppositeId(state.getPlayerIds(), playerId);
    Zone movedZone = movedPiece.getZone();
    int movedSpace = movedPiece.getSpace();
    boolean stackOrShortcutAvailable = isShortcutAvailable(movedZone, movedSpace, turn)
        || isStackAvailable(movedZone, movedSpace, state.getPieces(turn));
    
    if (die == 6 || stackOrShortcutAvailable) {
      expectedOperations = Lists.newArrayList();
      expectedOperations.add(new SetTurn(playerId));
      if (stackOrShortcutAvailable) {
        expectedOperations.add(new SetRandomInteger(DIE, DIE_FROM, DIE_TO));
      }
      expectedOperations.add(new Set(ACTION, JUMP));
      // Add player piece movements
      expectedOperations.addAll(getOperationsMovePlayerPieces(turn, playerMovedPieces));
      // Add any opponent piece movements (if they were in player's way - send to Hangar)
      expectedOperations.addAll(getOperationsOpponentToHangar(turn, opponentPiecesToMove));
      // A jump move always follows an actual move (or move --> stack, etc.),
      // so you don't have to change the lastTwoMoves/Rolls since the pieces moved are the same.
      expectedOperations.add(new Set(LAST_TWO_ROLLS, state.getLastTwoRolls()));
      expectedOperations.add(new Set(LAST_TWO_MOVES, state.getLastTwoMoves()));
    }
    else {
      expectedOperations = Lists.newArrayList(
          new SetTurn(oppositeId),
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, JUMP));
      // Add player piece movements
      expectedOperations.addAll(getOperationsMovePlayerPieces(turn, playerMovedPieces));
      // Add any opponent piece movements (if they were in player's way - send to Hangar)
      expectedOperations.addAll(getOperationsOpponentToHangar(turn, opponentPiecesToMove));
      expectedOperations.add(new Set(LAST_TWO_ROLLS, EMPTY_ROLLS));
      expectedOperations.add(new Set(LAST_TWO_MOVES, EMPTY_MOVES));
    }
    
    return expectedOperations;
  }
  
  
  /**
   * Returns a list of operations for when ACTION == TAKE_SHORTCUT.
   */
  List<Operation> getOperationsTakeShortcut(AeroplaneChessState state, 
      List<Piece> playerMovedPieces, List<Piece> opponentMovedPieces, int playerId) {
    List<Operation> expectedOperations;
    Color turn = state.getTurn();
    int die = state.getDie(); 
    int oppositeId = getOppositeId(state.getPlayerIds(), playerId);
    
    if (playerMovedPieces.size() > 1) {
    // Check that if there are multiple pieces moved, they are all stacked
      check(moveAllStacked(playerMovedPieces, state, turn), 
            "Illegal to move multiple unstacked pieces.");
    }
    else if (playerMovedPieces.size() == 1) {
      // Check that if you moved one piece, it is not stacked with others
      check(!getStatePiece(state, turn, playerMovedPieces.get(0).getPieceId()).isStacked(),
          "Illegal to move single piece out of stacked pieces.");
    }
    else {
      /* Not moving any pieces on TAKE_SHORTCUT action signifies "giving up" the shortcut.
       * If player rolled a 6, then set turn to self and roll again, otherwise give up the turn.
       */
      if (die == 6) {
        expectedOperations = Lists.newArrayList(
            new SetTurn(playerId),
            new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
            new Set(ACTION, TAKE_SHORTCUT),
            new Set(LAST_TWO_ROLLS, state.getLastTwoRolls()),
            new Set(LAST_TWO_MOVES, state.getLastTwoMoves()));
      }
      else {
        expectedOperations = Lists.newArrayList(
            new SetTurn(oppositeId),
            new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
            new Set(ACTION, TAKE_SHORTCUT),
            new Set(LAST_TWO_ROLLS, EMPTY_ROLLS),
            new Set(LAST_TWO_MOVES, EMPTY_MOVES));
      }
      return expectedOperations;
    }
    
    // Check that the pieces are all still faceup
    for (Piece piece : playerMovedPieces) {
      check(!piece.isFaceDown(), "Illegal to set to facedown on the track.");
    }
    
    /* Check that the pieces moved were previously on a valid shortcut space (if one is,
     * then they all are, since the stacked test was passed)
     */
    Piece movedPiece = playerMovedPieces.get(0);
    Piece lastPiece = getStatePiece(state, turn, movedPiece.getPieceId());
    check(isShortcutAvailable(lastPiece.getZone(), lastPiece.getSpace(), turn),
        "Shortcut not available on that space.");
    
    // Check that pieces are being moved to the correct position
    Zone movedZone = movedPiece.getZone();
    int movedSpace = movedPiece.getSpace();
    check(isShortcutEnd(movedPiece.getZone(), movedSpace, turn),
        "Moved incorrect amount of spaces on shortcut.");
    
    // Get any opponent's pieces that should be moved on landing
    List<Piece> stateOpponentPieces = state.getPieces(turn.getOppositeColor());
    List<Piece> opponentPiecesToMove = getOpponentPiecesOnSpace(
        stateOpponentPieces, 
        T + format((lastPiece.getSpace() + SHORTCUT_AMOUNT) % TOTAL_SPACES));
    // Add opponent's piece(s) that were in the way of the shortcut (in the final stretch)
    opponentPiecesToMove.addAll(getOpponentPiecesOnSpace(
        stateOpponentPieces, 
        SHORTCUT_FINAL_INTERSECTION));
    
    /* 
     * On a shortcut move, you should set turn to the other player (and roll die for them) unless
     * you previously rolled a 6 or there is a stack move available.  
     * Otherwise, you should set turn to yourself and roll the die if there is no stack available.
     * (If there is a stack available, then the die is rolled (if necessary) after the stack.)
     */
    boolean isStackAvailable = isStackAvailable(movedZone, movedSpace, state.getPieces(turn));
    if (die == 6 || isStackAvailable) {
      expectedOperations = Lists.newArrayList();
      expectedOperations.add(new SetTurn(playerId));
      if (!isStackAvailable) {
        expectedOperations.add(new SetRandomInteger(DIE, DIE_FROM, DIE_TO));
      }
      expectedOperations.add(new Set(ACTION, TAKE_SHORTCUT));
      // Add player piece movements
      expectedOperations.addAll(getOperationsMovePlayerPieces(turn, playerMovedPieces));
      // Add any opponent piece movements (if they were in player's way - send to Hangar)
      expectedOperations.addAll(getOperationsOpponentToHangar(turn, opponentPiecesToMove));
      expectedOperations.add(new Set(LAST_TWO_ROLLS, getNewLastTwoRolls(state, die)));
      // A shortcut move always follows an actual move (or move --> stack, etc.),
      // so you don't have to change the lastTwoMoves since the pieces moved are the same.
      expectedOperations.add(new Set(LAST_TWO_MOVES, state.getLastTwoMoves()));
    }
    else {  // die != 6 && !isStackAvailable
      expectedOperations = Lists.newArrayList(
          new SetTurn(oppositeId),
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, TAKE_SHORTCUT));
      // Add player piece movements
      expectedOperations.addAll(getOperationsMovePlayerPieces(turn, playerMovedPieces));
      // Add any opponent piece movements (if they were in player's way - send to Hangar)
      expectedOperations.addAll(getOperationsOpponentToHangar(turn, opponentPiecesToMove));
      expectedOperations.add(new Set(LAST_TWO_ROLLS, EMPTY_ROLLS));
      expectedOperations.add(new Set(LAST_TWO_MOVES, EMPTY_MOVES));
    }
    
    return expectedOperations;
  }
  
  /**
   * Returns the expected move. Aside from the first move (setting the board), players can choose
   * one of the following actions: taxi, move, stack, jump, takeShortcut. Whether the action is 
   * available and what order to take the action depends on the state of the board and the player's 
   * pieces (ie., jump is available after a move or a move and a stack, but not after a shortcut, 
   * and of course you can only jump if your piece just landed on a space of the same color).
   */
  List<Operation> getExpectedOperations(VerifyMove verifyMove) {
    List<Operation> lastMove = verifyMove.getLastMove();
    Map<String, Object> lastApiState = verifyMove.getLastState();
    List<Integer> playerIds = verifyMove.getPlayerIds();
    int lastMovePlayerId = verifyMove.getLastMovePlayerId();
    Color turn = Color.fromPlayerOrder(playerIds.indexOf(lastMovePlayerId));
    List<Operation> expectedOperations = Lists.newArrayList();
    
    if (lastApiState.isEmpty()) {
      return getInitialOperations(playerIds.get(0));
    }
    
    AeroplaneChessState lastState = gameApiStateToAeroplaneChessState(lastApiState, 
         turn, playerIds);
    
    List<Piece> playerMovedPieces = getMovedPieces(lastMove, turn);
    List<Piece> opponentMovedPieces = getMovedPieces(lastMove, turn.getOppositeColor());
    
    // Actions: taxi, move, stack, jump, takeShortcut
    if (lastMove.contains(new Set(ACTION, TAXI))) {
      expectedOperations = getOperationsTaxi(
          lastState, 
          playerMovedPieces, 
          opponentMovedPieces, 
          lastMovePlayerId);
    }
    else if (lastMove.contains(new Set(ACTION, MOVE))) {
      expectedOperations = getOperationsMove(
          lastState, 
          playerMovedPieces, 
          opponentMovedPieces, 
          lastMovePlayerId);
    }
    else if (lastMove.contains(new Set(ACTION, STACK))) {
      expectedOperations = getOperationsStack(
          lastState, 
          playerMovedPieces, 
          opponentMovedPieces, 
          lastMovePlayerId);
    }
    else if (lastMove.contains(new Set(ACTION, JUMP))) {
      expectedOperations = getOperationsJump(
          lastState, 
          playerMovedPieces, 
          opponentMovedPieces, 
          lastMovePlayerId);
    } 
    else if (lastMove.contains(new Set(ACTION, TAKE_SHORTCUT))) {
      expectedOperations = getOperationsTakeShortcut(
          lastState, 
          playerMovedPieces, 
          opponentMovedPieces, 
          lastMovePlayerId);
    }
    else {
      check(false, "Player must specify ACTION on every move!");
    }
    
    return expectedOperations;
  }
  
  /** 
   * Returns a GameApi representation of a piece's location for an unstacked, face up piece.
   * (This is the state for the majority of the pieces on the board.) 
   */
  ImmutableList<String> getNewPiece(String location) {
    String region = location.substring(0, 1);
    int space = Integer.parseInt(location.substring(1, 2));
    
    checkArgument((space >= 0)
        && ((region.equals(H) && space < PIECES_PER_PLAYER) 
            || (region.equals(L) && space == 0)) 
            || (region.equals(T) && space < TOTAL_SPACES) 
            || (region.equals(F) && space < TOTAL_FINAL_SPACES));
    
    return ImmutableList.of(location, UNSTACKED, FACEUP);
  }
  
  /**
   * Adds pieces to the board for R and Y players, and rolls the die.
   */
  List<Operation> getInitialOperations(int rId) {
    List<Operation> operations = Lists.newArrayList();
    
    // Order: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves
    operations.add(new SetTurn(rId));
    operations.add(new SetRandomInteger(DIE, DIE_FROM, DIE_TO));
    operations.add(new Set(ACTION, INITIALIZE));
    
    // add Red player's pieces
    for (int i = 0; i <= PIECES_PER_PLAYER; i++) {
      operations.add(new Set(R + i, getNewPiece("H0" + i)));
    }
    
    // add Yellow player's pieces
    for (int i = 0; i <= PIECES_PER_PLAYER; i++) {
      operations.add(new Set(Y + i, getNewPiece("H0" + i)));
    }
    
    // initialize lastTwoRolls and lastTwoMoves
    operations.add(new Set(LAST_TWO_ROLLS, EMPTY_ROLLS));
    operations.add(new Set(LAST_TWO_MOVES, EMPTY_MOVES));
    
    return operations;
  }
  
  void checkMoveIsLegal(VerifyMove verifyMove) {
    // Expected operations differ depending on state of the board and the action
    // chosen by the player.
    List<Operation> expectedOperations = getExpectedOperations(verifyMove);
    List<Operation> lastMove = verifyMove.getLastMove();
    check(expectedOperations.equals(lastMove), expectedOperations, lastMove);
    
    if (verifyMove.getLastState().isEmpty()) {  // Check that the first move is by the red player
      check(verifyMove.getLastMovePlayerId() == verifyMove.getPlayerIds().get(0));
    }
  }
  
  /**
   *  Checks if condition is true, if not then flattens arguments for debugging purposes.
   */
  static void check(boolean val, Object ... debugArguments) {
    if (!val) {
      throw new RuntimeException("We have a hacker! debugArguments="
          + Arrays.toString(debugArguments));
    }
  }
  
  /**
   *  Returns a new Piece from GameApi representation of the piece.
   * ImmutableList.<String>of(location, stacked, faceup) --> Piece(..)  
   */
  Piece gameApiPieceToAeroplaneChessPiece(List<String> piece, int pieceId, String color) {
    String location = piece.get(0);
    String zoneString = location.substring(0, 1);
    int space = -1;
    
    try {
      space = Integer.parseInt(location.substring(1, 3));
    }
    catch (NumberFormatException e) {
      throw new IllegalArgumentException("Location of " + location + " formatted incorrectly");
    }
    
    boolean isStacked = piece.get(1).equals(STACKED);
    boolean isFaceDown = piece.get(2).equals(FACEDOWN);
    
    return new Piece(Zone.fromFirstLetter(zoneString), pieceId, space, 
        Color.fromFirstLetter(color), isStacked, isFaceDown);
  }
  
  /** 
   * Returns GameApi representation of piece from Piece.
   * Piece(...) --> ImmutableList.<String>of(location, stacked, faceup)
   */
  
  ImmutableList<String> pieceToGameApiPiece(Piece piece) {
    String location = piece.getLocation();
    String stacked = piece.isStacked() ? STACKED : UNSTACKED;
    String faceup = piece.isFaceDown() ? FACEDOWN : FACEUP;
    
    return ImmutableList.<String>of(location, stacked, faceup);
  }
  
  /**
   * Returns {@link AeroplaneChessState} from GameApi representation.  
   */
  @SuppressWarnings("unchecked")
  AeroplaneChessState gameApiStateToAeroplaneChessState(Map<String, Object> gameApiState,
      Color turn, List<Integer> playerIds) {
    
    int die = (Integer) gameApiState.get(DIE);
    Action action = Action.fromLowerString((String) gameApiState.get(ACTION));
    
    List<Piece> rPieces = Lists.newArrayList();
    List<Piece> yPieces = Lists.newArrayList();
    List<String> pieceList;
    
    for (int i = 0; i < PIECES_PER_PLAYER; i++) {
      pieceList = (List<String>) gameApiState.get(R + i); 
      rPieces.add(gameApiPieceToAeroplaneChessPiece(pieceList, i, R));
      
      pieceList = (List<String>) gameApiState.get(Y + i); 
      yPieces.add(gameApiPieceToAeroplaneChessPiece(pieceList, i, Y));
    }
    
    List<Integer> lastTwoRolls = (List<Integer>) gameApiState.get(LAST_TWO_ROLLS);
    List<String> lastTwoMoves = (List<String>) gameApiState.get(LAST_TWO_MOVES);
    
    return new AeroplaneChessState(
        turn, 
        ImmutableList.copyOf(playerIds), 
        die, 
        action, 
        ImmutableList.copyOf(rPieces), 
        ImmutableList.copyOf(yPieces), 
        ImmutableList.copyOf(lastTwoRolls), 
        ImmutableList.copyOf(lastTwoMoves));
  }
}