package api.requests;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.builders.UserBuilder;
import api.support.http.InterfaceUrls;
import io.vertx.core.json.JsonObject;

public class RequestsAPIDeletionTests extends APITests {

  @Test
  public void canDeleteAllRequests()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID requesterId = usersClient.create(new UserBuilder()).getId();

    UUID firstItemId = itemsFixture.basedUponSmallAngryPlanet().getId();
    UUID secondItemId = itemsFixture.basedUponNod().getId();
    UUID thirdItemId = itemsFixture.basedUponInterestingTimes().getId();

    loansFixture.checkOutItem(firstItemId);
    loansFixture.checkOutItem(secondItemId);
    loansFixture.checkOutItem(thirdItemId);

    requestsClient.create(new RequestBuilder()
      .withItemId(firstItemId)
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(secondItemId)
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(thirdItemId)
      .withRequesterId(requesterId));

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(InterfaceUrls.requestsUrl(),
      ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HTTP_NO_CONTENT));

    CompletableFuture<Response> getAllCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(), ResponseHandler.any(getAllCompleted));

    Response getAllResponse = getAllCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getAllResponse.getStatusCode(), is(HTTP_OK));

    JsonObject allRequests = getAllResponse.getJson();

    List<JsonObject> requests = getRequests(allRequests);

    assertThat(requests.size(), is(0));
    assertThat(allRequests.getInteger("totalRecords"), is(0));
  }

  @Test
  public void canDeleteAnIndividualRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID firstId = UUID.randomUUID();
    UUID secondId = UUID.randomUUID();
    UUID thirdId = UUID.randomUUID();

    UUID requesterId = usersClient.create(new UserBuilder()).getId();

    UUID firstItemId = itemsFixture.basedUponNod().getId();
    UUID secondItemId = itemsFixture.basedUponSmallAngryPlanet().getId();
    UUID thirdItemId = itemsFixture.basedUponTemeraire().getId();

    loansFixture.checkOutItem(firstItemId);
    loansFixture.checkOutItem(secondItemId);
    loansFixture.checkOutItem(thirdItemId);

    requestsClient.create(new RequestBuilder()
      .withId(firstId)
      .withItemId(firstItemId)
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withId(secondId)
      .withItemId(secondItemId)
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withId(thirdId)
      .withItemId(thirdItemId)
      .withRequesterId(requesterId));

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(InterfaceUrls.requestsUrl(String.format("/%s", secondId)),
      ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HTTP_NO_CONTENT));

    assertThat(requestsClient.getById(firstId).getStatusCode(), is(HTTP_OK));

    assertThat(requestsClient.getById(secondId).getStatusCode(), is(HTTP_NOT_FOUND));

    assertThat(requestsClient.getById(thirdId).getStatusCode(), is(HTTP_OK));

    CompletableFuture<Response> getAllCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(), ResponseHandler.any(getAllCompleted));

    Response getAllResponse = getAllCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Get all requests failed: \"%s\"", getAllResponse.getBody()),
      getAllResponse.getStatusCode(), is(HTTP_OK));

    JsonObject allRequests = getAllResponse.getJson();

    List<JsonObject> requests = getRequests(allRequests);

    assertThat(requests.size(), is(2));
    assertThat(allRequests.getInteger("totalRecords"), is(2));
  }

  private List<JsonObject> getRequests(JsonObject page) {
    return JsonArrayHelper.toList(page.getJsonArray("requests"));
  }

}
