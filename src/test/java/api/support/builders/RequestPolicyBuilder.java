package api.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class RequestPolicyBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String name;
  private final String description;
  private final boolean allowHold;
  private final boolean allowPage;
  private final boolean allowRecal;

  public RequestPolicyBuilder() {
    this(UUID.randomUUID(),
      "Example Request Policy",
      "An example request policy",
      false,
      false,
      false
    );
  }

  private RequestPolicyBuilder(
    UUID id,
    String name,
    String description,
    boolean allowHold,
    boolean allowPage,
    boolean allowRecal) {

    this.id = id;
    this.name = name;
    this.description = description;
    this.allowHold = allowHold;
    this.allowPage = allowPage;
    this.allowRecal = allowRecal;
  }

  @Override
  public JsonObject create() {
    JsonObject request = new JsonObject();

    if (id != null) {
      put(request, "id", id.toString());
    }

    put(request, "name", this.name);
    put(request, "description", this.description);
    put(request, "allowHold", this.allowHold);
    put(request, "allowPage", this.allowPage);
    put(request, "allowRecal", this.allowRecal);

    return request;
  }
}