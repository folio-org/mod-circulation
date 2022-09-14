package org.folio.circulation.domain;

import java.time.ZonedDateTime;
import java.util.Collection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;

@NoArgsConstructor
@AllArgsConstructor
@With
@Getter
public class ActualCostRecord {
  private String id;
  private ItemLossType lossType;
  private ZonedDateTime lossDate;
  private ZonedDateTime expirationDate;
  private ActualCostRecordUser user;
  private ActualCostRecordLoan loan;
  private ActualCostRecordItem item;
  private ActualCostRecordInstance instance;
  private ActualCostRecordFeeFine feeFine;
  private ZonedDateTime creationDate;
}
