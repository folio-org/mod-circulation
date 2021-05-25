package api.loans;

import static api.support.APITestContext.getUserId;
import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static api.support.Wait.waitAtLeast;
import static api.support.builders.ItemBuilder.INTELLECTUAL_ITEM;
import static api.support.fakes.PublishedEvents.byEventType;
import static api.support.fakes.PublishedEvents.byLogEventType;
import static api.support.fixtures.AddressExamples.SiriusBlack;
import static api.support.fixtures.TemplateContextMatchers.getUserContextMatchers;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasItemBarcodeParameter;
import static api.support.matchers.EventMatchers.isValidCheckInLogEvent;
import static api.support.matchers.EventMatchers.isValidItemCheckedInEvent;
import static api.support.matchers.ItemMatchers.isAvailable;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.OverdueFineMatcher.isValidOverdueFine;
import static api.support.matchers.PatronNoticeMatcher.hasEmailNoticeProperties;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsBeforeNow;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.waitAtMost;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.domain.EventType.ITEM_CHECKED_IN;
import static org.folio.circulation.domain.representations.logs.LogEventType.CHECK_IN;
import static org.folio.circulation.domain.representations.logs.LogEventType.NOTICE;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.joda.time.DateTimeZone.UTC;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.folio.circulation.domain.User;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.joda.time.Seconds;
import org.junit.Test;

import api.support.APITests;
import api.support.CheckInByBarcodeResponse;
import api.support.MultipleJsonRecords;
import api.support.builders.Address;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.FeeFineBuilder;
import api.support.builders.FeeFineOwnerBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.OverdueFinePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.fakes.FakePubSub;
import api.support.fixtures.TemplateContextMatchers;
import api.support.http.CqlQuery;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;
import lombok.val;

public class CheckInByBarcodeTests extends APITests {
  @Test
  public void canCloseAnOpenLoanByCheckingInTheItem() {
    final IndividualResource james = usersFixture.james();

    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      item -> item
        .withTemporaryLocation(homeLocation.getId())
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume"));

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(nod, james,
      new DateTime(2018, 3, 1, 13, 25, 46, UTC));

    DateTime expectedSystemReturnDate = DateTime.now(UTC);

    final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(new DateTime(2018, 3, 5, 14 ,23, 41, UTC))
        .at(checkInServicePointId));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("Closed loan should be present",
      loanRepresentation, notNullValue());

    assertThat(loanRepresentation.getString("userId"), is(james.getId().toString()));

    assertThat("Should have return date",
      loanRepresentation.getString("returnDate"), is("2018-03-05T14:23:41.000Z"));

    assertThat("Should have system return date similar to now",
      loanRepresentation.getString("systemReturnDate"),
      is(withinSecondsAfter(Seconds.seconds(10), expectedSystemReturnDate)));

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
public void verifyItemEffectiveLocationIdAtCheckOut() {
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
  public void canCreateStaffSlipContextOnCheckInByBarcode() {
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();

    DateTime requestDate = new DateTime(2019, 7, 22, 10, 22, 54, UTC);
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
      .fulfilToHoldShelf()
      .withRequestExpiration(requestExpiration)
      .withHoldShelfExpiration(holdShelfExpiration)
      .withPickupServicePointId(servicePoint.getId())
      .withDeliveryAddressType(addressTypesFixture.home().getId())
      .withPatronComments("I need the book")
      .withTags(new RequestBuilder.Tags(asList("new", "important"))));

    DateTime checkInDate = new DateTime(2019, 7, 25, 14, 23, 41, UTC);
    CheckInByBarcodeResponse response = checkInFixture.checkInByBarcode(item, checkInDate, servicePoint.getId());

    User requesterUser = new User(requester.getJson());
    JsonObject staffSlipContext = response.getStaffSlipContext();
    JsonObject userContext = staffSlipContext.getJsonObject("requester");
    JsonObject requestContext = staffSlipContext.getJsonObject("request");

    assertThat(userContext.getString("firstName"), is(requesterUser.getFirstName()));
    assertThat(userContext.getString("lastName"), is(requesterUser.getLastName()));
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
    assertThat(requestContext.getString("holdShelfExpirationDate"), isEquivalentTo(toZonedStartOfDay(holdShelfExpiration)));
    assertThat(requestContext.getString("requestID"), is(request.getId()));
    assertThat(requestContext.getString("servicePointPickup"), is(servicePoint.getJson().getString("name")));
    assertThat(requestContext.getString("patronComments"), is("I need the book"));
  }

  @Test
  public void cannotCheckInItemThatCannotBeFoundByBarcode() {
    final Response response = checkInFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .withItemBarcode("543593485458")
        .on(DateTime.now())
        .at(UUID.randomUUID()));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "No item with barcode 543593485458 exists")));
  }

  @Test
  public void cannotCheckInWithoutAServicePoint() {
    DateTime loanDate = new DateTime(2018, 3, 1, 13, 25, 46, UTC);

    final IndividualResource james = usersFixture.james();
    final IndividualResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james, loanDate);

    final Response response = checkInFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(DateTime.now())
        .atNoServicePoint());

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(hasMessage(
        "Checkin request must have a service point id")));
  }

  @Test
  public void cannotCheckInWithoutAnItem() {
    DateTime loanDate = new DateTime(2018, 3, 1, 13, 25, 46, UTC);

    final IndividualResource james = usersFixture.james();
    final IndividualResource nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod, james, loanDate);

    final Response response = checkInFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .noItem()
        .on(DateTime.now())
        .at(UUID.randomUUID()));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "Checkin request must have an item barcode")));
  }

  @Test
  public void cannotCheckInWithoutACheckInDate() {
    DateTime loanDate = new DateTime(2018, 3, 1, 13, 25, 46, UTC);

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
  public void canCheckInAnItemWithoutAnOpenLoan() {
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
      nod, new DateTime(2018, 3, 5, 14, 23, 41, UTC),
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
  public void canCheckInAnItemTwice() {
    DateTime loanDate = new DateTime(2018, 3, 1, 13, 25, 46, UTC);

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
      new DateTime(2018, 3, 5, 14, 23, 41, UTC),
      checkInServicePointId);

    final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      nod, new DateTime(2018, 3, 5, 14, 23, 41, UTC),
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
  public void intellectualItemCannotBeCheckedIn() {
    final var checkInServicePointId = servicePointsFixture.cd1().getId();

    final var homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));

    final var nod = itemsFixture.basedUponNod(item -> item
      .intellectualItem()
      .withBarcode("10304054")
      .withTemporaryLocation(homeLocation));

    final var response = checkInFixture.attemptCheckInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .on(new DateTime(2018, 3, 5, 14, 23, 41, UTC))
      .at(checkInServicePointId));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Nod (Book) (Barcode: 10304054) has the item status Intellectual item and cannot be checked in"),
      hasItemBarcodeParameter(nod))));

    final var fetchedNod = itemsFixture.getById(nod.getId());

    assertThat(fetchedNod, hasItemStatus(INTELLECTUAL_ITEM));
  }

  @Test
  public void patronNoticeOnCheckInIsNotSentWhenCheckInLoanNoticeIsDefinedAndLoanExists() {
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

    DateTime loanDate = new DateTime(2018, 3, 1, 13, 25, 46, UTC);

    final IndividualResource james = usersFixture.james();

    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));

    final ItemResource nod = itemsFixture.basedUponNod(
      builder -> builder.withTemporaryLocation(homeLocation.getId()));

    checkOutFixture.checkOutByBarcode(nod, james, loanDate);

    DateTime checkInDate = new DateTime(2018, 3, 5, 14, 23, 41, UTC);
    final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(checkInDate)
        .at(checkInServicePointId));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("Closed loan should be present",
      loanRepresentation, notNullValue());

    waitAtLeast(1, SECONDS)
      .until(patronNoticesClient::getAll, empty());

    waitAtLeast(1, SECONDS)
      .until(() -> FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), empty());
  }

  @Test
  public void shouldNotSendPatronNoticeWhenCheckInNoticeIsDefinedAndCheckInDoesNotCloseLoan() {
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
      nod, new DateTime(2018, 3, 5, 14, 23, 41, UTC),
      checkInServicePointId);

    assertThat("Response should not include a loan",
      checkInResponse.getJson().containsKey("loan"), is(false));

    waitAtLeast(1, SECONDS)
      .until(patronNoticesClient::getAll, empty());

    waitAtLeast(1, SECONDS)
      .until(() -> FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), empty());
  }

  @Test
  public void patronNoticeOnCheckInAfterCheckOutAndRequestToItem() {
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();

    checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    DateTime requestDate = new DateTime(2019, 7, 22, 10, 22, 54, UTC);
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
      .fulfilToHoldShelf()
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

    DateTime checkInDate = new DateTime(2019, 7, 25, 14, 23, 41, UTC);
    checkInFixture.checkInByBarcode(item, checkInDate, servicePointId);

    checkPatronNoticeEvent(request, requester, item, availableNoticeTemplateId);
  }

  @Test
  public void patronNoticeOnCheckInAfterRequestToItem() {
    ItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    DateTime requestDate = new DateTime(2019, 5, 5, 10, 22, 54, UTC);
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
      .fulfilToHoldShelf()
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
      new DateTime(2019, 5, 10, 14, 23, 41, UTC),
      servicePointId);

    checkPatronNoticeEvent(request, requester, item, availableNoticeTemplateId);
  }

  @Test
  public void availableNoticeIsSentOnceWhenItemStatusIsChangedToAwaitingPickup() {
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

    DateTime requestDate = new DateTime(2019, 10, 9, 10, 0);
    requestsFixture.place(new RequestBuilder()
      .page()
      .forItem(requestedItem)
      .by(usersFixture.steve())
      .withPickupServicePointId(pickupServicePointId)
      .withRequestDate(requestDate));

    DateTime checkInDate = new DateTime(2019, 10, 10, 12, 30);

    checkInFixture.checkInByBarcode(requestedItem, checkInDate, pickupServicePointId);

    waitAtMost(1, SECONDS)
      .until(patronNoticesClient::getAll, hasSize(1));

    waitAtMost(1, SECONDS)
      .until(() -> FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(1));

    patronNoticesClient.deleteAll();
    FakePubSub.clearPublishedEvents();

    //Check-in again and verify no notice are sent
    checkInFixture.checkInByBarcode(requestedItem, checkInDate, pickupServicePointId);

    waitAtMost(1, SECONDS)
      .until(patronNoticesClient::getAll, empty());

    waitAtMost(1, SECONDS)
      .until(() -> FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), empty());
  }

  @Test
  public void overdueFineShouldBeChargedWhenItemIsOverdue() {
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
      new DateTime(2020, 1, 1, 12, 0, 0, UTC));

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
        .on(new DateTime(2020, 1, 25, 12, 0, 0, UTC))
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
  public void overdueFineIsChargedForCorrectOwnerWhenMultipleOwnersExist() {
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
      new DateTime(2020, 1, 1, 12, 0, 0, UTC));

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
        .on(new DateTime(2020, 1, 25, 12, 0, 0, UTC))
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
  public void overdueFineIsNotCreatedWhenThereIsNoOwnerForServicePoint() {
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
      new DateTime(2020, 1, 1, 12, 0, 0, UTC));

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
      .on(new DateTime(2020, 1, 25, 12, 0, 0, UTC))
      .at(checkInServicePointId));

    waitAtMost(1, SECONDS);

    List<JsonObject> createdAccounts = accountsClient.getAll();

    assertThat("No fee/fine records should have been created", createdAccounts, hasSize(0));
  }

  @Test
  public void overdueRecallFineShouldBeChargedWhenItemIsOverdueAfterRecall() {
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

    DateTime checkOutDate = new DateTime(2020, 1, 15, 0, 0, 0, UTC);
    DateTime recallRequestExpirationDate = checkOutDate.plusDays(5);
    DateTime checkInDate = checkOutDate.plusDays(10);
    mockClockManagerToReturnFixedDateTime(new DateTime(2020, 1, 18, 0, 0, 0, UTC));

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
      .fulfilToHoldShelf()
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
  public void noOverdueFineShouldBeChargedForOverdueFinePolicyWithNoOverdueFine() {
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
      new DateTime(2020, 1, 1, 12, 0, 0, UTC));

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
      .on(new DateTime(2020, 1, 25, 12, 0, 0, UTC))
      .at(checkInServicePointId));

    waitAtLeast(1, SECONDS)
      .until(accountsClient::getAll, empty());

    waitAtLeast(1, SECONDS)
      .until(feeFineActionsClient::getAll, empty());
  }
   
  @Test
  public void shouldNotCreateOverdueFineWithResolutionFoundByLibrary() {
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
      new DateTime(2020, 1, 1, 12, 0, 0, UTC));

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
      .on(new DateTime(2020, 1, 25, 12, 0, 0, UTC))
      .at(checkInServicePointId)
      .claimedReturnedResolution("Found by library"));

    waitAtLeast(1, SECONDS)
      .until(accountsClient::getAll, empty());

    waitAtLeast(1, SECONDS)
      .until(feeFineActionsClient::getAll, empty());
  }

  @Test
  public void overdueFineCalculatedCorrectlyWhenHourlyFeeFinePolicyIsApplied() {
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

    DateTime checkOutDate = new DateTime(2020, 1, 18, 18, 0, 0, UTC);
    DateTime checkInDate = new DateTime(2020, 1, 22, 15, 30, 0, UTC);

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
  public void canCheckInLostAndPaidItem() {
    final ItemResource item = itemsFixture.basedUponNod();
    var checkOutResource = checkOutFixture.checkOutByBarcode(item, usersFixture.steve()).getJson();
    declareLostFixtures.declareItemLost(checkOutResource);

    checkInFixture.checkInByBarcode(item);

    assertThat(itemsClient.getById(item.getId()).getJson(), isAvailable());

    waitAtMost(1, SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(6));

    Response response = loansClient.getById(UUID.fromString(checkOutResource.getString("id")));
    JsonObject loan = response.getJson();

    assertThatPublishedLoanLogRecordEventsAreValid(loan);
  }

  @Test
  public void canCheckInAgedToLostItem() {
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
  public void itemCheckedInEventIsPublished() {
    final IndividualResource james = usersFixture.james();
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(checkInServicePointId));
    final IndividualResource nod = itemsFixture.basedUponNod(item ->
      item.withPermanentLocation(homeLocation.getId()));

    DateTime checkOutDate = new DateTime(2020, 1, 18, 18, 0, 0, UTC);
    DateTime checkInDate = new DateTime(2020, 1, 22, 15, 30, 0, UTC);

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
  }

  @Test
  public void availableNoticeIsSentUponCheckInWhenRequesterBarcodeWasChanged() {
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
    DateTime requestDate = new DateTime(2019, 10, 9, 10, 0);

    requestsFixture.place(new RequestBuilder()
      .page()
      .forItem(requestedItem)
      .by(requester)
      .withPickupServicePointId(pickupServicePointId)
      .withRequestDate(requestDate));

    DateTime checkInDate = new DateTime(2019, 10, 10, 12, 30);

    JsonObject updatedRequesterJson = requester.getJson().put("barcode", "updated_barcode");
    usersClient.replace(requester.getId(), updatedRequesterJson);

    checkInFixture.checkInByBarcode(requestedItem, checkInDate, pickupServicePointId);

    JsonObject patronNotice = waitAtMost(1, SECONDS)
      .until(patronNoticesClient::getAll, hasSize(1))
      .get(0);

    assertThat(patronNotice, hasEmailNoticeProperties(requester.getId(), templateId,
      getUserContextMatchers(updatedRequesterJson)));

    waitAtMost(1, SECONDS)
      .until(() -> FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(1));
  }

  private void checkPatronNoticeEvent(IndividualResource request, IndividualResource requester,
    ItemResource item, UUID expectedTemplateId) {

    waitAtMost(1, SECONDS)
      .until(patronNoticesClient::getAll, hasSize(1));

    List<JsonObject> sentNotices = patronNoticesClient.getAll();

    Map<String, Matcher<String>> noticeContextMatchers = new HashMap<>();
    noticeContextMatchers.putAll(getUserContextMatchers(requester));
    noticeContextMatchers.putAll(TemplateContextMatchers.getItemContextMatchers(item, true));
    noticeContextMatchers.putAll(TemplateContextMatchers.getRequestContextMatchers(request));

    assertThat(sentNotices, hasItems(
      hasEmailNoticeProperties(requester.getId(), expectedTemplateId, noticeContextMatchers)));

    waitAtMost(1, SECONDS)
      .until(() -> FakePubSub.getPublishedEventsAsList(byLogEventType(NOTICE.value())), hasSize(1));
  }

  private void verifyCheckInOperationRecorded(UUID itemId, UUID servicePoint) {
    final CqlQuery query = CqlQuery.queryFromTemplate("itemId=%s", itemId);
    final MultipleJsonRecords recordedOperations = checkInOperationClient.getMany(query);

    assertThat(recordedOperations.totalRecords(), is(1));

    recordedOperations.forEach(checkInOperation -> {
      assertThat(checkInOperation.getString("occurredDateTime"),
        withinSecondsBeforeNow(Seconds.seconds(2)));
      assertThat(checkInOperation.getString("itemId"), is(itemId.toString()));
      assertThat(checkInOperation.getString("servicePointId"), is(servicePoint.toString()));
      assertThat(checkInOperation.getString("performedByUserId"), is(getUserId()));
    });
  }

  private ZonedDateTime toZonedStartOfDay(LocalDate date) {
    final var startOfDay = date.atStartOfDay();

    return ZonedDateTime.of(startOfDay, ZoneId.systemDefault().normalized());
  }

  private LocalDate toLocalDate(DateTime dateTime) {
    return LocalDate.of(dateTime.getYear(), dateTime.getMonthOfYear(), dateTime.getDayOfMonth());
  }
}
