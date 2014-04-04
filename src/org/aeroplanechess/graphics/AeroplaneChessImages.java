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

import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.ImageResource;

public interface AeroplaneChessImages extends ClientBundle {
  /* Pieces ----------*/
  @Source("images/pieces/r.png")
  ImageResource r();
  
  @Source("images/pieces/y.png")
  ImageResource y();
  
  @Source("images/pieces/b.png")
  ImageResource b();
  
  @Source("images/pieces/g.png")
  ImageResource g();
  
  @Source("images/pieces/r_highlight.png")
  ImageResource r_highlight();
  
  @Source("images/pieces/y_highlight.png")
  ImageResource y_highlight();
  
  @Source("images/pieces/b_highlight.png")
  ImageResource b_highlight();
  
  @Source("images/pieces/g_highlight.png")
  ImageResource g_highlight();
  
  @Source("images/pieces/r_facedown.png")
  ImageResource r_facedown();
  
  @Source("images/pieces/y_facedown.png")
  ImageResource y_facedown();
  
  @Source("images/pieces/b_facedown.png")
  ImageResource b_facedown();
  
  @Source("images/pieces/g_facedown.png")
  ImageResource g_facedown();
  
  @Source("images/pieces/empty.png")
  ImageResource empty();
  
  @Source("images/pieces/drop_highlight.png")
  ImageResource drop();
  
  /* Die ----------*/
  @Source("images/die/1.png")
  ImageResource one();
  
  @Source("images/die/2.png")
  ImageResource two();
  
  @Source("images/die/3.png")
  ImageResource three();
  
  @Source("images/die/4.png")
  ImageResource four();
  
  @Source("images/die/5.png")
  ImageResource five();
  
  @Source("images/die/6.png")
  ImageResource six();
  
  @Source("images/die/0.png")
  ImageResource roll();
  
  /* Board ----------*/
  @Source("images/board.png")
  ImageResource board();
}