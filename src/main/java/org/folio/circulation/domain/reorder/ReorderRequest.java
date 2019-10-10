package org.folio.circulation.domain.reorder;

public class ReorderRequest {
  private String id;
  private Integer newPosition;

  public String getId() {
    return id;
  }

  public Integer getNewPosition() {
    return newPosition;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setNewPosition(Integer newPosition) {
    this.newPosition = newPosition;
  }
}
