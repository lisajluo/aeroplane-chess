/*
 * A list of common constants used throughout Aeroplane Chess logic, presenter, tests, etc.
 * For reference:
 * The GameApi state entries used in Aeroplane Chess are:
 * die: die roll 1-6
 * action: taxi|initialize|move|stack|takeShortcut|jump
 *     only one action will be allowed per operation set (you cannot set this twice in a MakeMove)
 * R0...R3: [location, stacked|unstacked, faceup|facedown]
 *     where location = H|L|T|F (hangar, launch, track, final stretch) + padded 2-digit number
 * Y0...Y3: [location, stacked|unstacked, faceup|facedown]
 * lastTwoRolls: [-1, -1] if no rolls yet by the player, otherwise stores previous rolls
 * lastTwoMoves: array of size 2, entries are strings where each char is a piece. ex. ["12", "3"]
 *     lastTwoMoves is reset to ["", ""] if no moves made yet by the player
 */

package org.aeroplanechess.client;

import java.util.List;

import com.google.common.collect.ImmutableList;

public final class Constants {
  
  private Constants() { }  // Prevent instantiation/subclassing
  
  /* Die info. Range is [DIE_FROM, DIE_TO). */
  static final String DIE = "die";
  static final int DIE_FROM = 1;
  static final int DIE_TO = 7;
  
  /* Board and player numerics */
  static final int SHORTCUT_AMOUNT = 12;
  static final int JUMP_AMOUNT = 4;
  public static final int TOTAL_SPACES = 52;
  public static final int TOTAL_FINAL_SPACES = 6;
  static final int WIN_FINAL_SPACE = 5;
  public static final int PIECES_PER_PLAYER = 4;
  // If the green player is at a space >= 50 (or <= 3) then he can move into his final stretch.
  // Not currently in use for two-player.
  static final int GREEN_MOVE_TO_FINAL = 50;
  
  
  /* Regions on the board */
  static final String H = "H";  // Hangar
  static final String L = "L";  // Launch area
  static final String T = "T";  // Track
  static final String F = "F";  // Final stretch
  
  /* 
   * The location of the planes in the final stretch that are sent back to the hangar
   * if another plane takes a shortcut crossing this path. 
   */
  static final String SHORTCUT_FINAL_INTERSECTION = "F02";
  static final int SHORTCUT_FINAL_SPACE = 2;
  
  /* Players and space colors (2-player version with R|Y) */
  static final String R = "R";  // Red
  static final String Y = "Y";  // Yellow
  
  /* Whether piece is face up or face down (only in Hangar can they be face down) */
  static final String FACEUP = "faceup";
  static final String FACEDOWN = "facedown";
  
  /* 
   * Whether piece is stacked with 1 or more other pieces (only on track/final stretch)
   * For simplicity I assume that a player can choose to either stack all or none on a space. If
   * a third/forth piece lands on the same space, then the player has the choice to stack all
   * or unstack all (including any that were previously stacked). 
   */
  static final String STACKED = "stacked";
  static final String UNSTACKED = "unstacked";
  
  /* Actions the player can take.  One of: initialize, taxi, move, take_shortcut, stack, jump */
  static final String ACTION = "action";
  static final String INITIALIZE = "initialize";
  static final String TAXI = "taxi";
  static final String MOVE = "move";
  static final String STACK = "stack";
  static final String TAKE_SHORTCUT = "take_shortcut";
  static final String JUMP = "jump";
  
  /* Information needed to send pieces moved on last two moves to hangar (if 3 6's are rolled) */
  static final String LAST_TWO_ROLLS = "lastTwoRolls";
  static final String LAST_TWO_MOVES = "lastTwoMoves";
  
  /* Empty representations for lastTwoRolls and lastTwoMoves */
  static final List<Integer> EMPTY_ROLLS = ImmutableList.of(-1, -1);
  static final List<String> EMPTY_MOVES = ImmutableList.of("", "");
  
  /* Rolls for previous two 6's in a row */
  static final List<Integer> DOUBLE_SIX_ROLLS = ImmutableList.of(6, 6);
  
  /* Empty representations for Piece lists */
  static final List<Piece> EMPTY_PIECES = ImmutableList.<Piece>of();
  
  /* Names of the various GameApi operations */
  static final String SET = "Set";
  static final String SET_TURN = "SetTurn";
  
  /* Animation durations */
  public static final int NORMAL_DURATION = 300;
  public static final int SHORTCUT_DURATION = 1000;
}
