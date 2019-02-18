package api.support.builders;

import java.util.UUID;

import org.folio.circulation.domain.RequestType;

import io.vertx.core.json.JsonObject;

public class RequestPolicyBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String name;
  private final String description;
  private final RequestType type;

  public RequestPolicyBuilder() {
    this(UUID.randomUUID(),
      "Example Request Policy",
      "An example request policy",
      RequestType.HOLD
    );
  }

  private RequestPolicyBuilder(
    UUID id,
    String name,
    String description,
    RequestType type) {

    this.id = id;
    this.name = name;
    this.description = description;
    this.type = type;
  }

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    if (id != null) {
      put(request, "id", id.toString());
    }

    put(request, "name", this.name);
    put(request, "description", this.description);

    JsonObject requestType = new JsonObject();
    put(requestType, "type", this.type.name());

    put(request, "requestType", requestType);

    return request;
  }
}