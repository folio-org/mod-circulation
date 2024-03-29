package api.requests;

import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.UUID;

import api.support.http.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

class RequestsAPIProxyTests extends APITests {
  @Test
  void canCreateProxiedRequestWhenCurrentActiveRelationship() {
    final IndividualResource smallAngryPlanet
      = itemsFixture.basedUponSmallAngryPlanet();

    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.steve());

    final IndividualResource sponsor = usersFixture.jessica();
    final IndividualResource proxy = usersFixture.james();

    proxyRelationshipsFixture.currentProxyFor(sponsor, proxy);

    final RequestBuilder request = new RequestBuilder()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(sponsor)
      .proxiedBy(proxy);

    final IndividualResource postResponse = requestsFixture.place(request);

    JsonObject representation = postResponse.getJson();

    assertThat("has information taken from proxying user",
      representation.containsKey("proxy"), is(true));

    final JsonObject proxyRepresentation = representation.getJsonObject("proxy");

    assertThat("last name is taken from proxying user",
      proxyRepresentation.getString("lastName"), is("Rodwell"));

    assertThat("first name is taken from proxying user",
      proxyRepresentation.getString("firstName"), is("James"));

    assertThat("middle name is not taken from proxying user",
      proxyRepresentation.containsKey("middleName"), is(false));

    assertThat("barcode is taken from proxying user",
      proxyRepresentation.getString("barcode"), is("6430530304"));
  }

  @Test
  void canCreateProxiedRequestWhenNonExpiringRelationship() {
    final IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();

    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(item, usersFixture.steve());

    final IndividualResource sponsor = usersFixture.jessica();
    final IndividualResource proxy = usersFixture.james();

    proxyRelationshipsFixture.nonExpiringProxyFor(sponsor, proxy);

    final RequestBuilder request = new RequestBuilder()
      .forItem(item)
      .withPickupServicePointId(pickupServicePointId)
      .by(sponsor)
      .proxiedBy(proxy);

    Response postResponse = requestsFixture.attemptPlace(request);

    assertThat(postResponse, hasStatus(HTTP_CREATED));
  }

  @Test
  void cannotCreateProxiedRequestWhenRelationshipIsInactive() {
    final IndividualResource smallAngryPlanet
      = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);

    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource james = usersFixture.james();

    proxyRelationshipsFixture.inactiveProxyFor(jessica, james);

    final RequestBuilder request = new RequestBuilder()
      .forItem(smallAngryPlanet)
      .by(jessica)
      .proxiedBy(james);

    final Response postResponse = requestsFixture.attemptPlace(request);

    assertThat(postResponse.getStatusCode(), is(422));

    assertThat(postResponse.getJson(), hasErrorWith(
      hasMessage("proxyUserId is not valid")));
  }

  @Test
  void cannotCreateProxiedRequestWhenRelationshipHasExpired() {
    final IndividualResource smallAngryPlanet
      = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);

    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource james = usersFixture.james();

    proxyRelationshipsFixture.expiredProxyFor(jessica, james);

    final RequestBuilder request = new RequestBuilder()
      .forItem(smallAngryPlanet)
      .by(jessica)
      .proxiedBy(james);

    final Response postResponse = requestsFixture.attemptPlace(request);

    assertThat(postResponse.getStatusCode(), is(422));

    assertThat(postResponse.getJson(), hasErrorWith(
      hasMessage("proxyUserId is not valid")));
  }

  @Test
  void cannotCreateProxiedRequestWhenRelationshipIsForOtherSponsor() {
    final IndividualResource smallAngryPlanet
      = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.steve());

    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource james = usersFixture.james();

    proxyRelationshipsFixture.expiredProxyFor(jessica, james);

    final RequestBuilder request = new RequestBuilder()
      .forItem(smallAngryPlanet)
      .by(charlotte)
      .proxiedBy(james);

    final Response postResponse = requestsFixture.attemptPlace(request);

    assertThat(postResponse.getStatusCode(), is(422));

    assertThat(postResponse.getJson(), hasErrorWith(
      hasMessage("proxyUserId is not valid")));
  }

  @Test
  void canUpdateProxiedRequestWhenValidProxyRelationship() {
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(temeraire, usersFixture.steve());

    final IndividualResource sponsor = usersFixture.jessica();
    final IndividualResource proxy = usersFixture.rebecca();

    final RequestBuilder request = new RequestBuilder()
      .recall()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(temeraire)
      .by(sponsor);

    final IndividualResource createdRequest = requestsFixture.place(request);

    proxyRelationshipsFixture.currentProxyFor(sponsor, proxy);

    final RequestBuilder updatedRequest = RequestBuilder.from(createdRequest)
      .proxiedBy(proxy);

    requestsFixture.replaceRequest(createdRequest.getId(), updatedRequest);

    final Response fetchedRequest = requestsFixture.getById(createdRequest.getId());

    final JsonObject representation = fetchedRequest.getJson();

    assertThat("has information taken from proxying user",
      representation.containsKey("proxy"), is(true));

    final JsonObject proxyRepresentation = representation.getJsonObject("proxy");

    assertThat("last name is taken from proxying user",
      proxyRepresentation.getString("lastName"), is("Stuart"));

    assertThat("first name is taken from proxying user",
      proxyRepresentation.getString("firstName"), is("Rebecca"));

    assertThat("middle name is not taken from proxying user",
      proxyRepresentation.containsKey("middleName"), is(false));

    assertThat("barcode is taken from proxying user",
      proxyRepresentation.getString("barcode"), is("6059539205"));
  }

  @Test
  void cannotUpdateProxiedRequestWhenRelationshipHasExpired() {
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(temeraire, usersFixture.rebecca());

    final IndividualResource sponsor = usersFixture.jessica();
    final IndividualResource proxy = usersFixture.james();

    final IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withPickupServicePointId(pickupServicePointId)
        .forItem(temeraire)
        .by(sponsor));

    proxyRelationshipsFixture.expiredProxyFor(sponsor, proxy);

    final RequestBuilder updatedRequest = RequestBuilder.from(createdRequest)
      .proxiedBy(proxy);

    final Response putResponse = requestsFixture.attemptToReplaceRequest(
      createdRequest.getId(), updatedRequest);

    assertThat(putResponse.getStatusCode(), is(422));

    assertThat(putResponse.getJson(), hasErrorWith(
      hasMessage("proxyUserId is not valid")));
  }

  @Test
  void cannotUpdateProxiedRequestWhenRelationshipIsForOtherSponsor() {
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(temeraire);

    final IndividualResource unexpectedSponsor = usersFixture.jessica();
    final IndividualResource otherUser = usersFixture.charlotte();
    final IndividualResource proxy = usersFixture.james();

    final RequestBuilder request = new RequestBuilder()
      .recall()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(temeraire)
      .by(otherUser);

    final IndividualResource createdRequest = requestsFixture.place(request);

    proxyRelationshipsFixture.currentProxyFor(unexpectedSponsor, proxy);

    final RequestBuilder updatedRequest = RequestBuilder.from(createdRequest)
      .proxiedBy(proxy);

    final Response putResponse = requestsFixture.attemptToReplaceRequest(
      createdRequest.getId(), updatedRequest);

    assertThat(putResponse.getStatusCode(), is(422));

    assertThat(putResponse.getJson(), hasErrorWith(
      hasMessage("proxyUserId is not valid")));
  }
}
