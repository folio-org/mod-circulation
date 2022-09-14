package org.folio.circulation.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;

@NoArgsConstructor
@AllArgsConstructor
@With
@Getter
public class ActualCostRecordItem {
  private String id;
  private String barcode;
  private String materialTypeId;
  private String materialType;
  private String permanentLocationId;
  private String permanentLocation;
  private String loanTypeId;
  private String loanType;
  private String holdingsRecordId;
  private CallNumberComponents effectiveCallNumber;
}
