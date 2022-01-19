package org.folio.circulation.domain;

import lombok.Value;

@Value
public class Holdings {
  public static Holdings unknown() {
    return new Holdings(null, null, null, null);
  }

  String id;
  String instanceId;
  String copyNumber;
  String permanentLocationId;
}
