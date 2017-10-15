package org.folio.circulation.api.requests;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.APITestSuite;
import org.folio.circulation.api.support.InterfaceUrls;
import org.folio.circulation.api.support.RequestRequestBuilder;
import org.folio.circulation.api.support.ResourceClient;
import org.folio.circulation.api.support.UserRequestBuilder;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.ItemRequestExamples.*;
import static org.folio.circulation.api.support.LoanPreparation.checkOutItem;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestsAPIDeletionTests {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final OkapiHttpClient client = APITestSuite.createClient(exception -> {
    log.error("Request to circulation module failed:", exception);
  });

  private final ResourceClient usersClient = ResourceClient.forUsers(client);
  private final ResourceClient requestsClient = ResourceClient.forRequests(client);
  private final ResourceClient itemsClient = ResourceClient.forItems(client);
  private final ResourceClient loansClient = ResourceClient.forLoans(client);

  @Before
  public void beforeEach()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    requestsClient.deleteAll();
    usersClient.deleteAllIndividually("users");
    itemsClient.deleteAll();
    loansClient.deleteAll();
  }

  @Test
  public void canDeleteAllRequests()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    UUID requesterId = usersClient.create(new UserRequestBuilder().create()).getId();

    UUID firstItemId = itemsClient.create(basedUponSmallAngryPlanet().create()).getId();
    UUID secondItemId = itemsClient.create(basedUponNod().create()).getId();
    UUID thirdItemId = itemsClient.create(basedUponInterestingTimes().create()).getId();

    checkOutItem(firstItemId, loansClient);
    checkOutItem(secondItemId, loansClient);
    checkOutItem(thirdItemId, loansClient);

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(firstItemId)
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(secondItemId)
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withItemId(thirdItemId)
      .withRequesterId(requesterId)
      .create());

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(InterfaceUrls.requestsUrl(),
      ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    CompletableFuture<Response> getAllCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(), ResponseHandler.any(getAllCompleted));

    Response getAllResponse = getAllCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getAllResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

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

    UUID requesterId = usersClient.create(new UserRequestBuilder().create()).getId();

    UUID firstItemId = itemsClient.create(basedUponNod().create()).getId();
    UUID secondItemId = itemsClient.create(basedUponSmallAngryPlanet().create()).getId();
    UUID thirdItemId = itemsClient.create(basedUponTemeraire().create()).getId();

    checkOutItem(firstItemId, loansClient);
    checkOutItem(secondItemId, loansClient);
    checkOutItem(thirdItemId, loansClient);

    requestsClient.create(new RequestRequestBuilder()
      .withId(firstId)
      .withItemId(firstItemId)
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withId(secondId)
      .withItemId(secondItemId)
      .withRequesterId(requesterId)
      .create());

    requestsClient.create(new RequestRequestBuilder()
      .withId(thirdId)
      .withItemId(thirdItemId)
      .withRequesterId(requesterId)
      .create());

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(InterfaceUrls.requestsUrl(String.format("/%s", secondId)),
      ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    assertThat(ResourceClient.forRequests(client).getById(firstId).getStatusCode(), is(HttpURLConnection.HTTP_OK));

    assertThat(ResourceClient.forRequests(client).getById(secondId).getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));

    assertThat(ResourceClient.forRequests(client).getById(thirdId).getStatusCode(), is(HttpURLConnection.HTTP_OK));

    CompletableFuture<Response> getAllCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(), ResponseHandler.any(getAllCompleted));

    Response getAllResponse = getAllCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getAllResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject allRequests = getAllResponse.getJson();

    List<JsonObject> requests = getRequests(allRequests);

    assertThat(requests.size(), is(2));
    assertThat(allRequests.getInteger("totalRecords"), is(2));
  }

  private List<JsonObject> getRequests(JsonObject page) {
    return JsonArrayHelper.toList(page.getJsonArray("requests"));
  }

}
