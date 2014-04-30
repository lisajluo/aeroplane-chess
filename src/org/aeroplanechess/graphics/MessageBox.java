package org.aeroplanechess.graphics;

import com.googlecode.mgwt.dom.client.event.tap.TapEvent;
import com.googlecode.mgwt.dom.client.event.tap.TapHandler;
import com.googlecode.mgwt.ui.client.MGWTStyle;
import com.googlecode.mgwt.ui.client.dialog.AlertDialog;
import com.googlecode.mgwt.ui.client.dialog.ConfirmDialog;
import com.googlecode.mgwt.ui.client.dialog.ConfirmDialog.ConfirmCallback;
import com.googlecode.mgwt.ui.client.dialog.Dialogs.AlertCallback;

/**
 * A modal widget showing the user either a question (to which they must select OK or Cancel) or an
 * informational dialog box with an OK button.
 */
public class MessageBox {  
  public MessageBox(String text, String yesText, String noText, final ConfirmCallback callback) {
    ConfirmDialog confirmDialog = new ConfirmDialog(
        MGWTStyle.getTheme().getMGWTClientBundle().getDialogCss(), text, "", callback, yesText, noText);
    confirmDialog.show();
  }
  
  public MessageBox(String text, String okText, final AlertCallback callback) {
    AlertDialog alertDialog = new AlertDialog(
        MGWTStyle.getTheme().getMGWTClientBundle().getDialogCss(), text, "", okText);

    alertDialog.addTapHandler(new TapHandler() {
      @Override
      public void onTap(TapEvent event) {
        if (callback != null) {
          callback.onButtonPressed();
        }

      }
    });

    alertDialog.show();
  }
}
