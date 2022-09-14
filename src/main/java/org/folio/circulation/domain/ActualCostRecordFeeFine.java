package org.folio.circulation.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;

@NoArgsConstructor
@AllArgsConstructor
@With
@Getter
public class ActualCostRecordFeeFine {
  private String accountId;
  private String ownerId;
  private String owner;
  private String typeId;
  private String type;
}
