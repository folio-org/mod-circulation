package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;

import java.util.UUID;

public class InstanceBuilder implements Builder {
  private final String title;
  private final UUID id;
  private final JsonArray contributors;

  public InstanceBuilder(String title) {
    id = UUID.randomUUID();
    this.title = title;
    this.contributors = new JsonArray();
  }

  public InstanceBuilder(UUID id, String title) {
    this.id = id;
    this.title = title;
    this.contributors = new JsonArray();
  }

  public InstanceBuilder(UUID id, String title, JsonArray contributors) {
    this.id = id;
    this.title = title;
    this.contributors = contributors;
  }


  @Override
  public JsonObject create() {
    return new JsonObject()
      .put("id", id.toString())
      .put("title", title)
      .put("source", "Local")
      .put("instanceTypeId", APITestSuite.booksInstanceTypeId().toString())
      .put("contributors", contributors);
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

  public InstanceBuilder withContributors(JsonArray contributors) {
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
