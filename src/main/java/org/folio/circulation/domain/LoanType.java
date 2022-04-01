package org.folio.circulation.domain;

import lombok.Value;

@Value
public class LoanType {
  public static LoanType unknown() {
    return new LoanType(null, null);
  }

  String id;
  String name;
}
