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
    /*final ListBox playerSelect = new ListBox();
      playerSelect.addItem("Red Player");
      playerSelect.addItem("Yellow Player");
      playerSelect.addItem("Viewer");
      playerSelect.addChangeHandler(new ChangeHandler() {
        @Override
        public void onChange(ChangeEvent event) {
          int selectedIndex = playerSelect.getSelectedIndex();
          String playerId = selectedIndex == 2 ? GameApi.VIEWER_ID
              : container.getPlayerIds().get(selectedIndex);
          container.updateUi(playerId);
        }
      });
      playerSelect.setStyleName("marginTop");
      FlowPanel flowPanel = new FlowPanel();
      flowPanel.add(aeroplaneChessGraphics);
      flowPanel.add(playerSelect);
      RootPanel.get("mainDiv").add(flowPanel); */
    RootPanel.get("mainDiv").add(aeroplaneChessGraphics);
    container.sendGameReady();
  }
}