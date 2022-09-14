package org.folio.circulation.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;

@NoArgsConstructor
@AllArgsConstructor
@With
@Getter
public class ActualCostRecordUser {
  private String id;
  private String barcode;
  private String firstName;
  private String lastName;
  private String middleName;
}
