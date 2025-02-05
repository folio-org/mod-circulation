package api.requests;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.builders.Address;
import api.support.builders.ItemBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.fixtures.ConfigurationExample.newYorkTimezoneConfiguration;
import static api.support.http.CqlQuery.noQuery;
import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.http.Limit.limit;
import static api.support.http.Limit.noLimit;
import static api.support.http.Offset.noOffset;
import static api.support.http.Offset.offset;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.JsonObjectMatcher.hasNoJsonPath;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.UUIDMatcher.is;
import static java.lang.String.format;
import static java.time.ZoneOffset.UTC;
import static java.util.Arrays.asList;
import static java.util.function.Function.identity;
import static org.folio.circulation.domain.representations.ItemProperties.CALL_NUMBER_COMPONENTS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestsAPIRetrievalTests extends APITests {
  private static final String NEW_TAG = "new";
  private static final String IMPORTANT_TAG = "important";
  private static final String ONE_COPY_NUMBER = "1";

  @Test
  void canGetARequestById() {
    UUID facultyGroupId = patronGroupsFixture.faculty().getId();
    UUID staffGroupId = patronGroupsFixture.staff().getId();
    UUID isbnIdentifierId = identifierTypesFixture.isbn().getId();
    String isbnValue = "9780866989732";

    final ItemResource smallAngryPlanet = itemsFixture
      .basedUponSmallAngryPlanet(
        identity(),
        instanceBuilder -> instanceBuilder.addIdentifier(isbnIdentifierId, isbnValue),
        itemBuilder -> itemBuilder
          .withCallNumber("itCn", "itCnPrefix", "itCnSuffix")
          .withEnumeration("enumeration1")
          .withChronology("chronology")
          .withVolume("vol.1")
          .withCopyNumber(ONE_COPY_NUMBER));

    final IndividualResource sponsor = usersFixture.rebecca(user -> user
      .withPatronGroupId(facultyGroupId));

    final IndividualResource proxy = usersFixture.steve(user -> user
      .withPatronGroupId(staffGroupId));

    final IndividualResource cd1 = servicePointsFixture.cd1();

    UUID pickupServicePointId = cd1.getId();

    proxyRelationshipsFixture.nonExpiringProxyFor(sponsor, proxy);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);

    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    final IndividualResource createdRequest = requestsFixture.place(
      new RequestBuilder()
        .recall()
        .itemRequestLevel()
        .withRequestDate(requestDate)
        .forItem(smallAngryPlanet)
        .withInstanceId(smallAngryPlanet.getInstanceId())
        .by(sponsor)
        .proxiedBy(proxy)
        .fulfillToHoldShelf()
        .withRequestExpiration(LocalDate.of(2017, 7, 30))
        .withHoldShelfExpiration(LocalDate.of(2017, 8, 31))
        .withPickupServicePointId(pickupServicePointId)
        .withTags(new RequestBuilder.Tags(asList(NEW_TAG, IMPORTANT_TAG)))
        .withPatronComments("I need the book"));

    Response getResponse = requestsFixture.getById(createdRequest.getId());

    assertThat(format("Failed to get request: %s", getResponse.getBody()),
      getResponse.getStatusCode(), is(HttpURLConnection.HTTP_OK));

    JsonObject representation = getResponse.getJson();

    assertThat(representation.getString("id"), is(createdRequest.getId()));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("requestLevel"), is("Item"));
    assertThat(representation.getString("requestDate"), isEquivalentTo(requestDate));
    assertThat(representation.getString("itemId"), is(smallAngryPlanet.getId()));
    assertThat(representation.getString("holdingsRecordId"), is(smallAngryPlanet.getHoldingsRecordId()));
    assertThat(representation.getString("instanceId"), is(smallAngryPlanet.getInstanceId()));
    assertThat(representation.getString("requesterId"), is(sponsor.getId()));
    assertThat(representation.getString("fulfillmentPreference"), is("Hold Shelf"));
    assertThat(representation.getString("requestExpirationDate"), is("2017-07-30T23:59:59.000Z"));
    assertThat(representation.getString("holdShelfExpirationDate"), is("2017-08-31"));
    assertThat(representation.getString("patronComments"), is("I need the book"));
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
    assertThat("barcode is taken from item",
      itemSummary.getString("barcode"), is("036000291452"));

    assertThat(itemSummary.getString("status"), is(CHECKED_OUT));

    assertThat(itemSummary.containsKey("copyNumber"), is(true));

    assertThat(itemSummary.getString("copyNumber"),
      is(ONE_COPY_NUMBER));

    final JsonObject instanceSummary = representation.getJsonObject("instance");
    assertThat("title is taken from instance",
      instanceSummary.getString("title"), is("The Long Way to a Small, Angry Planet"));

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

    JsonArray identifiers = instanceSummary.getJsonArray("identifiers");
    assertThat(identifiers, CoreMatchers.notNullValue());
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers.getJsonObject(0).getString("identifierTypeId"),
      is(isbnIdentifierId.toString()));
    assertThat(identifiers.getJsonObject(0).getString("value"),
      is(isbnValue));
    JsonArray contributors = instanceSummary.getJsonArray("contributorNames");
    assertThat(contributors, CoreMatchers.notNullValue());
    assertThat(contributors.size(), is(1));
    assertThat(contributors.getJsonObject(0).getString("name"), is("Chambers, Becky"));

    JsonArray editions = instanceSummary.getJsonArray("editions");
    assertThat(editions, notNullValue());
    assertThat(editions.size(), is(1));
    assertThat(editions.getString(0), is("First American Edition"));

    JsonArray publication = instanceSummary.getJsonArray("publication");
    assertThat(publication, notNullValue());
    assertThat(publication.size(), is(1));
    JsonObject firstPublication = publication.getJsonObject(0);
    assertThat(firstPublication.getString("publisher"), is("Alfred A. Knopf"));
    assertThat(firstPublication.getString("place"), is("New York"));
    assertThat(firstPublication.getString("dateOfPublication"), is("2016"));
  }

  @Test
  void titleLevelRequestRetrievalById() {
    settingsFixture.enableTlrFeature();

    UUID isbnIdentifierId = identifierTypesFixture.isbn().getId();
    String isbnValue = "9780866989427";
    ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      identity(),
      instanceBuilder -> instanceBuilder.addIdentifier(isbnIdentifierId, isbnValue),
      itemsFixture.addCallNumberStringComponents());
    checkOutFixture.checkOutByBarcode(smallAngryPlanet);

    UUID requesterId = usersFixture.steve().getId();
    ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    UUID instanceId = smallAngryPlanet.getInstanceId();

    IndividualResource requestResource = requestsClient.create(new RequestBuilder()
      .hold()
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withPickupServicePointId(pickupServicePointId)
      .withRequestDate(requestDate)
      .withRequesterId(requesterId));

    Response response = requestsFixture.getById(UUID.fromString(requestResource.getJson().getString("id")));
    JsonObject representation = response.getJson();

    assertThat(representation.getString("id"), is(requestResource.getJson().getString("id")));
    assertThat(representation.getString("requestType"), is("Hold"));
    assertThat(representation.getString("requestLevel"), is("Title"));

    final JsonObject instanceSummary = representation.getJsonObject("instance");
    assertThat("title is taken from instance",
      instanceSummary.getString("title"), is("The Long Way to a Small, Angry Planet"));

    JsonArray identifiers = instanceSummary.getJsonArray("identifiers");
    assertThat(identifiers, CoreMatchers.notNullValue());
    assertThat(identifiers.size(), is(1));
    assertThat(identifiers.getJsonObject(0).getString("identifierTypeId"),
      is(isbnIdentifierId.toString()));
    assertThat(identifiers.getJsonObject(0).getString("value"),
      is(isbnValue));

    JsonArray contributors = instanceSummary.getJsonArray("contributorNames");
    assertThat(contributors, CoreMatchers.notNullValue());
    assertThat(contributors.size(), is(1));
    assertThat(contributors.getJsonObject(0).getString("name"), is("Chambers, Becky"));

    JsonArray editions = instanceSummary.getJsonArray("editions");
    assertThat(editions, notNullValue());
    assertThat(editions.size(), is(1));
    assertThat(editions.getString(0), is("First American Edition"));

    JsonArray publication = instanceSummary.getJsonArray("publication");
    assertThat(publication, notNullValue());
    assertThat(publication.size(), is(1));
    JsonObject firstPublication = publication.getJsonObject(0);
    assertThat(firstPublication.getString("publisher"), is("Alfred A. Knopf"));
    assertThat(firstPublication.getString("place"), is("New York"));
    assertThat(firstPublication.getString("dateOfPublication"), is("2016"));
  }

  @Test
  void canGetARequestToBeFulfilledByDeliveryToAnAddressById() {
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

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource createdRequest = requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .by(charlotte)
      .deliverToAddress(workAddressType.getId()));

    JsonObject representation = requestsFixture.getById(createdRequest.getId()).getJson();

    assertThat(representation.getString("id"), is(not(emptyString())));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("fulfillmentPreference"), is("Delivery"));
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
  void requestNotFoundForUnknownId() {
    Response getResponse = requestsFixture.getById(UUID.randomUUID());

    assertThat(getResponse.getStatusCode(), is(HttpURLConnection.HTTP_NOT_FOUND));
  }

  @Test
  void canGetMultipleRequests() {
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

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);
    checkOutFixture.checkOutByBarcode(nod, charlotte);
    checkOutFixture.checkOutByBarcode(interestingTimes, charlotte);
    checkOutFixture.checkOutByBarcode(temeraire, jessica);
    checkOutFixture.checkOutByBarcode(uprooted, jessica);

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withRequesterId(requesterId)
      .withUserProxyId(proxyId)
      .withPickupServicePointId(pickupServicePointId)
      .withTags(new RequestBuilder.Tags(asList(NEW_TAG, IMPORTANT_TAG))));

    final IndividualResource requestForNod = requestsFixture.place(
      new RequestBuilder()
        .hold()
        .forItem(nod)
        .withRequesterId(requesterId)
        .withUserProxyId(proxyId)
        .withPickupServicePointId(pickupServicePointId2)
        .withPatronComments("Comment 2")
        .withTags(new RequestBuilder.Tags(asList(NEW_TAG, IMPORTANT_TAG))));

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(interestingTimes)
      .withRequesterId(requesterId)
      .withUserProxyId(proxyId)
      .withPickupServicePointId(pickupServicePointId)
      .withPatronComments("Comment 3")
      .withTags(new RequestBuilder.Tags(asList(NEW_TAG, IMPORTANT_TAG))));

    final IndividualResource requestForTemeraire = requestsFixture.place(
      new RequestBuilder()
        .hold()
        .forItem(temeraire)
        .withRequesterId(requesterId)
        .withUserProxyId(proxyId)
        .withPickupServicePointId(pickupServicePointId2)
        .withPatronComments("Comment 4")
        .withTags(new RequestBuilder.Tags(asList(NEW_TAG, IMPORTANT_TAG))));

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(uprooted)
      .withRequesterId(requesterId)
      .withUserProxyId(proxyId)
      .withPickupServicePointId(pickupServicePointId)
      .withPatronComments("Comment 5")
      .withTags(new RequestBuilder.Tags(asList(NEW_TAG, IMPORTANT_TAG))));

    MultipleJsonRecords requests = requestsFixture.getAllRequests();

    requests.forEach(this::requestHasExpectedProperties);
    requests.forEach(this::requestHasExpectedLoanProperties);
    requests.forEach(this::requestHasServicePointProperties);
    requests.forEach(this::requestHasPatronGroupProperties);
    requests.forEach(this::requestHasTags);

    requestHasCallNumberStringProperties(requests.getById(
      requestForNod.getId()), "nod");

    requestHasCallNumberStringProperties(requests.getById(
      requestForTemeraire.getId()), "tem");

    assertThatRequestsHavePatronComments(requests);
  }

  @Test
  void fulfilledByDeliveryIncludesAddressWhenFindingMultipleRequests() {
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

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .deliverToAddress(workAddressType.getId())
      .by(charlotte));

    final MultipleJsonRecords allRequests = requestsFixture.getAllRequests();

    assertThat(allRequests.size(), is(1));

    JsonObject representation = allRequests.getFirst();

    assertThat(representation.getString("id"), is(not(emptyString())));
    assertThat(representation.getString("requestType"), is("Recall"));
    assertThat(representation.getString("fulfillmentPreference"), is("Delivery"));
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
  void closedLoanForItemIsNotIncludedWhenFindingMultipleRequests() {
    final IndividualResource smallAngryPlanet
      = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource charlotte = usersFixture.charlotte();

    final IndividualResource james = usersFixture.james();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    final IndividualResource workAddressType = addressTypesFixture.work();

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .deliverToAddress(workAddressType.getId())
      .by(charlotte));

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    final MultipleJsonRecords allRequests = requestsFixture.getAllRequests();

    assertThat(allRequests.size(), is(1));

    JsonObject representation = allRequests.getFirst();

    //TODO: Figure out if this check makes sense?
    assertThat("Request should not have a current loan for the item",
      representation.containsKey("deliveryAddress"), is(false));
  }

  @Test
  void canPageAllRequests() {
    UUID requesterId = usersFixture.charlotte().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponNod(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponInterestingTimes(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponNod(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponUprooted(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsFixture.place(new RequestBuilder()
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
  void canSearchForRequestsByRequesterLastName() {
    UUID firstRequester = usersFixture.steve().getId();
    UUID secondRequester = usersFixture.jessica().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(firstRequester));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponNod(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(firstRequester));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponInterestingTimes(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(secondRequester));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(firstRequester));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponNod(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(firstRequester));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponUprooted(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(secondRequester));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(secondRequester));

    final MultipleJsonRecords requests = requestsFixture.getRequests(
      queryFromTemplate("requester.lastName=%s", "Pontefract"),
      noLimit(), noOffset());

    assertThat(requests.size(), is(3));
    assertThat(requests.totalRecords(), is(3));

    requests.forEach(this::requestHasExpectedProperties);
  }

  @Test
  void canSearchForRequestsByProxyLastName() {
    final IndividualResource sponsor = usersFixture.jessica();

    final IndividualResource firstProxy = usersFixture.charlotte();
    final IndividualResource secondProxy = usersFixture.james();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    proxyRelationshipsFixture.currentProxyFor(sponsor, firstProxy);
    proxyRelationshipsFixture.currentProxyFor(sponsor, secondProxy);

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .by(sponsor)
      .proxiedBy(firstProxy));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponNod(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .by(sponsor)
      .proxiedBy(secondProxy));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponNod(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .by(sponsor)
      .proxiedBy(secondProxy));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponUprooted(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .by(sponsor)
      .proxiedBy(firstProxy));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .by(sponsor)
      .proxiedBy(secondProxy));

    final MultipleJsonRecords requests = requestsFixture.getRequests(
      queryFromTemplate("proxy.lastName=%s", "Rodwell"),
      noLimit(), noOffset());

    assertThat(requests.size(), is(3));
    assertThat(requests.totalRecords(), is(3));

    requests.forEach(this::requestHasExpectedProperties);
  }

  @Test
  void canSearchForRequestsByInstanceTitle() {
    UUID requesterId = usersFixture.charlotte().getId();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponSmallAngryPlanet(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponNod(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponInterestingTimes(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponNod(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponUprooted(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsFixture.place(new RequestBuilder()
      .forItem(itemsFixture.basedUponTemeraire(ItemBuilder::checkOut))
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    final MultipleJsonRecords requests = requestsFixture.getRequests(
      queryFromTemplate("instance.title=%s", "Nod"),
      noLimit(), noOffset());

    assertThat(requests.size(), is(2));
    assertThat(requests.totalRecords(), is(2));

    requests.forEach(this::requestHasExpectedProperties);
  }

  @Test
  void requestExpirationDateShouldStoreInTenantsTimeZone() {
    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource sponsor = usersFixture.rebecca(user -> user
      .withPatronGroupId(patronGroupsFixture.faculty().getId()));
    final IndividualResource proxy = usersFixture.steve(user -> user
      .withPatronGroupId(patronGroupsFixture.staff().getId()));
    final IndividualResource cd1 = servicePointsFixture.cd1();
    UUID pickupServicePointId = cd1.getId();

    proxyRelationshipsFixture.nonExpiringProxyFor(sponsor, proxy);
    checkOutFixture.checkOutByBarcode(smallAngryPlanet);
    configClient.create(newYorkTimezoneConfiguration());

    final IndividualResource createdRequest = requestsFixture.place(
      new RequestBuilder()
        .withRequestDate(ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC))
        .forItem(smallAngryPlanet)
        .by(sponsor)
        .proxiedBy(proxy)
        .fulfillToHoldShelf()
        .withRequestExpiration(LocalDate.of(2017, 7, 31))
        .withHoldShelfExpiration(LocalDate.of(2017, 8, 31))
        .withPickupServicePointId(pickupServicePointId)
        .withTags(new RequestBuilder.Tags(asList(NEW_TAG, IMPORTANT_TAG)))
        .withPatronComments("I need the book"));

    Response response = requestsFixture.getById(createdRequest.getId());

    assertThat(format("Failed to get request: %s", response.getBody()),
      response.getStatusCode(), is(HttpURLConnection.HTTP_OK));
    assertThat(response.getJson().getString("requestExpirationDate"), is("2017-07-31T03:59:59.000Z"));
  }

  private void requestHasExpectedProperties(JsonObject request) {
    hasProperty("id", request, "request");
    hasProperty("requestType", request, "request");
    hasProperty("requestDate", request, "request");
    hasProperty("requesterId", request, "request");
    hasProperty("itemId", request, "request");
    hasProperty("instanceId", request, "request");
    hasProperty("holdingsRecordId", request, "request");
    hasProperty("fulfillmentPreference", request, "request");
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
      type, property, resource), resource, notNullValue());

    assertThat(format("%s should have %s: %s",
      type, property, resource), resource.containsKey(property), is(true));
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

  @SuppressWarnings("unchecked")
  private void assertThatRequestsHavePatronComments(MultipleJsonRecords requests) {
    assertThat(requests, containsInAnyOrder(
      hasNoJsonPath("patronComments"),
      hasJsonPath("patronComments", "Comment 2"),
      hasJsonPath("patronComments", "Comment 3"),
      hasJsonPath("patronComments", "Comment 4"),
      hasJsonPath("patronComments", "Comment 5")
    ));
  }


  @Test
  void printDetailsTest() {
    UserResource printDetailsRequester = usersFixture.charlotte();
    UUID printDetailsRequesterId = printDetailsRequester.getId();
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

    requestsFixture.place(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .deliverToAddress(workAddressType.getId())
      .by(charlotte)
      .withPrintDetails(new RequestBuilder
        .PrintDetails(49, printDetailsRequesterId.toString(),
        true, "2024-09-16T11:58:22.295+00:00")));

    final MultipleJsonRecords requests = requestsFixture.getRequests(
      queryFromTemplate("printDetails.isPrinted==%s", "true"),
      noLimit(), noOffset());

    assertThat(requests.size(), is(1));
    assertThat(requests.totalRecords(), is(1));

    JsonObject printDetails = requests.getFirst().getJsonObject("printDetails");
    assertThat(printDetails.getInteger("printCount"), is(49));
    assertThat(printDetails.getString("requesterId"),
      is(printDetailsRequesterId.toString()));
    assertTrue(printDetails.getBoolean("isPrinted"));

    JsonObject lastPrintRequester = printDetails.getJsonObject(
      "lastPrintRequester");
    JsonObject expectedLastPrintRequester = printDetailsRequester.getJson()
      .getJsonObject("personal");

    assertThat(lastPrintRequester.getString("firstName"),
      is(expectedLastPrintRequester.getString("firstName")));
    assertThat(lastPrintRequester.getString("lastName"),
      is(expectedLastPrintRequester.getString("lastName")));
  }
}
