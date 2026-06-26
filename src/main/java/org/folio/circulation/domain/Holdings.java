package org.folio.circulation.domain;

import lombok.Value;

@Value
public class Holdings {
  public static Holdings unknown(String id) {
    return new Holdings(id, null, null, null, null);
  }

  public static Holdings unknown() {
    return unknown(null);
  }

  String id;
  String instanceId;
  String copyNumber;
  String permanentLocationId;
  String effectiveLocationId;
}
