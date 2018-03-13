package org.folio.circulation.api.requests;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.support.APITests;
import org.folio.circulation.api.support.builders.RequestBuilder;
import org.folio.circulation.api.support.builders.UserBuilder;
import org.folio.circulation.api.support.http.InterfaceUrls;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.fixtures.LoanFixture.checkOutItem;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestsAPICreateMultipleRequestsTests extends APITests {

  @Test
  public void canCreateMultipleRequestsOfSameTypeForSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder -> itemBuilder
        .withBarcode("036000291452"))
      .getId();

    checkOutItem(itemId, loansClient);

    UUID firstRequesterId = usersClient.create(new UserBuilder()
      .withName("Jones", "Steven")
      .withBarcode("564376549214"))
      .getId();

    UUID secondRequesterId = usersClient.create(new UserBuilder()
      .withName("Williamson", "Casey")
      .withBarcode("340695406504"))
      .getId();

    UUID thirdRequesterId = usersClient.create(new UserBuilder()
      .withName("Stevenson", "Kelly")
      .withBarcode("670544032419"))
      .getId();

    requestsClient.create(new RequestBuilder()
      .hold()
      .withItemId(itemId)
      .withRequesterId(firstRequesterId)
      .create());

    requestsClient.create(new RequestBuilder()
      .hold()
      .withItemId(itemId)
      .withRequesterId(secondRequesterId)
      .create());

    JsonObject requestRequest = new RequestBuilder()
      .hold()
      .withItemId(itemId)
      .withRequesterId(thirdRequesterId)
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(InterfaceUrls.requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
  }

  @Test
  public void canCreateMultipleRequestsOfDifferentTypeForSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder -> itemBuilder
        .withBarcode("036000291452"))
      .getId();

    checkOutItem(itemId, loansClient);

    UUID firstRequesterId = usersClient.create(new UserBuilder()
      .withName("Jones", "Steven")
      .withBarcode("564376549214"))
      .getId();

    UUID secondRequesterId = usersClient.create(new UserBuilder()
      .withName("Williamson", "Casey")
      .withBarcode("340695406504"))
      .getId();

    UUID thirdRequesterId = usersClient.create(new UserBuilder()
      .withName("Stevenson", "Kelly")
      .withBarcode("670544032419"))
      .getId();

    requestsClient.create(new RequestBuilder()
      .hold()
      .withItemId(itemId)
      .withRequesterId(firstRequesterId)
      .create());

    requestsClient.create(new RequestBuilder()
      .page()
      .withItemId(itemId)
      .withRequesterId(secondRequesterId)
      .create());

    JsonObject requestRequest = new RequestBuilder()
      .recall()
      .withItemId(itemId)
      .withRequesterId(thirdRequesterId)
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(InterfaceUrls.requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", postResponse.getBody()),
      postResponse.getStatusCode(), is(HttpURLConnection.HTTP_CREATED));
  }
}
