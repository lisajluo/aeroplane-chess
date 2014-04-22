package org.aeroplanechess.graphics;

import com.googlecode.mgwt.ui.client.dialog.Dialogs;
import com.googlecode.mgwt.ui.client.dialog.ConfirmDialog.ConfirmCallback;
import com.googlecode.mgwt.ui.client.dialog.Dialogs.AlertCallback;

/**
 * A modal widget showing the user either a question (to which they must select OK or Cancel) or an
 * informational dialog box with an OK button.
 */
public class MessageBox {  
  public MessageBox(String text, final ConfirmCallback callback) {
    Dialogs.confirm(text, "", callback);
  }
  
  public MessageBox(String text, final AlertCallback callback) {
    Dialogs.alert(text, "", callback);
  }
}
