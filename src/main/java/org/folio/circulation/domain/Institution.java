package org.folio.circulation.domain;

import lombok.Value;

@Value
public class Institution {
  public static Institution unknown() {
    return unknown(null);
  }

  public static Institution unknown(String id) {
    return new Institution(id, null, null);
  }

  String id;
  String name;
  String code;
}
