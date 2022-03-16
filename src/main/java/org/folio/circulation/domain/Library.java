package org.folio.circulation.domain;

import lombok.Value;

@Value
public class Library {
  public static Library unknown(String id) {
    return new Library(id, null);
  }

  String id;
  String name;
}
