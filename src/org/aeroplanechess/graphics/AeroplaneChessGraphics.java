package org.aeroplanechess.graphics;

import java.util.List;
import java.util.Iterator;

import org.aeroplanechess.client.AeroplaneChessPresenter;
import org.aeroplanechess.client.AeroplaneChessPresenter.AeroplaneChessMessage;
import org.aeroplanechess.client.Piece;
import org.aeroplanechess.client.Piece.Zone;

import org.aeroplanechess.graphics.Board.Point;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.core.shared.GWT;

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
  /** True to enable clicks on the board (on many moves the player just chooses an option in
   * a popup selector).
   */
  private boolean enableDieClick = false;
  private boolean enablePieceClick = false;
  
  public AeroplaneChessGraphics() {
    AeroplaneChessImages aeroplaneChessImages = GWT.create(AeroplaneChessImages.class);
    this.imageSupplier = new AeroplaneChessImageSupplier(aeroplaneChessImages);
    AeroplaneChessGraphicsUiBinder uiBinder = GWT.create(AeroplaneChessGraphicsUiBinder.class);
    initWidget(uiBinder.createAndBindUi(this));
  }
  
  @Override
  public void setPresenter(AeroplaneChessPresenter aeroplaneChessPresenter) {
    this.presenter = aeroplaneChessPresenter;
  }

  @Override
  public void setViewerState(List<Piece> redPieces, List<Piece> yellowPieces,
      int die, AeroplaneChessMessage aeroplaneChessMessage) {
    boardArea.clear();
    Image board = new Image(imageSupplier.getBoard());
    boardArea.add(board);
    putPieces(redPieces);
    putPieces(yellowPieces);
    putDie(die, false);  // Never enable clicking on the die for the viewer
  }

  @Override
  public void setPlayerState(List<Piece> myPieces, List<Piece> opponentPieces,
      int die, AeroplaneChessMessage aeroplaneChessMessage) {
    boardArea.clear();
    Image board = new Image(imageSupplier.getBoard());
    boardArea.add(board);
    putPieces(myPieces);
    putPieces(opponentPieces);
    /* Only show the die change if it's your turn (when the play becomes synchronous then we
     * can show the opponent's die as well. For now it appears strange to show the die after your
     * turn has ended.
     */
    if (aeroplaneChessMessage == AeroplaneChessMessage.ROLL_AVAILABLE) {
      putDie(die, true);
    }
    
    switch (aeroplaneChessMessage) {
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
          image.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
              if (enablePieceClick) {
                presenter.piecesSelected(Optional.<Piece>of(piece));
                enablePieceClick = false;
              }
            }
          });
        }
        // Prevent the addition of any other handlers at this location. If it's the launch or 
        // unstacked pieces we can just arbitrarily pick the first one we see.
        if (piece.isStacked() || piece.getZone() == Zone.LAUNCH) {  
          stackedLocations.add(location);
        }
      }
      enablePieceClick = true;
    }
  }
  
  /**
   * Returns the widget in the board area with the TOP/LEFT coordinates specified.
   */
  private Widget getWidgetAtLocation(Point coordinates) {
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
}
