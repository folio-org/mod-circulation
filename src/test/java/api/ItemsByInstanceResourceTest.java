package api;

import static api.support.APITestContext.clearTempTenantId;
import static api.support.APITestContext.setTempTenantId;
import static api.support.http.InterfaceUrls.itemsByInstanceUrl;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.folio.HttpStatus.HTTP_OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.hamcrest.core.Is.is;

import java.util.List;
import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.SearchInstanceBuilder;
import api.support.http.IndividualResource;
import api.support.http.ResourceClient;
import api.support.matchers.UUIDMatcher;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class ItemsByInstanceResourceTest extends APITests {

  private static final String TENANT_ID_COLLEGE = "college";
  private static final String TENANT_ID_UNIVERSITY = "university";

  @Test
  void canGetInstanceById() {
    IndividualResource instance = instancesFixture.basedUponDunkirk();
    UUID instanceId = instance.getId();

    // create item in tenant "college"
    setTempTenantId(TENANT_ID_COLLEGE);
    IndividualResource collegeLocation = locationsFixture.mainFloor();
    IndividualResource collegeHoldings = holdingsFixture.defaultWithHoldings(instanceId);
    IndividualResource collegeItem = itemsFixture.createItemWithHoldingsAndLocation(
      collegeHoldings.getId(), collegeLocation.getId());
    clearTempTenantId();

    // create item in tenant "university"
    setTempTenantId(TENANT_ID_UNIVERSITY);
    IndividualResource universityLocation = locationsFixture.thirdFloor();
    IndividualResource universityHoldings = holdingsFixture.defaultWithHoldings(instanceId);
    IndividualResource universityItem = itemsFixture.createItemWithHoldingsAndLocation(
      universityHoldings.getId(), universityLocation.getId());
    clearTempTenantId();

    // make sure neither item exists in current tenant
    assertThat(itemsFixture.getById(collegeItem.getId()).getResponse().getStatusCode(), is(404));
    assertThat(itemsFixture.getById(universityItem.getId()).getResponse().getStatusCode(), is(404));

    List<JsonObject> searchItems = List.of(
      collegeItem.getJson().put("tenantId", TENANT_ID_COLLEGE),
      universityItem.getJson().put("tenantId", TENANT_ID_UNIVERSITY));

    JsonObject searchInstance = new SearchInstanceBuilder(instance.getJson())
      .withItems(searchItems)
      .create();

    ResourceClient.forSearchClient().create(searchInstance);
    Response response = get(String.format("query=(id==%s)", instanceId), 200);
    JsonObject responseJson = response.getJson();
    JsonArray items = responseJson.getJsonArray("items");

    assertThat(responseJson.getString("id"), UUIDMatcher.is(instanceId));
    assertThat(items, iterableWithSize(2));
    assertThat(items, hasItem(allOf(
      hasJsonPath("id", UUIDMatcher.is(collegeItem.getId())),
      hasJsonPath("tenantId", is(TENANT_ID_COLLEGE)))));
    assertThat(items, hasItem(allOf(
      hasJsonPath("id", UUIDMatcher.is(universityItem.getId())),
      hasJsonPath("tenantId", is(TENANT_ID_UNIVERSITY)))));
  }

  @Test
  void canGetEmptyResult() {
    UUID instanceId = UUID.randomUUID();

    ResourceClient.forSearchClient().replace(instanceId, new JsonObject());
    Response response = get(String.format("query=(id==%s)", instanceId), HTTP_OK.toInt());
    JsonObject responseJson = response.getJson();

    assertThat(responseJson.isEmpty(), is(true));
  }

  private Response get(String query, int expectedStatusCode) {
    return restAssuredClient.get(itemsByInstanceUrl(query), expectedStatusCode,
      "items-by-instance-request");
  }
}
