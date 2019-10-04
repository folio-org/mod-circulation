package org.folio.circulation.domain.reorder;

public class ReorderRequest {
  private String id;
  private Integer position;

  public String getId() {
    return id;
  }

  public Integer getPosition() {
    return position;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setPosition(Integer position) {
    this.position = position;
  }
}
