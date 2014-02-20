/* 
 * Credit to Prof. Zibin's CheatLogicTest.java
 */

package org.aeroplanechess.client;

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.aeroplanechess.client.GameApi.Operation;
import org.aeroplanechess.client.GameApi.Set;
import org.aeroplanechess.client.GameApi.SetTurn;
import org.aeroplanechess.client.GameApi.SetRandomInteger;
import org.aeroplanechess.client.GameApi.VerifyMove;
import org.aeroplanechess.client.GameApi.VerifyMoveDone;
import org.aeroplanechess.client.GameApi.EndGame;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

@RunWith(JUnit4.class)
public class AeroplaneChessLogicTest {

  AeroplaneChessLogic aeroplaneChessLogic = new AeroplaneChessLogic();
  /* 
   * The GameApi state entries used in Aeroplane Chess are:
   * die: die roll 1-6
   * action: taxi|move|stack|takeShortcut
   *     only one action will be allowed per operation set (you cannot set this twice in a MakeMove)
   * R0...R3: [location, stacked|unstacked, faceup|facedown]
   *     where location = H|L|T|F (hangar, launch, track, final stretch) + padded 2-digit number
   * Y0...Y3: [location, stacked|unstacked, faceup|facedown]
   * lastTwoRolls: [-1, -1] if no rolls yet by the player, otherwise stores previous rolls
   * lastTwoMoves: array of size 2, entries are strings where each char is a piece. ex. ["12", "3"]
   *     lastTwoMoves is reset to ["", ""] if no moves made yet by the player

   * When we send operations on these keys, it will always be in the above order.
   */
  
  /* Whether piece is face up or face down (only in Hangar can they be face down) */
  private static final String FACEUP = "faceup";
  private static final String FACEDOWN = "facedown";
  
  /* 
   * Whether piece is stacked with 1 or more other pieces (only on track/final stretch)
   * For simplicity I assume that a player can choose to either stack all or none on a space. If
   * a third/forth piece lands on the same space, then the player has the choice to stack all
   * or unstack all (including any that were previously stacked). 
   */
  private static final String STACKED = "stacked";
  private static final String UNSTACKED = "unstacked";
  
  /* Actions the player can take.  One of: initialize, taxi, move, takeShortcut, stack, jump */
  private static final String ACTION = "action";  
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
  
  /* Player info */
  private final int rId = 0;
  private final int yId = 1;
  private final String PLAYER_ID = "playerId";  
  private final Map<String, Object> rInfo = ImmutableMap.<String, Object>of(PLAYER_ID, rId);
  private final Map<String, Object> yInfo = ImmutableMap.<String, Object>of(PLAYER_ID, yId);
  private final List<Map<String, Object>> playersInfo = ImmutableList.of(rInfo, yInfo);
  
  /* States */
  private final Map<String, Object> emptyState = ImmutableMap.<String, Object>of();
  private final Map<String, Object> nonEmptyState = ImmutableMap.<String, Object>of("k", "v");
  
  /* Die info. Range is [DIE_FROM, DIE_TO). */
  private static final String DIE = "die";
  private static final int DIE_FROM = 1;
  private static final int DIE_TO = 7;
  
  private void assertMoveOk(VerifyMove verifyMove) {
    aeroplaneChessLogic.checkMoveIsLegal(verifyMove);
  }

  private void assertHacker(VerifyMove verifyMove) {
    VerifyMoveDone verifyDone = aeroplaneChessLogic.verify(verifyMove);
    assertEquals(verifyMove.getLastMovePlayerId(), verifyDone.getHackerPlayerId());
  }
  
  private VerifyMove move(
      int lastMovePlayerId, Map<String, Object> lastState, List<Operation> lastMove) {
    return new VerifyMove(playersInfo,
        // Don't need to check the resulting state (no hidden decisions)
        emptyState,
        lastState, lastMove, lastMovePlayerId, ImmutableMap.<Integer, Integer>of());
  }
  
  /* 
   * Returns a representation of a piece's location for an unstacked, face up piece
   * (This is the state for the majority of the pieces on the board.) 
   */
  private ImmutableList<String> getNewPiece(String location) {
    return aeroplaneChessLogic.getNewPiece(location);
  }
  
  /*
   * Adds pieces to the board for R and Y players, and rolls the die
   */
  private List<Operation> getInitialOperations() {
    return aeroplaneChessLogic.getInitialOperations(rId);
  }

  /* Games always begins with Red setting initial pieces and rolling the die */
  @Test
  public void testInitialMove() {
    assertMoveOk(move(rId, emptyState, getInitialOperations()));
  }

  @Test
  public void testInitialMoveByWrongPlayer() {
    assertHacker(move(yId, emptyState, getInitialOperations()));
  }

  @Test
  public void testInitialMoveFromNonEmptyState() {
    assertHacker(move(rId, nonEmptyState, getInitialOperations()));
    assertHacker(move(yId, nonEmptyState, getInitialOperations()));
  }

  /* Players can only launch planes on an even roll */
  @Test
  public void testTaxiFromEvenRoll() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 2)
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T12"))
        .put("R1", getNewPiece("H01"))
        .put("R2", getNewPiece("T51"))
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13"))
        .put("Y1", getNewPiece("L00"))
        .put("Y2", getNewPiece("H00"))
        .put("Y3", getNewPiece("T45"))
        .put(LAST_TWO_ROLLS, emptyRolls)  // Turn just switched so these are empty
        .put(LAST_TWO_MOVES, emptyMoves)   
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(yId), // Set turn to the other player since you can't jump or shortcut after
                          // taxi and since roll was not 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(ACTION, TAXI),  // Taxi should be legal on a 2
        new Set("R1", getNewPiece("L00")),  // Taxi plane to launch area
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Clear for other player
        new Set(LAST_TWO_MOVES, emptyMoves));  
    
    assertMoveOk(move(rId, state, operations));
    // Not Y's move
    assertHacker(move(yId, state, operations));
    // Can't taxi as first move
    assertHacker(move(rId, emptyState, operations));
    assertHacker(move(yId, emptyState, operations));
  }
  
  @Test
  public void testIllegalTaxiFromOddRoll() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T12"))
        .put("R1", getNewPiece("H01"))
        .put("R2", getNewPiece("T51"))
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13"))
        .put("Y1", getNewPiece("L00"))
        .put("Y2", getNewPiece("H00"))
        .put("Y3", getNewPiece("T45"))
        .put(LAST_TWO_ROLLS, emptyRolls)  // Turn just switched so these are empty
        .put(LAST_TWO_MOVES, emptyMoves)   
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(rId),  // Set turn to the other player since you can't jump or shortcut after
                           // taxi and since roll was not 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(ACTION, TAXI),  
        new Set("Y1", getNewPiece("L00")),  
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Clear for other player
        new Set(LAST_TWO_MOVES, emptyMoves));  // Clear for other player
    
    assertHacker(move(yId, state, operations));
  }
  
  /* Players can only stack planes on the track or final stretch */
  @Test
  public void testIllegalStackFromLaunch() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  // This is die state for *after* the stack (discarded if not a 6)
                      // (since there is the option to stack *between* moves)
        .put(ACTION, TAXI)  // Last action was taxi
        .put("R0", getNewPiece("T12"))
        .put("R1", getNewPiece("H01"))
        .put("R2", getNewPiece("T51"))
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13"))
        .put("Y1", getNewPiece("L00"))
        .put("Y2", getNewPiece("L01"))  // Y2 was already in Launch zone
        .put("Y3", getNewPiece("H03"))
        .put(LAST_TWO_ROLLS, ImmutableList.of(6, -1))  
        .put(LAST_TWO_MOVES, ImmutableList.of("1", ""))  // Last move was Y1 to Launch on die-6
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Set turn to myself until I use die: 3 after stack
        // Don't roll die if action is to stack and I previously rolled a 6
        new Set(ACTION, STACK),
        new Set("Y1", getNewPiece("L00")),
        new Set("Y2", getNewPiece("L00")),
        // Didn't use die on this move, don't set lastTwoRolls
        // A stack counts as "moving" both pieces so replace the last move
        new Set(LAST_TWO_MOVES, ImmutableList.of("12", "")));
    
    assertHacker(move(yId, state, operations));  // Can't stack in the Launch zone!
  }
  
  @Test
  public void testStackOnSameLocation() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 6)         // This is die state for *after* the stack 
        .put(ACTION, MOVE)   // (since there is the option to stack *between* moves)
        .put("R0", getNewPiece("T12"))  // Unstacked, face up
        .put("R1", getNewPiece("H01"))
        .put("R2", getNewPiece("T12"))  // Unstacked, face up
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13"))
        .put("Y1", getNewPiece("L00"))
        .put("Y2", getNewPiece("H00"))
        .put("Y3", getNewPiece("T45"))
        .put(LAST_TWO_ROLLS, ImmutableList.of(6, -1))
        .put(LAST_TWO_MOVES, ImmutableList.of("2", ""))  // Last move was R2 to same location as R0
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(rId), // Set turn to myself since I rolled a 6 before stack
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for after stacking
        new Set(ACTION, STACK),
        new Set("R0", ImmutableList.of("T12", STACKED, FACEUP)),  
        new Set("R2", ImmutableList.of("T12", STACKED, FACEUP)),  
        new Set(LAST_TWO_ROLLS, ImmutableList.of(6, 6)),
        // A stack counts as "moving" both pieces so replace the last move
        new Set(LAST_TWO_MOVES, ImmutableList.of("02", "")));
    
    assertMoveOk(move(rId, state, operations));
  }
  
  /* I assume that if one piece lands on the space where pieces of the same color reside, then the 
   * player has the choice to stack. If the player does not stack, then all pieces are unstacked; 
   * if the player does stack, then all pieces are stacked. (You cannot, for example, stack 2 pieces
   * and leave 1 free on the same space.)
   */
  @Test
  public void testStackAllOrNone() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 6)         
        .put(ACTION, MOVE)   
        .put("R0", ImmutableList.of("T12", STACKED, FACEUP))  // Stacked (previously), face up
        .put("R1", ImmutableList.of("T12", STACKED, FACEUP))  // Stacked, face up
        .put("R2", getNewPiece("T12"))  // Unstacked, face up
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13"))
        .put("Y1", getNewPiece("L00"))
        .put("Y2", getNewPiece("H00"))
        .put("Y3", getNewPiece("T45"))
        .put(LAST_TWO_ROLLS, ImmutableList.of(6, -1))
        .put(LAST_TWO_MOVES, ImmutableList.of("2", ""))  // Last move: R2 to same location as R0, R1
        .build();

    List<Operation> operationsStackAll = ImmutableList.<Operation>of(
        new SetTurn(rId), // Set turn to myself since I rolled a 6 before stack
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for after stacking
        new Set(ACTION, STACK),
        new Set("R0", ImmutableList.of("T12", STACKED, FACEUP)),
        new Set("R1", ImmutableList.of("T12", STACKED, FACEUP)),
        new Set("R2", ImmutableList.of("T12", STACKED, FACEUP)),  
        new Set(LAST_TWO_ROLLS, ImmutableList.of(6, 6)),
        // A stack counts as "moving" both pieces so replace the last move
        new Set(LAST_TWO_MOVES, ImmutableList.of("012", "")));
    
    List<Operation> operationsStackNone = ImmutableList.<Operation>of(
        new SetTurn(rId), // Set turn to myself since I rolled a 6 before stack
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for after unstacking
        new Set(ACTION, STACK),
        new Set("R0", getNewPiece("T12")),
        new Set("R1", getNewPiece("T12")),
        new Set("R2", getNewPiece("T12")), 
        new Set(LAST_TWO_ROLLS, ImmutableList.of(6, 6)),
        // Unstack means I didn't move multiple pieces on this move, only R2
        new Set(LAST_TWO_MOVES, ImmutableList.of("2", "")));
    
    List<Operation> operationsStackSome = ImmutableList.<Operation>of(
        new SetTurn(rId), // Set turn to myself until I use die: 3 after stack
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for after stacking
        new Set(ACTION, STACK),
        new Set("R0", ImmutableList.of("T12", STACKED, FACEUP)),
        new Set("R1", ImmutableList.of("T12", STACKED, FACEUP)),
        new Set("R2", getNewPiece("T12")),    // They are all on the same space so this is illegal  
        new Set(LAST_TWO_ROLLS, ImmutableList.of(6, 6)),
        new Set(LAST_TWO_MOVES, ImmutableList.of("2", "")));
    
    /* Player must use "stack" action to indicate stack all or stack none. He should not be able
     * to move pieces until after that.
     */
    List<Operation> operationsMoveJustOne = ImmutableList.<Operation>of(
        new SetTurn(rId), // Set turn to myself until you get back result of shortcut
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
        new Set(ACTION, MOVE),
        new Set("R0", ImmutableList.of("T12", STACKED, FACEUP)),
        new Set("R1", ImmutableList.of("T12", STACKED, FACEUP)),
        new Set("R2", getNewPiece("T15")),  
        new Set(LAST_TWO_ROLLS, ImmutableList.of(3, 6)),
        new Set(LAST_TWO_ROLLS, ImmutableList.of(6, 6)),
        new Set(LAST_TWO_MOVES, ImmutableList.of("2", "")));
    
    assertMoveOk(move(rId, state, operationsStackAll));
    assertMoveOk(move(rId, state, operationsStackNone));
    assertHacker(move(rId, state, operationsStackSome));
    assertHacker(move(rId, state, operationsMoveJustOne));
  }
  
  /* Stack is illegal if it is not a space you just landed on */
  @Test
  public void testIllegalStackOnNonLanding() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  // This is die state for *after* the stack (discarded if not a 6)
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T12"))  // Unstacked, face up -- several moves ago
        .put("R1", getNewPiece("T10"))  // Unstacked, face up -- several moves ago
        .put("R2", getNewPiece("T12"))  // Unstacked, face up -- last move
        .put("R3", getNewPiece("T10"))  // Unstacked, face up -- several moves ago
        .put("Y0", getNewPiece("T13"))
        .put("Y1", getNewPiece("L00"))
        .put("Y2", getNewPiece("H00"))
        .put("Y3", getNewPiece("T45"))
        .put(LAST_TWO_ROLLS, ImmutableList.of(6, -1)) 
        .put(LAST_TWO_MOVES, ImmutableList.of("2", ""))  // Last move was R2 to same location as R0
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(rId), // Set turn to myself until I use die: 3 after stack
        // Don't roll die since I plan to stack
        new Set(ACTION, STACK),
        new Set("R1", ImmutableList.of("T12", STACKED, FACEUP)),  // Neither R1 nor R3 were
        new Set("R3", ImmutableList.of("T12", STACKED, FACEUP)),            // just moved
        // Didn't roll die on this move, don't set lastTwoRolls
        // A stack counts as "moving" both pieces so replace the last move
        // (this is illegal too since neither R1 nor R3 were moved!) 
        new Set(LAST_TWO_MOVES, ImmutableList.of("13", "")));
    
    assertHacker(move(rId, state, operations));
  }
  
  /* Stack is illegal if the turn just switched to you */
  @Test
  public void testIllegalStackOnTurnSwitch() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  // Rolled by other player for you
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T12"))  // Unstacked, face up -- not on this turn
        .put("R1", getNewPiece("T15")) 
        .put("R2", getNewPiece("T12"))  // Unstacked, face up -- not on this turn
        .put("R3", getNewPiece("T25")) 
        .put("Y0", getNewPiece("T13"))
        .put("Y1", getNewPiece("L00"))
        .put("Y2", getNewPiece("H00"))
        .put("Y3", getNewPiece("T45"))
        .put(LAST_TWO_ROLLS, emptyRolls) 
        .put(LAST_TWO_MOVES, emptyMoves)  
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(rId),  // Set turn to myself until I use die: 3 after stack
        // Don't roll die since I plan to stack
        new Set(ACTION, STACK),
        new Set("R0", ImmutableList.of("T12", STACKED, FACEUP)),  // Neither R0 nor R2 were
        new Set("R2", ImmutableList.of("T12", STACKED, FACEUP)),            // just moved
        // Didn't roll die on this move, don't set lastTwoRolls
        // A stack counts as "moving" both pieces so replace the last move
        // (this is illegal too since we cannot stack as the first action on the turn!) 
        new Set(LAST_TWO_MOVES, ImmutableList.of("02", "")));
    
    assertHacker(move(rId, state, operations));
  }
  
  @Test
  public void testIllegalStackOnDifferentLocations() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 4)  // This is die state for *after* the stack (discarded if not a 6)
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T12"))  
        .put("R1", getNewPiece("H01"))
        .put("R2", getNewPiece("T11"))  
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13"))  // Unstacked, face up
        .put("Y1", getNewPiece("L00"))  
        .put("Y2", getNewPiece("H00"))  
        .put("Y3", getNewPiece("T45"))  // Unstacked, face up
        .put(LAST_TWO_ROLLS, ImmutableList.of(6, -1))  
        .put(LAST_TWO_MOVES, ImmutableList.of("0", ""))  // Last move was Y0
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Set turn to myself until I use die: 4 after stack
        // Don't roll die since I plan to stack
        new Set(ACTION, STACK),
        new Set("Y0", ImmutableList.of("T45", STACKED, FACEUP)),
        new Set("Y3", ImmutableList.of("T45", STACKED, FACEUP)),
        // Didn't roll die on this move, don't set lastTwoRolls
        // A stack counts as "moving" both pieces so replace the last move
        // (this is illegal too since pieces were not originally together!) 
        new Set(LAST_TWO_MOVES, ImmutableList.of("03", "")));
    
    assertHacker(move(yId, state, operations));
  }
  
  @Test
  public void testIllegalMoveUnstackedPieces() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 4)
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T12"))  
        .put("R1", getNewPiece("H01"))
        .put("R2", getNewPiece("T12"))  
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13"))  // Unstacked, face up
        .put("Y1", getNewPiece("L00"))  
        .put("Y2", getNewPiece("H00"))  
        .put("Y3", getNewPiece("T13"))  // Unstacked, face up
        .put(LAST_TWO_ROLLS, emptyRolls)  
        .put(LAST_TWO_MOVES, emptyMoves)
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Give turn to other player since no 6, jump or shortcut
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(ACTION, MOVE),
        new Set("Y0", ImmutableList.of("T17", STACKED, FACEUP)),
        new Set("Y3", ImmutableList.of("T17", STACKED, FACEUP)),
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Reset for other player
        new Set(LAST_TWO_MOVES, emptyMoves));
    
    assertHacker(move(yId, state, operations));
  }
  
  /* A shortcut is necessarily the last move of a turn unless you rolled a 6 previously.
   * There are no jumps after taking a shortcut.
   */
  @Test
  public void testTakeShortcutFromStack() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  
        .put(ACTION, STACK)  // It's currently R's turn and R chose to stack previously
        .put("R0", ImmutableList.of("T36", STACKED, FACEUP))  // Stacked pieces - shortcut space
        .put("R1", getNewPiece("H01"))
        .put("R2", ImmutableList.of("T36", STACKED, FACEUP))  // Stacked pieces - shortcut space
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13"))
        .put("Y1", getNewPiece("L00"))
        .put("Y2", getNewPiece("H00"))
        .put("Y3", getNewPiece("T45"))
        .put(LAST_TWO_ROLLS, ImmutableList.of(3, -1))
        .put(LAST_TWO_MOVES, ImmutableList.of("02", ""))  // Last move was stacking R0 and R2
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Give up turn to other player since didn't roll 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(ACTION, TAKE_SHORTCUT),
        // Move both stacked pieces across shortcut
        new Set("R0", ImmutableList.of("T48", STACKED, FACEUP)),
        new Set("R2", ImmutableList.of("T48", STACKED, FACEUP)),
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Clear for other player
        new Set(LAST_TWO_MOVES, emptyMoves));  
    
    assertMoveOk(move(rId, state, operations));
  }
  
  @Test
  public void testTakeShortcutFromMove() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T33")) 
        .put("R1", getNewPiece("H01"))
        .put("R2", getNewPiece("T36"))  // Shortcut space
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13"))
        .put("Y1", getNewPiece("L00"))
        .put("Y2", getNewPiece("H00"))
        .put("Y3", getNewPiece("T45"))
        .put(LAST_TWO_ROLLS, ImmutableList.of(3, -1))
        .put(LAST_TWO_MOVES, ImmutableList.of("2", ""))  
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Give up turn to other player since didn't roll 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(ACTION, TAKE_SHORTCUT),
        new Set("R2", getNewPiece("T48")),
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Clear for other player
        new Set(LAST_TWO_MOVES, emptyMoves));
    
    assertMoveOk(move(rId, state, operations));
  }
  
  // Taking a shortcut is optional.  If I choose not to take it, then switch turns.
  @Test
  public void testRefuseShortcutSwitchPlayer() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T33")) 
        .put("R1", getNewPiece("H01"))
        .put("R2", getNewPiece("T36"))  // Shortcut space
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13"))
        .put("Y1", getNewPiece("L00"))
        .put("Y2", getNewPiece("H00"))
        .put("Y3", getNewPiece("T45"))
        .put(LAST_TWO_ROLLS, ImmutableList.of(3, -1))
        .put(LAST_TWO_MOVES, ImmutableList.of("2", ""))  
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Give up turn to other player since didn't roll 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(ACTION, TAKE_SHORTCUT),  // This can be either taking or deciding not to take
                  // the shortcut, but since I didn't update any pieces then I didn't take it
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Clear for other player
        new Set(LAST_TWO_MOVES, emptyMoves));  
    
    assertMoveOk(move(rId, state, operations));
  }
  
  @Test
  public void testTakeShortcutSendOpposingToHangar() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T33")) 
        .put("R1", getNewPiece("H01"))
        .put("R2", getNewPiece("T36"))  // Shortcut space
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13"))
        .put("Y1", getNewPiece("F02"))  // In my way!
        .put("Y2", getNewPiece("H00"))
        .put("Y3", getNewPiece("T45"))
        .put(LAST_TWO_ROLLS, ImmutableList.of(3, -1))
        .put(LAST_TWO_MOVES, ImmutableList.of("2", ""))
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Give up turn to other player since didn't roll 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(ACTION, TAKE_SHORTCUT),
        new Set("R2", getNewPiece("T48")),
        new Set("Y1", getNewPiece("H01")),  // Send them on their merry way
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Clear for other player
        new Set(LAST_TWO_MOVES, emptyMoves));  
    
    assertMoveOk(move(rId, state, operations));
  }
  
  /* 
   * The only two situations where you can move after a shortcut are: 1) when you had rolled a 6 
   * to get to the shortcut, or 2) if there is a stack available after the shortcut (both situations
   * can also happen together).  You cannot jump after a shortcut.
   */
  @Test
  public void testAllowMoveAfterShortcut() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 6)  
        .put(ACTION, MOVE)  
        .put("R0", getNewPiece("T33")) 
        .put("R1", getNewPiece("H01"))
        .put("R2", getNewPiece("T36"))  // Shortcut space
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13"))
        .put("Y1", getNewPiece("F02"))  
        .put("Y2", getNewPiece("H00"))
        .put("Y3", getNewPiece("T45"))
        // Since we previously rolled a 6, we can set the turn back to ourselves after taking
        // this shortcut
        .put(LAST_TWO_ROLLS, ImmutableList.of(6, -1)) 
        .put(LAST_TWO_MOVES, ImmutableList.of("2", ""))
        .build();
    
    List<Operation> operationsMove = ImmutableList.<Operation>of(
        new SetTurn(rId),  // Set back to myself since I rolled a 6 before shortcut
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for after taking the shortcut
        new Set(ACTION, TAKE_SHORTCUT),
        new Set("R2", getNewPiece("T48")),
        new Set("Y1", getNewPiece("H01")),  // Bounce yellow piece back to hangar
        new Set(LAST_TWO_ROLLS, ImmutableList.of(6, 6)),
        new Set(LAST_TWO_MOVES, ImmutableList.of("2", "")));
    
    List<Operation> operationsPassTurn = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Give up turn to other player (but I should be keeping it)
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(ACTION, TAKE_SHORTCUT),
        new Set("R2", getNewPiece("T48")),
        new Set("Y1", getNewPiece("H01")),  // Bounce yellow piece back to hangar
        new Set(LAST_TWO_ROLLS, emptyRolls),
        new Set(LAST_TWO_MOVES, emptyMoves)); 
    
    assertMoveOk(move(rId, state, operationsMove));
    assertHacker(move(rId, state, operationsPassTurn));
  }
  
  /* The normal flow of gameplay is that when a piece lands on a space where there is another piece
   * of the same color, the player has the choice to stack. Subsequently the player might also
   * automatically jump (if he landed on a jump space).
   */
  @Test
  public void testStackThenJump() {
 // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  
        .put(ACTION, STACK)
        .put("R0", getNewPiece("T33")) 
        .put("R1", getNewPiece("H01"))
        .put("R2", getNewPiece("T40"))  // Jump available after landing here
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13")) 
        .put("Y1", getNewPiece("T44"))  
        .put("Y2", getNewPiece("H00"))
        .put("Y3", getNewPiece("T45"))
        .put(LAST_TWO_ROLLS, ImmutableList.of(3, -1))
        .put(LAST_TWO_MOVES, ImmutableList.of("2", ""))
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Give up turn to other player since didn't roll 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(ACTION, JUMP),
        new Set("R2", getNewPiece("T44")),
        new Set("Y1", getNewPiece("H01")),  // Send them on their merry way
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Clear for other player
        new Set(LAST_TWO_MOVES, emptyMoves));  
    
    assertMoveOk(move(rId, state, operations));
  }
  
  /* Can't move opponent's pieces except on initialization or if you land directly on them,
   * or if they are in the path of a shortcut.  If they are in your path on the track then
   * play proceeds normally without sending them back to the hangar.
   */
  @Test
  public void testIllegalMoveOpponentPieces() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T12"))  
        .put("R1", getNewPiece("H01"))
        .put("R2", getNewPiece("T12"))  
        .put("R3", getNewPiece("T40"))
        .put("Y0", getNewPiece("T14"))  
        .put("Y1", getNewPiece("T39"))  
        .put("Y2", getNewPiece("H00"))  
        .put("Y3", getNewPiece("T13"))  
        .put(LAST_TWO_ROLLS, emptyRolls)  
        .put(LAST_TWO_MOVES, emptyMoves)
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Give up turn to other player since didn't roll 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(ACTION, MOVE),
        new Set("Y1", getNewPiece("T42")),  // Move Y1 from T39 --> T42
        new Set("R3", getNewPiece("H01")),  // Should not be able to move this
                                          // Even though he is in my path, I didn't land on him
        new Set(LAST_TWO_ROLLS, emptyRolls),
        new Set(LAST_TWO_MOVES, emptyMoves));
    
    assertHacker(move(yId, state, operations));
  }
  
  @Test
  public void testMoveOpponentPiecesOnLanding() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T12"))  
        .put("R1", getNewPiece("H01"))
        .put("R2", getNewPiece("T12"))  
        .put("R3", getNewPiece("T42"))  // Piece Y1 is moved here, R3 is sent back to hangar
        .put("Y0", getNewPiece("T14"))  
        .put("Y1", getNewPiece("T39"))  
        .put("Y2", getNewPiece("H00"))  
        .put("Y3", getNewPiece("T13"))  
        .put(LAST_TWO_ROLLS, emptyRolls)  
        .put(LAST_TWO_MOVES, emptyMoves)
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(rId),  // Give up turn to other player since didn't roll 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(ACTION, MOVE),
        new Set("Y1", getNewPiece("T42")),  // Move Y1 from T39 --> T42
        new Set("R3", getNewPiece("H03")),  // Send them on their merry way
        new Set(LAST_TWO_ROLLS, emptyRolls),
        new Set(LAST_TWO_MOVES, emptyMoves));
    
    assertMoveOk(move(yId, state, operations));
  }
  
  
  @Test
  public void testJumpAndSendOpposingToHangar() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 1)
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T14"))  
        .put("R1", getNewPiece("T40"))  // R1 was just moved here, jump is available  
        .put("R2", getNewPiece("H00"))  
        .put("R3", getNewPiece("T13"))  
        .put("Y0", getNewPiece("T12"))  
        .put("Y1", getNewPiece("H01"))
        .put("Y2", getNewPiece("H01"))  
        .put("Y3", getNewPiece("T44"))  // Piece R1 is moved here after jump, Y3 is sent back
        .put(LAST_TWO_ROLLS, ImmutableList.of(1, -1))  
        .put(LAST_TWO_MOVES, ImmutableList.of("1", ""))
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Give up turn to other player since didn't roll 6 or land on shortcut
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(ACTION, JUMP),
        new Set("R1", getNewPiece("T44")),  // Jump R1 from T40 --> T44 since T40 is red
        new Set("Y3", getNewPiece("H03")),  // Send them on their merry way
        new Set(LAST_TWO_ROLLS, emptyRolls),
        new Set(LAST_TWO_MOVES, emptyMoves));
    
    assertMoveOk(move(rId, state, operations));
  }
  
  @Test
  public void testTakeIllegalOpponentShortcut() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T23"))  // Shortcut space - G
        .put("R1", getNewPiece("T10"))  // Shortcut space - Y
        .put("R2", getNewPiece("T49"))  // Shortcut space - B
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13"))
        .put("Y1", getNewPiece("L00"))
        .put("Y2", getNewPiece("H00"))
        .put("Y3", getNewPiece("T45"))
        .put(LAST_TWO_ROLLS, emptyRolls)
        .put(LAST_TWO_MOVES, emptyMoves)  
        .build();

    List<Operation> operationsGreen = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Give up turn to other player since didn't roll 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(ACTION, TAKE_SHORTCUT),
        new Set("R0", getNewPiece("T35")),  // Take G's shortcut -- illegal!
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Clear for other player
        new Set(LAST_TWO_MOVES, emptyMoves));  
    
    List<Operation> operationYellow = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Give up turn to other player since didn't roll 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(ACTION, TAKE_SHORTCUT),
        new Set("R1", getNewPiece("T22")),  // Take G's shortcut -- illegal!
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Clear for other player
        new Set(LAST_TWO_MOVES, emptyMoves));  
    
    List<Operation> operationBlue = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Give up turn to other player since didn't roll 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(ACTION, TAKE_SHORTCUT),
        new Set("R2", getNewPiece("T09")),  // Take B's shortcut -- illegal!
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Clear for other player
        new Set(LAST_TWO_MOVES, emptyMoves));  
    
    assertHacker(move(rId, state, operationsGreen));
    assertHacker(move(rId, state, operationYellow));
    assertHacker(move(rId, state, operationBlue));
  }
  
  @Test
  public void testMoveWrongNumberOfSpaces() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 5)
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T12"))  
        .put("R1", getNewPiece("H01"))
        .put("R2", getNewPiece("T12"))  
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13"))  
        .put("Y1", getNewPiece("L00"))  
        .put("Y2", getNewPiece("H00"))  
        .put("Y3", getNewPiece("T13")) 
        .put(LAST_TWO_ROLLS, emptyRolls)  
        .put(LAST_TWO_MOVES, emptyMoves)
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(rId),  // Give up turn to other player since didn't roll 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
        new Set(ACTION, MOVE),
        new Set("Y1", getNewPiece("T25")),  // { die: 5 } but moved 25 spaces
        new Set(LAST_TWO_ROLLS, emptyRolls),
        new Set(LAST_TWO_MOVES, emptyMoves));
    
    assertHacker(move(yId, state, operations));
  }
  
  @Test
  public void testIllegalMoveIntoOtherFinalStretch() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 5)
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T25"))  
        .put("R1", getNewPiece("T00"))
        .put("R2", getNewPiece("T29"))  
        .put("R3", getNewPiece("T40"))
        .put("Y0", getNewPiece("L02"))  
        .put("Y1", getNewPiece("L00"))  
        .put("Y2", getNewPiece("T29"))  
        .put("Y3", getNewPiece("T14")) 
        .put(LAST_TWO_ROLLS, emptyRolls)  
        .put(LAST_TWO_MOVES, emptyMoves)
        .build();

    List<Operation> operationsGreen = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Give up turn to other player since didn't roll 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
        new Set(ACTION, MOVE),
        new Set("R1", getNewPiece("F01")),  // T00 to F01 is a move to G's final stretch
        new Set(LAST_TWO_ROLLS, emptyRolls),
        new Set(LAST_TWO_MOVES, emptyMoves));
        
    List<Operation> operationsBlue = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Give up turn to other player since didn't roll 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
        new Set(ACTION, MOVE),
        new Set("R2", getNewPiece("F04")),  // T29 to F04 is a move to B's final stretch
        new Set(LAST_TWO_ROLLS, emptyRolls),
        new Set(LAST_TWO_MOVES, emptyMoves));
        
    List<Operation> operationsYellow = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Give up turn to other player since didn't roll 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
        new Set(ACTION, MOVE),
        new Set("R3", getNewPiece("F02")),  // T40 to F02 is a move to Y's final stretch
        new Set(LAST_TWO_ROLLS, emptyRolls),
        new Set(LAST_TWO_MOVES, emptyMoves));
    
    assertHacker(move(rId, state, operationsGreen));
    assertHacker(move(rId, state, operationsBlue));
    assertHacker(move(rId, state, operationsYellow));
  }
  
  @Test
  public void testMoveIntoOwnFinalStretch() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 5)
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T13"))  
        .put("R1", getNewPiece("T00"))
        .put("R2", getNewPiece("H03"))  
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("L02"))  
        .put("Y1", getNewPiece("L00"))  
        .put("Y2", getNewPiece("T29"))  
        .put("Y3", getNewPiece("T14")) 
        .put(LAST_TWO_ROLLS, emptyRolls)  
        .put(LAST_TWO_MOVES, emptyMoves)
        .build();
        
    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Give up turn to other player since didn't roll 6
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
        new Set(ACTION, MOVE),
        new Set("R0", getNewPiece("F01")),  // T13 to F01 is a move to R's final stretch
        new Set(LAST_TWO_ROLLS, emptyRolls),
        new Set(LAST_TWO_MOVES, emptyMoves));
    
    assertMoveOk(move(rId, state, operations));
  }
  
  @Test
  public void testTripleSixBackToHangar() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 6)  // This (3rd) roll was a 6
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T13"))  
        .put("R1", getNewPiece("L00"))
        .put("R2", ImmutableList.of("T22", STACKED, FACEUP))  
        .put("R3", ImmutableList.of("T22", STACKED, FACEUP))
        .put("Y0", getNewPiece("L02"))  
        .put("Y1", getNewPiece("L00"))  
        .put("Y2", getNewPiece("T29"))  
        .put("Y3", getNewPiece("T14")) 
        .put(LAST_TWO_ROLLS, ImmutableList.of(6, 6))  // And previous two rolls were 6's
        // Pieces that must go back to their hangars (2 and 3 are stacked)
        .put(LAST_TWO_MOVES, ImmutableList.of("1", "23"))  
        .build();
        
    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(yId),  // Set turn to Y since I have gotten 3 6's and can't make any more moves
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO), // Roll die for other player
        new Set(ACTION, MOVE),
        new Set("R1", getNewPiece("H01")),  // Send all pieces affected by last 2 rolls back
        new Set("R2", getNewPiece("H02")),  // to the hangar
        new Set("R3", getNewPiece("H03")),  
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Switching turn, clear these
        new Set(LAST_TWO_MOVES, emptyMoves));
    
    assertMoveOk(move(rId, state, operations));
  }
  
  @Test
  public void testEndGame() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 2)
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("F03"))  
        .put("R1", ImmutableList.of("H01", UNSTACKED, FACEDOWN))
        .put("R2", ImmutableList.of("H02", UNSTACKED, FACEDOWN))  
        .put("R3", ImmutableList.of("H03", UNSTACKED, FACEDOWN))
        .put("Y0", getNewPiece("L02"))  
        .put("Y1", getNewPiece("T40"))  
        .put("Y2", ImmutableList.of("H01", UNSTACKED, FACEDOWN))
        .put("Y3", ImmutableList.of("H02", UNSTACKED, FACEDOWN))  
        .put(LAST_TWO_ROLLS, emptyRolls)  
        .put(LAST_TWO_MOVES, emptyMoves) 
        .build();
        
    List<Operation> operations = ImmutableList.<Operation>of(
        new SetTurn(rId), 
        // No need to roll the die - I've won!
        new Set(ACTION, MOVE),
        // Move it straight to the hangar, face down
        new Set("R0", ImmutableList.of("H00", UNSTACKED, FACEDOWN)),  
        new EndGame(rId));

    assertMoveOk(move(rId, state, operations));
  }

  /* 
   * If you don't roll exactly the amount to get into the center base, you must backtrack that 
   * amount and can't end the game.
   */
  @Test
  public void testIllegalEndNeedBacktrack() {
    // State: die, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves 
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 5)
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("F03")) 
        .put("R1", getNewPiece("T40"))
        .put("R2", ImmutableList.of("H02", UNSTACKED, FACEDOWN))  
        .put("R3", ImmutableList.of("H03", UNSTACKED, FACEDOWN))
        .put("Y0", getNewPiece("F02"))  
        .put("Y1", ImmutableList.of("H03", UNSTACKED, FACEDOWN)) 
        .put("Y2", ImmutableList.of("H01", UNSTACKED, FACEDOWN))
        .put("Y3", ImmutableList.of("H02", UNSTACKED, FACEDOWN))  
        .put(LAST_TWO_ROLLS, emptyRolls)  
        .put(LAST_TWO_MOVES, emptyMoves)  
        .build();
        
    List<Operation> operationsIllegalWin = ImmutableList.<Operation>of(
        new SetTurn(yId), 
        // No need to roll the die if I had won (I didn't)
        new Set(ACTION, MOVE),
        // Move it straight to the hangar, face down
        new Set("Y0", ImmutableList.of("H00", UNSTACKED, FACEDOWN)),  
        new EndGame(yId));
    
    List<Operation> operationsBacktrack = ImmutableList.<Operation>of(
        new SetTurn(rId),  // Set turn to R since I just backtracked
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO), // Roll die for other player
        new Set(ACTION, MOVE),
        new Set("Y0", getNewPiece("T39")), // Backtracking 5 spaces F02 --> T39
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Switching turn, clear these
        new Set(LAST_TWO_MOVES, emptyMoves));

    assertHacker(move(yId, state, operationsIllegalWin));
    assertMoveOk(move(yId, state, operationsBacktrack));
  }
}