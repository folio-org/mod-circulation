package org.folio.circulation.domain;

import lombok.Value;

@Value
public class Campus {
  public static Campus unknown() {
    return unknown(null);
  }

  public static Campus unknown(String id) {
    return new Campus(id, null, null);
  }

  String id;
  String name;
  String code;
}
