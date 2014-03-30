/*
 * Modeled after Prof. Zibin's CheatPresenterTest.java 
 * https://github.com/yoav-zibin/cheat-game/blob/master/eclipse/tests/org/cheat/client/CheatPresenterTest.java
 */

package org.aeroplanechess.client;

import java.util.List;
import java.util.Map;
import java.util.Arrays;

import org.mockito.Mockito;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import org.aeroplanechess.client.AeroplaneChessPresenter.AeroplaneChessMessage;
import org.aeroplanechess.client.AeroplaneChessPresenter.View;
import org.aeroplanechess.client.AeroplaneChessState.Action;
import org.game_api.GameApi.Container;
import org.game_api.GameApi.Operation;
import org.game_api.GameApi.UpdateUI;
import org.game_api.GameApi.SetTurn;
import org.game_api.GameApi;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Lists;

import static org.aeroplanechess.client.Constants.*;

/** 
 * Tests for {@link AeroplaneChessPresenter}.
 * Test plan:
 * States to test:
 * 1) empty state
 * 2) turn just switched (roll), select pieces to move/taxi
 * 3) stack available
 * 4) shortcut available
 * 5) jump available (this is automatically handled by presenter, no view interaction)
 * 6) no moves available
 * 7) rolled three 6's - back to hangar
 * 8) about-to-end but die roll backtracks
 * 9) game-over (exact die roll)
 * playerIds to test:
 * 1) red player
 * 2) yellow player
 * 3) viewer
 * For each one of these states and for each playerId, I will test what methods the presenters 
 * call on the view and container, and that the view is calling methods on the presenter
 * correctly as well. (For this reason some of the tests are similar, but it's safer to be more
 * thorough and test for all the playerIds.)
 * Presenter calling on view:
 * 1) setPresenter
 * 2) setViewerState/setPlayerState
 * 3) choosePieces
 * View calling on presenter:
 * 1) dieRolled
 * 2) piecesSelected
 * 3) stackSelected
 * 4) shortcutSelected
 */
@RunWith(JUnit4.class)
public class AeroplaneChessPresenterTest {
  
  private AeroplaneChessPresenter aeroplaneChessPresenter;
  private final AeroplaneChessLogic aeroplaneChessLogic = new AeroplaneChessLogic();
  private View mockView;
  private Container mockContainer;
  
  /* Player info */
  private final String rId = "41";
  private final String yId = "42";
  private static final String PLAYER_ID = "playerId"; 
  private final ImmutableList<String> playerIds = ImmutableList.of(rId, yId);
  private final Map<String, Object> rInfo = ImmutableMap.<String, Object>of(PLAYER_ID, rId);
  private final Map<String, Object> yInfo = ImmutableMap.<String, Object>of(PLAYER_ID, yId);
  private final List<Map<String, Object>> playersInfo = ImmutableList.of(rInfo, yInfo);
  
  /* States to test*/
  private final Map<String, Object> emptyState = ImmutableMap.<String, Object>of();
  private final Map<String, Object> turnChangedState = createState(
      3,  // Die 
      Action.MOVE,  // Previous action
      // Red pieces
      ImmutableList.of(
          Arrays.asList("T12", UNSTACKED, FACEUP),
          Arrays.asList("H01", UNSTACKED, FACEUP),
          Arrays.asList("T51", UNSTACKED, FACEUP),
          Arrays.asList("T29", UNSTACKED, FACEUP)),
      // Yellow pieces
      ImmutableList.of(
          Arrays.asList("T13", UNSTACKED, FACEUP),
          Arrays.asList("L00", UNSTACKED, FACEUP),
          Arrays.asList("H02", UNSTACKED, FACEUP),
          Arrays.asList("T45", UNSTACKED, FACEUP)),
      EMPTY_ROLLS,  // lastTwoRolls
      EMPTY_MOVES  // lastTwoMoves
      );
  private final Map<String, Object> noMoveAvailableState = createState(
      3,  // Die 
      Action.MOVE,  // Previous action
      // Red pieces
      ImmutableList.of(
          Arrays.asList("H00", UNSTACKED, FACEUP),
          Arrays.asList("H01", UNSTACKED, FACEUP),
          Arrays.asList("H02", UNSTACKED, FACEUP),
          Arrays.asList("H03", UNSTACKED, FACEUP)),
      // Yellow pieces
      ImmutableList.of(
          Arrays.asList("H00", UNSTACKED, FACEUP),
          Arrays.asList("H01", UNSTACKED, FACEUP),
          Arrays.asList("H02", UNSTACKED, FACEUP),
          Arrays.asList("H03", UNSTACKED, FACEUP)),
      EMPTY_ROLLS,  // lastTwoRolls
      EMPTY_MOVES  // lastTwoMoves
      );
  private final Map<String, Object> tripleSixState = createState(
      6,  // Die - 3rd roll was 6
      Action.MOVE,  // Previous action
      // Red pieces
      ImmutableList.of(
          Arrays.asList("T13", UNSTACKED, FACEUP),  
          Arrays.asList("L00", UNSTACKED, FACEUP),  // Must send back to hangar
          Arrays.asList("T22", STACKED, FACEUP),  // Must send back to hangar
          Arrays.asList("T22", STACKED, FACEUP)),  // Must send back to hangar
      // Yellow pieces
      ImmutableList.of(
          Arrays.asList("T25", UNSTACKED, FACEUP),  
          Arrays.asList("T22", UNSTACKED, FACEUP),  // Must send back to hangar
          Arrays.asList("T19", STACKED, FACEUP),  // Must send back to hangar
          Arrays.asList("T19", STACKED, FACEUP)),  // Must send back to hangar
      DOUBLE_SIX_ROLLS,  // Last two rolls were both 6
      ImmutableList.of("1", "23")  // lastTwoMoves: pieces R|Y1, R|Y2, R|Y3
      );
  private final Map<String, Object> stackAvailableState = createState(
      6,  // Die 
      Action.MOVE,  // Previous action (led to stack being available)
      // Red pieces
      ImmutableList.of(
          Arrays.asList("T12", UNSTACKED, FACEUP),
          Arrays.asList("H01", UNSTACKED, FACEUP),
          Arrays.asList("T12", UNSTACKED, FACEUP),
          Arrays.asList("T29", UNSTACKED, FACEUP)),
      // Yellow pieces
      ImmutableList.of(
          Arrays.asList("T13", UNSTACKED, FACEUP),
          Arrays.asList("L00", UNSTACKED, FACEUP),
          Arrays.asList("T13", UNSTACKED, FACEUP),
          Arrays.asList("T45", UNSTACKED, FACEUP)),
      ImmutableList.of(6, -1),  // lastTwoRolls
      ImmutableList.of("2", "")  // lastTwoMoves: Last move was R|Y2 to same location as R|Y0
      );
  private final Map<String, Object> shortcutAvailableState = createState(
      3,  // Die 
      Action.MOVE,  // Previous action (led to shortcut being available)
      // Red pieces
      ImmutableList.of(
          Arrays.asList("T33", UNSTACKED, FACEUP),
          Arrays.asList("H01", UNSTACKED, FACEUP),
          Arrays.asList("T36", UNSTACKED, FACEUP),  // Shortcut space for R
          Arrays.asList("T29", UNSTACKED, FACEUP)),
      // Yellow pieces
      ImmutableList.of(
          Arrays.asList("T13", UNSTACKED, FACEUP),
          Arrays.asList("L00", UNSTACKED, FACEUP),
          Arrays.asList("T10", UNSTACKED, FACEUP),  // Shortcut space for Y
          Arrays.asList("T45", UNSTACKED, FACEUP)),
      ImmutableList.of(3, -1),  // lastTwoRolls
      ImmutableList.of("2", "")  // lastTwoMoves: Last move was R|Y2 to same location as R|Y0
      );
  private final Map<String, Object> jumpAvailableState = createState(
      3,  // Die 
      Action.STACK,  // Previous action (led to jump being available)
      // Red pieces
      ImmutableList.of(
          Arrays.asList("T18", UNSTACKED, FACEUP), // Sent back to Hangar on Y jump
          Arrays.asList("H01", UNSTACKED, FACEUP),
          Arrays.asList("T40", STACKED, FACEUP),  // Jump space for R
          Arrays.asList("T40", STACKED, FACEUP)),
      // Yellow pieces
      ImmutableList.of(
          Arrays.asList("T44", UNSTACKED, FACEUP), // Sent back to Hangar on R jump
          Arrays.asList("L00", UNSTACKED, FACEUP),
          Arrays.asList("T14", STACKED, FACEUP),  // Jump space for Y
          Arrays.asList("T14", STACKED, FACEUP)),
      ImmutableList.of(3, -1),  // lastTwoRolls
      ImmutableList.of("23", "")  // lastTwoMoves: Last move was stacking R|Y2 to space of R|Y3
      );
  private final Map<String, Object> nearEndBacktrackState = createState(
      5,  // Die: die is inexact roll to finish
      Action.MOVE,
      // Red pieces
      ImmutableList.of(
          Arrays.asList("F02", UNSTACKED, FACEUP),   // R can win with this piece on exact roll
          Arrays.asList("H01", UNSTACKED, FACEDOWN), // Because all other pieces are facedown
          Arrays.asList("H02", UNSTACKED, FACEDOWN), // in the Hangar
          Arrays.asList("H03", UNSTACKED, FACEDOWN)),
      // Yellow pieces
      ImmutableList.of(
          Arrays.asList("F02", UNSTACKED, FACEUP),   // Y can win with this piece on exact roll
          Arrays.asList("H01", UNSTACKED, FACEDOWN), // Because all other pieces are facedown
          Arrays.asList("H02", UNSTACKED, FACEDOWN), // in the Hangar
          Arrays.asList("H03", UNSTACKED, FACEDOWN)),
      EMPTY_ROLLS,  // Turn just switched
      EMPTY_MOVES  
      );
  private final Map<String, Object> endWinningState = createState(
      2,  // Die 
      Action.MOVE,
      // Red pieces
      ImmutableList.of(
          Arrays.asList("F03", UNSTACKED, FACEUP),   // R can win with this piece on exact roll
          Arrays.asList("H01", UNSTACKED, FACEDOWN), // Because all other pieces are facedown
          Arrays.asList("H02", UNSTACKED, FACEDOWN), // in the Hangar
          Arrays.asList("H03", UNSTACKED, FACEDOWN)),
      // Yellow pieces
      ImmutableList.of(
          Arrays.asList("F03", UNSTACKED, FACEUP),   // Y can win with this piece on exact roll
          Arrays.asList("H01", UNSTACKED, FACEDOWN), // Because all other pieces are facedown
          Arrays.asList("H02", UNSTACKED, FACEDOWN), // in the Hangar
          Arrays.asList("H03", UNSTACKED, FACEDOWN)),
      EMPTY_ROLLS,  // Turn just switched
      EMPTY_MOVES  
      );

  
  @Before
  public void runBefore() {
    mockView = Mockito.mock(View.class);
    mockContainer = Mockito.mock(Container.class);
    aeroplaneChessPresenter = new AeroplaneChessPresenter(mockView, mockContainer);
    verify(mockView).setPresenter(aeroplaneChessPresenter);
  }

  @After
  public void runAfter() {
    // This will ensure I didn't forget to declare any extra interaction the mocks have.
    verifyNoMoreInteractions(mockContainer);
    verifyNoMoreInteractions(mockView);
  }

  @Test
  public void testEmptyStateForR() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, rId, emptyState));
    verify(mockContainer).sendMakeMove(aeroplaneChessLogic.getInitialOperations(rId));
  }

  @Test
  public void testEmptyStateForY() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, rId, emptyState));
  }

  @Test
  public void testEmptyStateForViewer() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(GameApi.VIEWER_ID, rId, emptyState));
  }
  
  /* R "rolls" the die and selects the pieces that he can move. */
  @Test
  public void testRollDieAndChoosePiecesMoveAvailableForRTurnOfR() {
    AeroplaneChessState aeroplaneChessState =
        aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
            turnChangedState, Color.R, playerIds);
    
    List<Piece> redPieces = getPieces(Color.R, turnChangedState);
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, rId, turnChangedState));
    verify(mockView).setPlayerState(
        redPieces,  // My pieces
        getPieces(Color.Y, turnChangedState),  // Opponent pieces
        (int) turnChangedState.get(DIE), 
        AeroplaneChessMessage.ROLL_AVAILABLE);
    
    aeroplaneChessPresenter.dieRolled();
    /*
     *  Pieces that can be moved/taxi'd.  Since die = 3, this means pieces that are not
     *  in the Hangar (can't taxi).
     */
    List<Piece> movablePieces = Lists.newArrayList(
        redPieces.get(0),  // "T12", UNSTACKED, FACEUP
        // R2 = "H01", UNSTACKED, FACEUP, so we can't move it
        redPieces.get(2),  // "T51", UNSTACKED, FACEUP
        redPieces.get(3));  // "T29", UNSTACKED, FACEUP)
    
    verify(mockView).choosePieces(movablePieces, false);
    
    List<Piece> myPiecesToMove = Lists.newArrayList(
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("T15", UNSTACKED, FACEUP), 0, Color.R.name())
        );
    
    aeroplaneChessPresenter.piecesSelected(Optional.of(redPieces.get(0)));
    
    verify(mockContainer).sendMakeMove(
        aeroplaneChessLogic.getOperationsMove(
            aeroplaneChessState, 
            myPiecesToMove,
            EMPTY_PIECES, // No opponent pieces
            rId));
  }
  
  /* Y shouldn't have any interactions since it's not his turn, but he can view everything
   * on the board. */
  @Test
  public void testRollDieAndChoosePiecesMoveAvailableForYTurnOfR() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, rId, turnChangedState));
    verify(mockView).setPlayerState(
        getPieces(Color.Y, turnChangedState),  // My pieces
        getPieces(Color.R, turnChangedState),  // Opponent pieces
        (int) turnChangedState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* Viewer should never have any interactions, but can view everything on the board. */
  @Test
  public void testRollDieAndChoosePiecesMoveAvailableForViewerTurnOfR() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(GameApi.VIEWER_ID, rId, turnChangedState));
    verify(mockView).setViewerState(
        getPieces(Color.R, turnChangedState),  // Red pieces
        getPieces(Color.Y, turnChangedState),  // Yellow pieces
        (int) turnChangedState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }

  /* Y "rolls" the die and selects the pieces that he can move. */
  @Test
  public void testRollDieAndChoosePiecesMoveAvailableForYTurnOfY() {
    AeroplaneChessState aeroplaneChessState =
        aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
            turnChangedState, Color.Y, playerIds);
    
    List<Piece> yellowPieces = getPieces(Color.Y, turnChangedState);
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, yId, turnChangedState));
    verify(mockView).setPlayerState(
        yellowPieces,  // My pieces
        getPieces(Color.R, turnChangedState),  // Opponent pieces
        (int) turnChangedState.get(DIE), 
        AeroplaneChessMessage.ROLL_AVAILABLE);
    
    aeroplaneChessPresenter.dieRolled();
    
    /*
     *  Pieces that can be moved/taxi'd.  Since die = 3, this means pieces that are not
     *  in the Hangar (can't taxi).
     */
    List<Piece> movablePieces = Lists.newArrayList(
        yellowPieces.get(0),  // "T13", UNSTACKED, FACEUP
        yellowPieces.get(1),  // "L00", UNSTACKED, FACEUP
        // Y2 = "H02", UNSTACKED, FACEUP, so we can't move it
        yellowPieces.get(3));  // "T45", UNSTACKED, FACEUP
    
    verify(mockView).choosePieces(movablePieces, false);
    
    List<Piece> myPiecesToMove = Lists.newArrayList(
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("T16", UNSTACKED, FACEUP), 0, Color.Y.name())
        );
    
    aeroplaneChessPresenter.piecesSelected(Optional.of(yellowPieces.get(0)));
    
    verify(mockContainer).sendMakeMove(
        aeroplaneChessLogic.getOperationsMove(
            aeroplaneChessState, 
            myPiecesToMove,
            EMPTY_PIECES, // No opponent pieces
            yId));
  }
  
  /* R shouldn't have any interactions since it's not his turn, but he can view everything
   * on the board. */
  @Test
  public void testRollDieAndChoosePiecesMoveAvailableForRTurnOfY() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, yId, turnChangedState));
    verify(mockView).setPlayerState(
        getPieces(Color.R, turnChangedState),  // My pieces
        getPieces(Color.Y, turnChangedState),  // Opponent pieces
        (int) turnChangedState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  
  /* Viewer should never have any interactions, but can view everything on the board. */
  @Test
  public void testRollDieAndChoosePiecesMoveAvailableForViewerTurnOfY() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(GameApi.VIEWER_ID, yId, turnChangedState));
    verify(mockView).setViewerState(
        getPieces(Color.R, turnChangedState),  // Red pieces
        getPieces(Color.Y, turnChangedState),  // Yellow pieces
        (int) turnChangedState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* R "rolls" the die but there are no moves available. */
  @Test
  public void testRollDieAndChoosePiecesNoMoveAvailableForRTurnOfR() {
    AeroplaneChessState aeroplaneChessState =
        aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
            noMoveAvailableState, Color.R, playerIds);
    
    List<Piece> redPieces = getPieces(Color.R, noMoveAvailableState);
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, rId, noMoveAvailableState));
    verify(mockView).setPlayerState(
        redPieces,  // My pieces
        getPieces(Color.Y, noMoveAvailableState),  // Opponent pieces
        (int) noMoveAvailableState.get(DIE), 
        AeroplaneChessMessage.ROLL_AVAILABLE);
    
    aeroplaneChessPresenter.dieRolled();
    verify(mockView).choosePieces(EMPTY_PIECES, false);
    aeroplaneChessPresenter.piecesSelected(Optional.<Piece>absent());
    /*
     *  Pieces that can be moved/taxi'd.  Since die = 3, and all pieces are in the Hangar,
     *  no moves are available.  So, we pass the turn to the other player.
     */
    
    verify(mockContainer).sendMakeMove(
        aeroplaneChessLogic.getOperationsMove(
            aeroplaneChessState, 
            EMPTY_PIECES, // No player pieces
            EMPTY_PIECES, // No opponent pieces
            rId));
  }
  
  /* Y shouldn't have any interactions since it's not his turn, but he can view everything
   * on the board. */
  @Test
  public void testRollDieAndChoosePiecesNoMoveAvailableForYTurnOfR() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, rId, noMoveAvailableState));
    verify(mockView).setPlayerState(
        getPieces(Color.Y, noMoveAvailableState),  // My pieces
        getPieces(Color.R, noMoveAvailableState),  // Opponent pieces
        (int) noMoveAvailableState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* Viewer should never have any interactions, but can view everything on the board. */
  @Test
  public void testRollDieAndChoosePiecesNoMoveAvailableForViewerTurnOfR() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(GameApi.VIEWER_ID, rId, noMoveAvailableState));
    verify(mockView).setViewerState(
        getPieces(Color.R, noMoveAvailableState),  // Red pieces
        getPieces(Color.Y, noMoveAvailableState),  // Yellow pieces
        (int) noMoveAvailableState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* R shouldn't have any interactions since it's not his turn, but he can view everything
   * on the board. */
  @Test
  public void testRollDieAndChoosePiecesNoMoveAvailableForRTurnOfY() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, yId, noMoveAvailableState));
    verify(mockView).setPlayerState(
        getPieces(Color.R, noMoveAvailableState),  // My pieces
        getPieces(Color.Y, noMoveAvailableState),  // Opponent pieces
        (int) noMoveAvailableState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  
  /* Y "rolls" the die and but there are no moves available. */
  @Test
  public void testRollDieAndChoosePiecesNoMoveAvailableForYTurnOfY() {
    AeroplaneChessState aeroplaneChessState =
        aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
            noMoveAvailableState, Color.Y, playerIds);
    
    List<Piece> yellowPieces = getPieces(Color.Y, noMoveAvailableState);
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, yId, noMoveAvailableState));
    verify(mockView).setPlayerState(
        yellowPieces,  // My pieces
        getPieces(Color.R, noMoveAvailableState),  // Opponent pieces
        (int) noMoveAvailableState.get(DIE), 
        AeroplaneChessMessage.ROLL_AVAILABLE);
    
    aeroplaneChessPresenter.dieRolled();
    verify(mockView).choosePieces(EMPTY_PIECES, false);
    aeroplaneChessPresenter.piecesSelected(Optional.<Piece>absent());
    
    /*
     *  Pieces that can be moved/taxi'd.  Since die = 3, and all the pieces are in the Hangar, there
     *  are no moves available.  Pass the turn.
     */
    verify(mockContainer).sendMakeMove(
        aeroplaneChessLogic.getOperationsMove(
            aeroplaneChessState, 
            EMPTY_PIECES, // No player pieces
            EMPTY_PIECES, // No opponent pieces
            yId));
  }
  
  /* Viewer should never have any interactions, but can view everything on the board. */
  @Test
  public void testRollDieAndChoosePiecesNoMoveAvailableForViewerTurnOfY() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(GameApi.VIEWER_ID, yId, noMoveAvailableState));
    verify(mockView).setViewerState(
        getPieces(Color.R, noMoveAvailableState),  // Red pieces
        getPieces(Color.Y, noMoveAvailableState),  // Yellow pieces
        (int) noMoveAvailableState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* R "rolls" the die but it's a third 6 so he has to send back to the Hangar. */
  @Test
  public void testRollDieAndBackToHangarForRTurnOfR() {
    AeroplaneChessState aeroplaneChessState =
        aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
            tripleSixState, Color.R, playerIds);
    
    List<Piece> redPieces = getPieces(Color.R, tripleSixState);
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, rId, tripleSixState));
    verify(mockView).setPlayerState(
        redPieces,  // My pieces
        getPieces(Color.Y, tripleSixState),  // Opponent pieces
        (int) tripleSixState.get(DIE), 
        AeroplaneChessMessage.ROLL_AVAILABLE);
    
    aeroplaneChessPresenter.dieRolled();
    verify(mockView).choosePieces(EMPTY_PIECES, true);
    aeroplaneChessPresenter.piecesSelected(Optional.<Piece>absent());
    
    // Have to send pieces back to Hangar since we rolled three 6's in a row.
    List<Piece> piecesToHangar = Lists.newArrayList(
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("H01", UNSTACKED, FACEUP), 1, Color.R.name()),
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("H02", UNSTACKED, FACEUP), 2, Color.R.name()),
         aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("H03", UNSTACKED, FACEUP), 3, Color.R.name())
        );
    
    verify(mockContainer).sendMakeMove(
        aeroplaneChessLogic.getOperationsMove(
            aeroplaneChessState, 
            piecesToHangar, // Affected pieces
            EMPTY_PIECES, // No opponent pieces
            rId));
  }
  
  
  /* Y shouldn't have any interactions since it's not his turn, but he can view everything
   * on the board. */
  @Test
  public void testRollDieAndBackToHangarForYTurnOfR() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, rId, tripleSixState));
    verify(mockView).setPlayerState(
        getPieces(Color.Y, tripleSixState),  // My pieces
        getPieces(Color.R, tripleSixState),  // Opponent pieces
        (int) tripleSixState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* Viewer should never have any interactions, but can view everything on the board. */
  @Test
  public void testRollDieAndBackToHangarForViewerTurnOfR() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(GameApi.VIEWER_ID, rId, tripleSixState));
    verify(mockView).setViewerState(
        getPieces(Color.R, tripleSixState),  // Red pieces
        getPieces(Color.Y, tripleSixState),  // Yellow pieces
        (int) tripleSixState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* Y "rolls" the die but it was a third 6, send back to Hangar. */
  @Test
  public void testRollDieAndBackToHangarForYTurnOfY() {
    AeroplaneChessState aeroplaneChessState =
        aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
            tripleSixState, Color.Y, playerIds);
    
    List<Piece> yellowPieces = getPieces(Color.Y, tripleSixState);
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, yId, tripleSixState));
    verify(mockView).setPlayerState(
        yellowPieces,  // My pieces
        getPieces(Color.R, tripleSixState),  // Opponent pieces
        (int) tripleSixState.get(DIE), 
        AeroplaneChessMessage.ROLL_AVAILABLE);
    
    aeroplaneChessPresenter.dieRolled();
    verify(mockView).choosePieces(EMPTY_PIECES, true);
    aeroplaneChessPresenter.piecesSelected(Optional.<Piece>absent());
    
    // Have to send pieces back to Hangar since we rolled three 6's in a row.
    List<Piece> piecesToHangar = Lists.newArrayList(
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("H01", UNSTACKED, FACEUP), 1, Color.Y.name()),
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("H02", UNSTACKED, FACEUP), 2, Color.Y.name()),
         aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("H03", UNSTACKED, FACEUP), 3, Color.Y.name())
        );
    
    verify(mockContainer).sendMakeMove(
        aeroplaneChessLogic.getOperationsMove(
            aeroplaneChessState, 
            piecesToHangar, // Affected pieces
            EMPTY_PIECES, // No opponent pieces
            yId));
  }
  
  /* R shouldn't have any interactions since it's not his turn, but he can view everything
   * on the board. */
  @Test
  public void testRollDieAndBackToHangarForRTurnOfY() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, yId, tripleSixState));
    verify(mockView).setPlayerState(
        getPieces(Color.R, tripleSixState),  // My pieces
        getPieces(Color.Y, tripleSixState),  // Opponent pieces
        (int) tripleSixState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* Viewer should never have any interactions, but can view everything on the board. */
  @Test
  public void testRollDieAndBackToHangarForViewerTurnOfY() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(GameApi.VIEWER_ID, yId, tripleSixState));
    verify(mockView).setViewerState(
        getPieces(Color.R, tripleSixState),  // Red pieces
        getPieces(Color.Y, tripleSixState),  // Yellow pieces
        (int) tripleSixState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* R decides to stack the pieces. */
  @Test
  public void testStackAvailableForRTurnOfR() {
    AeroplaneChessState aeroplaneChessState =
        aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
            stackAvailableState, Color.R, playerIds);
    
    List<Piece> redPieces = getPieces(Color.R, stackAvailableState);
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, rId, stackAvailableState));
    verify(mockView).setPlayerState(
        redPieces,  // My pieces
        getPieces(Color.Y, stackAvailableState),  // Opponent pieces
        (int) stackAvailableState.get(DIE), 
        AeroplaneChessMessage.STACK_AVAILABLE);
    
    // Pieces that can be stacked as we just moved them. (Have to set representation to STACKED)
    Piece r0Stacked = aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
        Arrays.asList("T12", STACKED, FACEUP), 0, Color.R.name());
    Piece r2Stacked = aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
        Arrays.asList("T12", STACKED, FACEUP), 2, Color.R.name());
    List<Piece> stackablePieces = Lists.newArrayList(
        r0Stacked,  // "T12", STACKED, FACEUP
        // R2 = "H01", UNSTACKED, FACEUP, so we can't move it
        r2Stacked);  // "T12", STACKED, FACEUP
        // R4 = "T29", UNSTACKED, FACEUP
    
    aeroplaneChessPresenter.stackSelected(true);
    
    verify(mockContainer).sendMakeMove(
        aeroplaneChessLogic.getOperationsStack(
            aeroplaneChessState, 
            stackablePieces,
            EMPTY_PIECES, // Can't send opponent pieces on stack
            rId));
  }
  
  /* Y shouldn't have any interactions since it's not his turn, but he can view everything
   * on the board. */
  @Test
  public void testStackAvailableForYTurnOfR() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, rId, stackAvailableState));
    verify(mockView).setPlayerState(
        getPieces(Color.Y, stackAvailableState),  // My pieces
        getPieces(Color.R, stackAvailableState),  // Opponent pieces
        (int) stackAvailableState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* Viewer should never have any interactions, but can view everything on the board. */
  @Test
  public void testStackAvailableForViewerTurnOfR() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(GameApi.VIEWER_ID, rId, stackAvailableState));
    verify(mockView).setViewerState(
        getPieces(Color.R, stackAvailableState),  // Red pieces
        getPieces(Color.Y, stackAvailableState),  // Yellow pieces
        (int) stackAvailableState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* R shouldn't have any interactions since it's not his turn, but he can view everything
   * on the board. */
  @Test
  public void testStackAvailableForRTurnOfY() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, yId, stackAvailableState));
    verify(mockView).setPlayerState(
        getPieces(Color.R, stackAvailableState),  // My pieces
        getPieces(Color.Y, stackAvailableState),  // Opponent pieces
        (int) stackAvailableState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* Y decides **not** to stack the pieces. */
  @Test
  public void testStackAvailableForYTurnOfY() {
    AeroplaneChessState aeroplaneChessState =
        aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
            stackAvailableState, Color.Y, playerIds);
    
    List<Piece> yellowPieces = getPieces(Color.Y, stackAvailableState);
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, yId, stackAvailableState));
    verify(mockView).setPlayerState(
        yellowPieces,  // My pieces
        getPieces(Color.R, stackAvailableState),  // Opponent pieces
        (int) stackAvailableState.get(DIE), 
        AeroplaneChessMessage.STACK_AVAILABLE);
    
    // Pieces that can be stacked as we just moved them (however we chose not to stack)
    List<Piece> stackablePieces = Lists.newArrayList(
        yellowPieces.get(0),  // "T13", UNSTACKED, FACEUP
        // Y2 = "L00", UNSTACKED, FACEUP
        yellowPieces.get(2));  // "T13", UNSTACKED, FACEUP
        // Y4 = "T45", UNSTACKED, FACEUP
    
    aeroplaneChessPresenter.stackSelected(false);
    
    verify(mockContainer).sendMakeMove(
        aeroplaneChessLogic.getOperationsStack(
            aeroplaneChessState, 
            stackablePieces,
            EMPTY_PIECES, // Can't send opponent pieces on stack
            yId));
  }
  
  /* Viewer should never have any interactions, but can view everything on the board. */
  @Test
  public void testStackAvailableForViewerTurnOfY() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(GameApi.VIEWER_ID, yId, stackAvailableState));
    verify(mockView).setViewerState(
        getPieces(Color.R, stackAvailableState),  // Red pieces
        getPieces(Color.Y, stackAvailableState),  // Yellow pieces
        (int) stackAvailableState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* R decides to take the shortcut. */
  @Test
  public void testShortcutAvailableForRTurnOfR() {  
    AeroplaneChessState aeroplaneChessState =
        aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
            shortcutAvailableState, Color.R, playerIds);
    
    List<Piece> redPieces = getPieces(Color.R, shortcutAvailableState);
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, rId, shortcutAvailableState));
    verify(mockView).setPlayerState(
        redPieces,  // My pieces
        getPieces(Color.Y, shortcutAvailableState),  // Opponent pieces
        (int) shortcutAvailableState.get(DIE), 
        AeroplaneChessMessage.SHORTCUT_AVAILABLE);
    
    // Pieces that can take the shortcut
    List<Piece> shortcutPieces = Lists.newArrayList(
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("T48", UNSTACKED, FACEUP), 2, Color.R.name()));
    
    aeroplaneChessPresenter.shortcutSelected(true);
    
    verify(mockContainer).sendMakeMove(
        aeroplaneChessLogic.getOperationsTakeShortcut(
            aeroplaneChessState, 
            shortcutPieces,
            EMPTY_PIECES, // No shortcut opponents
            rId));
  }
  
  /* Y shouldn't have any interactions since it's not his turn, but he can view everything
   * on the board. */
  @Test
  public void testShortcutAvailableForYTurnOfR() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, rId, shortcutAvailableState));
    verify(mockView).setPlayerState(
        getPieces(Color.Y, shortcutAvailableState),  // My pieces
        getPieces(Color.R, shortcutAvailableState),  // Opponent pieces
        (int) shortcutAvailableState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* Viewer should never have any interactions, but can view everything on the board. */
  @Test
  public void testShortcutAvailableForViewerTurnOfR() {
    aeroplaneChessPresenter.updateUI(
        createUpdateUI(GameApi.VIEWER_ID, rId, shortcutAvailableState));
    verify(mockView).setViewerState(
        getPieces(Color.R, shortcutAvailableState),  // Red pieces
        getPieces(Color.Y, shortcutAvailableState),  // Yellow pieces
        (int) shortcutAvailableState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* Y decides **not** to take the shortcut. */
  @Test
  public void testShortcutAvailableForYTurnOfY() {  
    AeroplaneChessState aeroplaneChessState =
        aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
            shortcutAvailableState, Color.Y, playerIds);
    
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, yId, shortcutAvailableState));
    verify(mockView).setPlayerState(
        getPieces(Color.Y, shortcutAvailableState),  // My pieces
        getPieces(Color.R, shortcutAvailableState),  // Opponent pieces
        (int) shortcutAvailableState.get(DIE), 
        AeroplaneChessMessage.SHORTCUT_AVAILABLE);
    
    aeroplaneChessPresenter.shortcutSelected(false);
    
    verify(mockContainer).sendMakeMove(
        aeroplaneChessLogic.getOperationsTakeShortcut(
            aeroplaneChessState, 
            EMPTY_PIECES, // Since Y decided not to take the shortcut
            EMPTY_PIECES, // No shortcut opponents
            yId));
  }
  
  /* R shouldn't have any interactions since it's not his turn, but he can view everything
   * on the board. */
  @Test
  public void testShortcutAvailableForRTurnOfY() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, yId, shortcutAvailableState));
    verify(mockView).setPlayerState(
        getPieces(Color.R, shortcutAvailableState),  // My pieces
        getPieces(Color.Y, shortcutAvailableState),  // Opponent pieces
        (int) shortcutAvailableState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* Viewer should never have any interactions, but can view everything on the board. */
  @Test
  public void testShortcutAvailableForViewerTurnOfY() {
    aeroplaneChessPresenter.updateUI(
        createUpdateUI(GameApi.VIEWER_ID, yId, shortcutAvailableState));
    verify(mockView).setViewerState(
        getPieces(Color.R, shortcutAvailableState),  // Red pieces
        getPieces(Color.Y, shortcutAvailableState),  // Yellow pieces
        (int) shortcutAvailableState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* R's automatic jump after stacking on a jump space. */
  @Test
  public void testJumpAvailableForRTurnOfR() {  
    AeroplaneChessState aeroplaneChessState =
        aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
            jumpAvailableState, Color.R, playerIds);
    
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, rId, jumpAvailableState));
    verify(mockView).setPlayerState(
        getPieces(Color.R, jumpAvailableState),  // My pieces
        getPieces(Color.Y, jumpAvailableState),  // Opponent pieces
        (int) jumpAvailableState.get(DIE), 
        AeroplaneChessMessage.JUMP_AVAILABLE);
    
    aeroplaneChessPresenter.showJump();
    
    List<Piece> jumpPieces = Lists.newArrayList(
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("T44", STACKED, FACEUP), 2, Color.R.name()),
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("T44", STACKED, FACEUP), 3, Color.R.name()));
    
    List<Piece> opponentPieces = Lists.newArrayList(
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("H00", UNSTACKED, FACEUP), 0, Color.Y.name()));
    
    verify(mockContainer).sendMakeMove(
        aeroplaneChessLogic.getOperationsJump(
            aeroplaneChessState, 
            jumpPieces,
            opponentPieces,
            rId));
  }
  
  /* Y shouldn't have any interactions since it's not his turn, but he can view everything
   * on the board. */
  @Test
  public void testJumpAvailableForYTurnOfR() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, rId, jumpAvailableState));
    verify(mockView).setPlayerState(
        getPieces(Color.Y, jumpAvailableState),  // My pieces
        getPieces(Color.R, jumpAvailableState),  // Opponent pieces
        (int) jumpAvailableState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* Viewer should never have any interactions, but can view everything on the board. */
  @Test
  public void testJumpAvailableForViewerTurnOfR() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(GameApi.VIEWER_ID, rId, jumpAvailableState));
    verify(mockView).setViewerState(
        getPieces(Color.R, jumpAvailableState),  // Red pieces
        getPieces(Color.Y, jumpAvailableState),  // Yellow pieces
        (int) jumpAvailableState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* Y's automatic jump after stacking on a jump space. */
  @Test
  public void testJumpAvailableForYTurnOfY() {  
    AeroplaneChessState aeroplaneChessState =
        aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
            jumpAvailableState, Color.Y, playerIds);
    
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, yId, jumpAvailableState));
    verify(mockView).setPlayerState(
        getPieces(Color.Y, jumpAvailableState),  // My pieces
        getPieces(Color.R, jumpAvailableState),  // Opponent pieces
        (int) jumpAvailableState.get(DIE), 
        AeroplaneChessMessage.JUMP_AVAILABLE);
    
    aeroplaneChessPresenter.showJump();
    
    List<Piece> jumpPieces = Lists.newArrayList(
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("T18", STACKED, FACEUP), 2, Color.Y.name()),
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("T18", STACKED, FACEUP), 3, Color.Y.name()));
    
    List<Piece> opponentPieces = Lists.newArrayList(
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("H00", UNSTACKED, FACEUP), 0, Color.R.name()));
    
    verify(mockContainer).sendMakeMove(
        aeroplaneChessLogic.getOperationsJump(
            aeroplaneChessState, 
            jumpPieces,
            opponentPieces,
            yId));
  }
  
  /* R shouldn't have any interactions since it's not his turn, but he can view everything
   * on the board. */
  @Test
  public void testJumpAvailableForRTurnOfY() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, yId, jumpAvailableState));
    verify(mockView).setPlayerState(
        getPieces(Color.R, jumpAvailableState),  // My pieces
        getPieces(Color.Y, jumpAvailableState),  // Opponent pieces
        (int) jumpAvailableState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* Viewer should never have any interactions, but can view everything on the board. */
  @Test
  public void testJumpAvailableForViewerTurnOfY() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(GameApi.VIEWER_ID, yId, jumpAvailableState));
    verify(mockView).setViewerState(
        getPieces(Color.R, jumpAvailableState),  // Red pieces
        getPieces(Color.Y, jumpAvailableState),  // Yellow pieces
        (int) jumpAvailableState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* R "rolls" the die but since it is inexact, needs to backtrack to the Track zone. */
  @Test
  public void testRollDieAndNeedBacktrackForRTurnOfR() { 
    AeroplaneChessState aeroplaneChessState =
        aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
            nearEndBacktrackState, Color.R, playerIds);
    
    List<Piece> redPieces = getPieces(Color.R, nearEndBacktrackState);
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, rId, nearEndBacktrackState));
    verify(mockView).setPlayerState(
        redPieces,  // My pieces
        getPieces(Color.Y, nearEndBacktrackState),  // Opponent pieces
        (int) nearEndBacktrackState.get(DIE), 
        AeroplaneChessMessage.ROLL_AVAILABLE);
    
    aeroplaneChessPresenter.dieRolled();
    
    // The last piece needed to win the game: "F02", UNSTACKED, FACEUP
    Piece winningPiece = redPieces.get(0);
    verify(mockView).choosePieces(Lists.newArrayList(winningPiece), false);
    aeroplaneChessPresenter.piecesSelected(Optional.of(winningPiece));
    
    // Backtracking 5 spaces F02 --> T13 (for Red)
    List<Piece> backtrackPieces = Lists.newArrayList(
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("T13", UNSTACKED, FACEUP), 0, Color.R.name()));
    
    verify(mockContainer).sendMakeMove(
        aeroplaneChessLogic.getOperationsMove(
            aeroplaneChessState, 
            backtrackPieces,
            EMPTY_PIECES, // No opponent pieces
            rId));
  }
  
  /* Y shouldn't have any interactions since it's not his turn, but he can view everything
   * on the board. */
  @Test
  public void testRollDieAndNeedBacktrackForYTurnOfR() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, rId, nearEndBacktrackState));
    verify(mockView).setPlayerState(
        getPieces(Color.Y, nearEndBacktrackState),  // My pieces
        getPieces(Color.R, nearEndBacktrackState),  // Opponent pieces
        (int) nearEndBacktrackState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* Viewer should never have any interactions, but can view everything on the board. */
  @Test
  public void testRollDieAndNeedBacktrackForViewerTurnOfR() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(GameApi.VIEWER_ID, rId, nearEndBacktrackState));
    verify(mockView).setViewerState(
        getPieces(Color.R, nearEndBacktrackState),  // Red pieces
        getPieces(Color.Y, nearEndBacktrackState),  // Yellow pieces
        (int) nearEndBacktrackState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }

  /* Y "rolls" the die but since it is inexact, needs to backtrack to the Track zone. */
  @Test
  public void testRollDieAndNeedBacktrackForYTurnOfY() {
    AeroplaneChessState aeroplaneChessState =
        aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
            nearEndBacktrackState, Color.Y, playerIds);
    
    List<Piece> yellowPieces = getPieces(Color.Y, nearEndBacktrackState);
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, yId, nearEndBacktrackState));
    verify(mockView).setPlayerState(
        yellowPieces,  // My pieces
        getPieces(Color.R, nearEndBacktrackState),  // Opponent pieces
        (int) nearEndBacktrackState.get(DIE), 
        AeroplaneChessMessage.ROLL_AVAILABLE);
    
    aeroplaneChessPresenter.dieRolled();
    
    // The last piece needed to win the game: "F02", UNSTACKED, FACEUP
    Piece winningPiece = yellowPieces.get(0);
    verify(mockView).choosePieces(Lists.newArrayList(winningPiece), false);
    aeroplaneChessPresenter.piecesSelected(Optional.of(winningPiece));
    
    // Backtracking 5 spaces F02 --> T39 (for Yellow)
    List<Piece> backtrackPieces = Lists.newArrayList(
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("T39", UNSTACKED, FACEUP), 0, Color.Y.name()));
    
    verify(mockContainer).sendMakeMove(
        aeroplaneChessLogic.getOperationsMove(
            aeroplaneChessState, 
            backtrackPieces,
            EMPTY_PIECES, // No opponent pieces
            yId));
  }
  
  /* R shouldn't have any interactions since it's not his turn, but he can view everything
   * on the board. */
  @Test
  public void testRollDieAndNeedBacktrackForRTurnOfY() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, yId, nearEndBacktrackState));
    verify(mockView).setPlayerState(
        getPieces(Color.R, nearEndBacktrackState),  // My pieces
        getPieces(Color.Y, nearEndBacktrackState),  // Opponent pieces
        (int) nearEndBacktrackState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  
  /* Viewer should never have any interactions, but can view everything on the board. */
  @Test
  public void testRollDieAndNeedBacktrackForViewerTurnOfY() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(GameApi.VIEWER_ID, yId, nearEndBacktrackState));
    verify(mockView).setViewerState(
        getPieces(Color.R, nearEndBacktrackState),  // Red pieces
        getPieces(Color.Y, nearEndBacktrackState),  // Yellow pieces
        (int) nearEndBacktrackState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }  
  
  /* R "rolls" the die and wins the game. */
  @Test
  public void testRollDieAndWinningStateForRTurnOfR() {
    AeroplaneChessState aeroplaneChessState =
        aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
            endWinningState, Color.R, playerIds);
    
    List<Piece> redPieces = getPieces(Color.R, endWinningState);
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, rId, endWinningState));
    verify(mockView).setPlayerState(
        redPieces,  // My pieces
        getPieces(Color.Y, endWinningState),  // Opponent pieces
        (int) endWinningState.get(DIE), 
        AeroplaneChessMessage.ROLL_AVAILABLE);
    
    aeroplaneChessPresenter.dieRolled();
    
    // The last piece to win the game: "F03", UNSTACKED, FACEUP
    Piece winningPiece = redPieces.get(0);
    
    verify(mockView).choosePieces(Lists.newArrayList(winningPiece), false);
    aeroplaneChessPresenter.piecesSelected(Optional.of(winningPiece));
    
    List<Piece> winningPieces = Lists.newArrayList(
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("H00", UNSTACKED, FACEDOWN), 0, Color.R.name()));
    
    verify(mockContainer).sendMakeMove(
        aeroplaneChessLogic.getOperationsMove(
            aeroplaneChessState, 
            winningPieces,
            EMPTY_PIECES, // No opponent pieces
            rId));
  }
  
  /* Y shouldn't have any interactions since it's not his turn, but he can view everything
   * on the board. */
  @Test
  public void testRollDieAndWinningStateForYTurnOfR() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, rId, endWinningState));
    verify(mockView).setPlayerState(
        getPieces(Color.Y, endWinningState),  // My pieces
        getPieces(Color.R, endWinningState),  // Opponent pieces
        (int) endWinningState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  /* Viewer should never have any interactions, but can view everything on the board. */
  @Test
  public void testRollDieAndWinningStateForViewerTurnOfR() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(GameApi.VIEWER_ID, rId, endWinningState));
    verify(mockView).setViewerState(
        getPieces(Color.R, endWinningState),  // Red pieces
        getPieces(Color.Y, endWinningState),  // Yellow pieces
        (int) endWinningState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }

  /* Y "rolls" the die and wins the game. */
  @Test
  public void testRollDieAndWinningStateForYTurnOfY() {
    AeroplaneChessState aeroplaneChessState =
        aeroplaneChessLogic.gameApiStateToAeroplaneChessState(
            endWinningState, Color.Y, playerIds);
    
    List<Piece> yellowPieces = getPieces(Color.Y, endWinningState);
    aeroplaneChessPresenter.updateUI(createUpdateUI(yId, yId, endWinningState));
    verify(mockView).setPlayerState(
        yellowPieces,  // My pieces
        getPieces(Color.R, endWinningState),  // Opponent pieces
        (int) endWinningState.get(DIE), 
        AeroplaneChessMessage.ROLL_AVAILABLE);
    
    aeroplaneChessPresenter.dieRolled();
    
    // The last piece to win the game: "F03", UNSTACKED, FACEUP
    Piece winningPiece = yellowPieces.get(0);
    
    verify(mockView).choosePieces(Lists.newArrayList(winningPiece), false);
    aeroplaneChessPresenter.piecesSelected(Optional.of(winningPiece));
    
    List<Piece> winningPieces = Lists.newArrayList(
        aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(
            Arrays.asList("H00", UNSTACKED, FACEDOWN), 0, Color.Y.name()));
    
    verify(mockContainer).sendMakeMove(
        aeroplaneChessLogic.getOperationsMove(
            aeroplaneChessState, 
            winningPieces,
            EMPTY_PIECES, // No opponent pieces
            yId));
  }
  
  /* R shouldn't have any interactions since it's not his turn, but he can view everything
   * on the board. */
  @Test
  public void testRollDieAndWinningStateForRTurnOfY() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(rId, yId, endWinningState));
    verify(mockView).setPlayerState(
        getPieces(Color.R, endWinningState),  // My pieces
        getPieces(Color.Y, endWinningState),  // Opponent pieces
        (int) endWinningState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }
  
  
  /* Viewer should never have any interactions, but can view everything on the board. */
  @Test
  public void testRollDieAndWinningStateForViewerTurnOfY() {
    aeroplaneChessPresenter.updateUI(createUpdateUI(GameApi.VIEWER_ID, yId, endWinningState));
    verify(mockView).setViewerState(
        getPieces(Color.R, endWinningState),  // Red pieces
        getPieces(Color.Y, endWinningState),  // Yellow pieces
        (int) endWinningState.get(DIE), 
        AeroplaneChessMessage.OTHER_TURN);
  }  
  
  /**
   * Returns a list of pieces (in AeroplaneChess Piece representation) from GameApi state and
   * given the color of the player.
   */
  @SuppressWarnings("unchecked")
  private List<Piece> getPieces(Color color, Map<String, Object> gameApiState) {
    String player = color.name();
    List<Piece> playerPieces = Lists.newArrayList();
    
    for (int i = 0; i < PIECES_PER_PLAYER; i++) {
      List<String> gameApiPiece = (List<String>) gameApiState.get(player + i); 
      playerPieces.add(
          aeroplaneChessLogic.gameApiPieceToAeroplaneChessPiece(gameApiPiece, i, player));
    }
    
    return playerPieces;
  }
  
  /**
   * Returns GameApi AeroplaneChess state with the following keys:
   * die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves.
   * Inputs are all in GameApi representation (ie., pieces are lists of Strings).
   */
  private ImmutableMap<String, Object> createState(int die, Action lastAction, 
      List<List<String>> redPieces, List<List<String>> yellowPieces, 
      List<Integer> lastTwoRolls, List<String> lastTwoMoves) {
    Map<String, Object> state = Maps.newHashMap();
    state.put(DIE, die);
    state.put(ACTION, lastAction.name());
    state.put(LAST_TWO_ROLLS, lastTwoRolls);
    state.put(LAST_TWO_MOVES, lastTwoMoves);
    
    // Add pieces to state - order doesn't matter since it's a map
    for (int i = 0; i < PIECES_PER_PLAYER; i++) {
      state.put(R + i, redPieces.get(i));
      state.put(Y + i, yellowPieces.get(i));
    }
    
    return ImmutableMap.copyOf(state);
  }

  /**
   * Returns an UpdateUI that ignores lastState, lastMovePlayerId, playerIdToNumberOfTokensInPot
   * since those are not relevant to Aeroplane Chess 2-player version (all information can
   * be found in the state).
   */
  private UpdateUI createUpdateUI(
      String yourPlayerId, String turnOfPlayerId, Map<String, Object> state) {
    return new UpdateUI(yourPlayerId, playersInfo, state,
        emptyState, // we ignore lastState
        ImmutableList.<Operation>of(new SetTurn(turnOfPlayerId)),
        null,
        ImmutableMap.<String, Integer>of());
  }
  
}
