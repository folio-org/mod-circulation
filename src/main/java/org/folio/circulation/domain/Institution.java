package org.folio.circulation.domain;

import lombok.Value;

@Value
public class Institution {
  public static Institution unknown(String id) {
    return new Institution(id, null);
  }

  String id;
  String name;
}
