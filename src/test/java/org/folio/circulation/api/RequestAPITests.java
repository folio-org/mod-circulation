package org.folio.circulation.api;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.support.RequestRequestBuilder;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.TextDateTimeMatcher.isEquivalentTo;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestAPITests {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  OkapiHttpClient client = APITestSuite.createClient(exception -> {
    log.error("Request to circulation module failed:", exception);
  });

  @Before
  public void beforeEach()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    deleteAllRequests();
    deleteAllLoans();
    deleteAllItems();
  }

  @Test
  public void canCreateARequest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID id = UUID.randomUUID();
    UUID itemId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    JsonObject requestRequest = new RequestRequestBuilder()
      .recall()
      .withId(id)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(requesterId)
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response response = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    JsonObject representation = response.getJson();

    assertThat(representation.getString("id"), is(id.toString()));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("requestDate"), isEquivalentTo(requestDate));
    assertThat(representation.getString("itemId"), is(itemId.toString()));
    assertThat(representation.getString("requesterId"), is(requesterId.toString()));
    assertThat(representation.getString("fulfilmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is("2017-07-30"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2017-08-31"));
  }

  @Test
  public void getRequestByIdIsNotImplemented()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(requestsUrl(String.format("/%s", UUID.randomUUID())),
      ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_IMPLEMENTED));
  }

  @Test
  public void getAllRequestsIsNotImplemented()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException,
    UnsupportedEncodingException {

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(requestsUrl(), ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_IMPLEMENTED));
  }

  @Test
  public void replaceRequestIsNotImplemented()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(requestsUrl(String.format("/%s", UUID.randomUUID())),
      new JsonObject(), ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_IMPLEMENTED));
  }

  @Test
  public void canDeleteAllRequests()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    createRequest(new RequestRequestBuilder().create());
    createRequest(new RequestRequestBuilder().create());
    createRequest(new RequestRequestBuilder().create());
    createRequest(new RequestRequestBuilder().create());
    createRequest(new RequestRequestBuilder().create());

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(requestsUrl(),
      ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void deletingARequestIsNotImplemented()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException,
    UnsupportedEncodingException {

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(requestsUrl(String.format("/%s", UUID.randomUUID())),
      ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_IMPLEMENTED));
  }

  private static URL itemsUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.viaOkapiModuleUrl("/item-storage/items" + subPath);
  }

  private static URL loansUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.viaOkapiModuleUrl("/loan-storage/loans" + subPath);
  }

  private static URL requestsUrl()
    throws MalformedURLException {

    return requestsUrl("");
  }

  private static URL requestsUrl(String subPath)
    throws MalformedURLException {

    return APITestSuite.circulationModuleUrl("/circulation/requests" + subPath);
  }

  private IndividualResource createRequest(JsonObject loanRequest)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    return createResource(loanRequest, requestsUrl(), "request");
  }

  private IndividualResource createResource(
    JsonObject request,
    URL url,
    String resourceName)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    CompletableFuture<Response> createCompleted = new CompletableFuture<>();

    client.post(url, request,
      ResponseHandler.json(createCompleted));

    Response response = createCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create %s: %s", resourceName, response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));

    return new IndividualResource(response);
  }

  private void deleteAllRequests()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    APITestSuite.deleteAll(requestsUrl());
  }

  private void deleteAllLoans()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    APITestSuite.deleteAll(loansUrl(""));
  }

  private void deleteAllItems()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    APITestSuite.deleteAll(itemsUrl(""));
  }
}
