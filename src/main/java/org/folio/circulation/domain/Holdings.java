package org.folio.circulation.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class Holdings {
  public static Holdings unknown() {
    return new Holdings(null, null, null);
  }

  @Getter private final String instanceId;
  @Getter private final String copyNumber;
  @Getter private final String permanentLocationId;
}
