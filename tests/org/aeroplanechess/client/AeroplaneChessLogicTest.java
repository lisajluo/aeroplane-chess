/* 
 * Credit to Prof. Zibin's CheatLogicTest.java
 */

package org.aeroplanechess.client;

import static com.google.common.base.Preconditions.checkArgument;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.aeroplanechess.client.GameApi.Operation;
import org.aeroplanechess.client.GameApi.Set;
import org.aeroplanechess.client.GameApi.SetRandomInteger;
import org.aeroplanechess.client.GameApi.VerifyMove;
import org.aeroplanechess.client.GameApi.VerifyMoveDone;
import org.aeroplanechess.client.GameApi.EndGame;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

@RunWith(JUnit4.class)
public class AeroplaneChessLogicTest {

  /* The GameApi state entries used in Aeroplane Chess are:
   * die: die roll 1-6
   * turn: R|Y
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
  
  /* Player and space colors */
  private static final String TURN = "turn";  // turn: implementing 2-player version with R|Y
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
  
  /* Whether piece is stacked with 1 or more other pieces (only on track/final stretch)
   * For simplicity I assume that a player can choose to either stack all or none on a space. Once
   * the pieces are stacked, if another piece lands on that space then they must all be stacked. */
  private static final String STACKED = "stacked";
  private static final String UNSTACKED = "unstacked";
  
  /* Actions the player can take */
  private static final String ACTION = "action";  // action: initialize, taxi, move, stack
  private static final String INITIALIZE = "initialize";
  private static final String TAXI = "taxi";
  private static final String MOVE = "move";
  private static final String STACK = "stack";
  private static final String TAKE_SHORTCUT = "takeShortcut";
  
  /* Information needed to send pieces moved on last two moves to hangar (if 3 6's are rolled) */
  private static final String LAST_TWO_ROLLS = "lastTwoRolls";
  private static final String LAST_TWO_MOVES = "lastTwoMoves";
  
  /* Empty representations for lastTwoRolls and lastTwoMoves */
  private final List<Integer> emptyRolls = ImmutableList.of(-1, -1);
  private final List<String> emptyMoves = ImmutableList.of("", "");
  
  /* Player info */
  private final int rId = 0;
  private final int yId = 1;
  private final String playerId = "playerId";  
  private final Map<String, Object> rInfo = ImmutableMap.<String, Object>of(playerId, rId);
  private final Map<String, Object> yInfo = ImmutableMap.<String, Object>of(playerId, yId);
  private final List<Map<String, Object>> playersInfo = ImmutableList.of(rInfo, yInfo);
  
  /* States */
  private final Map<String, Object> emptyState = ImmutableMap.<String, Object>of();
  private final Map<String, Object> nonEmptyState = ImmutableMap.<String, Object>of("k", "v");
  
  /* Die info. Range is [DIE_FROM, DIE_TO). */
  private static final String DIE = "die";
  private static final int DIE_FROM = 1;
  private static final int DIE_TO = 7;
  
  private void assertMoveOk(VerifyMove verifyMove) {
    VerifyMoveDone verifyDone = new AeroplaneChessLogic().verify(verifyMove);
    assertEquals(new VerifyMoveDone(), verifyDone);
  }

  private void assertHacker(VerifyMove verifyMove) {
    VerifyMoveDone verifyDone = new AeroplaneChessLogic().verify(verifyMove);
    assertEquals(new VerifyMoveDone(verifyMove.getLastMovePlayerId(), "Hacker found"), verifyDone);
  }
  
  private VerifyMove move(
      int lastMovePlayerId, Map<String, Object> lastState, List<Operation> lastMove) {
    return new VerifyMove(rId, playersInfo,
        // Don't need to check the resulting state (no hidden decisions)
        emptyState,
        lastState, lastMove, lastMovePlayerId);
  }
  
  /* Returns a representation of a piece's location for an unstacked, face up piece
   * (This is the state for the majority of the pieces on the board.) */
  private ImmutableList<String> getNewPiece(String location) {
    String region = location.substring(0, 1);
    int index = Integer.parseInt(location.substring(1, 2));
    
    checkArgument((index >= 0)
        && ((region.equals(H) && index < 4) 
            || (region.equals(L) && index < 4) 
            || (region.equals(T) && index < 52) 
            || (region.equals(F) && index < 6)));
    
    return ImmutableList.of(location, UNSTACKED, FACEUP);
  }

  private List<Operation> getInitialOperations() {
    List<Operation> operations = Lists.newArrayList();
    
    /* Order: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    operations.add(new SetRandomInteger(DIE, DIE_FROM, DIE_TO));
    operations.add(new Set(TURN, R));
    operations.add(new Set(ACTION, INITIALIZE));
    
    // add Red player's pieces
    for (int i = 0; i <= 4; i++) {
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
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 2)
        .put(TURN, R)
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
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(TURN, Y),  // Set turn to the other player since you can't jump or shortcut after
                                   // taxi and since roll was not 6
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
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)
        .put(TURN, Y)
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
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(TURN, R),  // Set turn to the other player since you can't jump or shortcut after
                                  // taxi and since roll was not 6
        new Set(ACTION, TAXI),  
        new Set("Y1", getNewPiece("L00")),  
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Clear for other player
        new Set(LAST_TWO_MOVES, emptyMoves));  // Clear for other player
    
    assertHacker(move(yId, state, operations));
  }
  
  /* Players can only stack planes on the track or final stretch */
  @Test
  public void testIllegalStackFromLaunch() {
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  // This is die state for *after* the stack (discarded if not a 6)
        .put(TURN, Y)               // (since there is the option to stack *between* moves)
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
        // Don't roll die if action is to stack
        new Set(TURN, Y),  // Set turn to myself until you get back result of shortcut
        new Set(ACTION, STACK),
        new Set("Y1", getNewPiece("L00")),
        new Set("Y2", getNewPiece("L00")),
        // Didn't roll die on this move, don't set lastTwoRolls
        // A stack counts as "moving" both pieces so replace the last move
        new Set(LAST_TWO_MOVES, ImmutableList.of("12", "")));
    
    assertHacker(move(yId, state, operations));  // Can't stack in the Launch zone!
  }
  
  @Test
  public void testStackOnSameLocation() {
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  // This is die state for *after* the stack (discarded if not a 6)
        .put(TURN, R)               // (since there is the option to stack *between* moves)
        .put(ACTION, MOVE)
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
        // Don't roll die since I plan to stack, I will use { die: 3 } for after stacking
        new Set(TURN, R),  // Set turn to myself until you get back result of shortcut
        new Set(ACTION, STACK),
        new Set("R0", ImmutableList.of("T12", STACKED, FACEUP)),  
        new Set("R2", ImmutableList.of("T12", STACKED, FACEUP)),  
        // Didn't roll die on this move, don't set lastTwoRolls
        // A stack counts as "moving" both pieces so replace the last move
        new Set(LAST_TWO_MOVES, ImmutableList.of("02", "")));
    
    assertMoveOk(move(rId, state, operations));
  }
  
  /* Stack is illegal if it is not a space you just landed on */
  @Test
  public void testIllegalStackOnNonLanding() {
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  // This is die state for *after* the stack (discarded if not a 6)
        .put(TURN, R)
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T12"))  // Unstacked, face up -- last move
        .put("R1", getNewPiece("T10"))  // Unstacked, face up -- previous move
        .put("R2", getNewPiece("T12"))  // Unstacked, face up -- last move
        .put("R3", getNewPiece("T10"))  // Unstacked, face up -- previous move
        .put("Y0", getNewPiece("T13"))
        .put("Y1", getNewPiece("L00"))
        .put("Y2", getNewPiece("H00"))
        .put("Y3", getNewPiece("T45"))
        .put(LAST_TWO_ROLLS, ImmutableList.of(6, -1)) 
        .put(LAST_TWO_MOVES, ImmutableList.of("2", ""))  // Last move was R2 to same location as R0
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        // Don't roll die since I plan to stack
        new Set(TURN, R),  // Set turn to myself until you get back result of shortcut
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
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  // Rolled by other player for you
        .put(TURN, R)
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
        // Don't roll die since I plan to stack
        new Set(TURN, R),  // Set turn to myself until you get back result of shortcut
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
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 4)  // This is die state for *after* the stack (discarded if not a 6)
        .put(TURN, Y)
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
        // Don't roll die since I plan to stack
        new Set(TURN, Y),  // Set turn to myself until you get back result of shortcut
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
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 4)
        .put(TURN, Y)
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
        // No need to roll die since didn't roll a 6
        new Set(TURN, Y),  // Set turn to myself until you get back result of shortcut
        new Set(ACTION, MOVE),
        new Set("Y0", ImmutableList.of("T17", STACKED, FACEUP)),
        new Set("Y3", ImmutableList.of("T17", STACKED, FACEUP)),
        new Set(LAST_TWO_ROLLS, ImmutableList.of(4, -1)),
        new Set(LAST_TWO_MOVES, ImmutableList.of("03", "")));
    
    assertHacker(move(yId, state, operations));
  }
  
  /* A shortcut is necessarily the last move of a turn unless you rolled a 6 previously.
   * There are no jumps after taking a shortcut.
   */
  @Test
  public void testTakeShortcutFromStack() {
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  
        .put(TURN, R)               // It's currently my turn and I chose to stack previously
        .put(ACTION, STACK)
        .put("R0", ImmutableList.of("T36", STACKED, FACEUP))  // Stacked pieces - shortcut space
        .put("R1", getNewPiece("H01"))
        .put("R2", ImmutableList.of("T36", STACKED, FACEUP))  // Stacked pieces - shortcut space
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13"))
        .put("Y1", getNewPiece("L00"))
        .put("Y2", getNewPiece("H00"))
        .put("Y3", getNewPiece("T45"))
        .put(LAST_TWO_ROLLS, ImmutableList.of(6, -1))
        .put(LAST_TWO_MOVES, ImmutableList.of("02", ""))  // Last move was stacking R0 and R2
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(TURN, Y),  // Give up turn to other player since didn't roll 6
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
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  
        .put(TURN, R)               
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
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(TURN, Y),  // Give up turn to other player since didn't roll 6
        new Set(ACTION, TAKE_SHORTCUT),
        new Set("R2", getNewPiece("T48")),
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Clear for other player
        new Set(LAST_TWO_MOVES, emptyMoves));  
    
    assertMoveOk(move(rId, state, operations));
  }
  
  @Test
  public void testTakeShortcutSendOpposingToHangar() {
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  
        .put(TURN, R)               
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T33")) 
        .put("R1", getNewPiece("H01"))
        .put("R2", getNewPiece("T36"))  // Shortcut space
        .put("R3", getNewPiece("T29"))
        .put("Y0", getNewPiece("T13"))
        .put("Y1", getNewPiece("F03"))  // In my way!
        .put("Y2", getNewPiece("H00"))
        .put("Y3", getNewPiece("T45"))
        .put(LAST_TWO_ROLLS, ImmutableList.of(3, -1))
        .put(LAST_TWO_MOVES, ImmutableList.of("2", ""))
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(TURN, Y),  // Give up turn to other player since didn't roll 6
        new Set(ACTION, TAKE_SHORTCUT),
        new Set("R2", getNewPiece("T48")),
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
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)
        .put(TURN, Y)
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
        // No need to roll die since didn't roll a 6
        new Set(TURN, Y),  // Set turn to myself until you get back result of shortcut
        new Set(ACTION, MOVE),
        new Set("Y1", getNewPiece("T42")),  // Move Y1 from T39 --> T42
        new Set("R3", getNewPiece("H01")),  // Should not be able to move this
                                          // Even though he is in my path, I didn't land on him
        new Set(LAST_TWO_ROLLS, ImmutableList.of(3, -1)),
        new Set(LAST_TWO_MOVES, ImmutableList.of("1", "")));
    
    assertHacker(move(yId, state, operations));
  }
  
  @Test
  public void testMoveOpponentPiecesOnLanding() {
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)
        .put(TURN, Y)
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
        // No need to roll die since didn't roll a 6
        new Set(TURN, Y),  // Set turn to myself until you get back result of shortcut
        new Set(ACTION, MOVE),
        new Set("Y1", getNewPiece("T42")),  // Move Y1 from T39 --> T42
        new Set("R3", getNewPiece("H01")),  // Send them on their merry way
        new Set(LAST_TWO_ROLLS, ImmutableList.of(3, -1)),
        new Set(LAST_TWO_MOVES, ImmutableList.of("1", "")));
    
    assertMoveOk(move(yId, state, operations));
  }
  
  @Test
  public void testJumpAndSendOpposingToHangar() {
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 1)
        .put(TURN, Y)
        .put(ACTION, MOVE)
        .put("R0", getNewPiece("T12"))  
        .put("R1", getNewPiece("H01"))
        .put("R2", getNewPiece("T40"))  // Piece Y1 is moved here before jump, R2 is sent back
        .put("R3", getNewPiece("T44"))  // Piece Y1 is moved here after jump, R3 is sent back
        .put("Y0", getNewPiece("T14"))  
        .put("Y1", getNewPiece("T39"))  
        .put("Y2", getNewPiece("H00"))  
        .put("Y3", getNewPiece("T13"))  
        .put(LAST_TWO_ROLLS, emptyRolls)  
        .put(LAST_TWO_MOVES, emptyMoves)
        .build();

    List<Operation> operations = ImmutableList.<Operation>of(
        // No need to roll die since didn't roll a 6
        new Set(TURN, Y),  // Set turn to myself until you get back result of shortcut
        new Set(ACTION, MOVE),
        new Set("Y1", getNewPiece("T44")),  // Move Y1 from T39 --> T40 --> jump to T44
        new Set("R2", getNewPiece("H00")),  // Send them on their merry way
        new Set("R3", getNewPiece("H01")),  // Send them on their merry way
        new Set(LAST_TWO_ROLLS, ImmutableList.of(1, -1)),
        new Set(LAST_TWO_MOVES, ImmutableList.of("1", "")));
    
    assertMoveOk(move(yId, state, operations));
  }
  
  @Test
  public void testTakeIllegalOpponentShortcut() {
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)  
        .put(TURN, R)               
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
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(TURN, Y),  // Give up turn to other player since didn't roll 6
        new Set(ACTION, TAKE_SHORTCUT),
        new Set("R0", getNewPiece("T35")),  // Take G's shortcut -- illegal!
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Clear for other player
        new Set(LAST_TWO_MOVES, emptyMoves));  
    
    List<Operation> operationYellow = ImmutableList.<Operation>of(
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(TURN, Y),  // Give up turn to other player since didn't roll 6
        new Set(ACTION, TAKE_SHORTCUT),
        new Set("R1", getNewPiece("T22")),  // Take G's shortcut -- illegal!
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Clear for other player
        new Set(LAST_TWO_MOVES, emptyMoves));  
    
    List<Operation> operationBlue = ImmutableList.<Operation>of(
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),  // Roll die for other player
        new Set(TURN, Y),  // Give up turn to other player since didn't roll 6
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
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 5)
        .put(TURN, Y)
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
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
        new Set(TURN, Y),  // Set turn to myself until you get back result of shortcut
        new Set(ACTION, MOVE),
        new Set("Y1", getNewPiece("T25")),  // { die: 5 } but moved 25 spaces
        new Set(LAST_TWO_ROLLS, ImmutableList.of(5, -1)),
        new Set(LAST_TWO_MOVES, ImmutableList.of("1", "")));
    
    assertHacker(move(yId, state, operations));
  }
  
  @Test
  public void testIllegalMoveIntoOtherFinalStretch() {
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 5)
        .put(TURN, R)
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
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
        new Set(TURN, R),  // Set turn to myself until you get back result of shortcut
        new Set(ACTION, MOVE),
        new Set("R1", getNewPiece("F01")),  // T00 to F01 is a move to G's final stretch
        new Set(LAST_TWO_ROLLS, ImmutableList.of(5, -1)),
        new Set(LAST_TWO_MOVES, ImmutableList.of("1", "")));
        
    List<Operation> operationsBlue = ImmutableList.<Operation>of(
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
        new Set(TURN, R),  // Set turn to myself until you get back result of shortcut
        new Set(ACTION, MOVE),
        new Set("R2", getNewPiece("F05")),  // T29 to F05 is a move to B's final stretch
        new Set(LAST_TWO_ROLLS, ImmutableList.of(5, -1)),
        new Set(LAST_TWO_MOVES, ImmutableList.of("2", "")));
        
    List<Operation> operationsYellow = ImmutableList.<Operation>of(
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
        new Set(TURN, R),  // Set turn to myself until you get back result of shortcut
        new Set(ACTION, MOVE),
        new Set("R3", getNewPiece("F03")),  // T40 to F03 is a move to Y's final stretch
        new Set(LAST_TWO_ROLLS, ImmutableList.of(5, -1)),
        new Set(LAST_TWO_MOVES, ImmutableList.of("3", "")));
    
    assertHacker(move(rId, state, operationsGreen));
    assertHacker(move(rId, state, operationsBlue));
    assertHacker(move(rId, state, operationsYellow));
  }
  
  @Test
  public void testMoveIntoOwnFinalStretch() {
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 5)
        .put(TURN, R)
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
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO),
        new Set(TURN, R),  // Set turn to myself until you get back result of shortcut
        new Set(ACTION, MOVE),
        new Set("R0", getNewPiece("F01")),  // T13 to F01 is a move to R's final stretch
        new Set(LAST_TWO_ROLLS, ImmutableList.of(5, -1)),
        new Set(LAST_TWO_MOVES, ImmutableList.of("0", "")));
    
    assertMoveOk(move(rId, state, operations));
  }
  
  @Test
  public void testTripleSixBackToHangar() {
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 6)  // This (3rd) roll was a 6
        .put(TURN, R)
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
        new SetRandomInteger(DIE, DIE_FROM, DIE_TO), // Roll die for other player
        new Set(TURN, R),  // Set turn to Y since I have gotten 3 6's and can't make any more moves 
        new Set(ACTION, MOVE),
        new Set("R1", getNewPiece("H00")),  // Send all pieces affected by last 2 rolls back
        new Set("R2", getNewPiece("H00")),  // to the hangar
        new Set("R3", getNewPiece("H00")),  
        new Set(LAST_TWO_ROLLS, emptyRolls),  // Switching turn, clear these
        new Set(LAST_TWO_MOVES, emptyMoves));
    
    assertMoveOk(move(rId, state, operations));
  }
  
  @Test
  public void testEndGame() {
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 3)
        .put(TURN, R)
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
        // No need to roll the die - I've won!
        new Set(TURN, R), 
        new Set(ACTION, MOVE),
        // Move it straight to the hangar, face down
        new Set("R0", ImmutableList.of("H00", UNSTACKED, FACEDOWN)),  
        new EndGame(rId));

    assertMoveOk(move(rId, state, operations));
  }

  /* If you don't roll exactly the amount to get into the center base, you must backtrack that 
   * amount and can't end the game.
   */
  @Test
  public void testIllegalEndNeedBacktrack() {
    /* State: die, turn, action, R0...R3, Y0...Y3, lastTwoRolls, lastTwoMoves */
    Map<String, Object> state = ImmutableMap.<String, Object>builder()
        .put(DIE, 5)
        .put(TURN, Y)
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
        
    List<Operation> operations = ImmutableList.<Operation>of(
        // No need to roll the die if I had won (I didn't)
        new Set(TURN, Y), 
        new Set(ACTION, MOVE),
        // Move it straight to the hangar, face down
        new Set("Y0", ImmutableList.of("H00", UNSTACKED, FACEDOWN)),  
        new EndGame(yId));

    assertHacker(move(yId, state, operations));
  }
}