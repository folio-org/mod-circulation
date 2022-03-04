package org.folio.circulation.infrastructure.storage;

import java.util.HashMap;
import java.util.Map;

public class IdentityMap<T> {
  private final Map<String, T> identityMapHash = new HashMap<>();

  public Map<String, T> getIdentityMap() {
    return identityMapHash;
  }

}
