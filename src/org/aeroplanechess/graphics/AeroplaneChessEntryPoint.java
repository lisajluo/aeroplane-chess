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
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.RootPanel;
import com.googlecode.mgwt.dom.client.event.orientation.OrientationChangeEvent;
import com.googlecode.mgwt.dom.client.event.orientation.OrientationChangeEvent.ORIENTATION;
import com.googlecode.mgwt.dom.client.event.orientation.OrientationChangeHandler;
import com.googlecode.mgwt.dom.client.event.tap.TapEvent;
import com.googlecode.mgwt.dom.client.event.tap.TapHandler;
import com.googlecode.mgwt.ui.client.MGWT;
import com.googlecode.mgwt.ui.client.MGWTSettings;
import com.googlecode.mgwt.ui.client.MGWTSettings.ViewPort;
import com.googlecode.mgwt.ui.client.MGWTSettings.ViewPort.DENSITY;
import com.googlecode.mgwt.ui.client.MGWTStyle;
import com.googlecode.mgwt.ui.client.widget.Button;
import com.googlecode.mgwt.ui.client.widget.LayoutPanel;
import com.googlecode.mgwt.ui.client.theme.base.ButtonCss;
import com.googlecode.mgwt.ui.client.dialog.Dialogs.AlertCallback;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class AeroplaneChessEntryPoint implements EntryPoint {
  //ContainerConnector container;
  // Keeping old code in for debug purposes
  IteratingPlayerContainer container;
  AeroplaneChessPresenter aeroplaneChessPresenter;

  @Override
  public void onModuleLoad() {
    // Set viewport and other settings for mobile
    MGWT.applySettings(MGWTSettings.getAppSetting());
    
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
    //container = new ContainerConnector(game);
    container = new IteratingPlayerContainer(game, 2);
    AeroplaneChessGraphics aeroplaneChessGraphics = new AeroplaneChessGraphics();
    aeroplaneChessPresenter = new AeroplaneChessPresenter(aeroplaneChessGraphics, container);

    LayoutPanel buttonHolder = new LayoutPanel();
    buttonHolder.addStyleName("marginTop");
    
    final ButtonCss buttonCss = MGWTStyle.getTheme().getMGWTClientBundle().getButtonCss();
    final Button redPlayer = new Button("Red player");
    final Button yellowPlayer = new Button("Yellow player");
    final Button viewer = new Button("Viewer"); 
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
    RootPanel.get("mainDiv").add(flowPanel); 
    
    //RootPanel.get("mainDiv").add(aeroplaneChessGraphics);
    container.sendGameReady();
    container.updateUi(container.getPlayerIds().get(0));
    redPlayer.addStyleName(buttonCss.active());
  }
}