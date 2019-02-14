package api.support.builders;

import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class NoticePolicyBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String name;
  private final String description;
  private final boolean active;

  public NoticePolicyBuilder() {
    this(UUID.randomUUID(),
      "Example Notice Policy",
      "An example notice policy",
      false
    );
  }

  private NoticePolicyBuilder(
      UUID id,
      String name,
      String description,
      boolean active) {

      this.id = id;
      this.name = name;
      this.description = description;
      this.active = active;
    }

    @Override
    public JsonObject create() {
      JsonObject request = new JsonObject();

      if (id != null) {
        put(request, "id", id.toString());
      }

      put(request, "name", this.name);
      put(request, "description", this.description);
      put(request, "active", this.active);

      return request;
    }

    public NoticePolicyBuilder active() {
      return new NoticePolicyBuilder(
        this.id,
        this.name,
        this.description,
        true);
    }
}