package api.support.builders;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.util.UUID;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class InstanceBuilder extends JsonBuilder implements Builder {
  private final UUID id;
  private final String title;
  private final UUID instanceTypeId;
  private final JsonArray contributors;

  public InstanceBuilder(String title, UUID instanceTypeId) {
    this(UUID.randomUUID(), title, instanceTypeId);
  }

  private InstanceBuilder(UUID id, String title, UUID instanceTypeId) {
    this(id, title, new JsonArray(), instanceTypeId);
  }

  private InstanceBuilder(
    UUID id,
    String title,
    JsonArray contributors,
    UUID instanceTypeId) {

    this.id = id;
    this.title = title;
    this.contributors = contributors;
    this.instanceTypeId = instanceTypeId;
  }

  @Override
  public JsonObject create() {
    final JsonObject instance = new JsonObject();

    put(instance, "id", id);
    put(instance, "title", title);
    put(instance, "source", "Local");
    put(instance, "instanceTypeId", instanceTypeId);
    put(instance, "contributors", contributors);

    return instance;
  }

  public InstanceBuilder withId(UUID id) {
    return new InstanceBuilder(
      id,
      this.title,
      this.contributors,
      this.instanceTypeId);
  }

  public Builder withInstanceTypeId(UUID instanceTypeId) {
    return new InstanceBuilder(
      this.id,
      this.title,
      this.contributors,
      instanceTypeId);
  }

  public InstanceBuilder withContributor(String name, UUID typeId) {
    final JsonObject newContributor = new JsonObject();

    write(newContributor, "name", name);
    write(newContributor, "contributorNameTypeId", typeId);

    return withContributors(this.contributors.copy().add(newContributor));
  }

  private InstanceBuilder withContributors(JsonArray contributors) {
    return new InstanceBuilder(
      this.id,
      this.title,
      contributors,
      this.instanceTypeId);
  }
}
