/*
 * Source: https://github.com/yoav-zibin/cheat-game/blob/master/eclipse/src/org/cheat/client/Equality.java
 */

package org.aeroplanechess.client;

import java.util.Objects;

public abstract class Equality {

  public abstract Object getId();

  @Override
  public final boolean equals(Object other) {
    if (!(other instanceof Equality)) {
      return false;
    }
    return Objects.equals(getId(), ((Equality) other).getId());
  }

  @Override
  public final int hashCode() {
    return getId().hashCode();
  }
}