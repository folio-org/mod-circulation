package org.folio.circulation.domain;

public class MoveRequestRecord {

  private final String sourceItemId;
  private final String destinationItemId;

  public MoveRequestRecord(String sourceItemId, String destinationItemId) {
    this.sourceItemId = sourceItemId;
    this.destinationItemId = destinationItemId;
  }

  public String getSourceItemId() {
    return sourceItemId;
  }

  public String getDestinationItemId() {
    return destinationItemId;
  }
  
  public static MoveRequestRecord with(String sourceItemId, String destinationItemId) {
    return new MoveRequestRecord(sourceItemId, destinationItemId);
  }

}
