/* 
 * Modeled after Prof. Zibin's CheatLogic.java
 * https://github.com/yoav-zibin/cheat-game/blob/master/eclipse/src/org/cheat/client/CheatLogic.java
 */

package org.aeroplanechess.client;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Arrays;
import java.util.Map;

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
  
  /* Players and space colors (2-player version with R|Y) */
  private static final String R = "R";  // Red
  private static final String Y = "Y";  // Yellow
  
  /* Regions on the board */
  private static final String H = "H";  // Hangar
  private static final String L = "L";  // Launch area
  private static final String T = "T";  // Track
  private static final String F = "F";  // Final stretch
  
  /* Whether piece is face up or face down (only in Hangar can they be face down) */
  private static final String FACEUP = "faceup";
  private static final String FACEDOWN = "facedown";
  
  /* 
   * Whether piece is stacked with 1 or more other pieces (only on track/final stretch)
   * For simplicity I assume that a player can choose to either stack all or none on a space. Once
   * the pieces are stacked, if another piece lands on that space then they must all be stacked. 
   */
  private static final String STACKED = "stacked";
  private static final String UNSTACKED = "unstacked";
  
  /* Actions the player can take */
  private static final String ACTION = "action";  // action: initialize, taxi, move, stack, jump
  private static final String INITIALIZE = "initialize";
  private static final String TAXI = "taxi";
  private static final String MOVE = "move";
  private static final String STACK = "stack";
  private static final String TAKE_SHORTCUT = "takeShortcut";
  private static final String JUMP = "jump";

  /* Information needed to send pieces moved on last two moves to hangar (if 3 6's are rolled) */
  private static final String LAST_TWO_ROLLS = "lastTwoRolls";
  private static final String LAST_TWO_MOVES = "lastTwoMoves";
  
  /* Empty representations for lastTwoRolls and lastTwoMoves */
  private final List<Integer> emptyRolls = ImmutableList.of(-1, -1);
  private final List<String> emptyMoves = ImmutableList.of("", "");
  
  /* Die info. Range is [DIE_FROM, DIE_TO). */
  private static final String DIE = "die";
  private static final int DIE_FROM = 1;
  private static final int DIE_TO = 7;
  
  /* Board and player numerics */
  private static final int SHORTCUT_AMOUNT = 12;
  private static final int JUMP_AMOUNT = 4;
  private static final int TOTAL_SPACES = 52;
  private static final int TOTAL_FINAL_SPACES = 6;
  private static final int WIN_FINAL_SPACE = 5;
  private static final int PIECES_PER_PLAYER = 4;

  /* 
   * The location of the planes in the final stretch that are sent back to the hangar
   * if another plane takes a shortcut crossing this path. 
   */
  private static final String SHORTCUT_FINAL_INTERSECTION = "F02";
  
  /* Track spaces run clockwise from (T00) Red, Blue, Yellow, Green, Red, ... Green (T51) */
  private Color getTrackSpaceColor(int space) {
    checkArgument(space >= 0 && space < TOTAL_SPACES);
    return Color.values()[space % PIECES_PER_PLAYER];
  }
  
  /* Names of the various GameApi operations */
  private static final String SET = "Set";
  
  /*
   * A player can take a shortcut if his piece lands on the shortcut space of his color.
   * The shortcut spaces are: Y: T10, G: T23, R: T36, B: T49
   */
  private boolean isShortcutAvailable(Zone zone, int space, Color turn) {
    checkArgument(zone == Zone.TRACK);  // Shortcuts are only available from the track
    int start = 10, offset = 13;
    return ((space - start) % offset == 0) && (turn == getTrackSpaceColor(space));
  }
  
  /* 
   * If a piece/pieces lands on a space of the same color, it will automatically jump 4 spaces to 
   * the next space of that color.
   */
  private boolean isJumpAvailable(Zone zone, int space, Color turn) {
    checkArgument(zone == Zone.TRACK);  // Jumps are only available from the track
    return turn == getTrackSpaceColor(space);
  }
  
  /* 
   * Players can stack (or unstack) on the next move if any pieces are on the space where they 
   * attempt to move.
   */
  private boolean isStackAvailable(Zone zone, int space, List<Piece> pieces) {
    for (Piece piece : pieces) {
      if (piece.getSpace() == space && piece.getZone() == zone) {
        return true;
      }
    }
    return false;
  }
  
  /*
   * Each player's final stretch starts at a different track space. For now we only have 2 players,
   * R and Y (they start at T16 and T42, respectively).
   */
  private int getFinalStretchStart(Color turn) {
    int start = 16, offset = 13;
    int turnInt = turn.ordinal();
    return (start + (offset * turnInt)) % TOTAL_SPACES;
  }
  
  public VerifyMoveDone verify(VerifyMove verifyMove) {
    try {
      checkMoveIsLegal(verifyMove);
      return new VerifyMoveDone();
    } catch (Exception e) {
      return new VerifyMoveDone(verifyMove.getLastMovePlayerId(), e.getMessage());
    }
  }
  
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
  
  /* Get the Piece that was last moved, as it was *before* it was moved */
  private Piece getStatePiece(AeroplaneChessState state, Color turn, int pieceId) {
    return state.getPieces(turn).get(pieceId);
  }
  
  /* 
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
  
  private List<Piece> getOpponentPiecesOnSpace(List<Piece> opponentPieces, String location) {
    List<Piece> piecesToMove = Lists.newArrayList();
    
    for (Piece piece : opponentPieces) {
      if (piece.getLocation().equals(location)) {
        piecesToMove.add(piece);
      }
    }
    
    return piecesToMove;
  }
  
  /* 
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
  
  /*
   * lastTwoMoves stores the strings containing pieces that were moved on the last two moves or
   * an empty string if there was no move. Values are shifted over as in lastTwoRolls.
   */
  private List<String> getNewLastTwoMoves(AeroplaneChessState state, String pieces) {
    List<String> lastTwoMoves = state.getLastTwoMoves();
    
    return Lists.newArrayList(pieces, lastTwoMoves.get(0));
  }
  
  List<Operation> getOperationsTaxi(AeroplaneChessState state, List<Operation> lastMove, 
      Color turn, int playerId) {
    List<Operation> expectedOperations;
    List<Piece> playerMovedPieces = getMovedPieces(lastMove, turn);
    List<Piece> opponentMovedPieces = getMovedPieces(lastMove, turn.getOppositeColor());
    
    check(state.getDie() % 2 == 0, 
        "Illegal to TAXI on an odd roll. lastMove=", lastMove);
    check(opponentMovedPieces.isEmpty(), 
        "Illegal to move opponent's pieces on TAXI. lastMove=", lastMove);
    check(playerMovedPieces.size() == 1, 
        "You must TAXI only one piece at a time. lastMove=", lastMove);
    
    // Check that the piece that was moved was previously in the hangar
    Piece movedPiece = playerMovedPieces.get(0);
    int pieceId = movedPiece.getPieceId();
    check(getStatePiece(state, turn, pieceId).getZone() == Zone.HANGAR, 
        "Illegal to taxi piece not in Hangar. lastMove=", lastMove);
    
    /* 
     * On a taxi move, you should set turn to the other player (and roll die for them) unless
     * you previously rolled a 6.  
     * If you rolled a 6, you should set turn to yourself and roll the die again. 
     */
    int die = state.getDie();
    List<Integer> playerIds = state.getPlayerIds();
    int oppositeId = playerIds.get(0) == playerId ? playerIds.get(1) : playerIds.get(0);
    
    if (die == 6) {
      expectedOperations = ImmutableList.<Operation>of(
          new SetTurn(playerId),
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, TAXI),
          new Set(turn.name() + pieceId, getNewPiece(movedPiece.getLocation())),
          new Set(LAST_TWO_ROLLS, getNewLastTwoRolls(state, die)),
          new Set(LAST_TWO_MOVES, getNewLastTwoMoves(state, Integer.toString(pieceId))));
    }
    else {
      expectedOperations = ImmutableList.<Operation>of(
          new SetTurn(oppositeId),
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, TAXI),
          new Set(turn.name() + pieceId, getNewPiece(movedPiece.getLocation())),
          new Set(LAST_TWO_ROLLS, emptyRolls),
          new Set(LAST_TWO_MOVES, emptyMoves));
    }
    
    return expectedOperations;
  }
  
  /* True if last three rolls were all 6's.  Must send affected pieces back to hangar. */
  private boolean rolledThreeSixes(int die, AeroplaneChessState state) {
    return (die == 6 && state.getLastTwoRolls().equals(ImmutableList.of(6, 6)));
  }
  
  private List<String> backtrackGameApiPiece(Piece piece, String newLocation) {
    String stacked = piece.isStacked() ? STACKED : UNSTACKED;
    String faceup = piece.isFaceDown() ? FACEDOWN : FACEUP;
    
    return ImmutableList.<String>of(newLocation, stacked, faceup);
  }
  
  List<Operation> getOperationsMove(AeroplaneChessState state, List<Operation> lastMove, 
      Color turn, int playerId) {
    List<Operation> expectedOperations;
    List<Piece> playerMovedPieces = getMovedPieces(lastMove, turn);
    int die = state.getDie();
    
    if (playerMovedPieces.size() > 1) {
    /* Check that if there are multiple pieces moved, they are all stacked
     * You can only "move" multiple unstacked pieces if you rolled 3 6's and need to send
     * them back to the hangar.
     */
      check(rolledThreeSixes(die, state) || moveAllStacked(playerMovedPieces, state, turn), 
            "Illegal to move multiple unstacked pieces. lastMove=", lastMove);
    }
    else if (playerMovedPieces.size() == 1) {
      // Check that if you moved one piece, it is not stacked with others
      check(!getStatePiece(state, turn, playerMovedPieces.get(0).getPieceId()).isStacked(),
          "Illegal to move single piece out of stacked pieces. lastMove=", lastMove);
    }
    else {
      check(false, "No pieces moved in lastMove=", lastMove);
    }
    
    // Check that the pieces are all still faceup *IF* you are not moving to the hangar
    for (Piece piece : playerMovedPieces) {
      check(piece.getZone() == Zone.HANGAR || !piece.isFaceDown(), 
          "Illegal to set to facedown unless in Hangar. lastMove=", lastMove);
    }
    
    Piece movedPiece = playerMovedPieces.get(0);
    Piece statePiece = getStatePiece(state, turn, movedPiece.getPieceId());
    Zone stateZone = statePiece.getZone();
    Zone movedZone = movedPiece.getZone();
    int originalSpace = statePiece.getSpace();
    int movedSpace = movedPiece.getSpace();
    List<Integer> playerIds = state.getPlayerIds();
    int oppositeId = playerIds.get(0) == playerId ? playerIds.get(1) : playerIds.get(0);
    
    /* If you rolled an inexact roll, then you must backtrack. This can only happen if you were
     *  in the final stretch.
     */
    if (stateZone == Zone.FINAL_STRETCH && originalSpace + die != WIN_FINAL_SPACE) {
      List<Operation> operations = Lists.newArrayList(
          new SetTurn(oppositeId), 
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, MOVE));
      // Backtrack pieces
      for (Piece piece : playerMovedPieces) {
        if (originalSpace - die < 0) {  // Have to backtrack to Track zone
          int offset = die - originalSpace;
          int finalStretchStart = getFinalStretchStart(turn);
          operations.add(new Set(
              turn.name() + piece.getPieceId(), 
              backtrackGameApiPiece(
                  piece, 
                  T + String.format("%02d", (finalStretchStart - offset)))));
        }
        else {  // Can stay in Final Stretch
          operations.add(new Set(
              turn.name() + piece.getPieceId(), 
              backtrackGameApiPiece(piece, F + String.format("%02d", (originalSpace - die)))));
        }
      }
      
      operations.add(new Set(LAST_TWO_ROLLS, emptyRolls));
      operations.add(new Set(LAST_TWO_MOVES, emptyMoves));
       
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
            "Moved illegal amount of spaces. lastMove=", lastMove);
      }
      else if (stateZone == Zone.HANGAR || stateZone == Zone.LAUNCH) { 
        check(false, 
            "Illegal move to final stretch from non-track space. lastMove=", lastMove);
      }
      else {  // Track to Final Stretch
        int finalStretchStart = getFinalStretchStart(turn);
        check(finalStretchStart == (((originalSpace + die) % TOTAL_SPACES) - movedSpace - 1),
            "Illegal number of spaces moved. lastMove=", lastMove);
      }
    }
    else if (movedZone == Zone.TRACK) {
      if (stateZone == Zone.LAUNCH) {
        int start = 19, offset = 13;
        int turnInt = turn.ordinal();
        int launchStart = (start + (offset * turnInt)) % TOTAL_SPACES;
        check(launchStart + die == movedSpace, 
            "Moved incorrect spaces from launch. lastMove=", lastMove);
      }
      else if (stateZone == Zone.TRACK) {
        check(Math.abs(movedSpace - originalSpace) % TOTAL_SPACES == die, 
            "Moved incorrect amount of spaces. lastMove=", lastMove);
      }
      else {
        check(false, "Illegal move to track. lastMove=", lastMove);
      }
    }
    else if (movedZone == Zone.HANGAR) {
      // Moving to the Hangar requires them all to be facedown (unless it is from a triple 6 roll)
      if (!rolledThreeSixes(die, state)) {
        for (Piece piece : playerMovedPieces) {
          check(piece.isFaceDown(), 
              "Must set all pieces to facedown. lastMove=", lastMove);
        }
        
        /*
         * If the die roll is exactly the same number as needed to reach F05 (automatically goes to
         * Hangar), and ALL other pieces are already facedown in the Hangar, then we win.
         * Otherwise if the roll is inexact then player must backtrack that many steps.
         */
        // You must start exactly on the start of the final stretch, or in it
        int finalStretchStart = getFinalStretchStart(turn);
        if (originalSpace == finalStretchStart || stateZone == Zone.FINAL_STRETCH) {
          check((originalSpace == finalStretchStart && die == 6)
              || stateZone == Zone.FINAL_STRETCH && (originalSpace + die == WIN_FINAL_SPACE), 
            "Can't win on inexact spaces. lastMove=", lastMove);
          
          List<Operation> operations = Lists.newArrayList(
              new SetTurn(playerId),
              new Set(ACTION, MOVE));
          // Add player pieces - no opponent pieces necessary
          for (Piece piece : playerMovedPieces) {
            operations.add(
                new Set(turn.name() + piece.getPieceId(), pieceToGameApiPiece(piece)));
          }
          operations.add(new EndGame(playerId));

          return operations;
        }
        else { 
          check(false, "Moved illegal amount of spaces to Hangar. lastMove=", lastMove);
        }
      }
    }
    else {
      check(false, "Cannot use action MOVE with this zone. lastMove=", lastMove);
    }
    
    /*
     * If you rolled a third 6: must send all pieces affected by the last two rolls back to the 
     * hangar and pass turn to the other player.
     */
    List<String> lastTwoMoves = state.getLastTwoMoves();
    
    if (rolledThreeSixes(die, state)) {
      List<Operation> operations = Lists.newArrayList(
          new SetTurn(oppositeId),  
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO), // Roll die for other player
          new Set(ACTION, MOVE));
      
      for (int i = 0; i < PIECES_PER_PLAYER; i++) {
        if (lastTwoMoves.get(0).contains(Integer.toString(i)) 
              || lastTwoMoves.get(1).contains(Integer.toString(i))) {
          operations.add(new Set(turn.name() + i, getNewPiece(H + String.format("%02d", i))));
        }
      }
      
      operations.add(new Set(LAST_TWO_ROLLS, emptyRolls));
      operations.add(new Set(LAST_TWO_MOVES, emptyMoves));
      
      return operations;
    }
    
    // Get any opponent's pieces that should be moved on landing
    List<Piece> stateOpponentPieces = state.getPieces(turn.getOppositeColor());
    List<Piece> opponentPiecesToMove = getOpponentPiecesOnSpace(
        stateOpponentPieces, 
        movedZone.name().substring(0, 1) + movedSpace);
    
    /* If you rolled a (first or second) 6:
     * If a stack, shortcut, or jump is available after this move, then don't roll the die
     * (because that will be taken care of after the sequence of moves).  Otherwise, roll the die.
     * If you did not roll a 6:
     * Set turn to the other player and roll for them.
     */
    if (die == 6) {
      expectedOperations = Lists.newArrayList();
      expectedOperations.add(new SetTurn(playerId));
      if (!isShortcutAvailable(movedZone, movedSpace, turn)
            && !isJumpAvailable(movedZone, movedSpace, turn)
            && !isStackAvailable(movedZone, movedSpace, state.getPieces(turn))) {
        expectedOperations.add(new SetRandomInteger(DIE, DIE_FROM, DIE_TO));
      }
      expectedOperations.add(new Set(ACTION, MOVE));
      
      // Add player piece movements
      String playerMovedString = "";
      for (Piece piece : playerMovedPieces) {
        expectedOperations.add(
            new Set(turn.name() + piece.getPieceId(), pieceToGameApiPiece(piece)));
        playerMovedString += piece.getPieceId();
      }
      // Add any opponent piece movements (if they were in player's way - send to Hangar)
      for (Piece piece : opponentPiecesToMove) {
        int pieceId = piece.getPieceId();
        expectedOperations.add(
            new Set(turn.getOppositeColor().name() + pieceId,
                getNewPiece(H + String.format("%02d",  pieceId))));
      }
      expectedOperations.add(new Set(LAST_TWO_ROLLS, getNewLastTwoRolls(state, die)));
      expectedOperations.add(new Set(LAST_TWO_MOVES, getNewLastTwoMoves(state, playerMovedString)));
    }
    else {
      expectedOperations = Lists.newArrayList(
          new SetTurn(oppositeId),
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, MOVE));
      // Add player piece movements
      for (Piece piece : playerMovedPieces) {
        expectedOperations.add(
            new Set(turn.name() + piece.getPieceId(), pieceToGameApiPiece(piece)));
      }
      // Add any opponent piece movements (if they were in player's way - send to Hangar)
      for (Piece piece : opponentPiecesToMove) {
        int pieceId = piece.getPieceId();
        expectedOperations.add(
            new Set(turn.getOppositeColor().name() + pieceId,
                getNewPiece(H + String.format("%02d",  pieceId))));
      }
      expectedOperations.add(new Set(LAST_TWO_ROLLS, emptyRolls));
      expectedOperations.add(new Set(LAST_TWO_MOVES, emptyMoves));
    }
    
    return expectedOperations;
  }
  
  List<Operation> getOperationsStack(AeroplaneChessState state, List<Operation> lastMove, 
      Color turn, int playerId) {
    List<Operation> expectedOperations;
    List<Piece> playerMovedPieces = getMovedPieces(lastMove, turn);
    List<Piece> opponentMovedPieces = getMovedPieces(lastMove, turn.getOppositeColor());
    
    check(opponentMovedPieces.isEmpty(), 
        "Illegal to move opponent's pieces on STACK. lastMove=", lastMove);
    check(playerMovedPieces.size() > 1, 
        "You must stack more than one piece. lastMove=", lastMove);
    
    // Check that stacking does not change location of the pieces
    for (Piece piece: playerMovedPieces) {
      check(
          piece.getLocation().equals(getStatePiece(state, turn, piece.getPieceId()).getLocation()),
          "Illegal to change position of pieces while stacking. lastMove=", lastMove);
    }
    
    // Check that all the pieces moved are on the same space
    String stackLocation = playerMovedPieces.get(0).getLocation();
    for (Piece piece : playerMovedPieces) {
      check(piece.getLocation().equals(stackLocation),
          "Illegal to attempt stack on pieces at different positions. lastMove=", lastMove);
    }
    
    /*
     *  Check that there are no other pieces on the same space except the ones that you attempt to
     *  move (since you must stack all or none)
     */
    List<Piece> playerStatePieces = state.getPieces(turn);
    for (Piece statePiece : playerStatePieces) {
      if (statePiece.getLocation().equals(stackLocation)) {
        check(playerMovedPieces.contains(statePiece), 
            "Did not stack all the pieces on the space. lastMove=", lastMove);
      }
    }
    
    /*
     * Check that the one of the pieces stacked was moved last (you can't stack if you didn't just
     * land on that space)
     */
    String lastMovedIds = state.getLastTwoMoves().get(0);
    boolean found = false;
    for (Piece piece : playerMovedPieces) {
      if (lastMovedIds.contains(Integer.toString(piece.getPieceId()))) {
        found = true;
      }
    }
    check(found, "Attempting to stack pieces not moved last. lastMove=", lastMove);
    
    // Check that the pieces are all still faceup
    for (Piece piece : playerMovedPieces) {
      check(!piece.isFaceDown(), "You can only set facedown in the Hangar. lastMove=", lastMove);
    }
    
    /* 
     * On a stack move, you should set turn to the other player (and roll die for them) unless
     * you previously rolled a 6.  
     * If you rolled a 6, you should set turn to yourself and roll the die again. 
     */
    int die = state.getDie();
    List<Integer> playerIds = state.getPlayerIds();
    int oppositeId = playerIds.get(0) == playerId ? playerIds.get(1) : playerIds.get(0);
    String playerMoves = "";
    
    if (die == 6) {
      expectedOperations = Lists.newArrayList(
          new SetTurn(playerId),
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, STACK));
      // Add player piece movements
      for (Piece piece : playerMovedPieces) {
        expectedOperations.add(
            new Set(turn.name() + piece.getPieceId(), pieceToGameApiPiece(piece)));
        playerMoves += piece.getPieceId();
      }
      
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
            ImmutableList.<String>of(playerMoves, state.getLastTwoMoves().get(1))));
      }
      
    }
    else {
      expectedOperations = Lists.newArrayList(
          new SetTurn(oppositeId),
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, STACK));
      // Add player piece movements
      for (Piece piece : playerMovedPieces) {
        expectedOperations.add(
            new Set(turn.name() + piece.getPieceId(), pieceToGameApiPiece(piece)));
        playerMoves += piece.getPieceId();
      }
      expectedOperations.add(new Set(LAST_TWO_ROLLS, emptyRolls));
      expectedOperations.add(new Set(LAST_TWO_MOVES, emptyMoves));
    }
    
    return expectedOperations;
  }
  
  List<Operation> getOperationsJump(AeroplaneChessState state, List<Operation> lastMove, 
      Color turn, int playerId) {
    List<Operation> expectedOperations;
    List<Piece> playerMovedPieces = getMovedPieces(lastMove, turn);
    
    if (playerMovedPieces.size() > 1) {
    // Check that if there are multiple pieces moved, they are all stacked
      check(moveAllStacked(playerMovedPieces, state, turn), 
            "Illegal to move multiple unstacked pieces. lastMove=", lastMove);
    }
    else if (playerMovedPieces.size() == 1) {
      // Check that if you moved one piece, it is not stacked with others
      check(!getStatePiece(state, turn, playerMovedPieces.get(0).getPieceId()).isStacked(),
          "Illegal to move single piece out of stacked pieces. lastMove=", lastMove);
    }
    else {
      // Unlike TAKE_SHORTCUT, there must be pieces set in a Jump action
      check(false, "No pieces moved in lastMove=", lastMove);
    }
    
    // Check that the pieces are all still faceup
    for (Piece piece : playerMovedPieces) {
      check(!piece.isFaceDown(), "Illegal to set to facedown on the track. lastMove=", lastMove);
    }
    
    /* Check that the pieces moved were previously on a valid jump space (if one is,
     * then they all are, since the stacked test was passed)
     */
    int movedPieceId = playerMovedPieces.get(0).getPieceId();
    Piece lastPiece = getStatePiece(state, turn, movedPieceId);
    check(isJumpAvailable(lastPiece.getZone(), lastPiece.getSpace(), turn),
        "Jump not available on that space. lastMove=", lastMove);
    
    // Get any opponent's pieces that should be moved on landing
    List<Piece> stateOpponentPieces = state.getPieces(turn.getOppositeColor());
    List<Piece> opponentPiecesToMove = getOpponentPiecesOnSpace(
        stateOpponentPieces, 
        T + String.format("%02d", ((lastPiece.getSpace() + JUMP_AMOUNT) % TOTAL_SPACES)));
    
    /* 
     * On a jump move, you should set turn to the other player (and roll die for them) unless
     * you previously rolled a 6.  
     * If you rolled a 6, you should set turn to yourself and roll the die again. 
     */
    int die = state.getDie();
    List<Integer> playerIds = state.getPlayerIds();
    int oppositeId = playerIds.get(0) == playerId ? playerIds.get(1) : playerIds.get(0);
    
    if (die == 6) {
      expectedOperations = Lists.newArrayList(
          new SetTurn(playerId),
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, JUMP));
      // Add player piece movements
      for (Piece piece : playerMovedPieces) {
        expectedOperations.add(
            new Set(turn.name() + piece.getPieceId(), pieceToGameApiPiece(piece)));
      }
      // Add any opponent piece movements (if they were in player's way - send to Hangar)
      for (Piece piece : opponentPiecesToMove) {
        int pieceId = piece.getPieceId();
        expectedOperations.add(
            new Set(turn.getOppositeColor().name() + pieceId,
                getNewPiece(H + String.format("%02d",  pieceId))));
      }
      expectedOperations.add(new Set(LAST_TWO_ROLLS, getNewLastTwoRolls(state, die)));
      // A jump move always follows an actual move (or move --> stack, etc.),
      // so you don't have to change the lastTwoMoves since the pieces moved are the same.
      expectedOperations.add(new Set(LAST_TWO_MOVES, state.getLastTwoMoves()));
    }
    else {
      expectedOperations = Lists.newArrayList(
          new SetTurn(oppositeId),
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, JUMP));
      // Add player piece movements
      for (Piece piece : playerMovedPieces) {
        expectedOperations.add(
            new Set(turn.name() + piece.getPieceId(), pieceToGameApiPiece(piece)));
      }
      // Add any opponent piece movements (if they were in player's way - send to Hangar)
      for (Piece piece : opponentPiecesToMove) {
        int pieceId = piece.getPieceId();
        expectedOperations.add(
            new Set(turn.getOppositeColor().name() + pieceId,
                getNewPiece(H + String.format("%02d",  pieceId))));
      }
      expectedOperations.add(new Set(LAST_TWO_ROLLS, emptyRolls));
      expectedOperations.add(new Set(LAST_TWO_MOVES, emptyMoves));
    }
    
    return expectedOperations;
  }
  

  
  List<Operation> getOperationsTakeShortcut(AeroplaneChessState state, List<Operation> lastMove,
      Color turn, int playerId) {
    List<Operation> expectedOperations;
    List<Piece> playerMovedPieces = getMovedPieces(lastMove, turn);
    int die = state.getDie(); 
    List<Integer> playerIds = state.getPlayerIds();
    int oppositeId = playerIds.get(0) == playerId ? playerIds.get(1) : playerIds.get(0);
    
    if (playerMovedPieces.size() > 1) {
    // Check that if there are multiple pieces moved, they are all stacked
      check(moveAllStacked(playerMovedPieces, state, turn), 
            "Illegal to move multiple unstacked pieces. lastMove=", lastMove);
    }
    else if (playerMovedPieces.size() == 1) {
      // Check that if you moved one piece, it is not stacked with others
      check(!getStatePiece(state, turn, playerMovedPieces.get(0).getPieceId()).isStacked(),
          "Illegal to move single piece out of stacked pieces. lastMove=", lastMove);
    }
    else {
      /* Not moving any pieces on TAKE_SHORTCUT action signifies "giving up" the shortcut.
       * If player rolled a 6, then 
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
            new Set(LAST_TWO_ROLLS, emptyRolls),
            new Set(LAST_TWO_MOVES, emptyMoves));
      }
      return expectedOperations;
    }
    
    // Check that the pieces are all still faceup
    for (Piece piece : playerMovedPieces) {
      check(!piece.isFaceDown(), "Illegal to set to facedown on the track. lastMove=", lastMove);
    }
    
    /* Check that the pieces moved were previously on a valid shortcut space (if one is,
     * then they all are, since the stacked test was passed)
     */
    int movedPieceId = playerMovedPieces.get(0).getPieceId();
    Piece lastPiece = getStatePiece(state, turn, movedPieceId);
    check(isShortcutAvailable(lastPiece.getZone(), lastPiece.getSpace(), turn),
        "Shortcut not available on that space. lastMove=", lastMove);
    
    // Get any opponent's pieces that should be moved on landing
    List<Piece> stateOpponentPieces = state.getPieces(turn.getOppositeColor());
    List<Piece> opponentPiecesToMove = getOpponentPiecesOnSpace(
        stateOpponentPieces, 
        T + String.format("%02d", ((lastPiece.getSpace() + SHORTCUT_AMOUNT) % TOTAL_SPACES)));
    // Add opponent's piece(s) that were in the way of the shortcut (in the final stretch)
    opponentPiecesToMove.addAll(getOpponentPiecesOnSpace(
        stateOpponentPieces, 
        SHORTCUT_FINAL_INTERSECTION));
    
    /* 
     * On a shortcut move, you should set turn to the other player (and roll die for them) unless
     * you previously rolled a 6.  
     * If you rolled a 6, you should set turn to yourself and roll the die again. 
     */
    if (die == 6) {
      expectedOperations = Lists.newArrayList(
          new SetTurn(playerId),
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, TAKE_SHORTCUT));
      // Add player piece movements
      for (Piece piece : playerMovedPieces) {
        expectedOperations.add(
            new Set(turn.name() + piece.getPieceId(), pieceToGameApiPiece(piece)));
      }
      // Add any opponent piece movements (if they were in player's way - send to Hangar)
      for (Piece piece : opponentPiecesToMove) {
        int pieceId = piece.getPieceId();
        expectedOperations.add(
            new Set(turn.getOppositeColor().name() + pieceId,
                getNewPiece(H + String.format("%02d",  pieceId))));
      }
      expectedOperations.add(new Set(LAST_TWO_ROLLS, getNewLastTwoRolls(state, die)));
      // A shortcut move always follows an actual move (or move --> stack, etc.),
      // so you don't have to change the lastTwoMoves since the pieces moved are the same.
      expectedOperations.add(new Set(LAST_TWO_MOVES, state.getLastTwoMoves()));
    }
    else {
      expectedOperations = Lists.newArrayList(
          new SetTurn(oppositeId),
          new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
          new Set(ACTION, TAKE_SHORTCUT));
      // Add player piece movements
      for (Piece piece : playerMovedPieces) {
        expectedOperations.add(
            new Set(turn.name() + piece.getPieceId(), pieceToGameApiPiece(piece)));
      }
      // Add any opponent piece movements (if they were in player's way - send to Hangar)
      for (Piece piece : opponentPiecesToMove) {
        int pieceId = piece.getPieceId();
        expectedOperations.add(
            new Set(turn.getOppositeColor().name() + pieceId,
                getNewPiece(H + String.format("%02d",  pieceId))));
      }
      expectedOperations.add(new Set(LAST_TWO_ROLLS, emptyRolls));
      expectedOperations.add(new Set(LAST_TWO_MOVES, emptyMoves));
    }
    
    return expectedOperations;
  }
  
  /*
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
    
    if (lastApiState.isEmpty()) {
      return getInitialOperations(playerIds.get(0));
    }
    
    AeroplaneChessState lastState = gameApiStateToAeroplaneChessState(lastApiState, 
         turn, playerIds);
    
    // Actions: taxi, move, stack, jump, takeShortcut
    if (lastMove.contains(new Set(ACTION, TAXI))) {
      return getOperationsTaxi(lastState, lastMove, turn, lastMovePlayerId);
    }
    else if (lastMove.contains(new Set(ACTION, MOVE))) {
      return getOperationsMove(lastState, lastMove, turn, lastMovePlayerId);
    }
    else if (lastMove.contains(new Set(ACTION, STACK))) {
      return getOperationsStack(lastState, lastMove, turn, lastMovePlayerId);
    }
    else if (lastMove.contains(new Set(ACTION, JUMP))) {
      return getOperationsJump(lastState, lastMove, turn, lastMovePlayerId);
    } 
    else if (lastMove.contains(new Set(ACTION, TAKE_SHORTCUT))) {
      return getOperationsTakeShortcut(lastState, lastMove, turn, lastMovePlayerId);
    } /*
    else {
      check(false, "Player must specify ACTION on every move!");
    }*/
    return null;
  }
  
  /* 
   * Returns a GameApi representation of a piece's location for an unstacked, face up piece
   * (This is the state for the majority of the pieces on the board.) 
   */
  protected ImmutableList<String> getNewPiece(String location) {
    String region = location.substring(0, 1);
    int space = Integer.parseInt(location.substring(1, 2));
    
    checkArgument((space >= 0)
        && ((region.equals(H) && space < PIECES_PER_PLAYER) 
            || (region.equals(L) && space < PIECES_PER_PLAYER)) 
            || (region.equals(T) && space < TOTAL_SPACES) 
            || (region.equals(F) && space < TOTAL_FINAL_SPACES));
    
    return ImmutableList.of(location, UNSTACKED, FACEUP);
  }
  
  /*
   * Adds pieces to the board for R and Y players, and rolls the die
   */
  protected List<Operation> getInitialOperations(int rId) {
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
    for (int i = 0; i <= 4; i++) {
      operations.add(new Set(Y + i, getNewPiece("H0" + i)));
    }
    
    // initialize lastTwoRolls and lastTwoMoves
    operations.add(new Set(LAST_TWO_ROLLS, emptyRolls));
    operations.add(new Set(LAST_TWO_MOVES, emptyMoves));
    
    return operations;
  }
  
  protected void checkMoveIsLegal(VerifyMove verifyMove) {
    // Expected operations differ depending on state of the board and the action
    // chosen by the player.
    List<Operation> expectedOperations = getExpectedOperations(verifyMove);
    List<Operation> lastMove = verifyMove.getLastMove();
    check(expectedOperations.equals(lastMove), expectedOperations, lastMove);
    
    if (verifyMove.getLastState().isEmpty()) {  // Check that the first move is by the red player
      check(verifyMove.getLastMovePlayerId() == verifyMove.getPlayerIds().get(0));
    }
  }
  
  private void check(boolean val, Object ... debugArguments) {
    if (!val) {
      throw new RuntimeException("We have a hacker! debugArguments="
          + Arrays.toString(debugArguments));
    }
  }
  
  /* Returns a new Piece from GameApi representation of the piece
   * ImmutableList.<String>of(location, stacked, faceup) --> Piece(..)  
   */
  private Piece gameApiPieceToAeroplaneChessPiece(List<String> piece, int pieceId, String color) {
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
  
  /* 
   * Returns GameApi representation of piece from Piece.
   * Piece(...) --> ImmutableList.<String>of(location, stacked, faceup)
   */
  
  private ImmutableList<String> pieceToGameApiPiece(Piece piece) {
    String location = piece.getLocation();
    String stacked = piece.isStacked() ? STACKED : UNSTACKED;
    String faceup = piece.isFaceDown() ? FACEDOWN : FACEUP;
    
    return ImmutableList.<String>of(location, stacked, faceup);
  }
  
  @SuppressWarnings("unchecked")
  private AeroplaneChessState gameApiStateToAeroplaneChessState(Map<String, Object> gameApiState,
      Color turn, List<Integer> playerIds) {
    
    int die = (int) gameApiState.get(DIE);
    Action action = Action.fromLowerString((String) gameApiState.get(ACTION));
    
    List<Piece> rPieces = Lists.newArrayList();
    List<Piece> yPieces = Lists.newArrayList();
    List<String> pieceList;
    
    for (int i = 0; i < 4; i++) {
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