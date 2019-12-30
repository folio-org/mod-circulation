package api.requests;

import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.http.CqlQuery.noQuery;
import static api.support.http.InterfaceUrls.requestsUrl;
import static api.support.http.Limit.limit;
import static api.support.http.Offset.noOffset;
import static api.support.http.Offset.offset;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.folio.circulation.domain.representations.ItemProperties.CALL_NUMBER_COMPONENTS;
import static org.folio.circulation.support.http.client.ResponseHandler.any;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.joda.time.DateTimeZone.UTC;
import static org.junit.Assert.assertTrue;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.builders.Address;
import api.support.builders.ItemBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.CqlQuery;
import api.support.http.InventoryItemResource;
import api.support.http.Limit;
import api.support.http.Offset;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class RequestsAPIRetrievalTests extends APITests {
  private static final String NEW_TAG = "new";
  private static final String IMPORTANT_TAG = "important";
  private static final String ONE_COPY_NUMBER = "1";
  private static final String TWO_COPY_NUMBER = "2";

  @Test
  public void canGetARequestById() {
    UUID facultyGroupId = patronGroupsFixture.faculty().getId();
    UUID staffGroupId = patronGroupsFixture.staff().getId();

    final InventoryItemResource smallAngryPlanet = itemsFixture
      .basedUponSmallAngryPlanet(itemBuilder -> itemBuilder
        .withCallNumber("itCn", "itCnPrefix", "itCnSuffix")
        .withEnumeration("enumeration1")
        .withChronology("chronology")
        .withVolume("vol.1")
        .withCopyNumbers(asList(ONE_COPY_NUMBER, TWO_COPY_NUMBER)));

    final IndividualResource sponsor = usersFixture.rebecca(user -> user
      .withPatronGroupId(facultyGroupId));

    final IndividualResource proxy = usersFixture.steve(user -> user
      .withPatronGroupId(staffGroupId));

    final IndividualResource cd1 = servicePointsFixture.cd1();

    UUID pickupServicePointId = cd1.getId();

    proxyRelationshipsFixture.nonExpiringProxyFor(sponsor, proxy);

    loansFixture.checkOutByBarcode(smallAngryPlanet);

    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, UTC);

    final IndividualResource createdRequest = requestsClient.create(
      new RequestBuilder()
        .recall()
        .withRequestDate(requestDate)
        .forItem(smallAngryPlanet)
        .by(sponsor)
        .proxiedBy(proxy)
        .fulfilToHoldShelf()
        .withRequestExpiration(new LocalDate(2017, 7, 30))
        .withHoldShelfExpiration(new LocalDate(2017, 8, 31))
        .withPickupServicePointId(pickupServicePointId)
        .withTags(new RequestBuilder.Tags(asList(NEW_TAG, IMPORTANT_TAG))));

    Response getResponse = requestsClient.getById(createdRequest.getId());

    assertThat(format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(createdRequest.getId()));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("requestDate"), isEquivalentTo(requestDate));
    assertThat(representation.getString("itemId"), is(smallAngryPlanet.getId()));
    assertThat(representation.getString("requesterId"), is(sponsor.getId()));
    assertThat(representation.getString("fulfilmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is("2017-07-30"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2017-08-31"));
    assertThat(representation.getString("pickupServicePointId"),
      is(pickupServicePointId));

    assertThat(representation.getString("status"), is("Open - Not yet filled"));

    assertThat(representation.containsKey("proxy"), is(true));
    final JsonObject proxySummary = representation.getJsonObject("proxy");

    assertThat(proxySummary.containsKey("patronGroup"), is(true));
    assertThat(proxySummary.getString("patronGroupId"), is(staffGroupId));
    assertThat(proxySummary.getJsonObject("patronGroup").getString("id"),
      is(staffGroupId));

    assertThat(representation.containsKey("pickupServicePoint"), is(true));

    final JsonObject pickupServicePoint = representation
      .getJsonObject("pickupServicePoint");

    assertThat(pickupServicePoint.getString("name"), is(cd1.getJson()
      .getString("name")));

    assertThat(pickupServicePoint.getString("code"), is(cd1.getJson()
      .getString("code")));

    assertThat(pickupServicePoint.getString("discoveryDisplayName"),
      is(cd1.getJson().getString("discoveryDisplayName")));

    assertThat(pickupServicePoint.getBoolean("pickupLocation"),
      is(cd1.getJson().getBoolean("pickupLocation")));

    assertThat("has information taken from item",
      representation.containsKey("item"), is(true));

    final JsonObject itemSummary = representation.getJsonObject("item");

    assertThat("title is taken from item",
      itemSummary.getString("title"), is("The Long Way to a Small, Angry Planet"));

    assertThat("barcode is taken from item",
      itemSummary.getString("barcode"), is("036000291452"));

    assertThat(itemSummary.getString("status"), is(CHECKED_OUT));

    assertThat(itemSummary.containsKey("copyNumbers"), is(true));

    assertThat(itemSummary.getJsonArray("copyNumbers"),
      contains(ONE_COPY_NUMBER, TWO_COPY_NUMBER));

    assertThat("has information taken from requesting user",
      representation.containsKey("requester"), is(true));

    final JsonObject requesterSummary = representation.getJsonObject("requester");

    assertThat("last name is taken from requesting user",
      requesterSummary.getString("lastName"),
      is("Stuart"));

    assertThat("first name is taken from requesting user",
      requesterSummary.getString("firstName"),
      is("Rebecca"));

    assertThat("middle name is not taken from requesting user",
      requesterSummary.containsKey("middleName"),
      is(false));

    assertThat("barcode is taken from requesting user",
      requesterSummary.getString("barcode"),
      is("6059539205"));

    assertThat("barcode is taken from requesting user",
      requesterSummary.getString("barcode"),
      is("6059539205"));

    assertThat(requesterSummary.containsKey("patronGroup"), is(true));
    assertThat(requesterSummary.getString("patronGroupId"), is(facultyGroupId));
    assertThat(requesterSummary.getJsonObject("patronGroup").getString("id"),
      is(facultyGroupId));

    assertThat("current loan is present",
      representation.containsKey("loan"), is(true));

    assertThat("current loan has a due date",
      representation.getJsonObject("loan").containsKey("dueDate"), is(true));

    //TODO: Improve this by checking actual date
    assertThat("current loan has non-null due date",
      representation.getJsonObject("loan").getString("dueDate"), notNullValue());

    assertThat(representation.containsKey("tags"), is(true));
    final JsonObject tagsRepresentation = representation.getJsonObject("tags");

    assertThat(tagsRepresentation.containsKey("tagList"), is(true));
    assertThat(tagsRepresentation.getJsonArray("tagList"), contains(NEW_TAG, IMPORTANT_TAG));

    requestHasCallNumberStringProperties(representation, "");
  }

  @Test
  public void canGetARequestToBeFulfilledByDeliveryToAnAddressById() {
    final IndividualResource smallAngryPlanet =
      itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::available);

    final IndividualResource workAddressType = addressTypesFixture.work();

    final IndividualResource charlotte = usersFixture.charlotte(
      builder -> builder.withAddress(
        new Address(workAddressType.getId(),
          "Fake first address line",
          "Fake second address line",
          "Fake city",
          "Fake region",
          "Fake postal code",
          "Fake country code")));

    final IndividualResource james = usersFixture.james();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource createdRequest = requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .by(charlotte)
      .deliverToAddress(workAddressType.getId()));

    JsonObject representation = requestsClient.getById(createdRequest.getId()).getJson();

    assertThat(representation.getString("id"), is(not(emptyString())));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("fulfilmentPreference"), is("Delivery"));
    assertThat(representation.getString("deliveryAddressTypeId"), is(workAddressType.getId()));

    assertThat("Request should have a delivery address",
      representation.containsKey("deliveryAddress"), is(true));

    final JsonObject deliveryAddress = representation.getJsonObject("deliveryAddress");

    assertThat(deliveryAddress.getString("addressTypeId"), is(workAddressType.getId()));
    assertThat(deliveryAddress.getString("addressLine1"), is("Fake first address line"));
    assertThat(deliveryAddress.getString("addressLine2"), is("Fake second address line"));
    assertThat(deliveryAddress.getString("city"), is("Fake city"));
    assertThat(deliveryAddress.getString("region"), is("Fake region"));
    assertThat(deliveryAddress.getString("postalCode"), is("Fake postal code"));
    assertThat(deliveryAddress.getString("countryId"), is("Fake country code"));
  }

  @Test
  public void requestNotFoundForUnknownId() {
    Response getResponse = requestsClient.getById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  public void canGetMultipleRequests() {
    final IndividualResource cd1 = servicePointsFixture.cd1();
    final IndividualResource cd2 = servicePointsFixture.cd2();
    UUID pickupServicePointId = cd1.getId();
    UUID pickupServicePointId2 = cd2.getId();

    UUID facultyGroupId = patronGroupsFixture.faculty().getId();
    UUID staffGroupId = patronGroupsFixture.staff().getId();

    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource charlotte = usersFixture.charlotte();

    final IndividualResource sponsor = usersFixture.rebecca(user -> user
      .withPatronGroupId(facultyGroupId));

    final IndividualResource proxy = usersFixture.steve(user -> user
      .withPatronGroupId(staffGroupId));

    UUID proxyId = proxy.getId();
    UUID requesterId = sponsor.getId();

    proxyRelationshipsFixture.nonExpiringProxyFor(sponsor, proxy);

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource nod = itemsFixture
      .basedUponNod(itemsFixture.addCallNumberStringComponents("nod"));
    final IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    final IndividualResource temeraire = itemsFixture
      .basedUponTemeraire(itemsFixture.addCallNumberStringComponents("tem"));
    final IndividualResource uprooted = itemsFixture.basedUponUprooted();

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);
    loansFixture.checkOutByBarcode(nod, charlotte);
    loansFixture.checkOutByBarcode(interestingTimes, charlotte);
    loansFixture.checkOutByBarcode(temeraire, jessica);
    loansFixture.checkOutByBarcode(uprooted, jessica);

    requestsClient.create(new RequestBuilder()
      .hold()
      .withItemId(smallAngryPlanet.getId())
      .withRequesterId(requesterId)
      .withUserProxyId(proxyId)
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList(NEW_TAG, IMPORTANT_TAG))));

    requestsClient.create(new RequestBuilder()
      .hold()
      .withItemId(nod.getId())
      .withRequesterId(requesterId)
      .withUserProxyId(proxyId)
      .withPickupServicePointId(pickupServicePointId2)
      .withTags(new RequestBuilder.Tags(asList(NEW_TAG, IMPORTANT_TAG))));

    requestsClient.create(new RequestBuilder()
      .hold()
      .withItemId(interestingTimes.getId())
      .withRequesterId(requesterId)
      .withUserProxyId(proxyId)
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList(NEW_TAG, IMPORTANT_TAG))));

    requestsClient.create(new RequestBuilder()
      .hold()
      .withItemId(temeraire.getId())
      .withRequesterId(requesterId)
      .withUserProxyId(proxyId)
      .withPickupServicePointId(pickupServicePointId2)
      .withTags(new RequestBuilder.Tags(asList(NEW_TAG, IMPORTANT_TAG))));

    requestsClient.create(new RequestBuilder()
      .recall()
      .withItemId(uprooted.getId())
      .withRequesterId(requesterId)
      .withUserProxyId(proxyId)
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList(NEW_TAG, IMPORTANT_TAG))));

    MultipleJsonRecords requestList = requestsFixture.getAllRequests();

    requestList.forEach(this::requestHasExpectedProperties);
    requestList.forEach(this::requestHasExpectedLoanProperties);
    requestList.forEach(this::requestHasServicePointProperties);
    requestList.forEach(this::requestHasPatronGroupProperties);
    requestList.forEach(this::requestHasTags);

    requestHasCallNumberStringProperties(
      findRequestByItemId(requestList.stream(), nod.getId()), "nod");

    requestHasCallNumberStringProperties(
      findRequestByItemId(requestList.stream(), temeraire.getId()), "tem");
  }

  @Test
  public void fulfilledByDeliveryIncludesAddressWhenFindingMultipleRequests() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource workAddressType = addressTypesFixture.work();

    final IndividualResource charlotte = usersFixture.charlotte(
      builder -> builder.withAddress(
        new Address(workAddressType.getId(),
          "Fake first address line",
          "Fake second address line",
          "Fake city",
          "Fake region",
          "Fake postal code",
          "Fake country code")));

    final IndividualResource james = usersFixture.james();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .deliverToAddress(workAddressType.getId())
      .by(charlotte));

    final List<JsonObject> allRequests = requestsClient.getAll();

    assertThat(allRequests.size(), is(1));

    JsonObject representation = allRequests.get(0);

    assertThat(representation.getString("id"), is(not(emptyString())));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("fulfilmentPreference"), is("Delivery"));
    assertThat(representation.getString("deliveryAddressTypeId"), is(workAddressType.getId()));

    assertThat("Request should have a delivery address",
      representation.containsKey("deliveryAddress"), is(true));

    final JsonObject deliveryAddress = representation.getJsonObject("deliveryAddress");

    assertThat(deliveryAddress.getString("addressTypeId"), is(workAddressType.getId()));
    assertThat(deliveryAddress.getString("addressLine1"), is("Fake first address line"));
    assertThat(deliveryAddress.getString("addressLine2"), is("Fake second address line"));
    assertThat(deliveryAddress.getString("city"), is("Fake city"));
    assertThat(deliveryAddress.getString("region"), is("Fake region"));
    assertThat(deliveryAddress.getString("postalCode"), is("Fake postal code"));
    assertThat(deliveryAddress.getString("countryId"), is("Fake country code"));
  }

  @Test
  public void closedLoanForItemIsNotIncludedWhenFindingMultipleRequests() {
    final IndividualResource smallAngryPlanet
      = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource charlotte = usersFixture.charlotte();

    final IndividualResource james = usersFixture.james();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    final IndividualResource workAddressType = addressTypesFixture.work();

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .deliverToAddress(workAddressType.getId())
      .by(charlotte));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    final List<JsonObject> allRequests = requestsClient.getAll();

    assertThat(allRequests.size(), is(1));

    JsonObject representation = allRequests.get(0);

    assertThat("Request should not have a current loan for the item",
      representation.containsKey("deliveryAddress"), is(false));
  }

  @Test
  public void canPageAllRequests() {
    UUID requesterId = usersFixture.charlotte().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponNod(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponInterestingTimes(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponNod(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponUprooted(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    MultipleJsonRecords firstPage = requestsFixture.getRequests(noQuery(),
      limit(4), noOffset());

    MultipleJsonRecords secondPage = requestsFixture.getRequests(noQuery(),
      limit(4), offset(4));

    assertThat(firstPage.size(), is(4));
    assertThat(firstPage.totalRecords(), is(7));

    assertThat(secondPage.size(), is(3));
    assertThat(secondPage.totalRecords(), is(7));

    firstPage.forEach(this::requestHasExpectedProperties);
    secondPage.forEach(this::requestHasExpectedProperties);
  }

  @Test
  public void canSearchForRequestsByRequesterLastName()
    throws
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID firstRequester = usersFixture.steve().getId();
    UUID secondRequester = usersFixture.jessica().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::checkOut).getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(firstRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(firstRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponInterestingTimes(ItemBuilder::checkOut).getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(secondRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(firstRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(firstRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponUprooted(ItemBuilder::checkOut).getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(secondRequester));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(secondRequester));

    CompletableFuture<Response> getRequestsCompleted = new CompletableFuture<>();

    client.get(requestsUrl() + format("?query=requester.lastName=%s", "Pontefract"), any(getRequestsCompleted));

    Response getRequestsResponse = getRequestsCompleted.get(5, TimeUnit.SECONDS);

    assertThat(format("Failed to get requests: %s",
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
    throws
    InterruptedException,
    ExecutionException,
    TimeoutException {

    final IndividualResource sponsor = usersFixture.jessica();

    final IndividualResource firstProxy = usersFixture.charlotte();
    final IndividualResource secondProxy = usersFixture.james();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    proxyRelationshipsFixture.currentProxyFor(sponsor, firstProxy);
    proxyRelationshipsFixture.currentProxyFor(sponsor, secondProxy);

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .by(sponsor)
      .proxiedBy(firstProxy));

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponNod(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .by(sponsor)
      .proxiedBy(secondProxy));

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponNod(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .by(sponsor)
      .proxiedBy(secondProxy));

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponUprooted(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .by(sponsor)
      .proxiedBy(firstProxy));

    requestsClient.create(new RequestBuilder()
      .forItem(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .by(sponsor)
      .proxiedBy(secondProxy));

    CompletableFuture<Response> getRequestsCompleted = new CompletableFuture<>();

    client.get(requestsUrl() + format("?query=proxy.lastName=%s", "Rodwell"), any(getRequestsCompleted));

    Response getRequestsResponse = getRequestsCompleted.get(5, TimeUnit.SECONDS);

    assertThat(format("Failed to get requests: %s",
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
    throws
    InterruptedException,
    ExecutionException,
    TimeoutException {

    UUID requesterId = usersFixture.charlotte().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::checkOut).getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponInterestingTimes(ItemBuilder::checkOut).getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponNod(ItemBuilder::checkOut).getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponUprooted(ItemBuilder::checkOut).getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsClient.create(new RequestBuilder()
      .withItemId(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut).getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    CompletableFuture<Response> getRequestsCompleted = new CompletableFuture<>();

    client.get(requestsUrl() + "?query=item.title=Nod", any(getRequestsCompleted));

    Response getRequestsResponse = getRequestsCompleted.get(5, TimeUnit.SECONDS);

    assertThat(format("Failed to get requests: %s",
      getRequestsResponse.getBody()),
      getRequestsResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject wrappedRequests = getRequestsResponse.getJson();

    List<JsonObject> requests = getRequests(wrappedRequests);

    assertThat(requests.size(), is(2));
    assertThat(wrappedRequests.getInteger("totalRecords"), is(2));

    requests.forEach(this::requestHasExpectedProperties);
  }

  private List<JsonObject> getRequests(JsonObject page) {
    System.out.println("Found requests");
    System.out.println(page.getJsonArray("requests").encodePrettily());
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

  private void requestHasExpectedLoanProperties(JsonObject request) {
    hasProperty("dueDate", request.getJsonObject("loan"), "loan");
  }

  private void requestHasServicePointProperties(JsonObject request) {
    hasProperty("pickupServicePointId", request, "request");
    hasProperty("pickupServicePoint", request, "request");
    hasProperty("name", request.getJsonObject("pickupServicePoint"),
      "pickupServicePoint");
  }

  private void requestHasPatronGroupProperties(JsonObject request) {
    hasProperty("proxy", request, "proxy");

    hasProperty("patronGroupId", request.getJsonObject("proxy"), "proxy");
    hasProperty("patronGroup", request.getJsonObject("proxy"), "patronGroup");
    hasProperty("id", request.getJsonObject("proxy").getJsonObject("patronGroup"), "id");
    hasProperty("group", request.getJsonObject("proxy").getJsonObject("patronGroup"), "group");
    hasProperty("desc", request.getJsonObject("proxy").getJsonObject("patronGroup"), "desc");

    hasProperty("requester", request, "requester");

    hasProperty("patronGroupId", request.getJsonObject("requester"), "requester");
    hasProperty("patronGroup", request.getJsonObject("requester"), "patronGroup");
    hasProperty("id", request.getJsonObject("requester").getJsonObject("patronGroup"), "id");
    hasProperty("group", request.getJsonObject("requester").getJsonObject("patronGroup"), "desc");
    hasProperty("desc", request.getJsonObject("requester").getJsonObject("patronGroup"), "group");
  }

  private void requestHasTags(JsonObject request) {
    hasProperty("tags", request, "tags");
    hasProperty("tagList", request.getJsonObject("tags"), "List");
  }

  protected void hasProperty(String property, JsonObject resource, String type) {
    assertThat(format("%s should have %s: %s: is missing outer property",
      type, property, resource),
      resource, notNullValue());

    assertThat(format("%s should have %s: %s",
      type, property, resource),
      resource.containsKey(property), is(true));
  }

  private void requestHasCallNumberStringProperties(JsonObject request, String prefix) {
    JsonObject item = request.getJsonObject("item");

    assertTrue(item.containsKey(CALL_NUMBER_COMPONENTS));
    JsonObject callNumberComponents = item.getJsonObject(CALL_NUMBER_COMPONENTS);

    assertThat(callNumberComponents.getString("callNumber"), is(prefix + "itCn"));
    assertThat(callNumberComponents.getString("prefix"), is(prefix + "itCnPrefix"));
    assertThat(callNumberComponents.getString("suffix"), is(prefix + "itCnSuffix"));

    assertThat(item.getString("enumeration"), is(prefix + "enumeration1"));
    assertThat(item.getString("chronology"), is(prefix + "chronology"));
    assertThat(item.getString("volume"), is(prefix + "vol.1"));
  }

  private JsonObject findRequestByItemId(Stream<JsonObject> requests, UUID itemId) {
    return requests
      .filter(req -> itemId.toString().equals(req.getString("itemId")))
      .findFirst()
      .orElseThrow(() -> new AssertionError("Can not find Request for item: "
        + itemId));
  }
}
