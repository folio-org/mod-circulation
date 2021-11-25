package org.folio.circulation.domain;

import lombok.Value;

@Value
public class MaterialType {
  public static MaterialType unknown() {
    return new MaterialType(null);
  }

  String name;
}
