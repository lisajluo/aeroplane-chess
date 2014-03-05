/*
 * Modeled after Prof. Zibin's CheatPresenter.java 
 * https://github.com/yoav-zibin/cheat-game/blob/master/eclipse/src/org/cheat/client/CheatPresenter.java
 */

package org.aeroplanechess.client;

import java.util.List;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;

import org.aeroplanechess.client.GameApi.UpdateUI;
import org.aeroplanechess.client.GameApi.Container;
import org.aeroplanechess.client.GameApi.Operation;
import org.aeroplanechess.client.GameApi.SetTurn;

import org.aeroplanechess.client.Piece.Zone;

import static org.aeroplanechess.client.Constants.*;
import static org.aeroplanechess.client.AeroplaneChessLogic.check;

/**
 * The presenter controlling the graphics in Aeroplane Chess.
 * In the MVP pattern, the Model is AeroplaneChessState,
 * the View (implementing AeroplaneChess.View) will contain the graphics,
 * and the Presenter is AeroplaneChessPresenter.
 */
public class AeroplaneChessPresenter {
  /**
   * Messages passed between the View and Presenter (mutually exclusive):
   * ROLL_AVAILABLE: if we are at the beginning of a turn and need to show the die roll.
   *                 TAXI and MOVE are also taken care of from this stage.
   * STACK_AVAILABLE: player just moved/jumped/took shortcut and can now stack/unstack
   * SHORTCUT_AVAILABLE: player just moved/jumped/stacked and can now take shortcut (or not)
   * JUMP_AVAILABLE: player just moved/stacked and now jump will automatically occur
   * OTHER_TURN: it's the turn of the other player, so nothing is available
   */
  public enum AeroplaneChessMessage {
    ROLL_AVAILABLE, STACK_AVAILABLE, SHORTCUT_AVAILABLE, JUMP_AVAILABLE, OTHER_TURN
  }

  public interface View {
    /**
     * Sets the presenter. The viewer will call certain methods on the presenter:
     * When the player "rolls" the die (it's been rolled but view needs to show it) 
     * - {@link #dieRolled}
     * When the player takes any of the following actions:
     * - {@link #piecesSelected}
     * - {@link #stackSelected}
     * - {@link #shortcutSelected}
     * When the player has to jump (the move is automatic but View can show to the player):
     * - {@link #showJump}
     * The presenter calls certain methods on the view:
     * When the presenter needs to pass which pieces are available to move/taxi. 
     * - {@link #choosePieces}
     * 
     * A simple move might look as follows to the viewer:
     * 1) The viewer calls {@link #dieRolled}. Nothing in the underlying state changes, but now the 
     *    value of the die roll, as well as what pieces are available to be moved, are passed to 
     *    the viewer.
     * 2) Suppose the roll was a 3, and there are planes on the track. Now the viewer calls
     *    {@link #piecesSelected} to move a piece on the board.
     * 3) Suppose a stack move is available. Now the viewer calls {@link #stackSelected} with his
     *    choice to stack/unstack
     *
     * To the presenter:
     * 1) (After the die roll) The presenter calls {@link #choosePieces} and passes the currently 
     *    available pieces (that can be moved or taxi'd).
     * 2) (After the move) If a stack/shortcut is available on the space where the player landed,
     *    the view will show a button presenting the choice to the player. Otherwise, if a jump
     *    is available, the presenter tells the view (which can show to the player) and automates
     *    the move.
     */
    void setPresenter(AeroplaneChessPresenter aeroplaneChessPresenter);

    /** Sets the state for a viewer, i.e., not one of the players. */
    void setViewerState(List<Piece> redPieces, List<Piece> yellowPieces, int die,
        AeroplaneChessMessage aeroplaneChessMessage);

    /**
     * Sets the state for a player (whether the player has the turn or not).
     * Whether or not certain board interactions are available (ie., TAXI, TAKE_SHORTCUT, STACK, 
     * etc.) depends on AeroplaneChessMessage.
     */
    void setPlayerState(List<Piece> myPieces, List<Piece> opponentPieces, int die,
        AeroplaneChessMessage aeroplaneChessMessage);
    
    /**
     * Asks the player to choose which pieces to taxi or move. The player will select using
     * {@link #piecesSelected}.  Implicit in the piece selection is the possible action 
     * (since you can only taxi in the Hangar/move in track).
     * @param backToHangar True if player must send pieces back to the hangar (third 6 roll).
     * @param possiblePieces An empty list if player cannot make a taxi/move.
     */
    void choosePieces(List<Piece> possiblePieces, boolean backToHangar);
  }
  
  private final AeroplaneChessLogic aeroplaneChessLogic = new AeroplaneChessLogic();
  private final View view;
  private final Container container;
  /** Colors are R|Y, or empty for a Viewer. */
  private Optional<Color> myColor;
  private AeroplaneChessState aeroplaneChessState;

  public AeroplaneChessPresenter(View view, Container container) {
    this.view = view;
    this.container = container;
    view.setPresenter(this);
  }
  
  /** 
   * Updates the presenter and the view with the state in updateUI.
   * If the viewer is a player in the game, then he will be able to:
   * - Select the die with {@link #dieRolled} on AeroplaneChessMessage.ROLL_AVAILABLE 
   * - Select stack/unstack with {@link #stackSelected} on AeroplaneChessMessage.STACK_AVAILABLE
   * - Select take/don't take shortcut {@link #shortcutSelected} on 
   *   AeroplaneChessMessage.SHORTCUT_AVAILABLE
   */
  public void updateUI(UpdateUI updateUI) {
    List<Integer> playerIds = updateUI.getPlayerIds();
    int yourPlayerId = updateUI.getYourPlayerId();
    int yourPlayerIndex = updateUI.getPlayerIndex(yourPlayerId);
    myColor = yourPlayerIndex == 0 ? Optional.of(Color.R)
        : yourPlayerIndex == 1 ? Optional.of(Color.Y) 
        : Optional.<Color>absent();
        
    
    if (updateUI.getState().isEmpty()) {
      // The R player sends the initial setup move.
      if (myColor.isPresent() && myColor.get().isRed()) {
        sendInitialMove(playerIds);
      }
      return;
    }
    
    /* Gets the turn of current player from the GameApi state */
    Color turn = null;
    for (Operation operation : updateUI.getLastMove()) {
      if (operation.getMessageName() == SET_TURN) {
        turn = Color.fromPlayerOrder(playerIds.indexOf(((SetTurn) operation).getPlayerId()));
      }
    }
    
    aeroplaneChessState = aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
        updateUI.getState(), 
        turn, 
        playerIds);
    
    if (updateUI.isViewer()) {  // The viewer can see the board and die roll but can't interact.
      view.setViewerState(
          aeroplaneChessState.getPieces(Color.R), 
          aeroplaneChessState.getPieces(Color.Y), 
          aeroplaneChessState.getDie(), 
          getAeroplaneChessMessage());
      return;
    }
    
    if (updateUI.isAiPlayer()) {
      // TODO: implement AI in a later HW!
      //container.sendMakeMove(..);
      return;
    }
    
    // Either R or Y player
    Color myC = myColor.get();
    Color opponentColor = myC.getOppositeColor();
    
    view.setPlayerState(
        aeroplaneChessState.getPieces(myC), 
        aeroplaneChessState.getPieces(opponentColor), 
        aeroplaneChessState.getDie(), 
        getAeroplaneChessMessage());
  }
  
  /**
   * Returns a message that is sent to the View specifying how the View should interact with the 
   * player; see AeroplaneChessMessage
   * @return
   */
  private AeroplaneChessMessage getAeroplaneChessMessage() {
    if (!isMyTurn()) {
      return AeroplaneChessMessage.OTHER_TURN;
    }
    /*
     * Turn has just switched, so need to display the die roll for the player. This will allow
     * for some interaction for "rolling" (ie., click on a die).
     */
    if (aeroplaneChessState.getLastTwoRolls().equals(EMPTY_ROLLS)) {
      return AeroplaneChessMessage.ROLL_AVAILABLE;
    }
    
    Color turn = aeroplaneChessState.getTurn();
    /*
     * It's not the first move for the player, so some pieces were moved. piecesMovedLast
     * is a non-empty string for that reason.
     */
    String piecesMovedLast = aeroplaneChessState.getLastTwoMoves().get(0);
    List<Piece> piecesNotMovedLast = Lists.newArrayList();
    
    for (Piece piece : aeroplaneChessState.getPieces(turn)) {
      if (!piecesMovedLast.contains(Integer.toString(piece.getPieceId()))) {
        piecesNotMovedLast.add(piece);
      }
    }
    
    // Get the zone and space of one of the pieces moved last
    Piece movedPiece = aeroplaneChessState.getPieces(turn).get(
        Integer.parseInt(piecesMovedLast.substring(0, 1)));
    Zone movedZone = movedPiece.getZone();
    int movedSpace = movedPiece.getSpace();
    
    boolean stackAvailable = aeroplaneChessLogic.isStackAvailable(
        movedZone, movedSpace, piecesNotMovedLast); 
    boolean jumpAvailable = aeroplaneChessLogic.isJumpAvailable(
        aeroplaneChessState.getAction(), movedZone, movedSpace, turn);
    boolean shortcutAvailable = aeroplaneChessLogic.isShortcutAvailable(
        movedZone, movedSpace, turn);
    /*
     * 
     *  We also need to display the die roll on the second or third roll of a 6 
     *  (but you only roll again when there are no stack, jump, etc. moves left).
     */
    if (!stackAvailable && !jumpAvailable && !shortcutAvailable) {
      return AeroplaneChessMessage.ROLL_AVAILABLE;
    }
    
    /*
     * Moves are checked for in this order exactly: stack, jump, shortcut. Stack takes precedence
     * over jump/shortcut since that allows multiple pieces to take the jump/shortcut (if the
     * player chooses to stack them). 
     */
    if (stackAvailable) {
      return AeroplaneChessMessage.STACK_AVAILABLE;
    }
    
    if (jumpAvailable) {
      return AeroplaneChessMessage.JUMP_AVAILABLE;
    }
    
    /*
     * Shortcut must be available since all other possibilities were checked. MOVE and TAXI are 
     * called implicitly through getPossiblePieces after we initially send 
     * AeroplaneChessMessage.ROLL_AVAILABLE, so we should only fall into this state if there
     * is a shortcut available.
     */
    return AeroplaneChessMessage.SHORTCUT_AVAILABLE;
  }
  
  private boolean isMyTurn() {
    return myColor.isPresent() && myColor.get() == aeroplaneChessState.getTurn();
  }
  
  /** 
   * Returns a list of pieces from the state that can be moved or taxi'd.  This could be empty 
   * if the roll was odd and all the pieces are in the Hangar. 
   */
  private List<Piece> getPossiblePieces() {
    List<Piece> possiblePieces = Lists.newArrayList();
    List<Piece> myPieces = aeroplaneChessState.getPieces(myColor.get());
    int die = aeroplaneChessState.getDie();
    
    for (Piece piece : myPieces) {
      if (!piece.isFaceDown()) {
        Zone location = piece.getZone();
        
        if (location == Zone.HANGAR) {
          if (die % 2 == 0) { // Pieces that can be taxi'd
            possiblePieces.add(piece);
          }
        }
        else {  // Track, Final Stretch, or Launch
          possiblePieces.add(piece);  // Pieces that can be moved
        }
      }
    }

    return possiblePieces;
  }
  
  /** Sends the initial move to set the board pieces and roll the die (in state only). */
  private void sendInitialMove(List<Integer> playerIds) {
    container.sendMakeMove(aeroplaneChessLogic.getInitialOperations(playerIds.get(0)));
  }
  
  /**
   *  Returns a list of any opponent pieces that were on the space the player will land on 
   *  (so they can be sent back to the Hangar).
   */
  private List<Piece> getOpponentPiecesToMove(int space, Zone zone) {
    List<Piece> piecesToMove = Lists.newArrayList();
    Color myC = myColor.get();
    
    for (Piece opponentPiece : aeroplaneChessState.getPieces(myC.getOppositeColor())) {
      if (opponentPiece.getZone() == zone && opponentPiece.getSpace() == space) {
        int pieceId = opponentPiece.getPieceId();
        piecesToMove.add(new Piece(
            Zone.HANGAR,
            pieceId,
            pieceId,
            myC.getOppositeColor(),
            false,
            false));
      }
    }
    
    return piecesToMove;
  }
  
  /**
   * Returns true if the space is counter-clockwise of the new space (within six spaces, since
   * that's the max number of spaces we can move on a die roll). This is used to verify moving 
   * into the final stretch.
   */
  private boolean isCounterClockwiseOf(int oldSpace, int newSpace) {
    return oldSpace < newSpace || 
        oldSpace >= GREEN_MOVE_TO_FINAL 
          && newSpace == aeroplaneChessLogic.getStart(Color.G, Zone.FINAL_STRETCH);
  }
  
  /**
   * Taxis the piece out of the Hangar into the launch.
   * @param oldPiece The piece before movement
   */
  private void makeTaxiMove(Piece oldPiece) {
    // There will only be one piece selected to taxi
    int pieceId = oldPiece.getPieceId();
    Piece newPiece = new Piece(
        Zone.LAUNCH,  // A taxi move always moves to Launch
        pieceId,  // Same piece id
        0,  // Location within Launch is the same for all pieces
        myColor.get(),
        false,  // Pieces in launch are never stacked
        false);  // Pieces in Launch are never facedown
    
    container.sendMakeMove(
        aeroplaneChessLogic.getOperationsTaxi(
            aeroplaneChessState, 
            Lists.newArrayList(newPiece), 
            EMPTY_PIECES,  // Can't send opponent pieces on taxi
            getPlayerId()));
  }
  
  /**
   * Move a piece to the track or final stretch.  The View can send just one piece, and the 
   * Presenter will get all the stacked pieces (if that piece was stacked) to send the move.
   * @param oldPiece The piece before movement
   */
  private void makeMoveMove(Piece oldPiece) {
    // All pieces selected were in the same location (taken care of by UI)
    Zone oldZone = oldPiece.getZone();
    int oldSpace = oldPiece.getSpace();
    
    Color myC = myColor.get();
    Zone newZone;
    int newSpace;
    boolean newIsFaceDown = false;
    int die = aeroplaneChessState.getDie();
    int finalStretchStart = aeroplaneChessLogic.getStart(myC, Zone.FINAL_STRETCH);

    if ((oldZone == Zone.FINAL_STRETCH)  // A move into the Final Stretch or Hangar (if exact)
        || (oldZone == Zone.TRACK 
            && isCounterClockwiseOf(oldSpace, finalStretchStart) 
            && (oldSpace + die) % TOTAL_SPACES > finalStretchStart)
         || (oldZone == Zone.TRACK && oldSpace == finalStretchStart)) {
      if ((oldZone == Zone.FINAL_STRETCH && oldSpace + die == WIN_FINAL_SPACE) 
          || (oldZone == Zone.TRACK && die == 6 && oldSpace == finalStretchStart)) {  
        // Move into Hangar (exact roll)
        newZone = Zone.HANGAR;
        newSpace = -1;  // Flag to move into home space in Hangar (ie., R1 goes to H01..)
        newIsFaceDown = true;
      }
      else if ((oldZone == Zone.TRACK && oldSpace == finalStretchStart && die != 6) || 
          (oldZone == Zone.FINAL_STRETCH && oldSpace + die > WIN_FINAL_SPACE
            && oldSpace - die < 0)) {
        // Backtrack pieces to track zone
        newZone = Zone.TRACK;
        newSpace = finalStretchStart - (die - oldSpace) + 1;
      }
      else if (oldZone == Zone.TRACK) {
        // No need to backtrack -- end up in final stretch
        newZone = Zone.FINAL_STRETCH;
        newSpace = oldSpace + die - finalStretchStart - 1;
      }
      else {
        newZone = Zone.FINAL_STRETCH;
        if (oldSpace + die > WIN_FINAL_SPACE) {
          // Backtrack pieces to part of final stretch
          newSpace = oldSpace - die;
        }
        else {
          // Move was within final stretch
          newSpace = oldSpace + die;
        }
        
      }
    }
    else if (oldZone == Zone.LAUNCH) {  // A move into the track from the Launch
      int launchStart = aeroplaneChessLogic.getStart(myC, Zone.LAUNCH);
      newZone = Zone.TRACK;
      newSpace = launchStart + die;
    }
    else {  // A regular move along the track
      newZone = Zone.TRACK;
      newSpace = (oldSpace + die) % TOTAL_SPACES;
    }
    
    // Get pieces as they were on the board
    List<Piece> oldPieces = Lists.newArrayList(oldPiece);
    List<Piece> allMyPieces = aeroplaneChessState.getPieces(myC);
    
    if (oldPiece.isStacked()) { // Add other stacked pieces if necessary
      for (Piece piece : allMyPieces) {
        if (piece.getZone() == oldZone && piece.getSpace() == oldSpace && !piece.equals(oldPiece)) {
          oldPieces.add(piece);
        }
      }
    }
      
    // Create pieces as they will be after the move
    List<Piece> newPieces = Lists.newArrayList();
    for (Piece piece : oldPieces) {
      int pieceId = oldPiece.getPieceId(); 
      newPieces.add(new Piece(
          newZone, 
          pieceId, 
          newSpace == -1 ? pieceId : newSpace, // Move to the new space or home Hangar space
          myC,
          piece.isStacked(),  // Never change stacked on a move 
          newIsFaceDown));  // This can change if moved to Hangar from Final Stretch
    }

    /*
     *  Get any opponent pieces that were on the space the player will land on and send them
     *  to the Hangar.
     */
    List<Piece> opponentPiecesToMove = getOpponentPiecesToMove(newSpace, newZone);
     
    container.sendMakeMove(
        aeroplaneChessLogic.getOperationsMove(
            aeroplaneChessState, 
            newPieces, 
            opponentPiecesToMove,
            getPlayerId()));
  }
  
  /**
   * Sends a move with no pieces moved (setting turn to and rolling the die for the other player).
   * This can happen if all the player's pieces are in the Hangar and he cannot taxi due to rolling
   * an odd die. 
   */
  private void passTurn() {
    container.sendMakeMove(
        aeroplaneChessLogic.getOperationsMove(
            aeroplaneChessState, 
            EMPTY_PIECES, // No player pieces
            EMPTY_PIECES, // No opponent pieces
            getPlayerId()));
  }
  
  /**
   * Sends a move with all of the pieces that were moved in the last two moves returned to the 
   * Hangar.  This happens if the player rolled three 6's in a row.
   */
  private void sendBackToHangar() {
    List<Piece> piecesToSend = Lists.newArrayList();
    List<String> lastTwoMoves = aeroplaneChessState.getLastTwoMoves();
    
    for (int i = 0; i < PIECES_PER_PLAYER; i++) {
      if (lastTwoMoves.get(0).contains(Integer.toString(i)) 
          || lastTwoMoves.get(1).contains(Integer.toString(i))) {
        // Pieces are sent back to the Hangar faceup and unstacked
        piecesToSend.add(new Piece(Zone.HANGAR, i, i, myColor.get(), false, false)); 
      }
    }
    
    container.sendMakeMove(
        aeroplaneChessLogic.getOperationsMove(
            aeroplaneChessState, 
            piecesToSend,  // Affected player pieces
            EMPTY_PIECES,  // No opponent pieces
            getPlayerId()));
  }
  
  /**
   * Returns the player id from the AeroplaneChess state given my color. R is always the first
   * player, so his playerId will be first in the list.
   */
  private int getPlayerId() {
    return aeroplaneChessState.getPlayerIds().get(myColor.get().isRed() ? 0 : 1);
  }
  
  
  /* ***
   * Below: Methods the view can call
   * 
   * ***/
  
  /**
   * "Rolls" the die and indicates to the presenter to choose the pieces that can be moved
   * with that die roll. The view can only call this method if the presenter passed 
   * AeroplaneChessMessage.ROLL_AVAILABLE in {@link View#setPlayerState}.
   */
  public void dieRolled() {
    check(isMyTurn());
    if (aeroplaneChessLogic.rolledThreeSixes(aeroplaneChessState)) {
      // Must send pieces back to the Hangar if a third 6 was rolled
      view.choosePieces(EMPTY_PIECES, true);
    }
    else {
      view.choosePieces(getPossiblePieces(), false);
    }
  }
  
  /**
   * Selects pieces to move on the current turn.  The view will select only one piece on the "click"
   * and the Presenter will look to see if it is stacked, and move all stacked pieces at once
   * if it is.  Thus the view is always sending just one piece.  The view can only call this method 
   * if the presenter called {@link View#choosePieces}.  The view can also send no pieces (this
   * is just to tell the presenter to make an "empty" move, such as back to the hangar or passing
   * the turn if no moves are possible).
   */
  public void piecesSelected(Optional<Piece> piece) {
    check(isMyTurn());
    List<Piece> possiblePieces = getPossiblePieces();
    if (aeroplaneChessLogic.rolledThreeSixes(aeroplaneChessState)) {
      sendBackToHangar();
    }
    else if (possiblePieces.isEmpty()) {
      passTurn();
    }
    else {
      check(piece.isPresent() && getPossiblePieces().contains(piece.get()));
      Piece pieceToMove = piece.get();
      if (pieceToMove.getZone() == Zone.HANGAR) {  // Player chose to taxi pieces
        makeTaxiMove(pieceToMove);
      }
      else {  // Player chose to move pieces on the track 
        makeMoveMove(pieceToMove);
      }
    }
  }
  
  /**
   * Sends a stack move (stack or unstack). The view can only call this method if the presenter 
   * passed AeroplaneChessMessage.STACK_AVAILABLE in {@link View#setPlayerState}.
   */
  public void stackSelected(boolean stack) {
    check(isMyTurn() && getAeroplaneChessMessage() == AeroplaneChessMessage.STACK_AVAILABLE);
    Color myC = myColor.get();
    List<Piece> allMyPieces = aeroplaneChessState.getPieces(myC);
    List<Piece> myStackedPieces = Lists.newArrayList();
    String piecesMovedLast = aeroplaneChessState.getLastTwoMoves().get(0);
    String movedLocation = "";
    
    // Find the location of a piece that was moved last
    for (int i = 0; i < PIECES_PER_PLAYER; i++) {
      if (piecesMovedLast.contains(Integer.toString(i))) {
        movedLocation = allMyPieces.get(i).getLocation();
        break;
      }
    }
    
    // Add the pieces that were moved and those already on the space (since they weren't moved last)
    for (Piece piece : allMyPieces) {
      if (piece.getLocation().equals(movedLocation)) {
        myStackedPieces.add(new Piece(
            piece.getZone(),
            piece.getPieceId(), 
            piece.getSpace(),
            myC,
            stack,  // Depending on what the player selected, you either stack or unstack all
            piece.isFaceDown()));
      }
    }
    
    container.sendMakeMove(
        aeroplaneChessLogic.getOperationsStack(
            aeroplaneChessState, 
            myStackedPieces,
            EMPTY_PIECES, // Can't send opponent pieces on stack
            getPlayerId()));
  }
  
  /**
   * Sends a shortcut move (take or don't take the shortcut). The view can only call this method if 
   * the presenter passed AeroplaneChessMessage.SHORTCUT_AVAILABLE in {@link View#setPlayerState}.
   */
  public void shortcutSelected(boolean takeShortcut) {
    check(isMyTurn() && getAeroplaneChessMessage() == AeroplaneChessMessage.SHORTCUT_AVAILABLE);
    List<Piece> myPieces = Lists.newArrayList();
    List<Piece> opponentPiecesToMove = Lists.newArrayList();
    
    if (takeShortcut) {
      String piecesMovedLast = aeroplaneChessState.getLastTwoMoves().get(0);
      Color myC = myColor.get();
      
      for (int i = 0; i < PIECES_PER_PLAYER; i++) {
        if (piecesMovedLast.contains(Integer.toString(i))) {
          Piece piece = aeroplaneChessState.getPieces(myC).get(i);
          myPieces.add(new Piece(
              Zone.TRACK,  // Shortcuts always end on the track
              i, 
              ((piece.getSpace() + SHORTCUT_AMOUNT) % TOTAL_SPACES),  // End of shortcut
              myC,
              piece.isStacked(),
              piece.isFaceDown()));
        }
      }
      
      int landSpace = myPieces.get(0).getSpace();
      // Add opponent's pieces that were on the shortcut end. Always on the track.
      opponentPiecesToMove.addAll(getOpponentPiecesToMove(landSpace, Zone.TRACK));
      // Add opponent's piece(s) that were in the way of the shortcut (in the final stretch)
      opponentPiecesToMove.addAll(
          getOpponentPiecesToMove(SHORTCUT_FINAL_SPACE, Zone.FINAL_STRETCH));
    }
    else {  
      /*
       *  Player decided not to take the shortcut, so we send a move of empty pieces
       *  and empty opponentPieces
       */
    }
    
    container.sendMakeMove(
        aeroplaneChessLogic.getOperationsTakeShortcut(
            aeroplaneChessState, 
            myPieces, 
            opponentPiecesToMove,
            getPlayerId()));
  }
  
  /**
   * If a player lands on a jump space, ie., a space of the player's color, and no stack is 
   * available, then the piece(s) automatically jump 4 spaces.  (There are exceptions, such as
   * if the space begins the Final Stretch, or if it ends a shortcut.)  The view can only call this 
   * method if the presenter passed AeroplaneChessMessage.JUMP_AVAILABLE in 
   * {@link View#setPlayerState}.
   */
  public void showJump() {
    check(isMyTurn() && getAeroplaneChessMessage() == AeroplaneChessMessage.JUMP_AVAILABLE);
    List<Piece> myPieces = Lists.newArrayList();
    String piecesMovedLast = aeroplaneChessState.getLastTwoMoves().get(0);
    Color myC = myColor.get();
    
    for (int i = 0; i < PIECES_PER_PLAYER; i++) {
      if (piecesMovedLast.contains(Integer.toString(i))) {
        Piece piece = aeroplaneChessState.getPieces(myC).get(i);
        myPieces.add(new Piece(
            Zone.TRACK, // Jump spaces are only on the track
            i, 
            (piece.getSpace() + JUMP_AMOUNT) % TOTAL_SPACES, // Jumps are 4 spaces
            myC, 
            piece.isStacked(), 
            piece.isFaceDown())); 
      }
    }
    
    /*
     *  Get any opponent pieces that were on the space the player will land on and send them
     *  to the Hangar. Jump spaces are always on the track.
     */
    int landSpace = myPieces.get(0).getSpace();
    List<Piece> opponentPiecesToMove = getOpponentPiecesToMove(landSpace, Zone.TRACK);
    
    container.sendMakeMove(
        aeroplaneChessLogic.getOperationsJump(
            aeroplaneChessState, 
            myPieces,
            opponentPiecesToMove,
            getPlayerId()));
  }
}
