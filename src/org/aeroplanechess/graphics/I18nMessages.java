package org.aeroplanechess.graphics;

import com.google.gwt.i18n.client.Messages;

public interface I18nMessages extends Messages {
  @DefaultMessage("You won!")
  String won();
  
  @DefaultMessage("You lost!")
  String lost();
  
  @DefaultMessage("You rolled a {0}!")
  String rolled(int die);
  
  @DefaultMessage("Stack all pieces?")
  String stack();
  
  @DefaultMessage("Take shortcut?")
  String shortcut();
  
  @DefaultMessage("No moves available.")
  String noMoves();
  
  @DefaultMessage("Rolled three 6''s! Last three moves go back to the Hangar.")
  String backToHangar();
  
  @DefaultMessage("Automatic jump!")
  String jump();
  
  @DefaultMessage("Red player")
  String redPlayer();
  
  @DefaultMessage("Yellow player")
  String yellowPlayer();
  
  @DefaultMessage("Viewer")
  String viewer();
  
  @DefaultMessage("OK")
  String ok();
  
  @DefaultMessage("Yes")
  String yes();
  
  @DefaultMessage("No")
  String no();
}
