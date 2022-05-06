package org.folio.circulation.domain;

import lombok.Value;

@Value
public class MaterialType {
  public static MaterialType unknown() {
    return unknown(null);
  }

  public static MaterialType unknown(String id) {
    return new MaterialType(id, null, null);
  }

  String id;
  String name;
  String source;
}
