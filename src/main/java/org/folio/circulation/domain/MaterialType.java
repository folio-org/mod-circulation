package org.folio.circulation.domain;

import lombok.Value;

@Value
public class MaterialType {
  public static MaterialType unknown() {
    return new MaterialType(null, null, null);
  }

  String id;
  String name;
  String source;
}
