package org.folio.circulation.domain;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ItemTest {

  @Test
  public void titleFromInstanceTakesPriorityOverItem() {
    final Item item = Item.from(new JsonObject().put("title", "itemTitle"))
      .withInstance(new JsonObject().put("title", "instanceTitle"));

    assertThat(item.getTitle(), is("instanceTitle"));
  }

  @Test
  public void titleFromItemReturnedIfNoInstance() {
    final Item item = Item.from(new JsonObject().put("title", "itemTitle"));

    assertThat(item.getTitle(), is("itemTitle"));
  }

  @Test
  public void identifiersFromInstanceTakesPriorityOverItem() {
    JsonObject itemRepresentation = new JsonObject()
      .put("identifiers", createIdentifiers("itemIdentifier"));
    JsonObject instanceRepresentation = new JsonObject()
      .put("identifiers", createIdentifiers("instanceIdentifier"));

    final Item item = Item.from(itemRepresentation)
      .withInstance(instanceRepresentation);

    assertIdentifier(item.getIdentifiers(), "instanceIdentifier");
  }

  @Test
  public void identifiersFromItemReturnedIfNoInstance() {
    JsonObject itemRepresentation = new JsonObject()
      .put("identifiers", createIdentifiers("itemIdentifier"));

    final Item item = Item.from(itemRepresentation);

    assertIdentifier(item.getIdentifiers(), "itemIdentifier");
  }

  private void assertIdentifier(JsonArray identifiers, String identifierValue) {
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers.getJsonObject(0).getString("value"),
      is(identifierValue));
  }

  private JsonArray createIdentifiers(String identifierValue) {
    return new JsonArray().add(new JsonObject()
      .put("identifierTypeId", UUID.randomUUID().toString())
      .put("value", identifierValue));
  }
}
