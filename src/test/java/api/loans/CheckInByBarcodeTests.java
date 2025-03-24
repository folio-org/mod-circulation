package api.loans;

import static api.support.APITestContext.getUserId;
import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static api.support.Wait.waitAtLeast;
import static api.support.builders.ItemBuilder.INTELLECTUAL_ITEM;
import static api.support.fakes.PublishedEvents.byEventType;
import static api.support.fakes.PublishedEvents.byLogEventType;
import static api.support.fixtures.AddressExamples.SiriusBlack;
import static api.support.fixtures.TemplateContextMatchers.getLoanAdditionalInfoContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getUserContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.isPreferredName;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasItemBarcodeParameter;
import static api.support.matchers.EventMatchers.doesNotContainUserBarcode;
import static api.support.matchers.EventMatchers.isValidCheckInLogEvent;
import static api.support.matchers.EventMatchers.isValidItemCheckedInEvent;
import static api.support.matchers.ItemMatchers.isAvailable;
import static api.support.matchers.ItemMatchers.isAwaitingPickup;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.OverdueFineMatcher.isValidOverdueFine;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.RequestMatchers.hasPosition;
import static api.support.matchers.RequestMatchers.isOpenAwaitingPickup;
import static api.support.matchers.RequestMatchers.isOpenNotYetFilled;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsBeforeNow;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.utl.PatronNoticeTestHelper.clearSentPatronNoticesAndPubsubEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfPublishedEvents;
import static api.support.utl.PatronNoticeTestHelper.verifyNumberOfSentNotices;
import static java.time.ZoneOffset.UTC;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.domain.EventType.ITEM_CHECKED_IN;
import static org.folio.circulation.domain.RequestStatus.CLOSED_CANCELLED;
import static org.folio.circulation.domain.RequestStatus.CLOSED_PICKUP_EXPIRED;
import static org.folio.circulation.domain.RequestStatus.CLOSED_UNFILLED;
import static org.folio.circulation.domain.RequestType.HOLD;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.domain.policy.ExpirationDateManagement.KEEP_THE_CURRENT_DUE_DATE;
import static org.folio.circulation.domain.representations.logs.LogEventType.CHECK_IN;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE_ERROR;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.collection.ArrayMatching.arrayContainingInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import api.support.builders.ServicePointBuilder;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.CheckInByBarcodeResponse;
import api.support.MultipleJsonRecords;
import api.support.TlrFeatureStatus;
import api.support.builders.AddInfoRequestBuilder;
import api.support.builders.Address;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.FeeFineBuilder;
import api.support.builders.FeeFineOwnerBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.OverdueFinePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.fakes.FakeModNotify;
import api.support.fakes.FakePubSub;
import api.support.fixtures.TemplateContextMatchers;
import api.support.http.CheckOutResource;
import api.support.http.CqlQuery;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import api.support.matchers.RequestMatchers;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.val;

class CheckInByBarcodeTests extends APITests {
  private static final String CURRENT_DATE_TIME = "currentDateTime";
  private final static String HOLD_SHELF = "Hold Shelf";
  private final static String DELIVERY = "Delivery";
  private final static String OPEN_NOT_YET_FILLED = "Open - Not yet filled";
  private final static String OPEN_AWAITING_PICKUP = "Open - Awaiting pickup";
  private final static String OPEN_AWAITING_DELIVERY = "Open - Awaiting delivery";
  private static final String LOAN_INFO_ADDED = "testing patron info";

  public CheckInByBarcodeTests() {
    super(true, true);
  }

  @Test
  void canCloseAnOpenLoanByCheckingInTheItem() {
    final IndividualResource james = usersFixture.james();

    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      item -> item
        .withTemporaryLocation(homeLocation.getId())
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume")
        .withDisplaySummary("test displaySummary"));

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(nod, james,
      ZonedDateTime.of(2018, 3, 1, 13, 25, 46, 0, UTC));

    ZonedDateTime expectedSystemReturnDate = ClockUtil.getZonedDateTime();

    final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(ZonedDateTime.of(2018, 3, 5, 14 ,23, 41, 0, UTC))
        .at(checkInServicePointId));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("Closed loan should be present",
      loanRepresentation, notNullValue());

    assertThat(loanRepresentation.getString("userId"), is(james.getId().toString()));

    assertThat("Should have return date",
      loanRepresentation.getString("returnDate"), is("2018-03-05T14:23:41.000Z"));

    assertThat("Should have system return date similar to now",
      loanRepresentation.getString("systemReturnDate"),
      is(withinSecondsAfter(10, expectedSystemReturnDate)));

    assertThat("status is not closed",
      loanRepresentation.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("action is not checkedin",
      loanRepresentation.getString("action"), is("checkedin"));

    assertThat("ID should be included for item",
      loanRepresentation.getJsonObject("item").getString("id"), is(nod.getId()));

    assertThat("title is taken from item",
      loanRepresentation.getJsonObject("item").getString("title"),
      is("Nod"));

    assertThat("barcode is taken from item",
      loanRepresentation.getJsonObject("item").getString("barcode"),
      is("565578437802"));

    assertThat("Should not have snapshot of item status, as current status is included",
      loanRepresentation.containsKey("itemStatus"), is(false));

    assertThat("Response should include an item",
      checkInResponse.getJson().containsKey("item"), is(true));

    final JsonObject itemFromResponse = checkInResponse.getItem();

    assertThat("title is included for item",
      itemFromResponse.getString("title"), is("Nod"));

    assertThat("ID should be included for item",
      itemFromResponse.getString("id"), is(nod.getId()));

    assertThat("barcode is included for item",
      itemFromResponse.getString("barcode"), is("565578437802"));

    assertThat("has item enumeration",
      itemFromResponse.getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      itemFromResponse.getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item displaySummary",
      itemFromResponse.getString("displaySummary"), is("test displaySummary"));

    assertThat("has item volume",
      itemFromResponse.getString("volume"), is("testVolume"));

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("item status is not available",
      updatedNod.getJsonObject("status").getString("name"), is("Available"));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("stored loan status is not closed",
      storedLoan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("item status snapshot in storage is not Available",
      storedLoan.getString("itemStatus"), is("Available"));

    assertThat("Checkin Service Point Id should be stored.",
      storedLoan.getString("checkinServicePointId"), is(checkInServicePointId));

    verifyCheckInOperationRecorded(nod.getId(), checkInServicePointId);
  }

@Test
void verifyItemEffectiveLocationIdAtCheckOut() {
  final IndividualResource james = usersFixture.james();

  final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

  final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
    item -> item.withPrimaryServicePoint(checkInServicePointId));

  final String anotherLocationId = locationsFixture.thirdFloor().getId().toString();

  final IndividualResource nod = itemsFixture.basedUponNod(
    item -> item
      .withTemporaryLocation(homeLocation.getId()));

  checkOutFixture.checkOutByBarcode(nod, james);

  // Change the item's effective location to verify itemEffectiveLocationIdAtCheckOut is unchanged
  JsonObject update = nod.getJson();
  update.put("temporaryLocationId", anotherLocationId);
  itemsClient.replace(nod.getId(), update);

  final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
    new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .at(checkInServicePointId));

  JsonObject loanRepresentation = checkInResponse.getLoan();
  JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

  assertThat("New location should not equal old location",
    homeLocation.getId().toString(), not(anotherLocationId));
  assertThat("The item's temporary location ID should be updated",
    updatedNod.getString("temporaryLocationId"), is(anotherLocationId));
  assertThat("itemEffectiveLocationIdAtCheckOut should match the original location ID at checkout",
    loanRepresentation.getString("itemEffectiveLocationIdAtCheckOut"), is(homeLocation.getId().toString()));
}

  @Test
  void canCreateStaffSlipContextOnCheckInByBarcode() {
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();

    ZonedDateTime requestDate = ZonedDateTime.of(2019, 7, 22, 10, 22, 54, 0, UTC);
    IndividualResource servicePoint = servicePointsFixture.cd1();
    Address address = SiriusBlack();
    IndividualResource requester = usersFixture.steve(builder ->
      builder.withAddress(address));

    final var requestExpiration = java.time.LocalDate.of(2019, 7, 30);
    final var holdShelfExpiration = java.time.LocalDate.of(2019, 8, 31);
    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .withId(UUID.randomUUID())
      .open()
      .page()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfillToHoldShelf()
      .withRequestExpiration(requestExpiration)
      .withHoldShelfExpiration(holdShelfExpiration)
      .withPickupServicePointId(servicePoint.getId())
      .withDeliveryAddressType(addressTypesFixture.home().getId())
      .withPatronComments("I need the book")
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

    ZonedDateTime checkInDate = ZonedDateTime.of(2019, 7, 25, 14, 23, 41, 0, UTC);
    CheckInByBarcodeResponse response = checkInFixture.checkInByBarcode(item, checkInDate, servicePoint.getId());

    User requesterUser = new User(requester.getJson());
    JsonObject staffSlipContext = response.getStaffSlipContext();
    JsonObject userContext = staffSlipContext.getJsonObject("requester");
    JsonObject requestContext = staffSlipContext.getJsonObject("request");

    assertThat(userContext.getString("firstName"), is(requesterUser.getFirstName()));
    assertThat(userContext.getString("lastName"), is(requesterUser.getLastName()));
    assertThat(userContext.getString("preferredFirstName"), isPreferredName(requesterUser.getPersonal()));
    assertThat(userContext.getString("patronGroup"), is("Regular Group"));
    assertThat(userContext.getString("middleName"), is(requesterUser.getMiddleName()));
    assertThat(userContext.getString("barcode"), is(requesterUser.getBarcode()));
    assertThat(userContext.getString("addressLine1"), is(address.getAddressLineOne()));
    assertThat(userContext.getString("addressLine2"), is(address.getAddressLineTwo()));
    assertThat(userContext.getString("city"), is(address.getCity()));
    assertThat(userContext.getString("region"), is(address.getRegion()));
    assertThat(userContext.getString("postalCode"), is(address.getPostalCode()));
    assertThat(userContext.getString("countryId"), is(address.getCountryId()));
    assertThat(requestContext.getString("deliveryAddressType"), is(addressTypesFixture.home().getJson().getString("addressType")));
    assertThat(requestContext.getString("requestExpirationDate"), isEquivalentTo(
      ZonedDateTime.of(requestExpiration.atTime(23, 59, 59), ZoneOffset.UTC)));
    assertThat(requestContext.getString("holdShelfExpirationDate"), isEquivalentTo(
      ZonedDateTime.of(holdShelfExpiration.atStartOfDay(), ZoneOffset.UTC)));
    assertThat(requestContext.getString("requestID"), is(request.getId()));
    assertThat(requestContext.getString("servicePointPickup"), is(servicePoint.getJson().getString("name")));
    assertThat(requestContext.getString("patronComments"), is("I need the book"));
    assertNotNull(staffSlipContext.getString(CURRENT_DATE_TIME));
  }

  @Test
  void cannotCheckInItemThatCannotBeFoundByBarcode() {
    final Response response = checkInFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .withItemBarcode("543593485458")
        .on(ClockUtil.getZonedDateTime())
        .at(UUID.randomUUID()));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "No item with barcode 543593485458 exists")));
  }

  @Test
  void checkInFailsWhenEventPublishingFailsWithBadRequestError() {
    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 1, 13, 25, 46, 0, UTC);
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve, loanDate);

    FakePubSub.setFailPublishingWithBadRequestError(true);
    Response response = checkInFixture.attemptCheckInByBarcode(500,
      new CheckInByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .on(getZonedDateTime())
        .at(UUID.randomUUID()));

    assertThat(response.getBody(), containsString(
      "Error during publishing Event Message in PubSub. Status code: 400"));
  }

  @Test
  void cannotCheckInWithoutAServicePoint() {
    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 1, 13, 25, 46, 0, UTC);

    final IndividualResource james = usersFixture.james();
    final IndividualResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james, loanDate);

    final Response response = checkInFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(ClockUtil.getZonedDateTime())
        .atNoServicePoint());

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(hasMessage(
        "Checkin request must have a service point id")));
  }

  @Test
  void cannotCheckInWithoutAnItem() {
    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 1, 13, 25, 46, 0, UTC);

    final IndividualResource james = usersFixture.james();
    final IndividualResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james, loanDate);

    final Response response = checkInFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .noItem()
        .on(ClockUtil.getZonedDateTime())
        .at(UUID.randomUUID()));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "Checkin request must have an item barcode")));
  }

  @Test
  void cannotCheckInWithoutACheckInDate() {
    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 1, 13, 25, 46, 0, UTC);

    final IndividualResource james = usersFixture.james();
    final IndividualResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james, loanDate);

    final Response response = checkInFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .onNoOccasion()
        .at(UUID.randomUUID()));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "Checkin request must have an check in date")));
  }

  @Test
  void canCheckInAnDcbItem() {
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    IndividualResource instance = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(instance.getId());
    IndividualResource locationsResource = locationsFixture.mainFloor();
    var barcode = "100002222";
    var instanceTitle = "virtual title";
    final IndividualResource circulationItem = circulationItemsFixture.createCirculationItem(barcode, holdings.getId(), locationsResource.getId(), instanceTitle);
    final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(circulationItem, ZonedDateTime.now(), checkInServicePointId);

    assertThat("Response should include an item",
      checkInResponse.getJson().containsKey("item"), is(true));

    final JsonObject itemFromResponse = checkInResponse.getItem();

    assertThat("barcode is included for item",
      itemFromResponse.getString("barcode"), is(barcode));

    assertThat("item title should match dcb instance title",
      itemFromResponse.getString("title"), is(instanceTitle));
  }

  @Test
  void slipContainsLendingLibraryCodeForDcb() {
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    IndividualResource instance = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(instance.getId());
    IndividualResource locationsResource = locationsFixture.mainFloor();
    var barcode = "100002222";
    var lendingLibraryCode = "11223";
    final IndividualResource circulationItem = circulationItemsFixture.createCirculationItemWithLendingLibrary(barcode, holdings.getId(), locationsResource.getId(), lendingLibraryCode);

    final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(circulationItem, ZonedDateTime.now(), checkInServicePointId);
    JsonObject staffSlipContext = checkInResponse.getStaffSlipContext();
    JsonObject itemContext = staffSlipContext.getJsonObject("item");

    assertThat(itemContext.getString("effectiveLocationInstitution"), is(lendingLibraryCode));
  }

  @Test
  void canCheckInAnItemWithoutAnOpenLoan() {
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      item -> item
        .withTemporaryLocation(homeLocation.getId())
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume"));

    final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      nod, ZonedDateTime.of(2018, 3, 5, 14, 23, 41, 0, UTC),
      checkInServicePointId);

    assertThat("Response should not include a loan",
      checkInResponse.getJson().containsKey("loan"), is(false));

    assertThat("Response should include an item",
      checkInResponse.getJson().containsKey("item"), is(true));

    final JsonObject itemFromResponse = checkInResponse.getItem();

    assertThat("ID should be included for item",
      itemFromResponse.getString("id"), is(nod.getId()));

    assertThat("title is included for item",
      itemFromResponse.getString("title"), is("Nod"));

    assertThat("has item enumeration",
      itemFromResponse.getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      itemFromResponse.getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      itemFromResponse.getString("volume"), is("testVolume"));

    assertThat("barcode is included for item",
      itemFromResponse.getString("barcode"), is("565578437802"));
  }

  @Test
  void canCheckInAnItemTwice() {
    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 1, 13, 25, 46, 0, UTC);

    final IndividualResource james = usersFixture.james();

    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      item -> item
        .withTemporaryLocation(homeLocation.getId())
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume"));

    checkOutFixture.checkOutByBarcode(nod, james, loanDate);

    checkInFixture.checkInByBarcode(nod,
      ZonedDateTime.of(2018, 3, 5, 14, 23, 41, 0, UTC),
      checkInServicePointId);

    final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      nod, ZonedDateTime.of(2018, 3, 5, 14, 23, 41, 0, UTC),
      checkInServicePointId);

    assertThat("Response should not include a loan",
      checkInResponse.getJson().containsKey("loan"), is(false));

    assertThat("Response should include an item",
      checkInResponse.getJson().containsKey("item"), is(true));

    final JsonObject itemFromResponse = checkInResponse.getItem();

    assertThat("ID should be included for item",
      itemFromResponse.getString("id"), is(nod.getId()));

    assertThat("title is included for item",
      itemFromResponse.getString("title"), is("Nod"));

    assertThat("barcode is included for item",
      itemFromResponse.getString("barcode"), is("565578437802"));

    assertThat("has item enumeration",
      itemFromResponse.getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      itemFromResponse.getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      itemFromResponse.getString("volume"), is("testVolume"));
  }

  @Test
  void intellectualItemCannotBeCheckedIn() {
    final var checkInServicePointId = servicePointsFixture.cd1().getId();

    final var homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));

    final var nod = itemsFixture.basedUponNod(item -> item
      .intellectualItem()
      .withBarcode("10304054")
      .withTemporaryLocation(homeLocation));

    final var response = checkInFixture.attemptCheckInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .on(ZonedDateTime.of(2018, 3, 5, 14, 23, 41, 0, UTC))
      .at(checkInServicePointId));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Nod (Book) (Barcode: 10304054) has the item status Intellectual item and cannot be checked in"),
      hasItemBarcodeParameter(nod))));

    final var fetchedNod = itemsFixture.getById(nod.getId());

    assertThat(fetchedNod, hasItemStatus(INTELLECTUAL_ITEM));
  }

  @Test
  void patronNoticeOnCheckInIsNotSentWhenCheckInLoanNoticeIsDefinedAndLoanExists() {
    UUID checkInTemplateId = UUID.randomUUID();
    JsonObject checkOutNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(checkInTemplateId)
      .withCheckInEvent()
      .create();
    JsonObject renewNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withEventType("Renew")
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with checkout notice")
      .withLoanNotices(Arrays.asList(checkOutNoticeConfiguration, renewNoticeConfiguration));

    use(noticePolicy);

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 3, 1, 13, 25, 46, 0, UTC);

    final IndividualResource james = usersFixture.james();

    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));

    final ItemResource nod = itemsFixture.basedUponNod(
      builder -> builder.withTemporaryLocation(homeLocation.getId()));

    checkOutFixture.checkOutByBarcode(nod, james, loanDate);

    ZonedDateTime checkInDate = ZonedDateTime.of(2018, 3, 5, 14, 23, 41, 0, UTC);
    final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(checkInDate)
        .at(checkInServicePointId));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("Closed loan should be present",
      loanRepresentation, notNullValue());

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void shouldNotSendPatronNoticeWhenCheckInNoticeIsDefinedAndCheckInDoesNotCloseLoan() {
    UUID checkInTemplateId = UUID.randomUUID();
    JsonObject checkOutNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(checkInTemplateId)
      .withCheckInEvent()
      .create();
    JsonObject renewNoticeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withEventType("Renew")
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with checkout notice")
      .withLoanNotices(Arrays.asList(checkOutNoticeConfiguration, renewNoticeConfiguration));
    use(noticePolicy);

    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      builder -> builder.withTemporaryLocation(homeLocation.getId()));

    final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      nod, ZonedDateTime.of(2018, 3, 5, 14, 23, 41, 0, UTC),
      checkInServicePointId);

    assertThat("Response should not include a loan",
      checkInResponse.getJson().containsKey("loan"), is(false));

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void patronNoticeOnCheckInAfterCheckOutAndRequestToItem() {
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();

    CheckOutResource loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());
    addPatronInfoToLoan(loan.getId().toString());

    ZonedDateTime requestDate = ZonedDateTime.of(2019, 7, 22, 10, 22, 54, 0, UTC);
    UUID servicePointId = servicePointsFixture.cd1().getId();
    IndividualResource requester = usersFixture.steve();

    //recall request
    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .withId(UUID.randomUUID())
      .open()
      .recall()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfillToHoldShelf()
      .withRequestExpiration(LocalDate.of(2019, 7, 30))
      .withHoldShelfExpiration(LocalDate.of(2019, 8, 31))
      .withPickupServicePointId(servicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

    UUID availableNoticeTemplateId = UUID.randomUUID();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy notice")
      .withLoanNotices(Collections
        .singletonList(new NoticeConfigurationBuilder()
          .withTemplateId(availableNoticeTemplateId).withAvailableEvent().create()));

    use(noticePolicy);

    ZonedDateTime checkInDate = ZonedDateTime.of(2019, 7, 25, 14, 23, 41, 0, UTC);
    checkInFixture.checkInByBarcode(item, checkInDate, servicePointId);

    checkPatronNoticeEvent(request, requester, item, availableNoticeTemplateId, true);
  }

  @Test
  void patronNoticeOnCheckInAfterRequestToItem() {
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    ZonedDateTime requestDate = ZonedDateTime.of(2019, 5, 5, 10, 22, 54, 0, UTC);
    UUID servicePointId = servicePointsFixture.cd1().getId();
    IndividualResource requester = usersFixture.steve();

    // page request
    IndividualResource request = requestsFixture.place(new RequestBuilder()
      .withId(UUID.randomUUID())
      .open()
      .page()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfillToHoldShelf()
      .withRequestExpiration(LocalDate.of(2019, 5, 1))
      .withHoldShelfExpiration(LocalDate.of(2019, 6, 1))
      .withPickupServicePointId(servicePointId)
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

    UUID availableNoticeTemplateId = UUID.randomUUID();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy notice")
      .withLoanNotices(Collections
        .singletonList(new NoticeConfigurationBuilder()
          .withTemplateId(availableNoticeTemplateId).withAvailableEvent().create()));

    use(noticePolicy);

    checkInFixture.checkInByBarcode(item,
      ZonedDateTime.of(2019, 5, 10, 14, 23, 41, 0, UTC),
      servicePointId);

    checkPatronNoticeEvent(request, requester, item, availableNoticeTemplateId, false);
  }

  @Test
  void patronNoticeIsSentOnceWhenItemAndRequestStatusIsChangedToAwaitingPickup() {
    JsonObject availableNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withAvailableEvent()
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with available notice")
      .withLoanNotices(Collections.singletonList(availableNoticeConfig));

    use(noticePolicy);

    ItemResource requestedItem = itemsFixture.basedUponNod();
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    ZonedDateTime requestDate = ZonedDateTime.of(2019, 10, 9, 10, 0, 0, 0, UTC);
    requestsFixture.place(new RequestBuilder()
      .page()
      .forItem(requestedItem)
      .by(usersFixture.steve())
      .withPickupServicePointId(pickupServicePointId)
      .withRequestDate(requestDate));

    ZonedDateTime checkInDate = ZonedDateTime.of(2019, 10, 10, 12, 30, 0, 0, UTC);

    checkInFixture.checkInByBarcode(requestedItem, checkInDate, pickupServicePointId);

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    clearSentPatronNoticesAndPubsubEvents();

    //Check-in again and verify no notice are sent
    checkInFixture.checkInByBarcode(requestedItem, checkInDate, pickupServicePointId);

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void requestAwaitingPickupNoticeIsNotSentWhenUserWasNotFound() {
    JsonObject availableNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withAvailableEvent()
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with available notice")
      .withLoanNotices(Collections.singletonList(availableNoticeConfig));

    use(noticePolicy);

    ItemResource requestedItem = itemsFixture.basedUponNod();
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    ZonedDateTime requestDate = ZonedDateTime.of(2019, 10, 9, 10, 0, 0, 0, UTC);
    UserResource steve = usersFixture.steve();
    requestsFixture.place(new RequestBuilder()
      .page()
      .forItem(requestedItem)
      .by(steve)
      .withPickupServicePointId(pickupServicePointId)
      .withRequestDate(requestDate));

    ZonedDateTime checkInDate = ZonedDateTime.of(2019, 10, 10, 12, 30, 0, 0, UTC);

    usersClient.delete(steve);

    CheckInByBarcodeResponse response = checkInFixture.checkInByBarcode(requestedItem, checkInDate, pickupServicePointId);

    JsonObject staffSlipContext = response.getStaffSlipContext();
    JsonObject userContext = staffSlipContext.getJsonObject("requester");
    assertNull(userContext);
    assertNotNull(staffSlipContext.getString(CURRENT_DATE_TIME));

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void requestAwaitingPickupNoticeIsNotSentWhenPatronNoticeRequestsFails() {
    JsonObject availableNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(UUID.randomUUID())
      .withAvailableEvent()
      .create();
    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with available notice")
      .withLoanNotices(Collections.singletonList(availableNoticeConfig));

    use(noticePolicy);

    ItemResource requestedItem = itemsFixture.basedUponNod();
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    ZonedDateTime requestDate = ZonedDateTime.of(2019, 10, 9, 10, 0, 0, 0, UTC);
    UserResource steve = usersFixture.steve();
    requestsFixture.place(new RequestBuilder()
      .page()
      .forItem(requestedItem)
      .by(steve)
      .withPickupServicePointId(pickupServicePointId)
      .withRequestDate(requestDate));

    ZonedDateTime checkInDate = ZonedDateTime.of(2019, 10, 10, 12, 30, 0, 0, UTC);

    FakeModNotify.setFailPatronNoticesWithBadRequest(true);

    checkInFixture.checkInByBarcode(requestedItem, checkInDate, pickupServicePointId);

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 1);
  }

  @Test
  void patronNoticeIsSentForRequestAwaitingPickupWhenPreviousRequestWasCancelled() {
    patronNoticeIsSentForRequestAwaitingPickupWhenPreviousRequestWasClosed(CLOSED_CANCELLED);
  }

  @Test
  void patronNoticeIsSentForRequestAwaitingPickupWhenPreviousRequestHasExpired() {
    patronNoticeIsSentForRequestAwaitingPickupWhenPreviousRequestWasClosed(CLOSED_UNFILLED);
  }

  @Test
  void patronNoticeIsSentForRequestAwaitingPickupWhenPreviousRequestPickupExpired() {
    patronNoticeIsSentForRequestAwaitingPickupWhenPreviousRequestWasClosed(CLOSED_PICKUP_EXPIRED);
  }

  @Test
  void verifyDepartmentOnStaffSlipContextCheckInByBarcode() {
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();

    ZonedDateTime requestDate = ZonedDateTime.of(2019, 7, 22, 10, 22, 54, 0, UTC);
    IndividualResource servicePoint = servicePointsFixture.cd1();
    Address address = SiriusBlack();
    var departmentId1 = UUID.randomUUID();
    var departmentId2 = UUID.randomUUID();
    JsonArray departmentIds = new JsonArray(List.of(departmentId1));

    // Requester with 1 Department
    IndividualResource requester = usersFixture.steve(builder ->
      builder.withAddress(address).withDepartments(departmentIds));
    departmentFixture.department1(departmentId1.toString());
    departmentFixture.department2(departmentId2.toString());

    final var requestExpiration = java.time.LocalDate.of(2019, 7, 30);
    final var holdShelfExpiration = java.time.LocalDate.of(2019, 8, 31);
    requestsFixture.place(new RequestBuilder()
      .withId(UUID.randomUUID())
      .open()
      .page()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfillToHoldShelf()
      .withRequestExpiration(requestExpiration)
      .withHoldShelfExpiration(holdShelfExpiration)
      .withPickupServicePointId(servicePoint.getId())
      .withDeliveryAddressType(addressTypesFixture.home().getId())
      .withPatronComments("I need the book")
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

    ZonedDateTime checkInDate = ZonedDateTime.of(2019, 7, 25, 14, 23, 41, 0, UTC);
    CheckInByBarcodeResponse response = checkInFixture.checkInByBarcode(item, checkInDate, servicePoint.getId());

    JsonObject staffSlipContext = response.getStaffSlipContext();
    JsonObject userContext = staffSlipContext.getJsonObject("requester");
    assertThat(userContext.getString("departments"), is("test department1"));
    assertNotNull(staffSlipContext.getString(CURRENT_DATE_TIME));

    item = itemsFixture.basedUponNod();
    // Requester with 2 Departments
    requester = usersFixture.charlotte(builder ->
      builder.withAddress(address).withDepartments(new JsonArray(List.of(departmentId1, departmentId2))));
    requestsFixture.place(new RequestBuilder()
      .withId(UUID.randomUUID())
      .open()
      .page()
      .forItem(item)
      .by(requester)
      .withRequestDate(requestDate)
      .fulfillToHoldShelf()
      .withRequestExpiration(requestExpiration)
      .withHoldShelfExpiration(holdShelfExpiration)
      .withPickupServicePointId(servicePoint.getId())
      .withDeliveryAddressType(addressTypesFixture.home().getId())
      .withPatronComments("I need the book")
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

    response = checkInFixture.checkInByBarcode(item, checkInDate, servicePoint.getId());
    staffSlipContext = response.getStaffSlipContext();
    userContext = staffSlipContext.getJsonObject("requester");
    assertThat(userContext.getString("departments").split("; "),
      arrayContainingInAnyOrder(equalTo("test department1"), equalTo("test department2")));
  }

  private void patronNoticeIsSentForRequestAwaitingPickupWhenPreviousRequestWasClosed(
    RequestStatus firstRequestUpdateStatus) {

    UUID requestAwaitingPickupTemplateId = UUID.randomUUID();
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    use(new NoticePolicyBuilder()
      .withName("Policy with Request Awaiting Pickup notice")
      .withLoanNotices(Collections.singletonList(
        new NoticeConfigurationBuilder()
        .withTemplateId(requestAwaitingPickupTemplateId)
        .withAvailableEvent()
        .create()
      )));

    ZonedDateTime requestDate = ZonedDateTime.of(2020, 11, 30, 13, 30, 0, 0, UTC);
    ZonedDateTime requestExpirationDateTime = requestDate.plusMonths(1);
    LocalDate requestExpirationDate = toLocalDate(requestExpirationDateTime);
    ZonedDateTime firstCheckInDate = requestDate.plusDays(1);
    ZonedDateTime secondCheckInDate = firstCheckInDate.plusDays(1);


    ItemResource item = itemsFixture.basedUponNod();

    // create two requests for the same item

    UserResource firstRequester = usersFixture.steve();
    IndividualResource firstRequest = requestsFixture.place(new RequestBuilder()
      .page()
      .forItem(item)
      .by(firstRequester)
      .withPickupServicePointId(pickupServicePointId)
      .withRequestDate(requestDate)
      .withRequestExpiration(requestExpirationDate)
      .withHoldShelfExpiration(requestExpirationDate));

    UserResource secondRequester = usersFixture.james();
    IndividualResource secondRequest = requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(item)
      .by(secondRequester)
      .withPickupServicePointId(pickupServicePointId)
      .withRequestDate(requestDate)
      .withRequestExpiration(requestExpirationDate)
      .withHoldShelfExpiration(requestExpirationDate));

    // verify that both requests are "Open - Not yet filled"

    assertThatRequestStatusIs(firstRequest.getId(), RequestStatus.OPEN_NOT_YET_FILLED);
    assertThatRequestStatusIs(secondRequest.getId(), RequestStatus.OPEN_NOT_YET_FILLED);

    // check the item in

    checkInFixture.checkInByBarcode(item, firstCheckInDate, pickupServicePointId);

    // check that both item and first request are awaiting pickup

    assertThatItemStatusIs(item.getId(), ItemStatus.AWAITING_PICKUP);
    assertThatRequestStatusIs(firstRequest.getId(), RequestStatus.OPEN_AWAITING_PICKUP);

    // verify that Request Awaiting Pickup notice was sent for first request

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
    checkPatronNoticeEvent(firstRequest, firstRequester, item, requestAwaitingPickupTemplateId, false);

    clearSentPatronNoticesAndPubsubEvents();

    // Check-in again and verify that same notice is not sent repeatedly

    checkInFixture.checkInByBarcode(item, firstCheckInDate, pickupServicePointId);

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    // close first request

    switch (firstRequestUpdateStatus) {
      case CLOSED_CANCELLED:
        requestsFixture.cancelRequest(firstRequest);
        break;
      // request expiration and subsequent queue reordering is managed by mod-circulation-storage
      // for purposes of this test request position has to be changed manually
      case CLOSED_UNFILLED:
        requestsFixture.expireRequest(firstRequest);
        updateRequestPosition(secondRequest, 1);
        break;
      case CLOSED_PICKUP_EXPIRED:
        requestsFixture.expireRequestPickup(firstRequest);
        updateRequestPosition(secondRequest, 1);
        break;
      default:
        fail("Unsupported request status");
    }

    // verify that first request was closed, and that item is still awaiting pickup

    assertThatItemStatusIs(item.getId(), ItemStatus.AWAITING_PICKUP);
    assertThatRequestStatusIs(firstRequest.getId(), firstRequestUpdateStatus);

    // check the item in again
    checkInFixture.checkInByBarcode(item, secondCheckInDate, pickupServicePointId);

    // verify that item is still awaiting pickup, and that second request is now awaiting pickup

    assertThatItemStatusIs(item.getId(), ItemStatus.AWAITING_PICKUP);
    assertThatRequestStatusIs(secondRequest.getId(), RequestStatus.OPEN_AWAITING_PICKUP);

    // verify that Request Awaiting Pickup notice was sent to second requester

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    checkPatronNoticeEvent(secondRequest, secondRequester, item, requestAwaitingPickupTemplateId, false);

    clearSentPatronNoticesAndPubsubEvents();

    // Check-in again and verify that same notice is not sent repeatedly

    checkInFixture.checkInByBarcode(item, secondCheckInDate, pickupServicePointId);

    verifyNumberOfSentNotices(0);
    verifyNumberOfPublishedEvents(NOTICE, 0);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);
  }

  @Test
  void overdueFineShouldBeChargedWhenItemIsOverdue() {
    useFallbackPolicies(loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandardDoNotCountClosed().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final IndividualResource james = usersFixture.james();
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));
    final IndividualResource nod = itemsFixture.basedUponNod(item ->
      item.withPermanentLocation(homeLocation.getId()));

    checkOutFixture.checkOutByBarcode(nod, james,
      ZonedDateTime.of(2020, 1, 1, 12, 0, 0, 0, UTC));

    JsonObject servicePointOwner = new JsonObject();
    servicePointOwner.put("value", homeLocation.getJson().getString("primaryServicePoint"));
    servicePointOwner.put("label", "label");
    UUID ownerId = UUID.randomUUID();
    feeFineOwnersClient.create(new FeeFineOwnerBuilder()
      .withId(ownerId)
      .withOwner("fee-fine-owner")
      .withServicePointOwner(Collections.singletonList(servicePointOwner))
    );

    UUID feeFineId = UUID.randomUUID();
    feeFinesClient.create(new FeeFineBuilder()
      .withId(feeFineId)
      .withFeeFineType("Overdue fine")
      .withOwnerId(ownerId)
      .withAutomatic(true)
    );

    CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(ZonedDateTime.of(2020, 1, 25, 12, 0, 0, 0, UTC))
        .at(checkInServicePointId));
    JsonObject checkedInLoan = checkInResponse.getLoan();

    waitAtMost(1, SECONDS)
      .until(accountsClient::getAll, hasSize(1));

    List<JsonObject> createdAccounts = accountsClient.getAll();

    assertThat("Fee/fine record should be created", createdAccounts, hasSize(1));

    JsonObject account = createdAccounts.get(0);

    assertThat(account, isValidOverdueFine(checkedInLoan, nod,
      homeLocation.getJson().getString("name"), ownerId, feeFineId, 5.0));

    waitAtMost(1, SECONDS)
      .until(feeFineActionsClient::getAll, hasSize(1));

    List<JsonObject> createdFeeFineActions = feeFineActionsClient.getAll();
    assertThat("Fee/fine action record should be created", createdFeeFineActions, hasSize(1));

    JsonObject createdFeeFineAction = createdFeeFineActions.get(0);
    assertThat("user ID is included",
      createdFeeFineAction.getString("userId"), is(checkedInLoan.getString("userId")));
    assertThat("account ID is included",
      createdFeeFineAction.getString("accountId"), is(account.getString("id")));
    assertThat("balance is included",
      createdFeeFineAction.getDouble("balance"), is(account.getDouble("amount")));
    assertThat("amountAction is included",
      createdFeeFineAction.getDouble("amountAction"), is(account.getDouble("amount")));
    assertThat("typeAction is included",
      createdFeeFineAction.getString("typeAction"), is("Overdue fine"));
  }

  @Test
  void overdueFineIsChargedForCorrectOwnerWhenMultipleOwnersExist() {
    useFallbackPolicies(loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandardDoNotCountClosed().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final IndividualResource james = usersFixture.james();
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));
    final IndividualResource nod = itemsFixture.basedUponNod(item ->
      item.withPermanentLocation(homeLocation.getId()));

    checkOutFixture.checkOutByBarcode(nod, james,
      ZonedDateTime.of(2020, 1, 1, 12, 0, 0, 0, UTC));

    for (int i = 0; i < 10; i++) {
      feeFineOwnersClient.create(new FeeFineOwnerBuilder()
        .withId(UUID.randomUUID())
        .withOwner("fee-fine-owner-" + i)
      );
    }

    waitAtMost(3, SECONDS)
      .until(feeFineOwnersClient::getAll, hasSize(10));

    JsonObject servicePointOwner = new JsonObject();
    servicePointOwner.put("value", homeLocation.getJson()
      .getString("primaryServicePoint"));

    servicePointOwner.put("label", "label");
    UUID servicePointOwnerId = UUID.randomUUID();
    feeFineOwnersClient.create(new FeeFineOwnerBuilder()
      .withId(servicePointOwnerId)
      .withOwner("fee-fine-owner")
      .withServicePointOwner(Collections.singletonList(servicePointOwner))
    );

    UUID feeFineId = UUID.randomUUID();
    feeFinesClient.create(new FeeFineBuilder()
      .withId(feeFineId)
      .withFeeFineType("Overdue fine")
      .withAutomatic(true)
    );

    CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(ZonedDateTime.of(2020, 1, 25, 12, 0, 0, 0, UTC))
        .at(checkInServicePointId));
    JsonObject checkedInLoan = checkInResponse.getLoan();

    waitAtMost(3, SECONDS)
      .until(accountsClient::getAll, hasSize(1));

    List<JsonObject> createdAccounts = accountsClient.getAll();

    assertThat("Fee/fine record should be created", createdAccounts, hasSize(1));

    JsonObject account = createdAccounts.get(0);

    assertThat(account, isValidOverdueFine(checkedInLoan, nod,
      homeLocation.getJson().getString("name"), servicePointOwnerId, feeFineId, 5.0));
  }

  @Test
  void overdueFineIsNotCreatedWhenThereIsNoOwnerForServicePoint() {
    useFallbackPolicies(loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandardDoNotCountClosed().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final IndividualResource james = usersFixture.james();
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));
    final IndividualResource nod = itemsFixture.basedUponNod(item ->
      item.withPermanentLocation(homeLocation.getId()));

    checkOutFixture.checkOutByBarcode(nod, james,
      ZonedDateTime.of(2020, 1, 1, 12, 0, 0, 0, UTC));

    final UUID servicePointForOwner = servicePointsFixture.cd2().getId();

    JsonObject servicePointOwner = new JsonObject();
    servicePointOwner.put("value", servicePointForOwner.toString());
    servicePointOwner.put("label", "label");
    UUID ownerId = UUID.randomUUID();
    feeFineOwnersClient.create(new FeeFineOwnerBuilder()
      .withId(ownerId)
      .withOwner("fee-fine-owner")
      .withServicePointOwner(Collections.singletonList(servicePointOwner))
    );

    UUID feeFineId = UUID.randomUUID();
    feeFinesClient.create(new FeeFineBuilder()
      .withId(feeFineId)
      .withFeeFineType("Overdue fine")
      .withOwnerId(ownerId)
      .withAutomatic(true)
    );

    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .on(ZonedDateTime.of(2020, 1, 25, 12, 0, 0, 0, UTC))
      .at(checkInServicePointId));

    waitAtMost(1, SECONDS);

    List<JsonObject> createdAccounts = accountsClient.getAll();

    assertThat("No fee/fine records should have been created", createdAccounts, hasSize(0));
  }

  @Test
  void overdueRecallFineShouldBeChargedWhenItemIsOverdueAfterRecall() {
    useFallbackPolicies(loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandardDoNotCountClosed().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final IndividualResource james = usersFixture.james();
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));
    final IndividualResource nod = itemsFixture.basedUponNod(item ->
      item.withPermanentLocation(homeLocation.getId()));

    ZonedDateTime checkOutDate = ZonedDateTime.of(2020, 1, 15, 0, 0, 0, 0, UTC);
    ZonedDateTime recallRequestExpirationDate = checkOutDate.plusDays(5);
    ZonedDateTime checkInDate = checkOutDate.plusDays(10);
    mockClockManagerToReturnFixedDateTime(ZonedDateTime.of(2020, 1, 18, 0, 0, 0, 0, UTC));

    checkOutFixture.checkOutByBarcode(nod, james, checkOutDate);

    Address address = SiriusBlack();
    IndividualResource requester = usersFixture.steve(builder ->
      builder.withAddress(address));

    requestsFixture.place(new RequestBuilder()
      .withId(UUID.randomUUID())
      .open()
      .recall()
      .forItem(nod)
      .by(requester)
      .fulfillToHoldShelf()
      .withRequestExpiration(toLocalDate(recallRequestExpirationDate))
      .withHoldShelfExpiration(toLocalDate(recallRequestExpirationDate))
      .withPickupServicePointId(UUID.fromString(homeLocation.getJson().getString("primaryServicePoint")))
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

    JsonObject servicePointOwner = new JsonObject();
    servicePointOwner.put("value", homeLocation.getJson().getString("primaryServicePoint"));
    servicePointOwner.put("label", "label");
    UUID ownerId = UUID.randomUUID();
    feeFineOwnersClient.create(new FeeFineOwnerBuilder()
      .withId(ownerId)
      .withOwner("fee-fine-owner")
      .withServicePointOwner(Collections.singletonList(servicePointOwner)));

    UUID feeFineId = UUID.randomUUID();
    feeFinesClient.create(new FeeFineBuilder()
      .withId(feeFineId)
      .withFeeFineType("Overdue fine")
      .withOwnerId(ownerId)
      .withAutomatic(true));

    CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .on(checkInDate)
      .at(checkInServicePointId));
    JsonObject checkedInLoan = checkInResponse.getLoan();

    final var createdAccounts = waitAtMost(1, SECONDS)
      .until(accountsClient::getAll, hasSize(1));

    mockClockManagerToReturnDefaultDateTime();

    JsonObject account = createdAccounts.get(0);

    assertThat(account, isValidOverdueFine(checkedInLoan, nod,
      homeLocation.getJson().getString("name"), ownerId, feeFineId, 10.0));
  }

  @Test
  void noOverdueFineShouldBeChargedForOverdueFinePolicyWithNoOverdueFine() {
    useFallbackPolicies(loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.noOverdueFine().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final IndividualResource james = usersFixture.james();
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));
    final IndividualResource nod = itemsFixture.basedUponNod(item ->
      item.withPermanentLocation(homeLocation.getId()));

    checkOutFixture.checkOutByBarcode(nod, james,
      ZonedDateTime.of(2020, 1, 1, 12, 0, 0, 0, UTC));

    JsonObject servicePointOwner = new JsonObject();
    servicePointOwner.put("value", homeLocation.getJson().getString("primaryServicePoint"));
    servicePointOwner.put("label", "label");
    UUID ownerId = UUID.randomUUID();
    feeFineOwnersClient.create(new FeeFineOwnerBuilder()
      .withId(ownerId)
      .withOwner("fee-fine-owner")
      .withServicePointOwner(Collections.singletonList(servicePointOwner)));

    UUID feeFineId = UUID.randomUUID();
    feeFinesClient.create(new FeeFineBuilder()
      .withId(feeFineId)
      .withFeeFineType("Overdue fine")
      .withOwnerId(ownerId));

    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .on(ZonedDateTime.of(2020, 1, 25, 12, 0, 0, 0, UTC))
      .at(checkInServicePointId));

    waitAtLeast(1, SECONDS)
      .until(accountsClient::getAll, empty());

    waitAtLeast(1, SECONDS)
      .until(feeFineActionsClient::getAll, empty());
  }

  @Test
  void shouldNotCreateOverdueFineWithResolutionFoundByLibrary() {
    useFallbackPolicies(loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandardDoNotCountClosed().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final IndividualResource james = usersFixture.james();
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));
    final IndividualResource nod = itemsFixture.basedUponNod(item ->
      item.withPermanentLocation(homeLocation.getId()));

    checkOutFixture.checkOutByBarcode(nod, james,
      ZonedDateTime.of(2020, 1, 1, 12, 0, 0, 0, UTC));

    JsonObject servicePointOwner = new JsonObject();
    servicePointOwner.put("value", homeLocation.getJson().getString("primaryServicePoint"));
    servicePointOwner.put("label", "label");
    UUID ownerId = UUID.randomUUID();
    feeFineOwnersClient.create(new FeeFineOwnerBuilder()
      .withId(ownerId)
      .withOwner("fee-fine-owner")
      .withServicePointOwner(Collections.singletonList(servicePointOwner)));

    UUID feeFineId = UUID.randomUUID();
    feeFinesClient.create(new FeeFineBuilder()
      .withId(feeFineId)
      .withFeeFineType("Overdue fine")
      .withOwnerId(ownerId));

    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .on(ZonedDateTime.of(2020, 1, 25, 12, 0, 0, 0, UTC))
      .at(checkInServicePointId)
      .claimedReturnedResolution("Found by library"));

    waitAtLeast(1, SECONDS)
      .until(accountsClient::getAll, empty());

    waitAtLeast(1, SECONDS)
      .until(feeFineActionsClient::getAll, empty());
  }

  @Test
  void overdueFineCalculatedCorrectlyWhenHourlyFeeFinePolicyIsApplied() {
    useFallbackPolicies(loanPoliciesFixture.create(new LoanPolicyBuilder()
        .withId(UUID.randomUUID())
        .withName("Three days policy")
        .withDescription("Can circulate item")
        .rolling(Period.days(3))
        .unlimitedRenewals()
        .renewFromSystemDate()).getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.create(new OverdueFinePolicyBuilder()
        .withId(UUID.randomUUID())
        .withName("One per hour overdue fine policy")
        .withOverdueFine(
          new JsonObject()
            .put("quantity", 1.0)
            .put("intervalId", "hour"))
        .withCountClosed(false)).getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    final IndividualResource james = usersFixture.james();
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));
    final IndividualResource nod = itemsFixture.basedUponNod(item ->
      item.withPermanentLocation(homeLocation.getId()));

    ZonedDateTime checkOutDate = ZonedDateTime.of(2020, 1, 18, 18, 0, 0, 0, UTC);
    ZonedDateTime checkInDate = ZonedDateTime.of(2020, 1, 22, 15, 30, 0, 0, UTC);

    checkOutFixture.checkOutByBarcode(nod, james, checkOutDate);

    JsonObject servicePointOwner = new JsonObject();
    servicePointOwner.put("value", homeLocation.getJson().getString("primaryServicePoint"));
    servicePointOwner.put("label", "label");
    UUID ownerId = UUID.randomUUID();
    feeFineOwnersClient.create(new FeeFineOwnerBuilder()
      .withId(ownerId)
      .withOwner("fee-fine-owner")
      .withServicePointOwner(Collections.singletonList(servicePointOwner)));

    UUID feeFineId = UUID.randomUUID();
    feeFinesClient.create(new FeeFineBuilder()
      .withId(feeFineId)
      .withFeeFineType("Overdue fine")
      .withOwnerId(ownerId)
      .withAutomatic(true)
      .withDefaultAmount(1.0));

    CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(checkInDate)
        .at(checkInServicePointId));

    JsonObject checkedInLoan = checkInResponse.getLoan();

    waitAtMost(1, SECONDS)
      .until(accountsClient::getAll, hasSize(1));

    List<JsonObject> createdAccounts = accountsClient.getAll();

    assertThat("Fee/fine record should be created", createdAccounts, hasSize(1));

    JsonObject account = createdAccounts.get(0);

    assertThat(account, isValidOverdueFine(checkedInLoan, nod,
      homeLocation.getJson().getString("name"), ownerId, feeFineId, 7.0));
  }

  @Test
  void canCheckInLostAndPaidItem() {
    final ItemResource item = itemsFixture.basedUponNod();
    var checkOutResource = checkOutFixture.checkOutByBarcode(item, usersFixture.steve()).getJson();
    declareLostFixtures.declareItemLost(checkOutResource);

    checkInFixture.checkInByBarcode(item);

    assertThat(itemsClient.getById(item.getId()).getJson(), isAvailable());

    waitAtMost(1, SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(5));

    Response response = loansClient.getById(UUID.fromString(checkOutResource.getString("id")));
    JsonObject loan = response.getJson();

    assertThatPublishedLoanLogRecordEventsAreValid(loan);
  }

  @Test
  void canCheckInAgedToLostItem() {
    val ageToLostResult = ageToLostFixture.createAgedToLostLoan();

    checkInFixture.checkInByBarcode(ageToLostResult.getItem());

    assertThat(itemsFixture.getById(ageToLostResult.getItemId()).getJson(), isAvailable());
    var loan = loansFixture.getLoanById(ageToLostResult.getLoanId()).getJson();
    assertThat(loan, isClosed());

    waitAtMost(1, SECONDS)
      // there should be 7 events published: ITEM_CHECKED_OUT, LOG_RECORDs: CHECK_OUT_EVENT
      // LOG_RECORD: LOAN (aged to lost), ITEM_AGED_TO_LOST, LOG_RECORD: LOAN (status change)
      // ITEM_CHECKED_IN, LOG_RECORDs: CHECK_IN_EVENT
      .until(FakePubSub::getPublishedEvents, hasSize(7));

    assertThatPublishedLoanLogRecordEventsAreValid(loan);
  }

  @Test
  void itemCheckedInEventIsPublished() {
    final IndividualResource james = usersFixture.james();
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));
    final IndividualResource nod = itemsFixture.basedUponNod(item ->
      item.withPermanentLocation(homeLocation.getId()));

    ZonedDateTime checkOutDate = ZonedDateTime.of(2020, 1, 18, 18, 0, 0, 0, UTC);
    ZonedDateTime checkInDate = ZonedDateTime.of(2020, 1, 22, 15, 30, 0, 0, UTC);

    checkOutFixture.checkOutByBarcode(nod, james, checkOutDate);

    CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(checkInDate)
        .at(checkInServicePointId));

    JsonObject checkedInLoan = checkInResponse.getLoan();

    // There should be four events published - first ones for "check out" and check out log event, second ones for "check in" and check in log event
    final var publishedEvents = waitAtMost(2, SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(4));

    final var checkedInEvent = publishedEvents.findFirst(byEventType(ITEM_CHECKED_IN.name()));

    assertThat(checkedInEvent, isValidItemCheckedInEvent(checkedInLoan));

    final var checkInLogEvent = publishedEvents.findFirst(byLogEventType(CHECK_IN.value()));

    assertThat(checkInLogEvent, isValidCheckInLogEvent(checkedInLoan));
    assertThatPublishedLoanLogRecordEventsAreValid(checkedInLoan);

    // Second CheckIn for In-House
    checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(checkInDate)
        .at(checkInServicePointId));

    final var secondPublishedEvents = waitAtMost(2, SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(5));
    final var checkedInEvent2 = secondPublishedEvents.findFirst(byEventType(ITEM_CHECKED_IN.name()));
    assertThat(checkedInEvent2, doesNotContainUserBarcode());
  }

  @Test
  void availableNoticeIsSentUponCheckInWhenRequesterBarcodeWasChanged() {
    UUID templateId = UUID.randomUUID();

    JsonObject availableNoticeConfig = new NoticeConfigurationBuilder()
      .withTemplateId(templateId)
      .withAvailableEvent()
      .create();

    NoticePolicyBuilder noticePolicy = new NoticePolicyBuilder()
      .withName("Test policy")
      .withLoanNotices(Collections.singletonList(availableNoticeConfig));

    use(noticePolicy);

    ItemResource requestedItem = itemsFixture.basedUponNod();
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    UserResource requester = usersFixture.steve();
    ZonedDateTime requestDate = ZonedDateTime.of(2019, 10, 9, 10, 0, 0, 0, UTC);

    requestsFixture.place(new RequestBuilder()
      .page()
      .forItem(requestedItem)
      .by(requester)
      .withPickupServicePointId(pickupServicePointId)
      .withRequestDate(requestDate));

    ZonedDateTime checkInDate = ZonedDateTime.of(2019, 10, 10, 12, 30, 0, 0, UTC);

    JsonObject updatedRequesterJson = requester.getJson().put("barcode", "updated_barcode");
    usersClient.replace(requester.getId(), updatedRequesterJson);

    checkInFixture.checkInByBarcode(requestedItem, checkInDate, pickupServicePointId);

    verifyNumberOfSentNotices(1);
    verifyNumberOfPublishedEvents(NOTICE, 1);
    verifyNumberOfPublishedEvents(NOTICE_ERROR, 0);

    assertThat(FakeModNotify.getFirstSentPatronNotice(), hasEmailNoticeProperties(requester.getId(), templateId,
      getUserContextMatchers(updatedRequesterJson)));
  }

  @Test
  void linkItemToHoldTLRWithHoldShelfWhenCheckedInItemThenFulfilledWithSuccess(){
    reconfigureTlrFeature(TlrFeatureStatus.NOT_CONFIGURED);
    settingsFixture.enableTlrFeature();
    UUID instanceId = instancesFixture.basedUponDunkirk().getId();
    IndividualResource defaultWithHoldings = holdingsFixture.defaultWithHoldings(instanceId);
    IndividualResource checkedOutItem = itemsClient.create(buildCheckedOutItemWithHoldingRecordsId(defaultWithHoldings.getId()));
    IndividualResource holdRequestBeforeFulfilled = requestsClient.create(buildHoldTLRWithHoldShelffulfillmentPreference(instanceId));

    checkInFixture.checkInByBarcode(checkedOutItem, servicePointsFixture.cd1().getId());

    //validating request before fulfilled
    IndividualResource holdRequestAfterFulfilled  = requestsClient.get(holdRequestBeforeFulfilled.getId());
    JsonObject representationBefore = holdRequestBeforeFulfilled.getJson();
    assertThat(representationBefore.getString("itemId"), nullValue());
    validateTLRequestByFields(representationBefore,
      HOLD_SHELF, instanceId, OPEN_NOT_YET_FILLED);

    //validating request after fulfilled
    JsonObject representation = holdRequestAfterFulfilled.getJson();
    assertThat(representation.getString("itemId"), is(checkedOutItem.getId().toString()));
    assertThat(representation.getString("holdingsRecordId"), is(defaultWithHoldings.getId()));
    validateTLRequestByFields(representation, HOLD_SHELF, instanceId, OPEN_AWAITING_PICKUP);
    IndividualResource itemAfter = itemsClient.get(checkedOutItem.getId());
    JsonObject itemRepresentation = itemAfter.getJson();
    assertThat(itemRepresentation.getJsonObject("status").getString("name"), is("Awaiting pickup"));
  }

  @Test
  void checkInItemWhenServicePointHasChangedToNoPickupLocation() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);
    var instanceId = instancesFixture.basedUponDunkirk().getId();
    var defaultWithHoldings = holdingsFixture.defaultWithHoldings(instanceId);
    var checkedOutItem = itemsClient.create(buildCheckedOutItemWithHoldingRecordsId(
      defaultWithHoldings.getId()));
    var holdRequestBeforeFulfilled = requestsClient.create(
      buildHoldTLRWithHoldShelffulfillmentPreference(instanceId));

    String servicePointCode = servicePointsFixture.cd1().getJson().getString("code");
    String servicePointName = "custom service point";
    int shelvingLagTime = 0;
    String discoveryDisplayName = servicePointsFixture.cd1().getJson()
      .getString("discoveryDisplayName");
    String description = servicePointsFixture.cd1().getJson().getString("description");

    ServicePointBuilder changedServicePoint = new ServicePointBuilder(
      servicePointsFixture.cd1().getId(), servicePointName, servicePointCode, discoveryDisplayName,
      description, shelvingLagTime, Boolean.FALSE, null, KEEP_THE_CURRENT_DUE_DATE.name(), false);

//    Update existing service point
    servicePointsFixture.update(servicePointCode, changedServicePoint);

    checkInFixture.checkInByBarcode(checkedOutItem, servicePointsFixture.cd1().getId());

    //validating request before fulfilled
    var holdRequestAfterFulfilled  = requestsClient.get(holdRequestBeforeFulfilled.getId());
    JsonObject representationBefore = holdRequestBeforeFulfilled.getJson();
    assertThat(representationBefore.getString("itemId"), nullValue());
    validateTLRequestByFields(representationBefore, HOLD_SHELF, instanceId, OPEN_NOT_YET_FILLED);

    //validating request after fulfilled
    JsonObject representation = holdRequestAfterFulfilled.getJson();
    assertThat(representation.getString("itemId"), is(checkedOutItem.getId().toString()));
    assertThat(representation.getString("holdingsRecordId"), is(defaultWithHoldings.getId()));

    validateTLRequestByFields(representation, HOLD_SHELF, instanceId, OPEN_AWAITING_PICKUP);

    IndividualResource itemAfter = itemsClient.get(checkedOutItem.getId());
    JsonObject itemRepresentation = itemAfter.getJson();
    assertThat(itemRepresentation.getJsonObject("status").getString("name"),
      is("Awaiting pickup"));

    JsonObject servicePointRepresentation = representation.getJsonObject("pickupServicePoint");
    assertThat(servicePointRepresentation.getBoolean("pickupLocation"), is(false));
  }

  @Test
  void linkItemToHoldTLRWithDeliveryWhenCheckedInThenFulfilledWithSuccess(){
    reconfigureTlrFeature(TlrFeatureStatus.NOT_CONFIGURED);
    settingsFixture.enableTlrFeature();
    UUID instanceId = instancesFixture.basedUponDunkirk().getId();
    IndividualResource defaultWithHoldings = holdingsFixture.defaultWithHoldings(instanceId);
    IndividualResource checkedOutItem = itemsClient.create(buildCheckedOutItemWithHoldingRecordsId(defaultWithHoldings.getId()));
    IndividualResource holdRequestBeforeFulfilled = requestsClient.create(buildHoldTLRWithDeliveryfulfillmentPreference(instanceId));

    checkInFixture.checkInByBarcode(checkedOutItem, servicePointsFixture.cd1().getId());

    //validating request before fulfilled
    IndividualResource holdRequestAfterFulfilled  = requestsClient.get(holdRequestBeforeFulfilled.getId());
    JsonObject representationBefore = holdRequestBeforeFulfilled.getJson();
    assertThat(representationBefore.getString("itemId"), nullValue());
    validateTLRequestByFields(representationBefore, DELIVERY, instanceId, OPEN_NOT_YET_FILLED);

    //validating request after fulfilled
    JsonObject representation = holdRequestAfterFulfilled.getJson();
    assertThat(representation.getString("itemId"), is(checkedOutItem.getId().toString()));
    assertThat(representation.getString("holdingsRecordId"), is(defaultWithHoldings.getId()));
    validateTLRequestByFields(representation, DELIVERY, instanceId, OPEN_AWAITING_DELIVERY);
    IndividualResource itemAfter = itemsClient.get(checkedOutItem.getId());
    JsonObject itemRepresentation = itemAfter.getJson();
    assertThat(itemRepresentation.getJsonObject("status").getString("name"), is("Awaiting delivery"));
  }

  @Test
  void requestsShouldChangePositionWhenTheyGoInFulfillmentOnCheckIn() {
    settingsFixture.enableTlrFeature();

    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(3);
    ItemResource firstItem = items.get(0);
    ItemResource secondItem = items.get(1);
    ItemResource thirdItem = items.get(2);

    UUID instanceId = firstItem.getInstanceId();
    checkOutFixture.checkOutByBarcode(thirdItem, usersFixture.rebecca());

    IndividualResource firstRequest = requestsFixture.placeItemLevelPageRequest(
      firstItem, instanceId, usersFixture.jessica());
    IndividualResource secondRequest = requestsFixture.placeItemLevelPageRequest(
      secondItem, instanceId, usersFixture.steve());
    IndividualResource thirdRequest = requestsFixture.placeTitleLevelHoldShelfRequest(
      instanceId, usersFixture.charlotte());

    assertThat(firstRequest.getJson(), allOf(isOpenNotYetFilled(), hasPosition(1)));
    assertThat(secondRequest.getJson(), allOf(isOpenNotYetFilled(), hasPosition(2)));
    assertThat(thirdRequest.getJson(), allOf(isOpenNotYetFilled(), hasPosition(3)));

    checkInFixture.checkInByBarcode(secondItem);

    assertThat(requestsFixture.getById(firstRequest.getId()).getJson(),
      allOf(isOpenNotYetFilled(), hasPosition(2)));
    assertThat(requestsFixture.getById(secondRequest.getId()).getJson(),
      allOf(isOpenAwaitingPickup(), hasPosition(1)));
    assertThat(requestsFixture.getById(thirdRequest.getId()).getJson(),
      allOf(isOpenNotYetFilled(), hasPosition(3)));

    checkInFixture.checkInByBarcode(thirdItem);

    assertThat(requestsFixture.getById(firstRequest.getId()).getJson(),
      allOf(isOpenNotYetFilled(), hasPosition(3)));
    assertThat(requestsFixture.getById(secondRequest.getId()).getJson(),
      allOf(isOpenAwaitingPickup(), hasPosition(1)));
    assertThat(requestsFixture.getById(thirdRequest.getId()).getJson(),
      allOf(isOpenAwaitingPickup(), hasPosition(2)));

    checkInFixture.checkInByBarcode(firstItem);

    assertThat(requestsFixture.getById(firstRequest.getId()).getJson(),
      allOf(isOpenAwaitingPickup(), hasPosition(3)));
    assertThat(requestsFixture.getById(secondRequest.getId()).getJson(),
      allOf(isOpenAwaitingPickup(), hasPosition(1)));
    assertThat(requestsFixture.getById(thirdRequest.getId()).getJson(),
      allOf(isOpenAwaitingPickup(), hasPosition(2)));
  }

  @Test
  void canCheckinItemWhenRequestForAnotherItemOfSameInstanceExists() {
    settingsFixture.enableTlrFeature();

    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    ItemResource firstItem = items.get(0);
    ItemResource secondItem = items.get(1);

    UUID instanceId = firstItem.getInstanceId();
    IndividualResource firstRequest = requestsFixture.placeItemLevelPageRequest(
      firstItem, instanceId, usersFixture.jessica());

    assertThat(firstRequest.getJson(), allOf(isOpenNotYetFilled(), hasPosition(1)));

    CheckInByBarcodeResponse response = checkInFixture.checkInByBarcode(secondItem);

    assertEquals(200, response.getResponse().getStatusCode());
    assertThat(requestsFixture.getById(firstRequest.getId()).getJson(),
      allOf(isOpenNotYetFilled(), hasPosition(1)));
  }

  @Test
  void canFulFillRecallRequestWhenCheckInAnotherItemOfSameInstance() {
    settingsFixture.enableTlrFeature();
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    ItemResource firstItem = items.get(0);
    ItemResource secondItem = items.get(1);

    checkOutFixture.checkOutByBarcode(firstItem, usersFixture.jessica());
    checkOutFixture.checkOutByBarcode(secondItem, usersFixture.james());

    IndividualResource recallRequest = requestsFixture.placeTitleLevelRecallRequest(
      firstItem.getInstanceId(), usersFixture.steve());
    assertThat(recallRequest.getJson(), allOf(isOpenNotYetFilled(), hasPosition(1)));
    assertThat(recallRequest.getJson().getString("itemId"), is(firstItem.getId().toString()));

    CheckInByBarcodeResponse secondItemCheckInResponse = checkInFixture.checkInByBarcode(secondItem);
    assertThat(secondItemCheckInResponse.getItem(), isAwaitingPickup());

    JsonObject recallRequestResponse = requestsFixture.getById(recallRequest.getId()).getJson();
    assertThat(recallRequestResponse, allOf(isOpenAwaitingPickup(), hasPosition(1)));
    assertThat(recallRequestResponse.getString("itemId"), is(secondItem.getId().toString()));

    CheckInByBarcodeResponse firstItemCheckInResponse = checkInFixture.checkInByBarcode(firstItem);
    assertThat(firstItemCheckInResponse.getItem(), isAvailable());
  }

  @Test
  void canFulFillRecallRequestWhenCheckInAnotherItemOfSameInstanceWithMultipleRecallRequests() {
    settingsFixture.enableTlrFeature();
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(3);
    ItemResource firstItem = items.get(0);
    ItemResource secondItem = items.get(1);
    ItemResource thirdItem = items.get(2);

    checkOutFixture.checkOutByBarcode(firstItem, usersFixture.jessica());
    checkOutFixture.checkOutByBarcode(thirdItem, usersFixture.james());
    checkOutFixture.checkOutByBarcode(secondItem, usersFixture.rebecca());

    UUID instanceId = firstItem.getInstanceId();
    IndividualResource recallRequest1 = requestsFixture.placeTitleLevelRecallRequest(
      instanceId, usersFixture.steve());
    IndividualResource recallRequest2 = requestsFixture.placeTitleLevelRecallRequest(
      instanceId, usersFixture.undergradHenry());

    assertThat(recallRequest1.getJson(), allOf(isOpenNotYetFilled(), hasPosition(1)));
    assertThat(recallRequest1.getJson().getString("itemId"), is(firstItem.getId().toString()));
    assertThat(recallRequest2.getJson(), allOf(isOpenNotYetFilled(), hasPosition(2)));
    assertThat(recallRequest2.getJson().getString("itemId"), is(thirdItem.getId().toString()));

    CheckInByBarcodeResponse secondItemCheckInResponse = checkInFixture.checkInByBarcode(secondItem);
    assertThat(secondItemCheckInResponse.getItem(), isAwaitingPickup());

    JsonObject recallRequestResponse1 = requestsFixture.getById(recallRequest1.getId()).getJson();
    assertThat(recallRequestResponse1, allOf(isOpenAwaitingPickup(), hasPosition(1)));
    assertThat(recallRequestResponse1.getString("itemId"), is(secondItem.getId().toString()));

    CheckInByBarcodeResponse firstItemCheckInResponse = checkInFixture.checkInByBarcode(firstItem);
    assertThat(firstItemCheckInResponse.getItem(), isAwaitingPickup());

    JsonObject recallRequestResponse2 = requestsFixture.getById(recallRequest2.getId()).getJson();
    assertThat(recallRequestResponse2, allOf(isOpenAwaitingPickup(), hasPosition(2)));
    assertThat(recallRequestResponse2.getString("itemId"), is(firstItem.getId().toString()));

    CheckInByBarcodeResponse thirdCheckInResponse = checkInFixture.checkInByBarcode(thirdItem);
    assertThat(thirdCheckInResponse.getItem(), isAvailable());
  }

  @Test
  void shouldNotLinkTitleLevelHoldRequestToAnItemUponCheckInWhenItemIsNonRequestable() {
    settingsFixture.enableTlrFeature();
    ItemResource item = itemsFixture.basedUponNod();
    checkOutFixture.checkOutByBarcode(item, usersFixture.rebecca());
    IndividualResource request = requestsFixture.placeTitleLevelRequest(HOLD, item.getInstanceId(),
      usersFixture.steve());

    useFallbackPolicies(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.nonRequestableRequestPolicy().getId(),
      noticePoliciesFixture.inactiveNotice().getId(),
      overdueFinePoliciesFixture.noOverdueFine().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(item);
    JsonObject requestAfterCheckIn = requestsFixture.getById(request.getId()).getJson();

    assertThat(requestAfterCheckIn.getString("itemId"), nullValue());
    assertThat(requestAfterCheckIn, RequestMatchers.isOpenNotYetFilled());

    final var publishedEvents = waitAtMost(2, SECONDS)
     .until(FakePubSub::getPublishedEvents, hasSize(5));
    final var checkedInEvent = publishedEvents.findFirst(byEventType(ITEM_CHECKED_IN.name()));
    assertThat(checkedInEvent, isValidItemCheckedInEvent(checkInResponse.getLoan()));
    final var checkInLogEvent = publishedEvents.findFirst(byLogEventType(CHECK_IN.value()));
    assertThat(checkInLogEvent, isValidCheckInLogEvent(checkInResponse.getLoan()));

  }

  @Test
  void shouldNotLinkTitleLevelHoldRequestToAnItemUponCheckInWhenItemIsNonLoanable() {
    settingsFixture.enableTlrFeature();
    ItemResource item = itemsFixture.basedUponNod();
    checkOutFixture.checkOutByBarcode(item, usersFixture.rebecca());
    IndividualResource request = requestsFixture.placeTitleLevelRequest(HOLD, item.getInstanceId(),
      usersFixture.steve());

    use(new LoanPolicyBuilder().withLoanable(false));
    checkInFixture.checkInByBarcode(item);
    JsonObject requestAfterCheckIn = requestsFixture.getById(request.getId()).getJson();

    assertThat(requestAfterCheckIn.getString("itemId"), nullValue());
    assertThat(requestAfterCheckIn, RequestMatchers.isOpenNotYetFilled());
  }

  @Test
  void shouldNotLinkTitleLevelRecallRequestToNewItemUponCheckInWhenItemIsNonRequestable() {
    settingsFixture.enableTlrFeature();

    UUID canCirculateLoanTypeId = loanTypesFixture.canCirculate().getId();
    UUID readingRoomLoanTypeId = loanTypesFixture.readingRoom().getId();

    UUID allowAllRequestPolicyId = requestPoliciesFixture.allowAllRequestPolicy().getId();
    UUID blockAllRequestPolicyId = requestPoliciesFixture.nonRequestableRequestPolicy().getId();
    UUID loanPolicyId = loanPoliciesFixture.canCirculateRolling().getId();
    UUID noticePolicyId = noticePoliciesFixture.inactiveNotice().getId();
    UUID overdueFinePolicyId = overdueFinePoliciesFixture.noOverdueFine().getId();
    UUID lostItemPolicyId = lostItemFeePoliciesFixture.facultyStandard().getId();

    circulationRulesFixture.updateCirculationRules(String.join("\n",
      "priority: t, g, s, c, b, a, m",
      "fallback-policy: r " + allowAllRequestPolicyId + " l " + loanPolicyId + " n " + noticePolicyId  + " o " + overdueFinePolicyId + " i " + lostItemPolicyId,
      "t " + canCirculateLoanTypeId + ": r " + allowAllRequestPolicyId + " l " + loanPolicyId + " n " + noticePolicyId  + " o " + overdueFinePolicyId + " i " + lostItemPolicyId,
      "t " + readingRoomLoanTypeId + ": r " + blockAllRequestPolicyId + " l " + loanPolicyId + " n " + noticePolicyId  + " o " + overdueFinePolicyId + " i " + lostItemPolicyId
    ));

    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    ItemResource requestableItem = items.get(0);
    ItemResource nonRequestableItem = items.get(1);

    itemsClient.replace(requestableItem.getId(),
      requestableItem.getJson().put("permanentLoanTypeId", canCirculateLoanTypeId.toString()));
    itemsClient.replace(nonRequestableItem.getId(),
      nonRequestableItem.getJson().put("permanentLoanTypeId", readingRoomLoanTypeId.toString()));

    checkOutFixture.checkOutByBarcode(requestableItem, usersFixture.steve());
    checkOutFixture.checkOutByBarcode(nonRequestableItem, usersFixture.james());

    IndividualResource recall = requestsFixture.placeTitleLevelRequest(RECALL,
      requestableItem.getInstanceId(), usersFixture.jessica());

    assertThat(recall.getJson(), RequestMatchers.isOpenNotYetFilled());
    assertThat(recall.getJson().getString("itemId"), is(requestableItem.getId()));

    checkInFixture.checkInByBarcode(nonRequestableItem);
    JsonObject recallAfterCheckIn = requestsFixture.getById(recall.getId()).getJson();

    assertThat(recallAfterCheckIn, RequestMatchers.isOpenNotYetFilled());
    assertThat(recallAfterCheckIn.getString("itemId"), is(requestableItem.getId()));
  }

  @Test
  void shouldNotLinkTitleLevelRecallRequestToNewItemUponCheckInWhenItemIsNonLoanable() {
    settingsFixture.enableTlrFeature();

    UUID canCirculateLoanTypeId = loanTypesFixture.canCirculate().getId();
    UUID readingRoomLoanTypeId = loanTypesFixture.readingRoom().getId();

    UUID loanableLoanPolicyId = loanPoliciesFixture.canCirculateRolling().getId();
    UUID nonLoanableLoanPolicyId = loanPoliciesFixture.create(
      new LoanPolicyBuilder().withLoanable(false)).getId();
    UUID requestPolicyId = requestPoliciesFixture.allowAllRequestPolicy().getId();
    UUID noticePolicyId = noticePoliciesFixture.inactiveNotice().getId();
    UUID overdueFinePolicyId = overdueFinePoliciesFixture.noOverdueFine().getId();
    UUID lostItemPolicyId = lostItemFeePoliciesFixture.facultyStandard().getId();

    circulationRulesFixture.updateCirculationRules(String.join("\n",
      "priority: t, g, s, c, b, a, m",
      "fallback-policy: l " + loanableLoanPolicyId + " r " + requestPolicyId + " n " + noticePolicyId  + " o " + overdueFinePolicyId + " i " + lostItemPolicyId,
      "t " + canCirculateLoanTypeId + ": l " + loanableLoanPolicyId + " r " + requestPolicyId + " n " + noticePolicyId  + " o " + overdueFinePolicyId + " i " + lostItemPolicyId,
      "t " + readingRoomLoanTypeId + ": l " + nonLoanableLoanPolicyId + " r " + requestPolicyId + " n " + noticePolicyId  + " o " + overdueFinePolicyId + " i " + lostItemPolicyId
    ));

    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    ItemResource loanableItem = items.get(0);
    ItemResource nonLoanableItem = items.get(1);
    UUID loanableItemId = loanableItem.getId();
    UUID nonLoanableItemId = nonLoanableItem.getId();

    checkOutFixture.checkOutByBarcode(loanableItem, usersFixture.steve());
    checkOutFixture.checkOutByBarcode(nonLoanableItem, usersFixture.james());

    itemsClient.replace(loanableItemId, itemsFixture.getById(
      loanableItemId).getJson().put("permanentLoanTypeId", canCirculateLoanTypeId.toString()));
    itemsClient.replace(nonLoanableItemId, itemsFixture.getById(
      nonLoanableItemId).getJson().put("permanentLoanTypeId", readingRoomLoanTypeId.toString()));

    IndividualResource recall = requestsFixture.placeTitleLevelRequest(RECALL,
      loanableItem.getInstanceId(), usersFixture.jessica());

    assertThat(recall.getJson(), RequestMatchers.isOpenNotYetFilled());
    assertThat(recall.getJson().getString("itemId"), is(loanableItemId));

    checkInFixture.checkInByBarcode(nonLoanableItem);
    JsonObject recallAfterCheckIn = requestsFixture.getById(recall.getId()).getJson();

    assertThat(recallAfterCheckIn, RequestMatchers.isOpenNotYetFilled());
    assertThat(recallAfterCheckIn.getString("itemId"), is(loanableItemId));
  }

  @Test
  void checkInShouldNotFailIfNoPrimaryServicePointForItemLocation() {
    var homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(UUID.randomUUID()));
    var nod = itemsFixture.basedUponNod(
      item -> item.withTemporaryLocation(homeLocation.getId()));

    var itemRepresentation = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(servicePointsFixture.cd1().getId())
      ).getItem();

    assertThat(itemRepresentation.getJsonObject("status").getString("name"), is("Available"));
  }

  private JsonObject buildCheckedOutItemWithHoldingRecordsId(UUID holdingRecordsId) {
    return new ItemBuilder()
      .forHolding(holdingRecordsId)
      .checkOut()
      .withMaterialType(materialTypesFixture.book().getId())
      .withPermanentLoanType(loanTypesFixture.canCirculate().getId())
      .withPermanentLocation(locationsFixture.mainFloor())
      .create();
  }

  private JsonObject buildHoldTLRWithHoldShelffulfillmentPreference(UUID instanceId) {
    return new RequestBuilder()
      .hold()
      .fulfillToHoldShelf()
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .withRequesterId(usersFixture.charlotte().getId()).create();
  }

  private JsonObject buildHoldTLRWithDeliveryfulfillmentPreference(UUID instanceId) {
    return new RequestBuilder()
      .hold()
      .deliverToAddress(servicePointsFixture.cd1().getId())
      .titleRequestLevel()
      .withInstanceId(instanceId)
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .withRequesterId(usersFixture.charlotte().getId()).create();
  }

  private void validateTLRequestByFields(JsonObject representation, String expectedfulfillmentPreference,
    UUID expectedInstanceId, String expectedStatus){
    assertThat(representation.getString("requestLevel"), is("Title"));
    assertThat(representation.getString("requestType"), is("Hold"));
    assertThat(representation.getString("fulfillmentPreference"), is(expectedfulfillmentPreference));
    assertThat(representation.getString("instanceId"), is(expectedInstanceId));
    assertThat(representation.getString("status"), is(expectedStatus));
    assertThat(representation.getString("position"), is("1"));
  }

  private void checkPatronNoticeEvent(IndividualResource request, IndividualResource requester,
    ItemResource item, UUID expectedTemplateId, boolean checkPatronInfo) {

    verifyNumberOfSentNotices(1);

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(getUserContextMatchers(requester));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(item, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getRequestContextMatchers(request));
    if (checkPatronInfo) {
      noticeContextMatchers.putAll(getLoanAdditionalInfoContextMatchers(LOAN_INFO_ADDED));
    }

    assertThat(FakeModNotify.getSentPatronNotices(), hasItems(
      hasEmailNoticeProperties(requester.getId(), expectedTemplateId, noticeContextMatchers)));

    verifyNumberOfPublishedEvents(NOTICE, 1);
  }

  private void verifyCheckInOperationRecorded(UUID itemId, UUID servicePoint) {
    final CqlQuery query = CqlQuery.queryFromTemplate("itemId=%s", itemId);
    final MultipleJsonRecords recordedOperations = checkInOperationClient.getMany(query);

    assertThat(recordedOperations.totalRecords(), is(1));

    recordedOperations.forEach(checkInOperation -> {
      assertThat(checkInOperation.getString("occurredDateTime"),
        withinSecondsBeforeNow(2));
      assertThat(checkInOperation.getString("itemId"), is(itemId.toString()));
      assertThat(checkInOperation.getString("servicePointId"), is(servicePoint.toString()));
      assertThat(checkInOperation.getString("performedByUserId"), is(getUserId()));
    });
  }

  private LocalDate toLocalDate(ZonedDateTime dateTime) {
    return LocalDate.of(dateTime.getYear(), dateTime.getMonthValue(), dateTime.getDayOfMonth());
  }

  private void assertThatItemStatusIs(UUID itemId, ItemStatus status) {
    final var item = itemsFixture.getById(itemId);

    assertThat(item.getStatusName(), is(status.getValue()));
  }

  private void assertThatRequestStatusIs(UUID requestId, RequestStatus status) {
    assertThat(
      Request.from(requestsFixture.getById(requestId).getJson()).getStatus(),
      is(status));
  }

  private void updateRequestPosition(IndividualResource request, int position) {
    requestsFixture.replaceRequest(request.getId(),
      RequestBuilder.from(request).withPosition(position));
  }

  private void addPatronInfoToLoan(String loanId){
    addInfoFixture.addInfo(new AddInfoRequestBuilder(loanId,
      "patronInfoAdded", LOAN_INFO_ADDED));
  }
}
