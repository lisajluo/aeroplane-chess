/* 
 * Modeled after Prof. Zibin's CheatGraphics.java
 * https://github.com/yoav-zibin/cheat-game/blob/master/eclipse/src/org/cheat/graphics/CheatGraphics.java
 * Animation modeled after https://code.google.com/p/nyu-gaming-course-2013/source/browse/trunk/
 * eclipse/src/org/simongellis/hw3/Graphics.java
 */
package org.aeroplanechess.graphics;

import java.util.List;
import java.util.Iterator;

import org.aeroplanechess.client.AeroplaneChessPresenter;
import org.aeroplanechess.client.Color;
import org.aeroplanechess.client.AeroplaneChessPresenter.AeroplaneChessMessage;
import org.aeroplanechess.client.AeroplaneChessState.Action;
import org.aeroplanechess.client.Piece;
import org.aeroplanechess.client.Piece.Zone;

import static org.aeroplanechess.client.Constants.NORMAL_DURATION;
import static org.aeroplanechess.client.Constants.SHORTCUT_DURATION;
import static org.aeroplanechess.client.Constants.PIECES_PER_PLAYER;

import org.aeroplanechess.graphics.Board.Point;
import org.aeroplanechess.sounds.AeroplaneChessSounds;
import com.google.gwt.user.client.Timer;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.gwt.dom.client.AudioElement;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.allen_sauer.gwt.dnd.client.DragContext;
import com.allen_sauer.gwt.dnd.client.DragEndEvent;
import com.allen_sauer.gwt.dnd.client.DragHandlerAdapter;
import com.allen_sauer.gwt.dnd.client.DragStartEvent;
import com.allen_sauer.gwt.dnd.client.PickupDragController;
import com.allen_sauer.gwt.dnd.client.drop.SimpleDropController;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.media.client.Audio;
import com.google.gwt.resources.client.ImageResource;

/**
 * Graphics for the Aeroplane Chess game (board, piece placement, die).
 */
public class AeroplaneChessGraphics extends Composite implements AeroplaneChessPresenter.View {
  
  public interface AeroplaneChessGraphicsUiBinder extends UiBinder<Widget, AeroplaneChessGraphics> {
  }

  @UiField
  AbsolutePanel boardArea;
  @UiField
  FlowPanel dieArea;
  private AeroplaneChessPresenter presenter;
  private final AeroplaneChessImageSupplier imageSupplier;
  boolean showAnimation = false;
  private PieceMovingAnimation animation;
  private static final String LIGHT_SHADOW = "lightShadow";
  private PickupDragController dragController;
  Image targetImage = null;
  SimplePanel target = null;
  private final AeroplaneChessSounds aeroplaneChessSounds;
  private Audio pieceMoved;
  private Audio pieceJumped;
  private Audio backToHangar;
  private Audio forwardToHangar;
  private Audio takeShortcut;
  private Audio dieRoll;
  private Audio winGame;
  private Audio loseGame;
  
  /** True to enable clicks on the board (on many moves the player just chooses an option in
   * a popup selector).
   */
  private boolean enableDieClick = false;
  private boolean enablePieceClick = false;
  private List<Piece> myOldPieces;
  private List<Piece> opponentOldPieces;
  
  public AeroplaneChessGraphics() {
    AeroplaneChessImages aeroplaneChessImages = GWT.create(AeroplaneChessImages.class);
    this.imageSupplier = new AeroplaneChessImageSupplier(aeroplaneChessImages);
    this.aeroplaneChessSounds = GWT.create(AeroplaneChessSounds.class);
    initializeAudio();
    AeroplaneChessGraphicsUiBinder uiBinder = GWT.create(AeroplaneChessGraphicsUiBinder.class);
    initWidget(uiBinder.createAndBindUi(this));
    initializeDragAndDrop();
  }
  
  @Override
  public void setPresenter(AeroplaneChessPresenter aeroplaneChessPresenter) {
    this.presenter = aeroplaneChessPresenter;
  }

  @Override
  public void setViewerState(List<Piece> redPieces, List<Piece> yellowPieces,
      int die, AeroplaneChessMessage aeroplaneChessMessage, Action lastAction) {
    boardArea.clear();
    Image board = new Image(imageSupplier.getBoard());
    boardArea.add(board);
    putPieces(redPieces);
    putPieces(yellowPieces);
    putDie(die, false);  // Never enable clicking on the die for the viewer
  }

  @Override
  public void setPlayerState(final List<Piece> myPieces, final List<Piece> opponentPieces, 
      final int die, final AeroplaneChessMessage message, Action lastAction) {
    
    if (myOldPieces == null || !showAnimation) { // Initialize board on first updateUI
      putBoard(myPieces, opponentPieces, message, die);
      checkGameOver(message);
    }
    else { // Animate on subsequent moves
      boolean multipleAnimations = animateMove(myPieces, opponentPieces, die, message, lastAction);
      
      Timer animationTimer = new Timer() { 
        public void run() {
          putBoard(myPieces, opponentPieces, message, die);
          checkGameOver(message);
        }
      }; 
      
      int firstDuration = lastAction == Action.TAKE_SHORTCUT ? SHORTCUT_DURATION : NORMAL_DURATION;
      int secondDuration = multipleAnimations ? NORMAL_DURATION : 0;
      animationTimer.schedule(firstDuration + secondDuration);
    }
    
    myOldPieces = myPieces;
    opponentOldPieces = opponentPieces;
    showAnimation = true;
  }
  
  /**
   * Shows a message and plays appropriate audio if the game has ended.
   */
  public void checkGameOver(AeroplaneChessMessage message) {
    if (message == AeroplaneChessMessage.WON_GAME) { 
      playAudio(winGame);
      new MessageBox("You won!", false, new MessageBox.OptionChosen() {
        @Override
        public void optionChosen(String option) {
          // Do nothing
        }
      }).center();
    }
    else if (message == AeroplaneChessMessage.LOST_GAME) { 
      playAudio(loseGame);
      new MessageBox("You lost!", false, new MessageBox.OptionChosen() {
        @Override
        public void optionChosen(String option) {
          // Do nothing
        }
      }).center();
    }
  }
  
  /**
   * Logs debug info to Chrome Developer Tools console.
   */
  public static native void console(String text)
  /*-{
      console.log(text);
  }-*/;
  
  /**
   * Put the pieces and show the die or a dialog box depending on the message received.
   */
  public void putBoard (List<Piece> myPieces, List<Piece> opponentPieces, 
      AeroplaneChessMessage message, int die) {
    removeDropHandlers();
    boardArea.clear();
    Image board = new Image(imageSupplier.getBoard());
    boardArea.add(board);
    putPieces(myPieces);
    putPieces(opponentPieces);
    
    /* Only show the die change if it's your turn (when the play becomes synchronous then we
     * can show the opponent's die as well. For now it appears strange to show the die after your
     * turn has ended.
     */
    if (message == AeroplaneChessMessage.ROLL_AVAILABLE) {
      putDie(die, true);
    }
    
    switch (message) {
      case STACK_AVAILABLE:
        showStackChoice();
        break;
      case SHORTCUT_AVAILABLE:
        showShortcutChoice();
        break;
      case JUMP_AVAILABLE:
        showJump();
        break;
      default: // Do nothing for OTHER_TURN. ROLL_AVAILABLE is handled by die onClick.
        break;
    }
  }

  @Override
  public void choosePieces(List<Piece> possiblePieces, boolean backToHangar) {
    if (backToHangar) {
      showBackToHangar();
    }
    else if (possiblePieces.isEmpty()) {
      showEmptyMove();
    }
    else {
      /* 
       * A list of pieces that are stacked.  We only need to add a handler for a stack of pieces to
       * one of those pieces.
       */
      List<String> stackedLocations = Lists.newArrayList();
      for (final Piece piece : possiblePieces) {
        String location = piece.getLocation();
        if (!stackedLocations.contains(location)) {
          Point coord = Board.getCoordinates(piece.getColor(), piece.getZone(), piece.getSpace());
          // We can get the Image of the piece at this location using the TOP and LEFT coordinates. 
          final Image image = (Image) getWidgetAtLocation(coord);
          image.setResource(imageSupplier.getPiece(PieceImage.Factory.getPiece(piece, true)));
          // Click functionality
          image.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
              if (enablePieceClick) {
                presenter.piecesSelected(Optional.<Piece>of(piece));
                enablePieceClick = false;
              }
            }
          });
          
          // Drag and drop functionality
          dragController.makeDraggable(image);
        }
        // Prevent the addition of any other handlers at this location. If it's the launch or 
        // stacked pieces we can just arbitrarily pick the first one we see.
        if (piece.isStacked() || piece.getZone() == Zone.LAUNCH) {  
          stackedLocations.add(location);
        }
      }
      enablePieceClick = true;
    }
  }
  
  /**
   * Adds drop handlers for every possible destination.
   */
  private void putDropHandlers(final Piece piece) {
    removeDropHandlers();
    Piece dropPiece = presenter.getDestination(piece);

    Point destination = Board.getCoordinates(
        dropPiece.getColor(), dropPiece.getZone(), dropPiece.getSpace());
    targetImage = new Image(imageSupplier.getDropHighlight());
    targetImage.getElement().addClassName(LIGHT_SHADOW);
    target = new SimplePanel(targetImage);
    boardArea.add(target, destination.getX(), destination.getY());     
    SimpleDropController dropController = new SimpleDropController(target) {
      @Override
      public void onDrop(DragContext context) {
        super.onDrop(context);
        showAnimation = false;
        presenter.piecesSelected(Optional.<Piece>of(piece));
      }
      
      @Override
      public void onEnter(DragContext context) {
        super.onEnter(context);
        targetImage.getElement().removeClassName(LIGHT_SHADOW);
      }
      
      @Override
      public void onLeave(DragContext context) {
        targetImage.getElement().addClassName(LIGHT_SHADOW);
        super.onLeave(context);
      }
    };
    dragController.registerDropController(dropController);
  }
  
  /**
   * Removes all drop handlers.
   */
  private void removeDropHandlers() {
    if (target != null) {
      target.clear();
      boardArea.remove(target);
    }
    dragController.unregisterDropControllers();
  }
  
  /**
   * Returns the widget in the board area with the TOP/LEFT coordinates specified.
   */
  private Widget getWidgetAtLocation(Point coordinates) throws IllegalArgumentException {
    Iterator<Widget> widgets = boardArea.iterator();
    while (widgets.hasNext()) {
      Widget widget = widgets.next();
      if (boardArea.getWidgetLeft(widget) == coordinates.getX() 
          && boardArea.getWidgetTop(widget) == coordinates.getY()) {
        return widget;
      }
    }
    throw new IllegalArgumentException("Did not find Widget=" + coordinates.toString());
  }
  
  /**
   * Put the pieces in their absolute top/left location on the board.
   */
  private void putPieces(List<Piece> pieces) {
    // A list of pieces that are stacked.  We only need to add a piece image to that location once.
    List<String> stackedLocations = Lists.newArrayList();
    for (Piece piece : pieces) {
      if (!stackedLocations.contains(piece.getLocation())) {
        Point location = Board.getCoordinates(piece.getColor(), piece.getZone(), piece.getSpace());
        Image image = new Image(imageSupplier.getPiece(PieceImage.Factory.getPiece(piece, false)));
        boardArea.add(image, location.getX(), location.getY());
      }
      // We treat launch as "stacked" since all pieces there go in the same location
      if (piece.isStacked() || piece.getZone() == Zone.LAUNCH) {
        stackedLocations.add(piece.getLocation());
      }
    }
  }
  
  /** 
   * Puts the image of the die given its rolled value - for the viewer/other player.
   * For the current player, the image is highlighted and clickable.  When the player clicks
   * on the highlighted die, it will be replaced with the image corresponding to the rolled
   * value, and the player will be allowed to select pieces to move (if any).
   */
  private void putDie(final int die, boolean clickable) {
    dieArea.clear();
    final Image image;
    if (clickable) {
      enableDieClick = true;
      image = new Image(imageSupplier.getDie(DieImage.Factory.getDie(0)));
      image.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          if (enableDieClick) {
            playAudio(dieRoll);
            image.setResource(imageSupplier.getDie(DieImage.Factory.getDie(die)));
            enableDieClick = false;
            new MessageBox("You rolled a " + die + "!", false, 
                new MessageBox.OptionChosen() {
                    @Override
                    public void optionChosen(String option) {
                      presenter.dieRolled();
                    }
                  }).center();
          }
        }
      });
    }
    else {
      image = new Image(imageSupplier.getDie(DieImage.Factory.getDie(die)));
    }
    
    dieArea.add(image);
  }
  
  /**
   * Pops up a dialog box asking the player to stack all the pieces (or none).
   */
  private void showStackChoice() {
    new MessageBox("Stack all pieces?", true, new MessageBox.OptionChosen() {
      @Override
      public void optionChosen(String option) {
        presenter.stackSelected(option.equals(MessageBox.YES));
      }
    }).center();
  }
  
  /**
   * Pops up a dialog box asking whether the player wants to take the shortcut or not.
   */
  private void showShortcutChoice() {
    new MessageBox("Take shortcut?", true, new MessageBox.OptionChosen() {
      @Override
      public void optionChosen(String option) {
        presenter.shortcutSelected(option.equals(MessageBox.YES));
      }
    }).center();
  }
  
  /**
   * Pops up a dialog box telling the player that no moves were possible.
   */
  private void showEmptyMove() {
    new MessageBox("No moves available.", false, new MessageBox.OptionChosen() {
      @Override
      public void optionChosen(String option) {
        presenter.piecesSelected(Optional.<Piece>absent());
      }
    }).center();
  }
  
  /**
   * Pops up a dialog box telling the player that their pieces must be sent back to the Hangar.
   */
  private void showBackToHangar() {
    new MessageBox(
        "Rolled three 6's! Last three moves go back to the Hangar.", 
        false, 
        new MessageBox.OptionChosen() {
            @Override
            public void optionChosen(String option) {
              presenter.piecesSelected(Optional.<Piece>absent());
            }
          }).center();
    }
  
  /**
   * Pops up a dialog box telling the player that a jump occurred.
   */
  private void showJump() {
    new MessageBox("Automatic jump!", false, new MessageBox.OptionChosen() {
      @Override
      public void optionChosen(String option) {
        presenter.showJump();
      }
    }).center();
  }
  
  /**
   * Initialize dragging on the board.
   */
  private void initializeDragAndDrop() {
    dragController = new PickupDragController(boardArea, false);
    dragController.setBehaviorDragStartSensitivity(3);
    dragController.setBehaviorMultipleSelection(false);
    dragController.setBehaviorConstrainedToBoundaryPanel(true);
    dragController.addDragHandler(new DragHandlerAdapter() {
      @Override
      public void onDragStart(DragStartEvent event) {
        Image draggedImage = (Image) event.getContext().draggable;
        int x = boardArea.getWidgetLeft(draggedImage);
        int y = boardArea.getWidgetTop(draggedImage);
        
        for (Piece piece : myOldPieces) {
          Point location = Board.getCoordinates(piece.getColor(), piece.getZone(), piece.getSpace());
          if (location.getX() == x && location.getY() == y) {
            if (multipleInLocation(myOldPieces, piece)) {
              Point startCoord = Board.getCoordinates(
                  piece.getColor(), piece.getZone(), piece.getSpace());
              Image oldImage = new Image(
                  imageSupplier.getPiece(PieceImage.Factory.getPiece(piece, false)));
              boardArea.add(oldImage, startCoord.getX(), startCoord.getY());
            }
            putDropHandlers(piece);
            break;
          }
        }
      }
      
      @Override
      public void onDragEnd(DragEndEvent event) {
        removeDropHandlers();
      }
    });
  }
  
  /**
   * Initializes audio resources (if supported).
   */
  private void initializeAudio() {
    if (Audio.isSupported()) {
      pieceMoved = Audio.createIfSupported();
      pieceMoved.setControls(false);
      if (pieceMoved.canPlayType(AudioElement.TYPE_WAV).equals(AudioElement.CAN_PLAY_PROBABLY) ||
          pieceMoved.canPlayType(AudioElement.TYPE_WAV).equals(AudioElement.CAN_PLAY_MAYBE)) {
        pieceMoved.addSource(
            aeroplaneChessSounds.pieceMovedWav().getSafeUri().asString(), AudioElement.TYPE_WAV);
      }
      if (pieceMoved.canPlayType(AudioElement.TYPE_MP3).equals(AudioElement.CAN_PLAY_PROBABLY) ||
          pieceMoved.canPlayType(AudioElement.TYPE_MP3).equals(AudioElement.CAN_PLAY_MAYBE)) {
        pieceMoved.addSource(
            aeroplaneChessSounds.pieceMovedMp3().getSafeUri().asString(), AudioElement.TYPE_MP3);
      }
      
      pieceJumped = Audio.createIfSupported();
      pieceJumped.setControls(false);
      if (pieceJumped.canPlayType(AudioElement.TYPE_WAV).equals(AudioElement.CAN_PLAY_PROBABLY) ||
          pieceJumped.canPlayType(AudioElement.TYPE_WAV).equals(AudioElement.CAN_PLAY_MAYBE)) {
        pieceJumped.addSource(
            aeroplaneChessSounds.pieceJumpedWav().getSafeUri().asString(), AudioElement.TYPE_WAV);
      }
      if (pieceJumped.canPlayType(AudioElement.TYPE_MP3).equals(AudioElement.CAN_PLAY_PROBABLY) ||
          pieceJumped.canPlayType(AudioElement.TYPE_MP3).equals(AudioElement.CAN_PLAY_MAYBE)) {
        pieceJumped.addSource(
            aeroplaneChessSounds.pieceJumpedMp3().getSafeUri().asString(), AudioElement.TYPE_MP3);
      }
      
      backToHangar = Audio.createIfSupported();
      backToHangar.setControls(false);
      if (backToHangar.canPlayType(AudioElement.TYPE_WAV).equals(AudioElement.CAN_PLAY_PROBABLY) ||
          backToHangar.canPlayType(AudioElement.TYPE_WAV).equals(AudioElement.CAN_PLAY_MAYBE)) {
        backToHangar.addSource(
            aeroplaneChessSounds.backToHangarWav().getSafeUri().asString(), AudioElement.TYPE_WAV);
      }
      if (backToHangar.canPlayType(AudioElement.TYPE_MP3).equals(AudioElement.CAN_PLAY_PROBABLY) ||
          backToHangar.canPlayType(AudioElement.TYPE_MP3).equals(AudioElement.CAN_PLAY_MAYBE)) {
        backToHangar.addSource(
            aeroplaneChessSounds.backToHangarMp3().getSafeUri().asString(), AudioElement.TYPE_MP3);
      }
      
      forwardToHangar = Audio.createIfSupported();
      forwardToHangar.setControls(false);
      if (forwardToHangar.canPlayType(AudioElement.TYPE_WAV).equals(AudioElement.CAN_PLAY_PROBABLY) ||
          forwardToHangar.canPlayType(AudioElement.TYPE_WAV).equals(AudioElement.CAN_PLAY_MAYBE)) {
        forwardToHangar.addSource(
            aeroplaneChessSounds.forwardToHangarWav().getSafeUri().asString(), AudioElement.TYPE_WAV);
      }
      if (forwardToHangar.canPlayType(AudioElement.TYPE_MP3).equals(AudioElement.CAN_PLAY_PROBABLY) ||
          forwardToHangar.canPlayType(AudioElement.TYPE_MP3).equals(AudioElement.CAN_PLAY_MAYBE)) {
        forwardToHangar.addSource(
            aeroplaneChessSounds.forwardToHangarMp3().getSafeUri().asString(), AudioElement.TYPE_MP3);
      }
      
      takeShortcut = Audio.createIfSupported();
      takeShortcut.setControls(false);
      if (takeShortcut.canPlayType(AudioElement.TYPE_WAV).equals(AudioElement.CAN_PLAY_PROBABLY) ||
          takeShortcut.canPlayType(AudioElement.TYPE_WAV).equals(AudioElement.CAN_PLAY_MAYBE)) {
        takeShortcut.addSource(
            aeroplaneChessSounds.takeShortcutWav().getSafeUri().asString(), AudioElement.TYPE_WAV);
      }
      if (takeShortcut.canPlayType(AudioElement.TYPE_MP3).equals(AudioElement.CAN_PLAY_PROBABLY) ||
          takeShortcut.canPlayType(AudioElement.TYPE_MP3).equals(AudioElement.CAN_PLAY_MAYBE)) {
        takeShortcut.addSource(
            aeroplaneChessSounds.takeShortcutMp3().getSafeUri().asString(), AudioElement.TYPE_MP3);
      }
      
      dieRoll = Audio.createIfSupported();
      dieRoll.setControls(false);
      if (dieRoll.canPlayType(AudioElement.TYPE_WAV).equals(AudioElement.CAN_PLAY_PROBABLY) ||
          dieRoll.canPlayType(AudioElement.TYPE_WAV).equals(AudioElement.CAN_PLAY_MAYBE)) {
        dieRoll.addSource(
            aeroplaneChessSounds.dieRollWav().getSafeUri().asString(), AudioElement.TYPE_WAV);
      }
      if (dieRoll.canPlayType(AudioElement.TYPE_MP3).equals(AudioElement.CAN_PLAY_PROBABLY) ||
          dieRoll.canPlayType(AudioElement.TYPE_MP3).equals(AudioElement.CAN_PLAY_MAYBE)) {
        dieRoll.addSource(
            aeroplaneChessSounds.dieRollMp3().getSafeUri().asString(), AudioElement.TYPE_MP3);
      }
      
      winGame = Audio.createIfSupported();
      winGame.setControls(false);
      if (winGame.canPlayType(AudioElement.TYPE_WAV).equals(AudioElement.CAN_PLAY_PROBABLY) ||
          winGame.canPlayType(AudioElement.TYPE_WAV).equals(AudioElement.CAN_PLAY_MAYBE)) {
        winGame.addSource(
            aeroplaneChessSounds.winGameWav().getSafeUri().asString(), AudioElement.TYPE_WAV);
      }
      if (winGame.canPlayType(AudioElement.TYPE_MP3).equals(AudioElement.CAN_PLAY_PROBABLY) ||
          winGame.canPlayType(AudioElement.TYPE_MP3).equals(AudioElement.CAN_PLAY_MAYBE)) {
        winGame.addSource(
            aeroplaneChessSounds.winGameMp3().getSafeUri().asString(), AudioElement.TYPE_MP3);
      }
      
      loseGame = Audio.createIfSupported();
      loseGame.setControls(false);
      if (loseGame.canPlayType(AudioElement.TYPE_WAV).equals(AudioElement.CAN_PLAY_PROBABLY) ||
          loseGame.canPlayType(AudioElement.TYPE_WAV).equals(AudioElement.CAN_PLAY_MAYBE)) {
        loseGame.addSource(
            aeroplaneChessSounds.loseGameWav().getSafeUri().asString(), AudioElement.TYPE_WAV);
      }
      if (loseGame.canPlayType(AudioElement.TYPE_MP3).equals(AudioElement.CAN_PLAY_PROBABLY) ||
          loseGame.canPlayType(AudioElement.TYPE_MP3).equals(AudioElement.CAN_PLAY_MAYBE)) {
        loseGame.addSource(
            aeroplaneChessSounds.loseGameMp3().getSafeUri().asString(), AudioElement.TYPE_MP3);
      }
    }
  }
  
  /**
   * Plays the audio sound effect if available.
   */
  private void playAudio(Audio audio) {
    if (audio != null) {
      audio.play();
    }
  }
  
  /**
   * Returns an Audio for the given Action taken.
   */
  private Audio getAudio(Action action) {
    switch (action) {
      case TAXI:
        return pieceMoved;
      case MOVE:
        return pieceMoved;
      case JUMP:
        return pieceJumped;
      case TAKE_SHORTCUT:
        return takeShortcut;
      default: // INITIALIZE, STACK
        return null;
    }
  }
  
  /**
   * Returns true if there are multiple pieces in the same location (in launch or stacked).
   */
  private boolean multipleInLocation(List<Piece> pieces, Piece comparePiece) {
    for (Piece piece : pieces) {
      if (piece.getLocation().equals(comparePiece.getLocation()) 
          && piece.getPieceId() != comparePiece.getPieceId()
          && !piece.isStacked()) {
        return true;
      }
    }
    return false;
  }
  
  /**
   * Animate pieces moved by comparing the old pieces (from previous updateUI) to the new pieces
   * (from current updateUI).  Returns true if there needs to be two animations (ie., my move
   * followed by sending opponent pieces back to the hangar).
   */
  private boolean animateMove(final List<Piece> myPieces, final List<Piece> opponentPieces, 
      final int die, final AeroplaneChessMessage message, Action lastAction) {
    
    boolean multipleAnimations = false;
    boolean shortcutTaken = false;
    boolean playedMySound = false;
    boolean playedOpponentSound = false;
    List<String> myStackedLocations = Lists.newArrayList();
    List<String> opponentStackedLocations = Lists.newArrayList();
    
    for (int i = 0; i < PIECES_PER_PLAYER; i ++) {
      final Piece myStartPiece = myOldPieces.get(i);
      final Piece myEndPiece = myPieces.get(i);
      final Piece opponentStartPiece = opponentOldPieces.get(i);
      final Piece opponentEndPiece = opponentPieces.get(i);
      shortcutTaken = lastAction == Action.TAKE_SHORTCUT;
      
      // Animate my pieces that were moved (only the first stacked piece)
      String myStartLocation = myStartPiece.getLocation();
      if (!myStackedLocations.contains(myStartLocation)
          && !myStartLocation.equals(myEndPiece.getLocation())) {
        if (myEndPiece.getZone() == Zone.HANGAR) {
          if (myEndPiece.isFaceDown()) { // Sent one piece forward to the Hangar
            animatePiece(
                myStartPiece, 
                myEndPiece, 
                NORMAL_DURATION,
                playedMySound ? null : forwardToHangar, // Play sound only for the first animation
                false,
                multipleInLocation(myPieces, myStartPiece));
            playedMySound = true;
          }
          else {
            if (die == 6) { // Piece was sent back to the Hangar after rolling three 6's
              animatePiece(
                  myStartPiece, 
                  myEndPiece, 
                  NORMAL_DURATION, 
                  playedMySound ? null : backToHangar, 
                  false,
                  multipleInLocation(myPieces, myStartPiece));
              playedMySound = true;
            }
            else { // Piece was sent back to the Hangar on opponent move: animate that piece last
              final boolean finalPlayedMySound = playedMySound;
              Timer myBackToHangarTimer = new Timer() {
                public void run() { 
                  animatePiece(
                      myStartPiece, 
                      myEndPiece, 
                      NORMAL_DURATION, 
                      finalPlayedMySound ? null : backToHangar, 
                      false,
                      multipleInLocation(myPieces, myStartPiece));
                }
              }; 
              myBackToHangarTimer.schedule(shortcutTaken ? SHORTCUT_DURATION : NORMAL_DURATION);
              playedMySound = true;
              multipleAnimations = true;
            }
          }
        }
        else { // Animate as usual (my pieces moved on my turn)
          animatePiece(
              myStartPiece, 
              myEndPiece, 
              shortcutTaken ? SHORTCUT_DURATION : NORMAL_DURATION,
              playedMySound ? null : getAudio(lastAction), 
              shortcutTaken,
              multipleInLocation(myPieces, myStartPiece));
        }
        // If it's the launch or stacked pieces we can just animate the first one we see.
        if ((myStartPiece.isStacked() && myEndPiece.getZone() != Zone.HANGAR) 
            || myStartPiece.getZone() == Zone.LAUNCH) {  
          myStackedLocations.add(myStartLocation);
        }
      }
      
      // Animate opponent pieces that were moved (only the first stacked piece)
      String opponentStartLocation = opponentStartPiece.getLocation();
      if (!opponentStackedLocations.contains(opponentStartLocation)
          && !opponentStartLocation.equals(opponentEndPiece.getLocation())) {
        if (opponentEndPiece.getZone() == Zone.HANGAR) {
          if (opponentEndPiece.isFaceDown()) { // Opponent Sent one piece forward to the Hangar
            animatePiece(
                opponentStartPiece, 
                opponentEndPiece, 
                NORMAL_DURATION,
                playedOpponentSound ? null : forwardToHangar, 
                false,
                multipleInLocation(opponentPieces, opponentStartPiece));
            playedOpponentSound = true;
          }
          else { // Piece was sent back to the Hangar on my move: animate that piece last
            final boolean finalPlayedOpponentSound = playedOpponentSound;
            Timer opponentBackToHangarTimer = new Timer() {
              public void run() { 
                animatePiece(
                    opponentStartPiece, 
                    opponentEndPiece, 
                    NORMAL_DURATION, 
                    finalPlayedOpponentSound ? null : backToHangar, 
                    false,
                    multipleInLocation(opponentPieces, opponentStartPiece));
              }
            }; 
            opponentBackToHangarTimer.schedule(shortcutTaken ? SHORTCUT_DURATION : NORMAL_DURATION);
            playedOpponentSound = true;
            multipleAnimations = true;
          }
        }
        else { // Animate as usual (opponent pieces moved on opponent turn)
          animatePiece(
              opponentStartPiece, 
              opponentEndPiece, 
              shortcutTaken ? SHORTCUT_DURATION : NORMAL_DURATION,
              playedOpponentSound ? null : getAudio(lastAction), 
              shortcutTaken,
              multipleInLocation(opponentPieces, opponentStartPiece));
          playedOpponentSound = true;
        }
        // If it's the launch or stacked pieces we can just animate the first one we see.
        if ((opponentStartPiece.isStacked() && opponentEndPiece.getZone() != Zone.HANGAR)  
            || opponentStartPiece.getZone() == Zone.LAUNCH) {  
          opponentStackedLocations.add(opponentStartLocation);
        }
      }
    }
    
    return multipleAnimations;
  }
  
  private void animatePiece(Piece start, Piece end, int duration, Audio audio, boolean playAtStart,
      boolean keepOldImage) {
    Point startCoord = Board.getCoordinates(start.getColor(), start.getZone(), start.getSpace());
    Image startImage = (Image) getWidgetAtLocation(startCoord); 
    ImageResource startResource = imageSupplier.getPiece(PieceImage.Factory.getPiece(start, false));
    
    Image endImage = new Image(imageSupplier.getEmptyPiece());
    Point endLocation = Board.getCoordinates(end.getColor(), end.getZone(), end.getSpace());
    boardArea.add(endImage, endLocation.getX(), endLocation.getY());
    ImageResource endResource = imageSupplier.getPiece(PieceImage.Factory.getPiece(end, false));
    
    if (keepOldImage) {
      Image oldImage = new Image(imageSupplier.getPiece(PieceImage.Factory.getPiece(start, false)));
      boardArea.add(oldImage, startCoord.getX(), startCoord.getY());
    }
    
    animation = new PieceMovingAnimation(startImage, endImage, startResource, endResource, 
        imageSupplier.getEmptyPiece(), audio, boardArea, playAtStart);
    animation.run(duration);
  }
}
