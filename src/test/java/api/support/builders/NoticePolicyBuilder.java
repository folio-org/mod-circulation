package api.support.builders;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.vertx.core.json.JsonObject;

public class NoticePolicyBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String name;
  private final String description;
  private final boolean active;
  private final List<JsonObject> loanNotices;
  private final List<JsonObject> requestNotices;

  public NoticePolicyBuilder() {
    this(UUID.randomUUID(),
      "Example Notice Policy",
      "An example notice policy",
      false,
      new ArrayList<>(),
      new ArrayList<>()
    );

  }

  private NoticePolicyBuilder(
    UUID id,
    String name,
    String description,
    boolean active,
    List<JsonObject> loanNotices,
    List<JsonObject> requestNotices) {

    this.id = id;
    this.name = name;
    this.description = description;
    this.active = active;
    this.loanNotices = loanNotices;
    this.requestNotices = requestNotices;
  }

  public NoticePolicyBuilder active() {
    return new NoticePolicyBuilder(
      this.id,
      this.name,
      this.description,
      true,
      this.loanNotices,
      this.requestNotices);
  }

  public NoticePolicyBuilder withName(String name) {
    return new NoticePolicyBuilder(
      this.id,
      name,
      this.description,
      this.active,
      this.loanNotices,
      this.requestNotices);
  }

  public NoticePolicyBuilder withId(UUID id) {
    return new NoticePolicyBuilder(
      id,
      this.name,
      this.description,
      this.active,
      this.loanNotices,
      this.requestNotices);
  }

  public NoticePolicyBuilder withLoanNotices(List<JsonObject> loanNotices) {
    return new NoticePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.active,
      loanNotices,
      this.requestNotices);
  }

  public NoticePolicyBuilder withRequestNotices(List<JsonObject> requestNotices) {
    return new NoticePolicyBuilder(
      this.id,
      this.name,
      this.description,
      this.active,
      this.loanNotices,
      requestNotices);
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
    put(request, "loanNotices", loanNotices);
    put(request, "requestNotices", requestNotices);

    return request;
  }
}
