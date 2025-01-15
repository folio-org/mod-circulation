package api.requests;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

class SearchSlipsTests extends APITests {
  private static final String TOTAL_RECORDS_KEY = "totalRecords";
  private static final String SEARCH_SLIPS_KEY = "searchSlips";

  @Test
  void responseShouldHaveEmptyListOfSearchSlipsRecords() {
    configurationsFixture.configurePrintHoldRequests(true);
    Response response = ResourceClient.forSearchSlips().getById(UUID.randomUUID());
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 0);
  }

  @Test
  void responseShouldHaveNoSearchSlipsRecords() {
    configurationsFixture.configurePrintHoldRequests(false);
    Response response = ResourceClient.forSearchSlips().getById(UUID.randomUUID());
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertResponseHasItems(response, 0);
  }

  private void assertResponseHasItems(Response response, int itemsCount) {
    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getJsonArray(SEARCH_SLIPS_KEY).size(), is(itemsCount));
    assertThat(responseJson.getInteger(TOTAL_RECORDS_KEY), is(itemsCount));
  }
}
