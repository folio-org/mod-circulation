package api.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import api.APITestSuite;

import java.util.UUID;

public class InstanceBuilder extends JsonBuilder implements Builder {
  private final String title;
  private final UUID id;
  private final JsonArray contributors;

  public InstanceBuilder(String title) {
    id = UUID.randomUUID();
    this.title = title;
    this.contributors = new JsonArray();
  }

  private InstanceBuilder(UUID id, String title) {
    this.id = id;
    this.title = title;
    this.contributors = new JsonArray();
  }

  private InstanceBuilder(UUID id, String title, JsonArray contributors) {
    this.id = id;
    this.title = title;
    this.contributors = contributors;
  }

  @Override
  public JsonObject create() {
    final JsonObject instance = new JsonObject();

    put(instance, "id", id);
    put(instance, "title", title);
    put(instance, "source", "Local");
    put(instance, "instanceTypeId", APITestSuite.booksInstanceTypeId());
    put(instance, "contributors", contributors);

    return instance;
  }

  public InstanceBuilder withId(UUID id) {
    return new InstanceBuilder(
      id,
      this.title
    );
  }

  public InstanceBuilder withTitle(String title) {
    return new InstanceBuilder(
      this.id,
      title
    );
  }

  private InstanceBuilder withContributors(JsonArray contributors) {
    return new InstanceBuilder(
      this.id,
      this.title,
      contributors
    );
  }

  public InstanceBuilder withContributor(String name) {
    JsonArray contributors = new JsonArray();

    contributors.add(new JsonObject()
      .put("name", name)
      .put("contributorNameTypeId",
        APITestSuite.personalContributorNameTypeId().toString()));

    return withContributors(contributors);
  }
}
