package api.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class MoveRequestBuilder extends JsonBuilder implements Builder {

  private final UUID id;
  private final UUID destinationItemId;
  private final String requestType;

  public MoveRequestBuilder(UUID id, UUID destinationItemId, String requestType) {
    this.id = id;
    this.destinationItemId = destinationItemId;
    this.requestType = requestType;
  }

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();
    put(request, "id", this.id);
    put(request, "destinationItemId", this.destinationItemId);
    put(request, "requestType", this.requestType);
    return request;
  }

}
