package org.folio.circulation.domain;

import lombok.Value;

@Value
public class Instance {
  public static Instance unknown() {
    return new Instance(null);
  }

  String title;
}
