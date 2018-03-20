package org.folio.circulation.api.requests;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.support.APITests;
import org.folio.circulation.api.support.builders.RequestBuilder;
import org.folio.circulation.api.support.http.InterfaceUrls;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.circulation.api.support.http.InterfaceUrls.requestsUrl;
import static org.folio.circulation.api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestsAPIProxyTests extends APITests {
  @Test
  public void canCreateProxiedRequestWhenCurrentActiveRelationship()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(item, usersFixture.steve());

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    usersFixture.currentProxyFor(sponsor, proxy);

    JsonObject requestRequest = new RequestBuilder()
      .forItem(item)
      .withRequesterId(sponsor.getId())
      .withUserProxyId(proxy.getId())
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse, hasStatus(HTTP_CREATED));

    JsonObject representation = postResponse.getJson();

    assertThat("has information taken from proxying user",
      representation.containsKey("proxy"), is(true));

    final JsonObject proxyRepresentation = representation.getJsonObject("proxy");

    assertThat("last name is taken from proxying user",
      proxyRepresentation.getString("lastName"),
      is("Rodwell"));

    assertThat("first name is taken from proxying user",
      proxyRepresentation.getString("firstName"),
      is("James"));

    assertThat("middle name is not taken from proxying user",
      proxyRepresentation.containsKey("middleName"),
      is(false));

    assertThat("barcode is taken from proxying user",
      proxyRepresentation.getString("barcode"),
      is("6430530304"));
  }

  @Test
  public void canCreateProxiedRequestWhenNonExpiringRelationship()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(item, usersFixture.steve());

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    usersFixture.nonExpiringProxyFor(sponsor, proxy);

    JsonObject requestRequest = new RequestBuilder()
      .forItem(item)
      .withRequesterId(sponsor.getId())
      .withUserProxyId(proxy.getId())
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse, hasStatus(HTTP_CREATED));
  }

  @Test
  public void cannotCreateProxiedRequestWhenRelationshipIsInactive()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(item, usersFixture.steve());

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    usersFixture.inactiveProxyFor(sponsor, proxy);

    JsonObject requestRequest = new RequestBuilder()
      .forItem(item)
      .withRequesterId(sponsor.getId())
      .withUserProxyId(proxy.getId())
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(422));
  }

  @Test
  public void cannotCreateProxiedRequestWhenRelationshipHasExpired()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(item, usersFixture.steve());

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    usersFixture.expiredProxyFor(sponsor, proxy);

    JsonObject requestRequest = new RequestBuilder()
      .forItem(item)
      .withRequesterId(sponsor.getId())
      .withUserProxyId(proxy.getId())
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(422));
  }

  @Test
  public void cannotCreateProxiedRequestWhenRelationshipIsForOtherSponsor()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(item, usersFixture.steve());

    IndividualResource unexpectedSponsor = usersFixture.jessica();
    IndividualResource otherUser = usersFixture.charlotte();
    IndividualResource proxy = usersFixture.james();

    usersFixture.expiredProxyFor(unexpectedSponsor, proxy);

    JsonObject requestRequest = new RequestBuilder()
      .forItem(item)
      .withRequesterId(otherUser.getId())
      .withUserProxyId(proxy.getId())
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse.getStatusCode(), is(422));
  }

  @Test
  public void canUpdateProxiedRequestWhenValidProxyRelationship()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponTemeraire().getId();

    loansFixture.checkOutItem(itemId);

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withId(id)
        .withRequestDate(requestDate)
        .withItemId(itemId)
        .withRequesterId(sponsor.getId())
        .fulfilToHoldShelf()
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    usersFixture.currentProxyFor(sponsor, proxy);

    JsonObject updatedRequest = createdRequest.copyJson();

    updatedRequest.put("proxyUserId", proxy.getId().toString());

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      updatedRequest, ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));
  }

  @Test
  public void cannotUpdateProxiedRequestWhenRelationshipHasExpired()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponTemeraire().getId();

    loansFixture.checkOutItem(itemId);

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withId(id)
        .withRequestDate(requestDate)
        .withItemId(itemId)
        .withRequesterId(sponsor.getId())
        .fulfilToHoldShelf()
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    usersFixture.expiredProxyFor(sponsor, proxy);

    JsonObject updatedRequest = createdRequest.copyJson();

    updatedRequest.put("proxyUserId", proxy.getId().toString());

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      updatedRequest, ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(422));
  }

  @Test
  public void cannotUpdateProxiedRequestWhenRelationshipIsForOtherSponsor()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID id = UUID.randomUUID();

    UUID itemId = itemsFixture.basedUponTemeraire().getId();

    loansFixture.checkOutItem(itemId);

    IndividualResource unexpectedSponsor = usersFixture.jessica();
    IndividualResource otherUser = usersFixture.charlotte();
    IndividualResource proxy = usersFixture.james();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withId(id)
        .withRequestDate(requestDate)
        .withItemId(itemId)
        .withRequesterId(otherUser.getId())
        .fulfilToHoldShelf()
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    usersFixture.currentProxyFor(unexpectedSponsor, proxy);

    JsonObject updatedRequest = createdRequest.copyJson();

    updatedRequest.put("proxyUserId", proxy.getId().toString());

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      updatedRequest, ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(422));
  }
}
