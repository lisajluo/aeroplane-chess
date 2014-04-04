/*
 * The below sounds are taken from freesound:
 * 
 * From Kastenfrosch: http://www.freesound.org/people/Kastenfrosch/
 * pieceJumped.wav/pieceJumped.mp3 are modified from gotItem.mp3
 * facedownToHangar.wav/facedownToHangar.mp3 are from successful.mp3
 * rollSixToHangar.wav/rollSixToHangar.mp3 are from lostItem.mp3
 * winGame.wav/winGame.mp3 are from gewonnen.mp3
 * loseGame.wav/loseGame.mp3 are from Verloren.mp3
 * 
 * From Laers: http://www.freesound.org/people/Laers/
 * takeShortcut.wav/takeShortcut.mp3 are modified from propplaneflyover.wav
 * 
 * From jcbatz: http://www.freesound.org/people/jcbatz/
 * dieRoll.wav/dieRoll.mp3 are modified from clatter1.aif
 */

package org.aeroplanechess.sounds;

import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.DataResource;

public interface AeroplaneChessSounds extends ClientBundle {
  @Source("files/pieceMoved.wav")
  DataResource pieceMovedWav();
  
  @Source("files/pieceMoved.mp3")
  DataResource pieceMovedMp3();
  
  @Source("files/pieceJumped.wav")
  DataResource pieceJumpedWav();
  
  @Source("files/pieceJumped.mp3")
  DataResource pieceJumpedMp3();
  
  @Source("files/backToHangar.wav")
  DataResource backToHangarWav();
  
  @Source("files/backToHangar.mp3")
  DataResource backToHangarMp3();
  
  @Source("files/forwardToHangar.wav")
  DataResource forwardToHangarWav();
  
  @Source("files/forwardToHangar.mp3")
  DataResource forwardToHangarMp3();
  
  @Source("files/takeShortcut.wav")
  DataResource takeShortcutWav();
  
  @Source("files/takeShortcut.mp3")
  DataResource takeShortcutMp3();
  
  @Source("files/dieRoll.wav")
  DataResource dieRollWav();
  
  @Source("files/dieRoll.mp3")
  DataResource dieRollMp3();
  
  @Source("files/winGame.wav")
  DataResource winGameWav();
  
  @Source("files/winGame.mp3")
  DataResource winGameMp3();
  
  @Source("files/loseGame.wav")
  DataResource loseGameWav();
  
  @Source("files/loseGame.mp3")
  DataResource loseGameMp3();
}