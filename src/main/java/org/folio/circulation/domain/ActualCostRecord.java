package org.folio.circulation.domain;

import java.util.Collection;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.With;

@NoArgsConstructor
@AllArgsConstructor
@With
@Getter
public class ActualCostRecord {
  private String id;
  private String userId;
  private String userBarcode;
  private String loanId;
  private ItemLossType lossType;
  private String dateOfLoss;
  private String title;
  private Collection<Identifier> identifiers;
  private String itemBarcode;
  private String loanType;
  private String effectiveCallNumber;
  private String permanentItemLocation;
  private String feeFineOwnerId;
  private String feeFineOwner;
  private String feeFineTypeId;
  private String feeFineType;
}
