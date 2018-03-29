package api.requests;

import io.vertx.core.json.JsonObject;
import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.builders.UserBuilder;
import api.support.http.InterfaceUrls;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.hamcrest.core.Is;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestsAPIStatusChangeTests extends APITests {
  @Test
  public void creatingAHoldRequestChangesTheItemStatus()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    loansFixture.checkOutItem(itemId);

    requestsClient.create(new RequestBuilder()
      .hold()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId()));

    Response changedItem = itemsClient.getById(itemId);

    assertThat(changedItem.getJson().getJsonObject("status").getString("name"),
      is("Checked out - Held"));
  }

  @Test
  public void creatingARecallRequestChangesTheItemStatus()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    loansFixture.checkOutItem(itemId);

    requestsClient.create(new RequestBuilder()
      .recall()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId()));

    Response changedItem = itemsClient.getById(itemId);

    assertThat(changedItem.getJson().getJsonObject("status").getString("name"),
      is("Checked out - Recalled"));
  }

  @Test
  public void creatingAPageRequestDoesNotChangesTheItemStatus()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet().getId();

    loansFixture.checkOutItem(itemId);

    requestsClient.create(new RequestBuilder()
      .page()
      .withId(id)
      .withItemId(itemId)
      .withRequesterId(usersClient.create(new UserBuilder()).getId()));

    Response changedItem = itemsClient.getById(itemId);

    assertThat(changedItem.getJson().getJsonObject("status").getString("name"),
      is("Checked out"));
  }

  @Test
  public void itemStatusIsChangedWhenMultipleRequestsMadeOfSameTypeForSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder -> itemBuilder
        .withBarcode("036000291452"))
      .getId();

    loansFixture.checkOutItem(itemId);

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
      .recall()
      .withItemId(itemId)
      .withRequesterId(firstRequesterId)
      .create());

    requestsClient.create(new RequestBuilder()
      .recall()
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
      postResponse.getStatusCode(), Is.is(HttpURLConnection.HTTP_CREATED));

    Response changedItem = itemsClient.getById(itemId);

    assertThat(changedItem.getJson().getJsonObject("status").getString("name"),
      is("Checked out - Recalled"));
  }

  @Test
  public void itemStatusIsBasedUponOldestRequestCreated()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder -> itemBuilder
        .withBarcode("036000291452"))
      .getId();

    loansFixture.checkOutItem(itemId);

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
      .recall()
      .withItemId(itemId)
      .withRequesterId(firstRequesterId)
      .withRequestDate(new DateTime(2018, 1, 24, 12, 43, 21, DateTimeZone.UTC))
      .create());

    requestsClient.create(new RequestBuilder()
      .page()
      .withItemId(itemId)
      .withRequesterId(secondRequesterId)
      .withRequestDate(new DateTime(2018, 1, 27, 11, 21, 43, DateTimeZone.UTC))
      .create());

    JsonObject requestRequest = new RequestBuilder()
      .hold()
      .withItemId(itemId)
      .withRequesterId(thirdRequesterId)
      .withRequestDate(new DateTime(2018, 2, 3, 8, 1, 6, DateTimeZone.UTC))
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(InterfaceUrls.requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", postResponse.getBody()),
      postResponse.getStatusCode(), Is.is(HttpURLConnection.HTTP_CREATED));

    Response changedItem = itemsClient.getById(itemId);

    assertThat(changedItem.getJson().getJsonObject("status").getString("name"),
      is("Checked out - Recalled"));
  }

  //This might change when page requests are analysed further
  @Test
  public void itemStatusDoesNotChangeWhenLastRequestIsPageRequest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID itemId = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder -> itemBuilder
        .withBarcode("036000291452"))
      .getId();

    loansFixture.checkOutItem(itemId);

    UUID firstRequesterId = usersClient.create(new UserBuilder()
      .withName("Jones", "Steven")
      .withBarcode("564376549214"))
      .getId();

    UUID secondRequesterId = usersClient.create(new UserBuilder()
      .withName("Williamson", "Casey")
      .withBarcode("340695406504"))
      .getId();

    requestsClient.create(new RequestBuilder()
      .recall()
      .withItemId(itemId)
      .withRequesterId(firstRequesterId)
      .create());

    JsonObject requestRequest = new RequestBuilder()
      .page()
      .withItemId(itemId)
      .withRequesterId(secondRequesterId)
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(InterfaceUrls.requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to create request: %s", postResponse.getBody()),
      postResponse.getStatusCode(), Is.is(HttpURLConnection.HTTP_CREATED));

    Response changedItem = itemsClient.getById(itemId);

    assertThat(changedItem.getJson().getJsonObject("status").getString("name"),
      is("Checked out - Recalled"));
  }
}
