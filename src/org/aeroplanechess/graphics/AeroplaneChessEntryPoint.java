/* 
 * Modeled after Prof. Zibin's CheatEntryPoint.java
 * https://github.com/yoav-zibin/cheat-game/blob/master/eclipse/src/org/cheat/graphics/CheatEntryPoint.java
 */

package org.aeroplanechess.graphics;

import org.aeroplanechess.client.AeroplaneChessLogic;
import org.aeroplanechess.client.AeroplaneChessPresenter;
import org.game_api.GameApi;
import org.game_api.GameApi.Game;
import org.game_api.GameApi.IteratingPlayerContainer;
import org.game_api.GameApi.ContainerConnector;
import org.game_api.GameApi.UpdateUI;
import org.game_api.GameApi.VerifyMove;
import org.aeroplanechess.graphics.AeroplaneChessGraphics;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.user.client.Window;
import com.google.gwt.event.logical.shared.ResizeEvent;
import com.google.gwt.event.logical.shared.ResizeHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.googlecode.mgwt.dom.client.event.orientation.OrientationChangeEvent;
import com.googlecode.mgwt.dom.client.event.orientation.OrientationChangeHandler;
import com.googlecode.mgwt.dom.client.event.tap.TapEvent;
import com.googlecode.mgwt.dom.client.event.tap.TapHandler;
import com.googlecode.mgwt.ui.client.MGWT;
import com.googlecode.mgwt.ui.client.MGWTSettings;
import com.googlecode.mgwt.ui.client.MGWTStyle;
import com.googlecode.mgwt.ui.client.widget.Button;
import com.googlecode.mgwt.ui.client.widget.LayoutPanel;
import com.googlecode.mgwt.ui.client.theme.base.ButtonCss;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class AeroplaneChessEntryPoint implements EntryPoint {
  ContainerConnector container;
  // Keeping old code in for debug purposes
  //IteratingPlayerContainer container;
  AeroplaneChessPresenter aeroplaneChessPresenter;

  @Override
  public void onModuleLoad() {
    // Set viewport and other settings for mobile
    MGWT.applySettings(MGWTSettings.getAppSetting());
    Window.enableScrolling(false);
    MGWT.addOrientationChangeHandler(new OrientationChangeHandler() {
      @Override
      public void onOrientationChanged(OrientationChangeEvent event) {
        scaleGame();
      }
    });
    Window.addResizeHandler(new ResizeHandler() {
      @Override
      public void onResize(ResizeEvent event) {
        scaleGame();
      }
    });
    
    Game game = new Game() {
      @Override
      public void sendVerifyMove(VerifyMove verifyMove) {
        container.sendVerifyMoveDone(new AeroplaneChessLogic().verify(verifyMove));
      }
      @Override
      public void sendUpdateUI(UpdateUI updateUI) {
        aeroplaneChessPresenter.updateUI(updateUI);
      }
    };
    container = new ContainerConnector(game);
    //container = new IteratingPlayerContainer(game, 2);
    AeroplaneChessGraphics aeroplaneChessGraphics = new AeroplaneChessGraphics();
    aeroplaneChessPresenter = new AeroplaneChessPresenter(aeroplaneChessGraphics, container);

    /*LayoutPanel buttonHolder = new LayoutPanel();
    buttonHolder.setHorizontal(true);
    final ButtonCss buttonCss = MGWTStyle.getTheme().getMGWTClientBundle().getButtonCss();
    I18nMessages i18n = (I18nMessages) GWT.create(I18nMessages.class);
    final Button redPlayer = new Button(i18n.redPlayer());
    final Button yellowPlayer = new Button(i18n.yellowPlayer());
    final Button viewer = new Button(i18n.viewer()); 
    redPlayer.setSmall(true);
    yellowPlayer.setSmall(true);
    viewer.setSmall(true);
    
    redPlayer.addTapHandler(new TapHandler() {
      @Override
      public void onTap(TapEvent event) {
        container.updateUi(container.getPlayerIds().get(0));
        redPlayer.addStyleName(buttonCss.active());
        yellowPlayer.removeStyleName(buttonCss.active());
        viewer.removeStyleName(buttonCss.active());
      }                    
    });
    
    yellowPlayer.addTapHandler(new TapHandler() {
      @Override
      public void onTap(TapEvent event) {
        container.updateUi(container.getPlayerIds().get(1));
        yellowPlayer.addStyleName(buttonCss.active());
        redPlayer.removeStyleName(buttonCss.active());
        viewer.removeStyleName(buttonCss.active());
      }                    
    });
    
    viewer.addTapHandler(new TapHandler() {
      @Override
      public void onTap(TapEvent event) {
        container.updateUi(GameApi.VIEWER_ID);
        viewer.addStyleName(buttonCss.active());
        redPlayer.removeStyleName(buttonCss.active());
        yellowPlayer.removeStyleName(buttonCss.active());
      }                    
    }); 
    
    buttonHolder.add(redPlayer);
    buttonHolder.add(yellowPlayer);
    buttonHolder.add(viewer);
  

    FlowPanel flowPanel = new FlowPanel();
    flowPanel.add(aeroplaneChessGraphics);
    flowPanel.add(buttonHolder);
    RootPanel.get("mainDiv").add(flowPanel); */
    
    RootPanel.get("mainDiv").add(aeroplaneChessGraphics);
    container.sendGameReady();
   /* container.updateUi(container.getPlayerIds().get(0));
    redPlayer.addStyleName(buttonCss.active());*/
    
    scaleGame();
  }
  
  /**
   * Dynamically gets window height and width to scale board coordinates properly.
   */
  private void scaleGame() {
    double scaleX = (double) Window.getClientWidth() / Board.GAME_WIDTH;
    double scaleY = (double) Window.getClientHeight() / Board.GAME_HEIGHT;
    double scale = Math.min(scaleX, scaleY);
    Board.setScale(scale);
  }
}