package api.support.builders;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class NoticePolicyBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String name;
  private final String description;
  private final boolean active;
  private final List<JsonObject> loanNotices;

  public NoticePolicyBuilder() {
    this(UUID.randomUUID(),
      "Example Notice Policy",
      "An example notice policy",
      false,
      new ArrayList<>()
    );

  }

  private NoticePolicyBuilder(
    UUID id,
    String name,
    String description,
    boolean active,
    List<JsonObject> loanNotices) {

    this.id = id;
    this.name = name;
    this.description = description;
    this.active = active;
    this.loanNotices = loanNotices;
  }

  public NoticePolicyBuilder active() {
    return new NoticePolicyBuilder(
      this.id,
      this.name,
      this.description,
      true,
      this.loanNotices);
  }

  public NoticePolicyBuilder withName(String name) {
    return new NoticePolicyBuilder(
      this.id,
      name,
      this.description,
      this.active,
      this.loanNotices);
  }

  public NoticePolicyBuilder withLoanNotices(List<JsonObject> loanNotices) {
    return new NoticePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.active,
      loanNotices);
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
    put(request, "loanNotices", new JsonArray(loanNotices));

    return request;
  }
}
