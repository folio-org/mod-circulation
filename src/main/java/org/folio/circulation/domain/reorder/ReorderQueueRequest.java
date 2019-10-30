package org.folio.circulation.domain.reorder;

import java.util.ArrayList;
import java.util.List;

public class ReorderQueueRequest {
  private List<ReorderRequest> reorderedQueue = new ArrayList<>();

  public List<ReorderRequest> getReorderedQueue() {
    return reorderedQueue;
  }

  public void setReorderedQueue(List<ReorderRequest> reorderedQueue) {
    this.reorderedQueue = reorderedQueue;
  }
}
