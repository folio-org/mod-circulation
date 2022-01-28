package org.folio.circulation.domain;

import lombok.AllArgsConstructor;
import lombok.With;

@AllArgsConstructor
public class MoveRequestRecord {

  private final String sourceItemId;
  private final String destinationItemId;
  @With
  private String destinationItemInstanceId;
  private final String sourceItemInstanceId;

  public String getDestinationItemInstanceId() {
    return destinationItemInstanceId;
  }

  public String getSourceItemInstanceId() {
    return sourceItemInstanceId;
  }

  public MoveRequestRecord(String sourceItemId, String destinationItemId,
    String sourceItemInstanceId) {
    this.sourceItemId = sourceItemId;
    this.destinationItemId = destinationItemId;
    this.sourceItemInstanceId = sourceItemInstanceId;
  }

  public String getSourceItemId() {
    return sourceItemId;
  }

  public String getDestinationItemId() {
    return destinationItemId;
  }

  public static MoveRequestRecord with(String sourceItemId, String destinationItemId,
    String sourceItemInstanceId) {
    return new MoveRequestRecord(sourceItemId, destinationItemId, sourceItemInstanceId);
  }

}
