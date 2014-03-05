// Copyright 2012 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// //////////////////////////////////////////////////////////////////////////////
package org.aeroplanechess.graphics;

import com.google.gwt.resources.client.ImageResource;

public class AeroplaneChessImageSupplier {
  private final AeroplaneChessImages aeroplaneChessImages;

  public AeroplaneChessImageSupplier(AeroplaneChessImages aeroplaneChessImages) {
    this.aeroplaneChessImages = aeroplaneChessImages;
  }

  public ImageResource getDie(DieImage dieImage) {
    switch (dieImage.value) {
      case 0:
        return aeroplaneChessImages.roll();
      case 1:
        return aeroplaneChessImages.one();
      case 2:
        return aeroplaneChessImages.two();
      case 3:
        return aeroplaneChessImages.three();
      case 4:
        return aeroplaneChessImages.four();
      case 5:
        return aeroplaneChessImages.five();
      case 6:
        return aeroplaneChessImages.six();
      default:
        throw new RuntimeException("No such value=" + dieImage.value);
    }
  }
  
  public ImageResource getPiece(PieceImage pieceImage) {
    switch (pieceImage.piece.getColor()) {
      case R:
        return pieceImage.piece.isFaceDown() ? aeroplaneChessImages.r_facedown()
            : pieceImage.highlighted ? aeroplaneChessImages.r_highlight() 
            : aeroplaneChessImages.r();
      case Y:
        return pieceImage.piece.isFaceDown() ? aeroplaneChessImages.y_facedown()
            : pieceImage.highlighted ? aeroplaneChessImages.y_highlight() 
            : aeroplaneChessImages.y();
      case B:
        return pieceImage.piece.isFaceDown() ? aeroplaneChessImages.b_facedown()
            : pieceImage.highlighted ? aeroplaneChessImages.b_highlight() 
            : aeroplaneChessImages.b();
      case G:
        return pieceImage.piece.isFaceDown() ? aeroplaneChessImages.g_facedown()
            : pieceImage.highlighted ? aeroplaneChessImages.g_highlight() 
            : aeroplaneChessImages.g();
      default:
        throw new RuntimeException("No such color=" + pieceImage.piece.getColor().name());
    }
  }
  
  public ImageResource getBoard() {
    return aeroplaneChessImages.board();
  }
}
