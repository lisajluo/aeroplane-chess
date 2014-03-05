/* 
 * Modeled after Prof. Zibin's PopupChoices.java
 * https://github.com/yoav-zibin/cheat-game/blob/master/eclipse/src/org/cheat/graphics/PopupChoices.java
 */
package org.aeroplanechess.graphics;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.HorizontalPanel;

/**
 * A modal widget showing the user either a question (to which they must select Yes or No) or an
 * informational dialog box with an OK button.  The first choice can be selected by pressing enter. 
 * {@link #center} will show the dialog box.
 */
public class MessageBox extends DialogBox {
  public static final String YES = "Yes";
  public static final String NO = "No";
  public static final String OK = "OK";
  
  private Button firstButton;
  
  public interface OptionChosen {
    void optionChosen(String choice);
  }
  
  public MessageBox(String text, boolean isQuestion, final OptionChosen optionChosen) {
    super(false, true);  // Modal
    setText(text);
    setAnimationEnabled(true);
    HorizontalPanel buttons = new HorizontalPanel();
    
    firstButton = createButton(isQuestion ? YES : OK, optionChosen);
    buttons.add(firstButton);
    
    if (isQuestion) {
      Button no = createButton(NO, optionChosen);
      buttons.add(no);
    }
    
    setWidget(buttons);
  }

  @Override
  public void center() {
    super.center();
    firstButton.setFocus(true);
  }
  
  /**
   * Creates a button that sends the string of its option to the View implementing MessageBox.
   * @param option
   * @param optionChosen
   * @return
   */
  private Button createButton(final String option, final OptionChosen optionChosen) {
    Button button = new Button(option);
    button.addClickHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        hide();
        optionChosen.optionChosen(option);
      }
    });
    
    return button;
  }
}
