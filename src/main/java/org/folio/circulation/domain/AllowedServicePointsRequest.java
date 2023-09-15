package org.folio.circulation.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@ToString
public class AllowedServicePointsRequest {
  private String requesterId;
  private String instanceId;
  private String itemId;

  public boolean isForTitleLevelRequest() {
    return instanceId != null;
  }

  public boolean isForItemLevelRequest() {
    return itemId != null;
  }
}
