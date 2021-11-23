package org.folio.circulation.domain;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class Holdings {
  static Holdings unknown() {
    return new Holdings(null, null, null);
  }

  public final String instanceId;
  public final String copyNumber;
  public final String permanentLocationId;
}
