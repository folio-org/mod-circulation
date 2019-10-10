package api.support.builders;

import org.folio.circulation.domain.reorder.ReorderRequest;
import org.folio.circulation.domain.reorder.ReorderQueueRequest;

import io.vertx.core.json.JsonObject;

public class ReorderQueueBuilder implements Builder {
  private final ReorderQueueRequest reorderQueueRequest = new ReorderQueueRequest();

  @Override
  public JsonObject create() {
    return JsonObject.mapFrom(reorderQueueRequest);
  }

  public ReorderQueueBuilder addReorderRequest(String requestId, int newPosition) {
    ReorderRequest reorderRequest = new ReorderRequest();
    reorderRequest.setId(requestId);
    reorderRequest.setNewPosition(newPosition);

    reorderQueueRequest.getReorderedQueue().add(reorderRequest);
    return this;
  }
}
