package api.requests;

import static api.support.http.InterfaceUrls.requestsUrl;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.InterfaceUrls;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class RequestsAPIProxyTests extends APITests {
  @Test
  public void canCreateProxiedRequestWhenCurrentActiveRelationship()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.steve());

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    proxyRelationshipsFixture.currentProxyFor(sponsor, proxy);

    JsonObject requestRequest = new RequestBuilder()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(sponsor)
      .proxiedBy(proxy)
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

    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(item, usersFixture.steve());

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    proxyRelationshipsFixture.nonExpiringProxyFor(sponsor, proxy);

    JsonObject requestRequest = new RequestBuilder()
      .forItem(item)
      .withPickupServicePointId(pickupServicePointId)
      .by(sponsor)
      .proxiedBy(proxy)
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

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource steve = usersFixture.steve();

    loansFixture.checkOutByBarcode(smallAngryPlanet, steve);

    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource james = usersFixture.james();

    proxyRelationshipsFixture.inactiveProxyFor(jessica, james);

    JsonObject requestRequest = new RequestBuilder()
      .forItem(smallAngryPlanet)
      .by(jessica)
      .proxiedBy(james)
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

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource steve = usersFixture.steve();

    loansFixture.checkOutByBarcode(smallAngryPlanet, steve);

    IndividualResource jessica = usersFixture.jessica();
    IndividualResource james = usersFixture.james();

    proxyRelationshipsFixture.expiredProxyFor(jessica, james);

    JsonObject requestRequest = new RequestBuilder()
      .forItem(smallAngryPlanet)
      .by(jessica)
      .proxiedBy(james)
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

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.steve());

    IndividualResource jessica = usersFixture.jessica();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource james = usersFixture.james();

    proxyRelationshipsFixture.expiredProxyFor(jessica, james);

    JsonObject requestRequest = new RequestBuilder()
      .forItem(smallAngryPlanet)
      .by(charlotte)
      .proxiedBy(james)
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

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(temeraire, usersFixture.steve());

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.rebecca();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withPickupServicePointId(pickupServicePointId)
        .forItem(temeraire)
        .by(sponsor));

    proxyRelationshipsFixture.currentProxyFor(sponsor, proxy);

    JsonObject updatedRequest = createdRequest.copyJson();

    updatedRequest.put("proxyUserId", proxy.getId().toString());

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(InterfaceUrls.requestsUrl(String.format("/%s", createdRequest.getId())),
      updatedRequest, ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(HttpURLConnection.HTTP_NO_CONTENT));

    final JsonObject representation = requestsClient.get(createdRequest).getJson();

    assertThat("has information taken from proxying user",
      representation.containsKey("proxy"), is(true));

    final JsonObject proxyRepresentation = representation.getJsonObject("proxy");

    assertThat("last name is taken from proxying user",
      proxyRepresentation.getString("lastName"),
      is("Stuart"));

    assertThat("first name is taken from proxying user",
      proxyRepresentation.getString("firstName"),
      is("Rebecca"));

    assertThat("middle name is not taken from proxying user",
      proxyRepresentation.containsKey("middleName"),
      is(false));

    assertThat("barcode is taken from proxying user",
      proxyRepresentation.getString("barcode"),
      is("6059539205"));
  }

  @Test
  public void cannotUpdateProxiedRequestWhenRelationshipHasExpired()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(temeraire, usersFixture.rebecca());

    IndividualResource sponsor = usersFixture.jessica();
    IndividualResource proxy = usersFixture.james();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withPickupServicePointId(pickupServicePointId)
        .forItem(temeraire)
        .by(sponsor));

    proxyRelationshipsFixture.expiredProxyFor(sponsor, proxy);

    JsonObject updatedRequest = createdRequest.copyJson();

    updatedRequest.put("proxyUserId", proxy.getId().toString());

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(InterfaceUrls.requestsUrl(String.format("/%s", createdRequest.getId())),
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

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(temeraire);

    IndividualResource unexpectedSponsor = usersFixture.jessica();
    IndividualResource otherUser = usersFixture.charlotte();
    IndividualResource proxy = usersFixture.james();

    IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withPickupServicePointId(pickupServicePointId)
        .forItem(temeraire)
        .by(otherUser));

    proxyRelationshipsFixture.currentProxyFor(unexpectedSponsor, proxy);

    JsonObject updatedRequest = createdRequest.copyJson();

    updatedRequest.put("proxyUserId", proxy.getId().toString());

    CompletableFuture<Response> putCompleted = new CompletableFuture<>();

    client.put(InterfaceUrls.requestsUrl(String.format("/%s", createdRequest.getId())),
      updatedRequest, ResponseHandler.any(putCompleted));

    Response putResponse = putCompleted.get(5, TimeUnit.SECONDS);

    assertThat(putResponse.getStatusCode(), is(422));
  }
}
