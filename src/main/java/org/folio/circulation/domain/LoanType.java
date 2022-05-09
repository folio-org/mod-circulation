package org.folio.circulation.domain;

import lombok.Value;

@Value
public class LoanType {
  public static LoanType unknown() {
    return unknown(null);
  }
  public static LoanType unknown(String id) {
    return new LoanType(id, null);
  }

  String id;
  String name;
}
