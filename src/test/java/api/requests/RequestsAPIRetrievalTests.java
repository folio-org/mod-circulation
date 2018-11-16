package api.requests;

import static api.APITestSuite.workAddressTypeId;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.Address;
import api.support.builders.ItemBuilder;
import api.support.builders.RequestBuilder;
import api.support.builders.UserBuilder;
import api.support.http.InterfaceUrls;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class RequestsAPIRetrievalTests extends APITests {
  @Test
  public void canGetARequestById()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID requestId = UUID.fromString("d9960d24-8862-4178-be2c-c1a574188a92"); //to track in logs
    UUID loanId = UUID.fromString("61d74730-5cdb-4675-ab88-1828ee1ad248");

    UUID itemId = UUID.fromString("60c50f1b-7d6c-4b59-863a-a4da213d9530");
    String enumeration = "DUMMY_ENUMERATION";
    
    UUID facultyGroupId = patronGroupsFixture.faculty().getId();
    groupsToDelete.add(facultyGroupId);

    UUID staffGroupId = patronGroupsFixture.staff().getId();
    groupsToDelete.add(staffGroupId);

    itemsFixture.basedUponSmallAngryPlanet(itemBuilder -> itemBuilder
        .withId(itemId)
        .withEnumeration(enumeration));
    
    final IndividualResource sponsor = usersFixture.rebecca(
        builder -> { return builder.withPatronGroupId(facultyGroupId); });
    final IndividualResource proxy = usersFixture.steve(
        builder -> { return builder.withPatronGroupId(staffGroupId); });

    final IndividualResource cd1 = servicePointsFixture.cd1();
    servicePointsToDelete.add(cd1.getId());

    UUID pickupServicePointId = cd1.getId();

    usersFixture.nonExpiringProxyFor(sponsor, proxy);

    loansFixture.checkOutItem(itemId, loanId);

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);

    requestsClient.create(new RequestBuilder()
      .recall()
      .withId(requestId)
      .withRequestDate(requestDate)
      .withItemId(itemId)
      .withRequesterId(sponsor.getId())
      .withUserProxyId(proxy.getId())
      .fulfilToHoldShelf()
      .withRequestExpiration(new LocalDate(2017, 7, 30))
      .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
      .withPickupServicePointId(pickupServicePointId));

    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(String.format("/%s", requestId)),
      ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(requestId.toString()));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("requestDate"), isEquivalentTo(requestDate));
    assertThat(representation.getString("itemId"), is(itemId.toString()));
    assertThat(representation.getString("requesterId"), is(sponsor.getId().toString()));
    assertThat(representation.getString("fulfilmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is("2017-07-30"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2017-08-31"));
    assertThat(representation.getString("pickupServicePointId"),
        is(pickupServicePointId.toString()));
    assertThat(representation.getString("status"), is("Open - Not yet filled"));
    assertThat(representation.containsKey("loan"), is(true));
    assertThat(representation.containsKey("proxy"), is(true));
    assertThat(representation.getJsonObject("proxy").containsKey("patronGroup"), is (true));
    assertThat(representation.getJsonObject("proxy").getString("patronGroupId"),
        is(staffGroupId.toString()));
    assertThat(representation.getJsonObject("proxy").getJsonObject("patronGroup").getString("id"),
        is(staffGroupId.toString()));
    assertThat(representation.containsKey("requester"), is(true));
    assertThat(representation.getJsonObject("requester").containsKey("patronGroup"), is (true));
    assertThat(representation.getJsonObject("requester").getString("patronGroupId"),
        is(facultyGroupId.toString()));
    assertThat(representation.getJsonObject("requester").getJsonObject("patronGroup").getString("id"),
        is(facultyGroupId.toString()));
    assertThat(representation.containsKey("pickupServicePoint"), is(true));
    assertThat(representation.getJsonObject("pickupServicePoint").getString("name"),
        is(cd1.getJson().getString("name")));
    assertThat(representation.getJsonObject("pickupServicePoint").getString("code"),
        is(cd1.getJson().getString("code")));
    assertThat(representation.getJsonObject("pickupServicePoint").getString("discoveryDisplayName"),
        is(cd1.getJson().getString("discoveryDisplayName")));
    assertThat(representation.getJsonObject("pickupServicePoint").getBoolean("pickupLocation"),
        is(cd1.getJson().getBoolean("pickupLocation")));
    assertThat("has information taken from item",
      representation.containsKey("item"), is(true));

    assertThat("title is taken from item",
      representation.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("barcode is taken from item",
      representation.getJsonObject("item").getString("barcode"),
      is("036000291452"));
    
    assertThat(representation.getJsonObject("item").getString("enumeration"),
        is(enumeration));
    
    assertThat(representation.getJsonObject("item").getString("status"),
        is(ItemBuilder.CHECKED_OUT));

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    assertThat("last name is taken from requesting user",
      representation.getJsonObject("requester").getString("lastName"),
      is("Stuart"));

    assertThat("first name is taken from requesting user",
      representation.getJsonObject("requester").getString("firstName"),
      is("Rebecca"));

    assertThat("middle name is not taken from requesting user",
      representation.getJsonObject("requester").containsKey("middleName"),
      is(false));

    assertThat("barcode is taken from requesting user",
      representation.getJsonObject("requester").getString("barcode"),
      is("6059539205"));
  }

  @Test
  public void canGetARequestToBeFulfilledByDeliveryToAnAddressById()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::available);

    final IndividualResource charlotte = usersFixture.charlotte(
      builder -> builder.withAddress(
        new Address(workAddressTypeId(),
          "Fake first address line",
          "Fake second address line",
          "Fake city",
          "Fake region",
          "Fake postal code",
          "Fake country code")));

    final IndividualResource james = usersFixture.james();

    loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource createdRequest = requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .deliverToAddress(workAddressTypeId())
      .by(charlotte));

    JsonObject representation = requestsClient.getById(createdRequest.getId()).getJson();

    assertThat(representation.getString("id"), is(not(emptyString())));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("fulfilmentPreference"), is("Delivery"));
    assertThat(representation.getString("deliveryAddressTypeId"), is(workAddressTypeId()));

    assertThat("Request should have a delivery address",
      representation.containsKey("deliveryAddress"), is(true));

    final JsonObject deliveryAddress = representation.getJsonObject("deliveryAddress");

    assertThat(deliveryAddress.getString("addressTypeId"), is(workAddressTypeId()));
    assertThat(deliveryAddress.getString("addressLine1"), is("Fake first address line"));
    assertThat(deliveryAddress.getString("addressLine2"), is("Fake second address line"));
    assertThat(deliveryAddress.getString("city"), is("Fake city"));
    assertThat(deliveryAddress.getString("region"), is("Fake region"));
    assertThat(deliveryAddress.getString("postalCode"), is("Fake postal code"));
    assertThat(deliveryAddress.getString("countryId"), is("Fake country code"));
  }

  @Test
  public void requestNotFoundForUnknownId()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    Response getResponse = ResourceClient.forRequests(client).getById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  
  @Test
  public void canGetMultipleRequests()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final IndividualResource cd1 = servicePointsFixture.cd1();
    final IndividualResource cd2 = servicePointsFixture.cd2();
    UUID pickupServicePointId = cd1.getId();
    UUID pickupServicePointId2 = cd2.getId();
    
    UUID facultyGroupId = patronGroupsFixture.faculty().getId();
    groupsToDelete.add(facultyGroupId);

    UUID staffGroupId = patronGroupsFixture.staff().getId();
    groupsToDelete.add(staffGroupId);

    final IndividualResource sponsor = usersFixture.rebecca(
        builder -> builder.withPatronGroupId(facultyGroupId));

    final IndividualResource proxy = usersFixture.steve(builder ->
      builder.withPatronGroupId(staffGroupId));

    UUID proxyId = proxy.getId();
    UUID requesterId = sponsor.getId();
    
    usersFixture.nonExpiringProxyFor(sponsor, proxy);

    servicePointsToDelete.add(cd1.getId());
    servicePointsToDelete.add(cd2.getId());

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId)
      .withUserProxyId(proxyId)
      .withPickupServicePointId(pickupServicePointId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId)
      .withUserProxyId(proxyId)
      .withPickupServicePointId(pickupServicePointId2));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponInterestingTimes(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId)
      .withUserProxyId(proxyId)
      .withPickupServicePointId(pickupServicePointId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId)
      .withUserProxyId(proxyId)
      .withPickupServicePointId(pickupServicePointId2));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId)
      .withUserProxyId(proxyId)
      .withPickupServicePointId(pickupServicePointId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponUprooted(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId)
      .withUserProxyId(proxyId)
      .withPickupServicePointId(pickupServicePointId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId)
      .withUserProxyId(proxyId)
      .withPickupServicePointId(pickupServicePointId2));
    
    CompletableFuture<Response> getCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(), ResponseHandler.any(getCompleted));

    Response getResponse = getCompleted.get(5, TimeUnit.SECONDS);
    
    assertThat(String.format("Failed to get list of requests: %s",
      getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    
    List<JsonObject> requestList = getRequests(getResponse.getJson());
    
    requestList.forEach(this::requestHasExpectedProperties);
    requestList.forEach(this::requestHasServicePointProperties);
    requestList.forEach(this::requestHasPatronGroupProperties);
  }

  @Test
  public void canGetARequestToBeFulfilledByDeliveryToAnAddressFromCollectionEndpoint()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      ItemBuilder::available);

    final IndividualResource charlotte = usersFixture.charlotte(
      builder -> builder.withAddress(
        new Address(workAddressTypeId(),
          "Fake first address line",
          "Fake second address line",
          "Fake city",
          "Fake region",
          "Fake postal code",
          "Fake country code")));

    final IndividualResource james = usersFixture.james();

    loansFixture.checkOut(smallAngryPlanet, james);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .deliverToAddress(workAddressTypeId())
      .by(charlotte));

    final List<JsonObject> allRequests = requestsClient.getAll();

    assertThat(allRequests.size(), is(1));

    JsonObject representation = allRequests.get(0);

    assertThat(representation.getString("id"), is(not(emptyString())));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("fulfilmentPreference"), is("Delivery"));
    assertThat(representation.getString("deliveryAddressTypeId"), is(workAddressTypeId()));

    assertThat("Request should have a delivery address",
      representation.containsKey("deliveryAddress"), is(true));

    final JsonObject deliveryAddress = representation.getJsonObject("deliveryAddress");

    assertThat(deliveryAddress.getString("addressTypeId"), is(workAddressTypeId()));
    assertThat(deliveryAddress.getString("addressLine1"), is("Fake first address line"));
    assertThat(deliveryAddress.getString("addressLine2"), is("Fake second address line"));
    assertThat(deliveryAddress.getString("city"), is("Fake city"));
    assertThat(deliveryAddress.getString("region"), is("Fake region"));
    assertThat(deliveryAddress.getString("postalCode"), is("Fake postal code"));
    assertThat(deliveryAddress.getString("countryId"), is("Fake country code"));
  }

  @Test
  public void canPageAllRequests()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID requesterId = usersClient.create(new UserBuilder()).getId();
    
    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponInterestingTimes(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponUprooted(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    CompletableFuture<Response> getFirstPageCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl() + "?limit=4",
      ResponseHandler.any(getFirstPageCompleted));

    CompletableFuture<Response> getSecondPageCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl() + "?limit=4&offset=4",
      ResponseHandler.any(getSecondPageCompleted));

    Response firstPageResponse = getFirstPageCompleted.get(5, TimeUnit.SECONDS);
    Response secondPageResponse = getSecondPageCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get first page of requests: %s",
      firstPageResponse.getBody()),
      firstPageResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    assertThat(String.format("Failed to get second page of requests: %s",
      secondPageResponse.getBody()),
      secondPageResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject firstPage = firstPageResponse.getJson();
    JsonObject secondPage = secondPageResponse.getJson();

    List<JsonObject> firstPageRequests = getRequests(firstPage);
    List<JsonObject> secondPageRequests = getRequests(secondPage);

    assertThat(firstPageRequests.size(), is(4));
    assertThat(firstPage.getInteger("totalRecords"), is(7));

    assertThat(secondPageRequests.size(), is(3));
    assertThat(secondPage.getInteger("totalRecords"), is(7));

    firstPageRequests.forEach(this::requestHasExpectedProperties);
    secondPageRequests.forEach(this::requestHasExpectedProperties);
  }

  @Test
  public void canSearchForRequestsByRequesterLastName()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID firstRequester = usersClient.create(new UserBuilder()
      .withName("Jones", "Steven"))
      .getId();

    UUID secondRequester = usersClient.create(new UserBuilder()
      .withName("Norton", "Jessica"))
      .getId();

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::checkOut).getId())
      .withRequesterId(firstRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withRequesterId(firstRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponInterestingTimes(ItemBuilder::checkOut).getId())
      .withRequesterId(secondRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withRequesterId(firstRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withRequesterId(firstRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponUprooted(ItemBuilder::checkOut).getId())
      .withRequesterId(secondRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withRequesterId(secondRequester));

    CompletableFuture<Response> getRequestsCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl()
        + String.format("?query=requester.lastName=%s", "Norton"),
      ResponseHandler.any(getRequestsCompleted));

    Response getRequestsResponse = getRequestsCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get requests: %s",
      getRequestsResponse.getBody()),
      getRequestsResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject wrappedRequests = getRequestsResponse.getJson();

    List<JsonObject> requests = getRequests(wrappedRequests);

    assertThat(requests.size(), is(3));
    assertThat(wrappedRequests.getInteger("totalRecords"), is(3));

    requests.forEach(this::requestHasExpectedProperties);
  }

  @Test
  public void canSearchForRequestsByProxyLastName()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final IndividualResource sponsor = usersFixture.jessica();

    final IndividualResource firstProxy = usersFixture.charlotte();
    final IndividualResource secondProxy = usersFixture.james();

    usersFixture.currentProxyFor(sponsor, firstProxy);
    usersFixture.currentProxyFor(sponsor, secondProxy);

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::checkOut))
      .by(sponsor)
      .proxiedBy(firstProxy));

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponNod(ItemBuilder::checkOut))
      .by(sponsor)
      .proxiedBy(secondProxy));

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponNod(ItemBuilder::checkOut))
      .by(sponsor)
      .proxiedBy(secondProxy));

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponUprooted(ItemBuilder::checkOut))
      .by(sponsor)
      .proxiedBy(firstProxy));

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut))
      .by(sponsor)
      .proxiedBy(secondProxy));

    CompletableFuture<Response> getRequestsCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl()
        + String.format("?query=proxy.lastName=%s", "Rodwell"),
      ResponseHandler.any(getRequestsCompleted));

    Response getRequestsResponse = getRequestsCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get requests: %s",
      getRequestsResponse.getBody()),
      getRequestsResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject wrappedRequests = getRequestsResponse.getJson();

    List<JsonObject> requests = getRequests(wrappedRequests);

    assertThat(requests.size(), is(3));
    assertThat(wrappedRequests.getInteger("totalRecords"), is(3));

    requests.forEach(this::requestHasExpectedProperties);
  }

  @Test
  public void canSearchForRequestsByItemTitle()
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID requesterId = usersClient.create(new UserBuilder()).getId();

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponInterestingTimes(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponUprooted(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withRequesterId(requesterId));

    CompletableFuture<Response> getRequestsCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl() + "?query=item.title=Nod",
      ResponseHandler.any(getRequestsCompleted));

    Response getRequestsResponse = getRequestsCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Failed to get requests: %s",
      getRequestsResponse.getBody()),
      getRequestsResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject wrappedRequests = getRequestsResponse.getJson();

    List<JsonObject> requests = getRequests(wrappedRequests);

    assertThat(requests.size(), is(2));
    assertThat(wrappedRequests.getInteger("totalRecords"), is(2));

    requests.forEach(this::requestHasExpectedProperties);
  }

  private List<JsonObject> getRequests(JsonObject page) {
    return JsonArrayHelper.toList(page.getJsonArray("requests"));
  }

  private void requestHasExpectedProperties(JsonObject request) {
    hasProperty("id", request, "request");
    hasProperty("requestType", request, "request");
    hasProperty("requestDate", request, "request");
    hasProperty("requesterId", request, "request");
    hasProperty("itemId", request, "request");
    hasProperty("fulfilmentPreference", request, "request");
    hasProperty("item", request, "request");
    hasProperty("requester", request, "request");
    hasProperty("status", request, "request");
  }
  
  private void requestHasServicePointProperties(JsonObject request) {
    hasProperty("pickupServicePointId", request, "request");
    hasProperty("pickupServicePoint", request, "request");
    hasProperty("name", request.getJsonObject("pickupServicePoint"),
        "pickupServicePoint");
  }
  
  private void requestHasPatronGroupProperties(JsonObject request) {
    hasProperty("proxy", request, "proxy");
    hasProperty("patronGroup", request.getJsonObject("proxy"), "patronGroup");
    hasProperty("id", request.getJsonObject("proxy").getJsonObject("patronGroup"), "id");
    hasProperty("group", request.getJsonObject("proxy").getJsonObject("patronGroup"), "group");
    hasProperty("desc", request.getJsonObject("proxy").getJsonObject("patronGroup"), "desc");
    hasProperty("requester", request, "requester");
    hasProperty("patronGroup", request.getJsonObject("requester"), "patronGroup");
    hasProperty("id", request.getJsonObject("requester").getJsonObject("patronGroup"), "id");
    hasProperty("group", request.getJsonObject("requester").getJsonObject("patronGroup"), "desc");
    hasProperty("desc", request.getJsonObject("requester").getJsonObject("patronGroup"), "group");
  }

  private void hasProperty(String property, JsonObject resource, String type) {
    assertThat(String.format("%s should have %s: %s",
      type, property, resource),
      resource.containsKey(property), is(true));
  }
}
