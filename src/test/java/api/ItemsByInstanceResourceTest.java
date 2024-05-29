package api;

import static api.support.http.InterfaceUrls.itemsByInstanceUrl;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.http.ItemResource;

class ItemsByInstanceResourceTest extends APITests {
  @Test
  void canGetInstanceById() {
    UUID instanceId = UUID.randomUUID();
    ItemResource itemResource = itemsFixture.basedUponDunkirk();
    searchFixture.basedUponDunkirk(instanceId, itemResource);
    Response response =
      get(String.format("query=(id==%s)", instanceId), 200);
    assertThat(response.getStatusCode(), is(200));
    assertThat(response.getJson().getString("id"), is(instanceId.toString()));
    assertThat(response.getJson().getJsonArray("items").size(), is(1));
    assertThat(response.getJson().getJsonArray("items").getJsonObject(0).getString("id"),
      is(itemResource.getId().toString()));
  }

  private Response get(String query, int expectedStatusCode) {
    return restAssuredClient.get(itemsByInstanceUrl(query), expectedStatusCode,
      "items-by-instance-request");
  }
}
