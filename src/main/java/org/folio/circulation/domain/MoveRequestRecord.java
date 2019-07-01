package org.folio.circulation.domain;

public class MoveRequestRecord {

  private final String originalItemId;
  private final String destinationItemId;

  public MoveRequestRecord(String originalItemId, String destinationItemId) {
    this.originalItemId = originalItemId;
    this.destinationItemId = destinationItemId;
  }

  public String getOriginalItemId() {
    return originalItemId;
  }

  public String getDestinationItemId() {
    return destinationItemId;
  }
  
  public static MoveRequestRecord with(String originalItemId, String destinationItemId) {
    return new MoveRequestRecord(originalItemId, destinationItemId);
  }

}
