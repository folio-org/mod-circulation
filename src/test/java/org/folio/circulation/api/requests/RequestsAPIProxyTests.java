package org.folio.circulation.api.requests;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.support.APITests;
import org.folio.circulation.api.support.builders.RequestBuilder;
import org.folio.circulation.api.support.builders.UserBuilder;
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
  public void canCreateProxiedRequestWhenValidRelationship()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(item, usersFixture.steve());

    DateTime expDate = new DateTime(2999, 2, 27, 10, 23, 43, DateTimeZone.UTC);

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    UUID recordId = usersFixture.proxyFor(sponsor.getId(), proxy.getId(),
      expDate).getId();

    JsonObject requestRequest = new RequestBuilder()
      .forItem(item)
      .withUserProxyId(recordId)
      .create();

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(requestsUrl(), requestRequest,
      ResponseHandler.json(postCompleted));

    Response postResponse = postCompleted.get(5, TimeUnit.SECONDS);

    assertThat(postResponse, hasStatus(HTTP_CREATED));
  }

  @Test
  public void cannotCreateProxiedRequestWhenRelationshipHasExpired()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(item, usersFixture.steve());

    DateTime expDate = new DateTime(1999, 2, 27, 10, 23, 43, DateTimeZone.UTC);

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    UUID recordId = usersFixture.proxyFor(sponsor.getId(), proxy.getId(),
      expDate).getId();

    JsonObject requestRequest = new RequestBuilder()
      .forItem(item)
      .withUserProxyId(recordId)
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

    UUID requesterId = usersClient.create(new UserBuilder()).getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withId(id)
        .withRequestDate(requestDate)
        .withItemId(itemId)
        .withRequesterId(requesterId)
        .fulfilToHoldShelf()
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    DateTime expDate = new DateTime(2999, 2, 27, 10, 23, 43, DateTimeZone.UTC);

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    UUID recordId = usersFixture.proxyFor(sponsor.getId(), proxy.getId(),
      expDate).getId();

    JsonObject updatedRequest = createdRequest.copyJson();

    updatedRequest.put("proxyUserId", recordId.toString());

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

    UUID requesterId = usersClient.create(new UserBuilder()).getId();

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withId(id)
        .withRequestDate(requestDate)
        .withItemId(itemId)
        .withRequesterId(requesterId)
        .fulfilToHoldShelf()
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31)));

    DateTime expDate = new DateTime(1999, 2, 27, 10, 23, 43, DateTimeZone.UTC);

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    UUID recordId = usersFixture.proxyFor(sponsor.getId(), proxy.getId(),
      expDate).getId();

    JsonObject updatedRequest = createdRequest.copyJson();

    updatedRequest.put("proxyUserId", recordId.toString());

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(InterfaceUrls.requestsUrl(String.format("/%s", id)),
      updatedRequest, ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(422));
  }
}
