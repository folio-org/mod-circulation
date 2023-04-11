package org.folio.circulation.domain;

import lombok.Value;

@Value
public class Departments {

  public static Departments unknown() {
    return unknown(null);
  }

  public static Departments unknown(String id) {
    return new Departments(id, null);
  }

  String id;
  String name;

}
