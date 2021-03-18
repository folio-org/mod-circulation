package api.loans;

import static api.requests.RequestsAPICreationTests.setupMissingItem;
import static api.support.APITestContext.END_OF_CURRENT_YEAR_DUE_DATE;
import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static api.support.builders.ItemBuilder.AVAILABLE;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.builders.ItemBuilder.CLAIMED_RETURNED;
import static api.support.fakes.PublishedEvents.byEventType;
import static api.support.fakes.PublishedEvents.byLogEventType;
import static api.support.fixtures.AutomatedPatronBlocksFixture.MAX_NUMBER_OF_ITEMS_CHARGED_OUT_MESSAGE;
import static api.support.fixtures.AutomatedPatronBlocksFixture.MAX_OUTSTANDING_FEE_FINE_BALANCE_MESSAGE;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasItemBarcodeParameter;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasLoanPolicyParameters;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasProxyUserBarcodeParameter;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasServicePointParameter;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasUserBarcodeParameter;
import static api.support.matchers.EventMatchers.isValidCheckOutLogEvent;
import static api.support.matchers.EventMatchers.isValidItemCheckedOutEvent;
import static api.support.matchers.ItemMatchers.isCheckedOut;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.ItemMatchers.isWithdrawn;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.LoanMatchers.isOpen;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static api.support.utl.BlockOverridesUtils.getMissingPermissions;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.domain.EventType.ITEM_CHECKED_OUT;
import static org.folio.circulation.domain.policy.DueDateManagement.KEEP_THE_CURRENT_DUE_DATE;
import static org.folio.circulation.domain.policy.Period.months;
import static org.folio.circulation.domain.representations.ItemProperties.CALL_NUMBER_COMPONENTS;
import static org.folio.circulation.domain.representations.logs.LogEventType.CHECK_OUT;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.joda.time.DateTimeZone.UTC;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.override.BlockOverrides;
import org.folio.circulation.domain.override.ItemLimitBlockOverride;
import org.folio.circulation.domain.override.ItemNotLoanableBlockOverride;
import org.folio.circulation.domain.override.PatronBlockOverride;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Seconds;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.builders.UserBuilder;
import api.support.fakes.FakePubSub;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.OkapiHeaders;
import api.support.http.UserResource;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.val;

public class CheckOutByBarcodeTests extends APITests {
  private static final DateTime TEST_LOAN_DATE =
    new DateTime(2019, 4, 10, 11, 35, 48, DateTimeZone.UTC);
  private static final DateTime TEST_DUE_DATE =
    new DateTime(2019, 4, 20, 11, 30, 0, DateTimeZone.UTC);
  public static final String OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION =
    "circulation.override-item-not-loanable-block";
  public static final String OVERRIDE_PATRON_BLOCK_PERMISSION =
    "circulation.override-patron-block";
  public static final String OVERRIDE_ITEM_LIMIT_BLOCK_PERMISSION =
    "circulation.override-item-limit-block";
  public static final String INSUFFICIENT_OVERRIDE_PERMISSIONS =
    "Insufficient override permissions";
  private static final String TEST_COMMENT = "Some comment";
  private static final String CHECKED_OUT_THROUGH_OVERRIDE = "checkedOutThroughOverride";
  private static final String PATRON_WAS_BLOCKED_MESSAGE = "Patron blocked from borrowing";

  @Test
  public void canCheckOutUsingItemAndUserBarcode() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      item -> item
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume"));

    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 18, 11, 43, 54, UTC);

    final UUID checkoutServicePointId = UUID.randomUUID();

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(steve.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("itemEffectiveLocationIdAtCheckOut should match item effective location ID",
      loan.getString("itemEffectiveLocationIdAtCheckOut"), is(smallAngryPlanet.getJson().getString("effectiveLocationId")));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("action should be checkedout",
      loan.getString("action"), is("checkedout"));

    assertThat("loan date should be as supplied",
      loan.getString("loanDate"), isEquivalentTo(loanDate));

    loanHasLoanPolicyProperties(loan, loanPoliciesFixture.canCirculateRolling());
    loanHasOverdueFinePolicyProperties(loan,  overdueFinePoliciesFixture.facultyStandard());
    loanHasLostItemPolicyProperties(loan,  lostItemFeePoliciesFixture.facultyStandard());

    loanHasPatronGroupProperties(loan, "Regular Group");

    assertThat("due date should be 3 weeks after loan date, based upon loan policy",
      loan.getString("dueDate"), isEquivalentTo(loanDate.plusWeeks(3)));

    assertThat("Checkout service point should be stored",
      loan.getString("checkoutServicePointId"), is(checkoutServicePointId));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));

    assertThat("has item information",
      loan.containsKey("item"), is(true));

    assertThat("title is taken from instance",
      loan.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("barcode is taken from item",
      loan.getJsonObject("item").getString("barcode"),
      is("036000291452"));

    assertThat("call number is 123456", loan.getJsonObject("item")
      .getString("callNumber"), is("123456"));

    assertThat("has item enumeration",
      loan.getJsonObject("item").getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      loan.getJsonObject("item").getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      loan.getJsonObject("item").getString("volume"), is("testVolume"));

    assertThat(loan.getJsonObject("item").encode() + " contains 'materialType'",
      loan.getJsonObject("item").containsKey("materialType"), is(true));

    assertThat("materialType is book", loan.getJsonObject("item")
      .getJsonObject("materialType").getString("name"), is("Book"));

    assertThat("item has contributors",
      loan.getJsonObject("item").containsKey("contributors"), is(true));

    JsonArray contributors = loan.getJsonObject("item").getJsonArray("contributors");

    assertThat("item has a single contributor",
      contributors.size(), is(1));

    assertThat("Becky Chambers is a contributor",
      contributors.getJsonObject(0).getString("name"), is("Chambers, Becky"));

    assertThat("has item status",
      loan.getJsonObject("item").containsKey("status"), is(true));

    assertThat("status is taken from item",
      loan.getJsonObject("item").getJsonObject("status").getString("name"),
      is("Checked out"));

    assertThat("Should not have snapshot of item status, as current status is included",
      loan.containsKey("itemStatus"), is(false));

    assertThat("item has location",
      loan.getJsonObject("item").containsKey("location"), is(true));

    assertThat("location is taken from holding",
      loan.getJsonObject("item").getJsonObject("location").getString("name"),
      is("3rd Floor"));

    List<JsonObject> patronSessionRecords = patronSessionRecordsClient.getAll();
    assertThat(patronSessionRecords, hasSize(1));

    JsonObject sessionRecord = patronSessionRecords.get(0);
    assertThat(sessionRecord.getString("patronId"), is(steve.getId()));
    assertThat(sessionRecord.getString("loanId"), is(response.getId()));
    assertThat(sessionRecord.getString("actionType"), is("Check-out"));

    assertTrue(loan.getJsonObject("item").containsKey(CALL_NUMBER_COMPONENTS));
    JsonObject callNumberComponents = loan.getJsonObject("item")
      .getJsonObject(CALL_NUMBER_COMPONENTS);

    assertThat(callNumberComponents.getString("callNumber"), is("123456"));
    assertThat(callNumberComponents.getString("prefix"), is("PREFIX"));
    assertThat(callNumberComponents.getString("suffix"), is("SUFFIX"));
  }

  @Test
  public void canCheckOutUsingFixedDueDateLoanPolicy() {

    IndividualResource loanPolicy = loanPoliciesFixture.canCirculateFixed();
    IndividualResource overdueFinePolicy = overdueFinePoliciesFixture.facultyStandard();
    IndividualResource lostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard();

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePolicy.getId(),
      lostItemFeePolicy.getId());

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = DateTime.now(UTC)
      .withMonthOfYear(3)
      .withDayOfMonth(18)
      .withHourOfDay(11)
      .withMinuteOfHour(43)
      .withSecondOfMinute(54)
      .withMillisOfSecond(0);

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    final JsonObject loan = response.getJson();

    assertThat("loan date should be as supplied",
      loan.getString("loanDate"), isEquivalentTo(loanDate));

    loanHasPatronGroupProperties(loan, "Regular Group");

    loanHasLoanPolicyProperties(loan, loanPolicy);
    loanHasOverdueFinePolicyProperties(loan,  overdueFinePolicy);
    loanHasLostItemPolicyProperties(loan,  lostItemFeePolicy);

    assertThat("due date should be based upon fixed due date schedule",
      loan.getString("dueDate"), isEquivalentTo(END_OF_CURRENT_YEAR_DUE_DATE));
  }

  @Test
  public void canCheckOutUsingDueDateLimitedRollingLoanPolicy() {
    FixedDueDateSchedulesBuilder dueDateLimitSchedule = new FixedDueDateSchedulesBuilder()
      .withName("March Only Due Date Limit")
      .addSchedule(FixedDueDateSchedule.wholeMonth(2018, 3));

    final UUID dueDateLimitScheduleId = loanPoliciesFixture.createSchedule(
      dueDateLimitSchedule).getId();

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Due Date Limited Rolling Policy")
      .rolling(Period.days(30))
      .limitedBySchedule(dueDateLimitScheduleId);

    final IndividualResource loanPolicy = loanPoliciesFixture.create(
      dueDateLimitedPolicy);
    final IndividualResource overdueFinePolicy = overdueFinePoliciesFixture.facultyStandard();
    final IndividualResource lostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard();

    UUID dueDateLimitedPolicyId = loanPolicy.getId();

    useFallbackPolicies(dueDateLimitedPolicyId,
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePolicy.getId(),
      lostItemFeePolicy.getId());

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 18, 11, 43, 54, UTC);

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    final JsonObject loan = response.getJson();

    assertThat("loan date should be as supplied",
      loan.getString("loanDate"), isEquivalentTo(loanDate));

    loanHasPatronGroupProperties(loan, "Regular Group");

    loanHasLoanPolicyProperties(loan, loanPolicy);
    loanHasOverdueFinePolicyProperties(loan, overdueFinePolicy);
    loanHasLostItemPolicyProperties(loan, lostItemFeePolicy);

    assertThat("due date should be limited by schedule",
      loan.getString("dueDate"),
      isEquivalentTo(new DateTime(2018, 3, 31, 23, 59, 59, UTC)));
  }

  @Test
  public void canGetLoanCreatedWhilstCheckingOut() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      smallAngryPlanet, steve);

    assertThat("Location header should be present", response.getLocation(),
      is(notNullValue()));

    loansFixture.getLoanByLocation(response);
  }

  @Test
  public void canCheckOutWithoutLoanDate() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime requestDate = DateTime.now();

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(UUID.randomUUID()));

    final JsonObject loan = response.getJson();

    assertThat("loan date should be as supplied",
      loan.getString("loanDate"),
      withinSecondsAfter(Seconds.seconds(10), requestDate));
  }

  @Test
  public void cannotCheckOutWhenLoaneeCannotBeFound() {
    val smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    val steve = usersFixture.steve();

    usersFixture.remove(steve);

    final Response response = checkOutFixture.attemptCheckOutByBarcode(smallAngryPlanet, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Could not find user with matching barcode"),
      hasUserBarcodeParameter(steve))));
  }

  @Test
  public void cannotCheckOutWhenLoaneeIsInactive() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve(UserBuilder::inactive);

    final Response response = checkOutFixture.attemptCheckOutByBarcode(
      smallAngryPlanet, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot check out to inactive user"),
      hasUserBarcodeParameter(steve))));
  }

  @Test
  public void cannotCheckOutByProxyWhenProxyingUserIsInactive() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    final IndividualResource james = usersFixture.james();
    final IndividualResource steve = usersFixture.steve(UserBuilder::inactive);

    proxyRelationshipsFixture.currentProxyFor(james, steve);

    final Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(james)
        .proxiedBy(steve)
        .at(UUID.randomUUID()));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot check out via inactive proxying user"),
      hasProxyUserBarcodeParameter(steve))));
  }

  @Test
  public void cannotCheckOutByProxyWhenNoRelationship() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    final Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .proxiedBy(james)
        .at(UUID.randomUUID()));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot check out item via proxy when relationship is invalid"),
      hasProxyUserBarcodeParameter(james))));
  }

  @Test
  public void cannotCheckOutByProxyToThemself() {
    val smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    val james = usersFixture.james();

    final Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(james)
        .proxiedBy(james)
        .at(UUID.randomUUID()));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("User cannot be proxy for themself"),
      hasUUIDParameter("proxyUserId", james.getId()))));
  }

  @Test
  public void cannotCheckOutWhenItemCannotBeFound() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    itemsClient.delete(smallAngryPlanet.getId());

    final Response response = checkOutFixture.attemptCheckOutByBarcode(
      smallAngryPlanet, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("No item with barcode 036000291452 could be found"),
      hasItemBarcodeParameter(smallAngryPlanet))));
  }

  @Test
  public void cannotCheckOutWhenItemIsAlreadyCheckedOut() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    final Response response = checkOutFixture.attemptCheckOutByBarcode(
      smallAngryPlanet, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Item is already checked out"),
      hasItemBarcodeParameter(smallAngryPlanet))));
  }

  @Test
  public void canCheckOutMissingItem() {
    final IndividualResource missingItem = setupMissingItem(itemsFixture);
    final IndividualResource steve = usersFixture.steve();

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      missingItem, steve);

    assertThat(response.getJson(), hasJsonPath("status.name", "Open"));
    assertThat(response.getJson(), hasJsonPath("borrower", notNullValue()));
    assertThat(response.getJson(), hasJsonPath("item", notNullValue()));
    assertThat(response.getJson(), hasJsonPath("item.status.name", CHECKED_OUT));
    assertThat(response.getJson(), hasJsonPath("itemId", missingItem.getId().toString()));

    assertThat(itemsClient.getById(missingItem.getId()).getJson(),
      hasJsonPath("status.name", CHECKED_OUT));
  }

  @Test
  public void cannotCheckOutWhenOpenLoanAlreadyExists() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();
    final IndividualResource steve = usersFixture.steve();

    loansStorageClient.create(new LoanBuilder()
      .open()
      .withItemId(smallAngryPlanet.getId())
      .withUserId(jessica.getId()));

    final Response response = checkOutFixture.attemptCheckOutByBarcode(
      smallAngryPlanet, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot check out item that already has an open loan"),
      hasItemBarcodeParameter(smallAngryPlanet))));
  }

  @Test
  public void shouldRejectCheckOutOfItemInDisallowedStatus() {
    final String barcode = String.valueOf(new Random().nextLong());
    final ItemResource claimedReturnedItem = itemsFixture
      .basedUponSmallAngryPlanet((ItemBuilder itemBuilder) -> itemBuilder
        .withBarcode(barcode)
        .claimedReturned());

    final Response response = checkOutFixture
      .attemptCheckOutByBarcode(claimedReturnedItem, usersFixture.steve());

    final String expectedMessage = String.format(
      "%s (Book) (Barcode: %s) has the item status Claimed returned and cannot be checked out",
      claimedReturnedItem.getInstance().getJson().getString("title"), barcode);

    assertThat(response.getJson(), hasErrorWith(allOf(hasMessage(expectedMessage),
      hasItemBarcodeParameter(claimedReturnedItem))));
  }

  @Test
  public void canCheckOutViaProxy() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    proxyRelationshipsFixture.currentProxyFor(jessica, james);

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .proxiedBy(james)
        .at(UUID.randomUUID()));

    JsonObject loan = response.getJson();

    assertThat("user id does not match",
      loan.getString("userId"), is(jessica.getId()));

    assertThat("proxy user id does not match",
      loan.getString("proxyUserId"), is(james.getId()));
  }

  @Test
  public void cannotCheckOutWhenLoanPolicyDoesNotExist() {
    final UUID nonExistentloanPolicyId = UUID.randomUUID();
    IndividualResource record = loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withId(nonExistentloanPolicyId)
      .withName("Example LoanPolicy"));
    useFallbackPolicies(nonExistentloanPolicyId,
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
    loanPoliciesFixture.delete(record);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 18, 11, 43, 54, UTC);

    final Response response = checkOutFixture.attemptCheckOutByBarcode(500,
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(UUID.randomUUID()));

    assertThat(response.getBody(), is(String.format(
      "Loan policy %s could not be found, please check circulation rules",
      nonExistentloanPolicyId)));
  }

  @Test
  public void cannotCheckOutWhenServicePointOfCheckoutNotPresent() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();

    final DateTime loanDate = new DateTime(2018, 3, 18, 11, 43, 54, UTC);

    final Response response = checkOutFixture.attemptCheckOutByBarcode(422,
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(james)
        .on(loanDate));

    assertThat(response.getStatusCode(), is(422));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Check out must be performed at a service point"),
      hasServicePointParameter(null))));
  }

  @Test
  public void canCheckOutUsingItemBarcodeThatContainsSpaces() {
    final IndividualResource steve = usersFixture.steve();
    IndividualResource smallAngryPlanet
      = itemsFixture.basedUponSmallAngryPlanet(item -> item
      .withBarcode("12345 67890"));

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(steve.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutUsingUserBarcodeThatContainsSpaces() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      item -> item
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume"));

    final IndividualResource steve
      = usersFixture.steve(user -> user.withBarcode("12345 67890"));

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(steve.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("has item enumeration",
      loan.getJsonObject("item").getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      loan.getJsonObject("item").getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      loan.getJsonObject("item").getString("volume"), is("testVolume"));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutUsingProxyUserBarcodeThatContainsSpaces() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      item -> item
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume"));

    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource steve
      = usersFixture.steve(user -> user.withBarcode("12345 67890"));

    proxyRelationshipsFixture.currentProxyFor(jessica, steve);

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .proxiedBy(steve)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(jessica.getId()));

    assertThat("proxy user ID should match barcode",
      loan.getString("proxyUserId"), is(steve.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("has item enumeration",
      loan.getJsonObject("item").getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      loan.getJsonObject("item").getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      loan.getJsonObject("item").getString("volume"), is("testVolume"));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutOnOrderItem() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      item -> item
        .onOrder()
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume"));

    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(jessica.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("has item enumeration",
      loan.getJsonObject("item").getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      loan.getJsonObject("item").getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      loan.getJsonObject("item").getString("volume"), is("testVolume"));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutOnOrderItemWithRequest() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      item -> item
        .onOrder()
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume"));

    final IndividualResource jessica = usersFixture.jessica();

    requestsFixture.place(new RequestBuilder()
      .withItemId(smallAngryPlanet.getId())
      .withRequesterId(jessica.getId())
      .withPickupServicePoint(servicePointsFixture.cd1()));

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(jessica.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("has item enumeration",
      loan.getJsonObject("item").getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      loan.getJsonObject("item").getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      loan.getJsonObject("item").getString("volume"), is("testVolume"));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutInProcessItem() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      item -> item
        .inProcess()
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume"));

    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(jessica.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("has item enumeration",
      loan.getJsonObject("item").getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      loan.getJsonObject("item").getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      loan.getJsonObject("item").getString("volume"), is("testVolume"));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutInProcessItemWithRequest() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      item -> item
        .inProcess()
        .withEnumeration("v.70:no.1-6")
        .withChronology("1987:Jan.-June")
        .withVolume("testVolume"));

    final IndividualResource jessica = usersFixture.jessica();

    requestsFixture.place(new RequestBuilder()
      .withItemId(smallAngryPlanet.getId())
      .withRequesterId(jessica.getId())
      .withPickupServicePoint(servicePointsFixture.cd1()));

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(servicePointsFixture.cd1()));

    final JsonObject loan = response.getJson();

    assertThat(loan.getString("id"), is(notNullValue()));

    assertThat("user ID should match barcode",
      loan.getString("userId"), is(jessica.getId()));

    assertThat("item ID should match barcode",
      loan.getString("itemId"), is(smallAngryPlanet.getId()));

    assertThat("status should be open",
      loan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("has item enumeration",
      loan.getJsonObject("item").getString("enumeration"), is("v.70:no.1-6"));

    assertThat("has item chronology",
      loan.getJsonObject("item").getString("chronology"), is("1987:Jan.-June"));

    assertThat("has item volume",
      loan.getJsonObject("item").getString("volume"), is("testVolume"));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void cannotCheckOutWhenItemIsNotLoanable() {
    IndividualResource notLoanablePolicy = loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("Not Loanable Policy")
        .withLoanable(false));

    useFallbackPolicies(
      notLoanablePolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.inactiveNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    ItemResource nod = itemsFixture.basedUponNod();
    IndividualResource steve = usersFixture.steve();
    Response response = checkOutFixture.attemptCheckOutByBarcode(nod, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Item is not loanable"),
      hasItemBarcodeParameter(nod),
      hasLoanPolicyParameters(notLoanablePolicy))));
  }

  @Test
  public void checkOutFailsWhenCirculationRulesReferenceInvalidLoanPolicyId() {
    UUID invalidLoanPolicyId = UUID.randomUUID();
    IndividualResource record = loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withId(invalidLoanPolicyId)
      .withName("Example loanPolicy"));
    setInvalidLoanPolicyReferenceInRules(invalidLoanPolicyId.toString());
    loanPoliciesFixture.delete(record);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 18, 11, 43, 54, UTC);

    final Response response = checkOutFixture.attemptCheckOutByBarcode(500,
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));

    assertThat(response.getBody(),
      is("Loan policy " + invalidLoanPolicyId + " could not be found, please check circulation rules"));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AVAILABLE));
  }

  @Test
  public void checkOutDoesNotFailWhenCirculationRulesReferenceInvalidNoticePolicyId() {
    UUID invalidNoticePolicyId = UUID.randomUUID();
    IndividualResource record = noticePoliciesFixture.create(new NoticePolicyBuilder()
      .withId(invalidNoticePolicyId)
      .withName("Example loanPolicy"));
    setInvalidNoticePolicyReferenceInRules(invalidNoticePolicyId.toString());
    noticePoliciesFixture.delete(record);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final DateTime loanDate = new DateTime(2018, 3, 18, 11, 43, 54, UTC);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(servicePointsFixture.cd1()));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutWhenItemLimitIsIgnoredForRulesWithoutMaterialTypeOrLoanType() {

    useFallbackPolicies(
      prepareLoanPolicyWithItemLimit(1).getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.inactiveNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    ItemResource firstItem = itemsFixture.basedUponNod();
    ItemResource secondItem = itemsFixture.basedUponDunkirk();
    IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(firstItem, steve);
    checkOutFixture.checkOutByBarcode(secondItem, steve);
  }

  @Test
  public void canCheckOutVideoMaterialWhenItemLimitIsReachedForBookMaterialType() {

    final UUID book = materialTypesFixture.book().getId();

    circulationRulesFixture.updateCirculationRules(createRules( "m " + book));

    IndividualResource firstBookTypeItem = itemsFixture.basedUponNod();
    IndividualResource secondBookTypeItem = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource videoTypeItem = itemsFixture.basedUponDunkirk();
    IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(firstBookTypeItem, steve);
    firstBookTypeItem = itemsClient.get(firstBookTypeItem);
    assertThat(firstBookTypeItem, hasItemStatus(CHECKED_OUT));

    Response response = checkOutFixture.attemptCheckOutByBarcode(secondBookTypeItem, steve);
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Patron has reached maximum limit of 1 items for material type"))));
    secondBookTypeItem = itemsClient.get(secondBookTypeItem);
    assertThat(secondBookTypeItem, hasItemStatus(AVAILABLE));

    checkOutFixture.checkOutByBarcode(videoTypeItem, steve);
    videoTypeItem = itemsClient.get(videoTypeItem);
    assertThat(videoTypeItem, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutWhenItemLimitIsReachedForReadingRoomLoanType() {

    final UUID readingRoom = loanTypesFixture.readingRoom().getId();

    circulationRulesFixture.updateCirculationRules(createRules("t " + readingRoom));

    IndividualResource firstBookTypeItem = itemsFixture.basedUponNod(itemBuilder -> itemBuilder.withTemporaryLoanType(readingRoom));
    IndividualResource secondBookTypeItem = itemsFixture.basedUponSmallAngryPlanet(itemBuilder -> itemBuilder.withTemporaryLoanType(readingRoom));
    IndividualResource videoTypeItem = itemsFixture.basedUponDunkirk();
    IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(firstBookTypeItem, steve);
    firstBookTypeItem = itemsClient.get(firstBookTypeItem);
    assertThat(firstBookTypeItem, hasItemStatus(CHECKED_OUT));

    Response response = checkOutFixture.attemptCheckOutByBarcode(secondBookTypeItem, steve);
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Patron has reached maximum limit of 1 items for loan type"))));
    secondBookTypeItem = itemsClient.get(secondBookTypeItem);
    assertThat(secondBookTypeItem, hasItemStatus(AVAILABLE));

    checkOutFixture.checkOutByBarcode(videoTypeItem, steve);
    videoTypeItem = itemsClient.get(videoTypeItem);
    assertThat(videoTypeItem, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutWhenItemLimitIsReachedForBookMaterialTypeAndReadingRoomLoanType() {

    final UUID readingRoom = loanTypesFixture.readingRoom().getId();
    final UUID book = materialTypesFixture.book().getId();

    circulationRulesFixture.updateCirculationRules(createRules("m " + book + " + t " + readingRoom));

    IndividualResource firstBookTypeItem = itemsFixture.basedUponNod(itemBuilder -> itemBuilder.withTemporaryLoanType(readingRoom));
    IndividualResource secondBookTypeItem = itemsFixture.basedUponSmallAngryPlanet(itemBuilder -> itemBuilder.withTemporaryLoanType(readingRoom));
    IndividualResource videoTypeItem = itemsFixture.basedUponDunkirk();
    IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(firstBookTypeItem, steve);
    firstBookTypeItem = itemsClient.get(firstBookTypeItem);
    assertThat(firstBookTypeItem, hasItemStatus(CHECKED_OUT));

    Response response = checkOutFixture.attemptCheckOutByBarcode(secondBookTypeItem, steve);
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Patron has reached maximum limit of 1 items for combination of material type and loan type"))));
    secondBookTypeItem = itemsClient.get(secondBookTypeItem);
    assertThat(secondBookTypeItem, hasItemStatus(AVAILABLE));

    checkOutFixture.checkOutByBarcode(videoTypeItem, steve);
    videoTypeItem = itemsClient.get(videoTypeItem);
    assertThat(videoTypeItem, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutWhenItemLimitIsReachedForBookMaterialTypeAndReadingRoomLoanTypeAndPatronGroup() {

    final UUID readingRoom = loanTypesFixture.readingRoom().getId();
    final UUID book = materialTypesFixture.book().getId();
    final UUID regular = patronGroupsFixture.regular().getId();

    circulationRulesFixture.updateCirculationRules(createRules("m " + book + " + t " + readingRoom + " + g " + regular));

    IndividualResource firstBookTypeItem = itemsFixture.basedUponNod(
      itemBuilder -> itemBuilder.withTemporaryLoanType(readingRoom));
    IndividualResource secondBookTypeItem = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder -> itemBuilder.withTemporaryLoanType(readingRoom));
    IndividualResource videoTypeItem = itemsFixture.basedUponDunkirk();
    IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(firstBookTypeItem, steve);
    firstBookTypeItem = itemsClient.get(firstBookTypeItem);
    assertThat(firstBookTypeItem, hasItemStatus(CHECKED_OUT));

    Response response = checkOutFixture.attemptCheckOutByBarcode(secondBookTypeItem, steve);
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Patron has reached maximum limit of 1 items for combination of patron group, material type and loan type"))));
    secondBookTypeItem = itemsClient.get(secondBookTypeItem);
    assertThat(secondBookTypeItem, hasItemStatus(AVAILABLE));

    checkOutFixture.checkOutByBarcode(videoTypeItem, steve);
    videoTypeItem = itemsClient.get(videoTypeItem);
    assertThat(videoTypeItem, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutWhenItemLimitIsReachedForBookMaterialTypeAndCanCirculateLoanTypeInMultipleLinesRules() {

    final String loanPolicyWithItemLimitId = prepareLoanPolicyWithItemLimit(1).getId().toString();
    final String anyRequestPolicy = requestPoliciesFixture.allowAllRequestPolicy().getId().toString();
    final String anyNoticePolicy = noticePoliciesFixture.activeNotice().getId().toString();
    final String anyOverdueFinePolicy = overdueFinePoliciesFixture.facultyStandard().getId().toString();
    final String anyLostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard().getId().toString();
    final UUID canCirculate = loanTypesFixture.canCirculate().getId();
    final UUID readingRoom = loanTypesFixture.readingRoom().getId();
    final UUID book = materialTypesFixture.book().getId();
    final UUID regular = patronGroupsFixture.regular().getId();

    String nestedRuleCanCirculate = "    t " + canCirculate + " + g " + regular +
      " : l " + loanPolicyWithItemLimitId + " r " + anyRequestPolicy + " n " + anyNoticePolicy  + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy;
    String nestedRuleReadingRoom = "    t " + readingRoom + " + g " + regular +
      " : l " + loanPolicyWithItemLimitId + " r " + anyRequestPolicy + " n " + anyNoticePolicy  + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy;
    String rules = createRules("m " + book) + "\n" + nestedRuleCanCirculate + "\n" + nestedRuleReadingRoom;
    circulationRulesFixture.updateCirculationRules(rules);

    IndividualResource firstBookTypeItem = itemsFixture.basedUponNod(itemBuilder -> itemBuilder.withTemporaryLoanType(canCirculate));
    IndividualResource secondBookTypeItem = itemsFixture.basedUponSmallAngryPlanet(itemBuilder -> itemBuilder.withTemporaryLoanType(canCirculate));
    IndividualResource bookTypeItemReadingRoomLoanType = itemsFixture.basedUponInterestingTimes(itemBuilder -> itemBuilder.withTemporaryLoanType(readingRoom));
    IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(firstBookTypeItem, steve);
    firstBookTypeItem = itemsClient.get(firstBookTypeItem);
    assertThat(firstBookTypeItem, hasItemStatus(CHECKED_OUT));

    Response response = checkOutFixture.attemptCheckOutByBarcode(secondBookTypeItem, steve);
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Patron has reached maximum limit of 1 items for combination of patron group, material type and loan type"))));
    secondBookTypeItem = itemsClient.get(secondBookTypeItem);
    assertThat(secondBookTypeItem, hasItemStatus(AVAILABLE));

    checkOutFixture.checkOutByBarcode(bookTypeItemReadingRoomLoanType, steve);
    bookTypeItemReadingRoomLoanType = itemsClient.get(bookTypeItemReadingRoomLoanType);
    assertThat(bookTypeItemReadingRoomLoanType, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void cannotCheckOutWhenItemLimitIsReachedForBookMaterialTypeAndLoanTypeIsNotPresent() {

    final UUID readingRoom = loanTypesFixture.readingRoom().getId();
    final UUID canCirculate = loanTypesFixture.canCirculate().getId();
    final UUID book = materialTypesFixture.book().getId();

    circulationRulesFixture.updateCirculationRules(createRules("m " + book));

    IndividualResource firstBookTypeItem = itemsFixture.basedUponNod(itemBuilder -> itemBuilder.withTemporaryLoanType(readingRoom));
    IndividualResource secondBookTypeItem = itemsFixture.basedUponSmallAngryPlanet(itemBuilder -> itemBuilder.withTemporaryLoanType(canCirculate));
    IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(firstBookTypeItem, steve);
    firstBookTypeItem = itemsClient.get(firstBookTypeItem);
    assertThat(firstBookTypeItem, hasItemStatus(CHECKED_OUT));

    Response response = checkOutFixture.attemptCheckOutByBarcode(secondBookTypeItem, steve);
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Patron has reached maximum limit of 1 items for material type"))));
    secondBookTypeItem = itemsClient.get(secondBookTypeItem);
    assertThat(secondBookTypeItem, hasItemStatus(AVAILABLE));
  }

  @Test
  public void cannotCheckOutWhenItemLimitIsReachedForBookMaterialTypeWithFixedDueDateSchedule() {

    final UUID book = materialTypesFixture.book().getId();

    circulationRulesFixture.updateCirculationRules(
      createRulesWithFixedDueDateInLoanPolicy( "m " + book));

    IndividualResource firstBookTypeItem = itemsFixture.basedUponNod();
    IndividualResource secondBookTypeItem = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource videoTypeItem = itemsFixture.basedUponDunkirk();
    IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(firstBookTypeItem, steve);
    firstBookTypeItem = itemsClient.get(firstBookTypeItem);
    assertThat(firstBookTypeItem, hasItemStatus(CHECKED_OUT));

    Response response = checkOutFixture.attemptCheckOutByBarcode(secondBookTypeItem, steve);
    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "Patron has reached maximum limit of 1 items for material type")));

    secondBookTypeItem = itemsClient.get(secondBookTypeItem);
    assertThat(secondBookTypeItem, hasItemStatus(AVAILABLE));

    checkOutFixture.checkOutByBarcode(videoTypeItem, steve);
    videoTypeItem = itemsClient.get(videoTypeItem);
    assertThat(videoTypeItem, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutWhenItemLimitWasReachedForBookMaterialAndItemIsClaimedReturned() {
    final UUID book = materialTypesFixture.book().getId();

    circulationRulesFixture.updateCirculationRules(createRules( "m " + book));

    IndividualResource firstBookTypeItem = itemsFixture.basedUponNod();
    IndividualResource secondBookTypeItem = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource steve = usersFixture.steve();

    IndividualResource firstLoan =
      checkOutFixture.checkOutByBarcode(firstBookTypeItem, steve);
    firstBookTypeItem = itemsClient.get(firstBookTypeItem);
    assertThat(firstBookTypeItem, hasItemStatus(CHECKED_OUT));

    Response response = checkOutFixture.attemptCheckOutByBarcode(secondBookTypeItem, steve);
    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "Patron has reached maximum limit of 1 items for material type")));

    secondBookTypeItem = itemsClient.get(secondBookTypeItem);
    assertThat(secondBookTypeItem, hasItemStatus(AVAILABLE));

    claimItemReturnedFixture.claimItemReturned(firstLoan.getId());
    firstBookTypeItem = itemsClient.get(firstBookTypeItem);
    assertThat(firstBookTypeItem, hasItemStatus(CLAIMED_RETURNED));

    checkOutFixture.checkOutByBarcode(secondBookTypeItem, steve);
    secondBookTypeItem = itemsClient.get(secondBookTypeItem);
    assertThat(secondBookTypeItem, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void canCheckOutWithdrawnItem() {
    final IndividualResource withdrawnItem = itemsFixture
      .basedUponSmallAngryPlanet(ItemBuilder::withdrawn);

    assertThat(withdrawnItem.getJson(), isWithdrawn());

    final IndividualResource response = checkOutFixture
      .checkOutByBarcode(withdrawnItem, usersFixture.steve());

    assertThat(response.getJson(), allOf(
      isOpen(),
      hasJsonPath("action", "checkedout"),
      hasJsonPath("itemId", withdrawnItem.getId().toString())
    ));

    assertThat(itemsClient.getById(withdrawnItem.getId()).getJson(), isCheckedOut());
  }

  @Test
  public void canCheckOutLostAndPaidItem() {
    final IndividualResource withdrawnItem = itemsFixture
      .basedUponSmallAngryPlanet(ItemBuilder::lostAndPaid);

    assertThat(withdrawnItem.getJson(), isLostAndPaid());

    final IndividualResource response = checkOutFixture
      .checkOutByBarcode(withdrawnItem, usersFixture.steve());

    assertThat(response.getJson(), allOf(
      isOpen(),
      hasJsonPath("action", "checkedout"),
      hasJsonPath("itemId", withdrawnItem.getId().toString())));

    assertThat(itemsClient.getById(withdrawnItem.getId()).getJson(), isCheckedOut());
  }

  @Test
  public void itemCheckedOutEventIsPublished() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(DateTime.now(UTC))
        .at(UUID.randomUUID()));

    final JsonObject loan = response.getJson();

    final var publishedEvents = Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(2));

    final var checkedOutEvent = publishedEvents.findFirst(byEventType(ITEM_CHECKED_OUT.name()));

    assertThat(checkedOutEvent, isValidItemCheckedOutEvent(loan));

    final var checkOutLogEvent = publishedEvents.findFirst(byLogEventType(CHECK_OUT.value()));

    assertThat(checkOutLogEvent, isValidCheckOutLogEvent(loan));
    assertThatPublishedLoanLogRecordEventsAreValid();
  }

  @Test
  public void checkOutRefusedWhenAutomatedBlockExistsForPatron() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    automatedPatronBlocksFixture.blockAction(steve.getId().toString(), true, false, false);

    final Response response = checkOutFixture.attemptCheckOutByBarcode(smallAngryPlanet, steve);

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage(MAX_NUMBER_OF_ITEMS_CHARGED_OUT_MESSAGE))));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage(MAX_OUTSTANDING_FEE_FINE_BALANCE_MESSAGE))));
  }

  @Test
  public void checkOutFailsWhenEventPublishingFailsWithBadRequestError() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    FakePubSub.setFailPublishingWithBadRequestError(true);

    Response response = checkOutFixture.attemptCheckOutByBarcode(500,
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(DateTime.now(UTC))
        .at(UUID.randomUUID()));

    assertThat(response.getBody(), containsString(
      "Error during publishing Event Message in PubSub. Status code: 400"));
  }

  @Test
  public void failedCheckOutWithMultipleValidationErrors() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UserResource steve = usersFixture.steve();

    IndividualResource loanPolicy = loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withId(UUID.randomUUID())
      .withName("Example loan policy")
      .withLoanable(true)
    );

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(DateTime.now(UTC))
        .at(UUID.randomUUID()));

    usersFixture.remove(steve);

    final Response secondCheckoutResponse = checkOutFixture.attemptCheckOutByBarcode(
      smallAngryPlanet, steve);

    assertThat(secondCheckoutResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Could not find user with matching barcode"),
      hasUserBarcodeParameter(steve))));

    assertThat(secondCheckoutResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Item is already checked out"),
      hasItemBarcodeParameter(smallAngryPlanet))));

    assertThat(secondCheckoutResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Cannot check out item that already has an open loan"),
      hasItemBarcodeParameter(smallAngryPlanet))));
  }

  @Test
  public void cannotOverrideItemNotLoanableBlockWhenOverrideBlocksIsNotPresent() {
    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION);

    setNotLoanablePolicy();
    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .on(TEST_LOAN_DATE), okapiHeaders);

    assertThat(response.getJson(), hasErrorWith(allOf(hasMessage("Item is not loanable"),
      hasParameter("loanPolicyName", "Not Loanable Policy"))));
  }

  @Test
  public void cannotOverrideItemNotLoanableBlockWhenUserDoesNotHavePermissions() {
    setNotLoanablePolicy();
    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .on(TEST_LOAN_DATE)
        .withOverrideBlocks(
          new BlockOverrides(new ItemNotLoanableBlockOverride(true, TEST_DUE_DATE),
            null, new ItemLimitBlockOverride(false), TEST_COMMENT)));
    assertThat(response.getJson(), hasErrorWith(hasMessage(INSUFFICIENT_OVERRIDE_PERMISSIONS)));
    assertThat(getMissingPermissions(response), hasSize(1));
    assertThat(getMissingPermissions(response),
      hasItem(OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION));
  }

  @Test
  public void cannotOverrideItemNotLoanableBlockWhenUserDoesNotHaveRequiredPermissions() {
    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_PATRON_BLOCK_PERMISSION);

    setNotLoanablePolicy();
    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .on(TEST_LOAN_DATE).withOverrideBlocks(new BlockOverrides(
        new ItemNotLoanableBlockOverride(true, TEST_DUE_DATE), null, null, TEST_COMMENT)),
      okapiHeaders);

    assertThat(response.getJson(), hasErrorWith(hasMessage(INSUFFICIENT_OVERRIDE_PERMISSIONS)));
    assertThat(getMissingPermissions(response), hasSize(1));
    assertThat(getMissingPermissions(response),
      hasItem(OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION));
  }

  @Test
  public void cannotOverrideItemNotLoanableBlockAndPatronBlockWhenUserDoesNotHavePermissions() {
    setNotLoanablePolicy();
    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .on(TEST_LOAN_DATE).withOverrideBlocks(new BlockOverrides(
        new ItemNotLoanableBlockOverride(true, TEST_DUE_DATE), new PatronBlockOverride(true),
        null, TEST_COMMENT)));

    assertThat(response.getJson(), hasErrorWith(hasMessage(INSUFFICIENT_OVERRIDE_PERMISSIONS)));
    assertThat(getMissingPermissions(response), hasSize(2));
    assertThat(getMissingPermissions(response), hasItems(OVERRIDE_PATRON_BLOCK_PERMISSION,
      OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION));
  }

  @Test
  public void cannotOverrideItemLimitBlockWhenUserDoesNotHavePermissions() {
    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponNod())
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .withOverrideBlocks(new BlockOverrides(
          null, null, new ItemLimitBlockOverride(true), TEST_COMMENT)));

    assertThat(response.getJson(), hasErrorWith(hasMessage(INSUFFICIENT_OVERRIDE_PERMISSIONS)));
    assertThat(getMissingPermissions(response), hasSize(1));
    assertThat(getMissingPermissions(response), hasItem(OVERRIDE_ITEM_LIMIT_BLOCK_PERMISSION));
  }

  @Test
  public void cannotOverridePatronBlockWhenUserDoesNotHavePermissions() {
    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponNod())
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .withOverrideBlocks(new BlockOverrides(
          null, new PatronBlockOverride(true), null, TEST_COMMENT)));

    assertThat(response.getJson(), hasErrorWith(hasMessage(INSUFFICIENT_OVERRIDE_PERMISSIONS)));
    assertThat(getMissingPermissions(response), hasSize(1));
    assertThat(getMissingPermissions(response), hasItem(OVERRIDE_PATRON_BLOCK_PERMISSION));
  }

  @Test
  public void cannotOverridePatronBlockWhenUserDoesNotHaveRequiredPermissions() {
    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_ITEM_LIMIT_BLOCK_PERMISSION);

    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponNod())
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .withOverrideBlocks(new BlockOverrides(
          null, new PatronBlockOverride(true), null, TEST_COMMENT)),
      okapiHeaders);

    assertThat(response.getJson(), hasErrorWith(hasMessage(INSUFFICIENT_OVERRIDE_PERMISSIONS)));
    assertThat(getMissingPermissions(response), hasSize(1));
    assertThat(getMissingPermissions(response), hasItem(OVERRIDE_PATRON_BLOCK_PERMISSION));
  }

  @Test
  public void canOverrideCheckoutWhenItemLimitWasReachedForBookMaterialType() {
    circulationRulesFixture.updateCirculationRules(createRules(
      "m " + materialTypesFixture.book().getId()));
    IndividualResource firstBookTypeItem = itemsFixture.basedUponNod();
    IndividualResource secondBookTypeItem = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource steve = usersFixture.steve();

    checkOutFixture.checkOutByBarcode(firstBookTypeItem, steve);
    firstBookTypeItem = itemsClient.get(firstBookTypeItem);
    assertThat(firstBookTypeItem, hasItemStatus(CHECKED_OUT));

    Response response = checkOutFixture.attemptCheckOutByBarcode(secondBookTypeItem, steve);
    assertThat(response.getJson(), hasErrorWith(
      hasMessage("Patron has reached maximum limit of 1 items for material type")));

    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_ITEM_LIMIT_BLOCK_PERMISSION);
    JsonObject loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(secondBookTypeItem)
        .to(steve)
        .at(UUID.randomUUID())
        .withOverrideBlocks(new BlockOverrides(
          null, null, new ItemLimitBlockOverride(true), TEST_COMMENT)),
      okapiHeaders).getJson();

    secondBookTypeItem = itemsClient.get(secondBookTypeItem);
    assertThat(secondBookTypeItem, hasItemStatus(CHECKED_OUT));
    assertThat(loan.getString("actionComment"), is(TEST_COMMENT));
    assertThat(loan.getString("action"), is(CHECKED_OUT_THROUGH_OVERRIDE));
  }

  @Test
  public void canOverrideCheckOutWhenItemNotLoanableBlockIsPresent() {
    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_ITEM_NOT_LOANABLE_BLOCK_PERMISSION);

    setNotLoanablePolicy();
    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    Response responseBlocked = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .on(TEST_LOAN_DATE)
        .withOverrideBlocks(new BlockOverrides(null, null, null, TEST_COMMENT)),
      okapiHeaders);

    assertThat(responseBlocked.getJson(), hasErrorWith(allOf(hasMessage("Item is not loanable"),
      hasParameter("loanPolicyName", "Not Loanable Policy"))));

    JsonObject loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(usersFixture.steve())
        .at(UUID.randomUUID())
        .on(TEST_LOAN_DATE)
        .withOverrideBlocks(new BlockOverrides(
          new ItemNotLoanableBlockOverride(true, new DateTime(TEST_DUE_DATE)),
          null, null, TEST_COMMENT)),
      okapiHeaders).getJson();

    item = itemsClient.get(item);
    assertThat(item, hasItemStatus(CHECKED_OUT));
    assertThat(loan.getString("actionComment"), is(TEST_COMMENT));
    assertThat(loan.getString("action"), is(CHECKED_OUT_THROUGH_OVERRIDE));
  }

  @Test
  public void canOverrideCheckOutWhenAutomatedBlockIsPresent() {
    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    automatedPatronBlocksFixture.blockAction(steve.getId().toString(), true, false, false);

    final Response response = checkOutFixture.attemptCheckOutByBarcode(item, steve);

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(),
      hasErrorWith(hasMessage(MAX_NUMBER_OF_ITEMS_CHARGED_OUT_MESSAGE)));
    assertThat(response.getJson(),
      hasErrorWith(hasMessage(MAX_OUTSTANDING_FEE_FINE_BALANCE_MESSAGE)));

    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_PATRON_BLOCK_PERMISSION);
    JsonObject loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(steve)
        .at(UUID.randomUUID())
        .on(TEST_LOAN_DATE)
        .withOverrideBlocks(new BlockOverrides(
          null, new PatronBlockOverride(true), null, TEST_COMMENT)),
      okapiHeaders).getJson();

    item = itemsClient.get(item);
    assertThat(item, hasItemStatus(CHECKED_OUT));
    assertThat(loan.getString("actionComment"), is(TEST_COMMENT));
    assertThat(loan.getString("action"), is(CHECKED_OUT_THROUGH_OVERRIDE));
  }

  @Test
  public void cannotCheckOutWhenItemIsAlreadyCheckedOutAndNoPatronBlockOverridePermissions() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource jessica = usersFixture.jessica();
    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    final IndividualResource steve = usersFixture.steve();
    automatedPatronBlocksFixture.blockAction(steve.getId().toString(), true, false, false);
    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(UUID.randomUUID())
        .on(TEST_LOAN_DATE)
        .withOverrideBlocks(new BlockOverrides(null, new PatronBlockOverride(true), null,
          TEST_COMMENT)));

    assertThat(response.getJson(), hasErrorWith(hasMessage(INSUFFICIENT_OVERRIDE_PERMISSIONS)));
    assertThat(response.getJson(), hasErrorWith(hasMessage("Item is already checked out")));
    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "Cannot check out item that already has an open loan")));
    assertThat(getMissingPermissions(response), hasSize(1));
    assertThat(getMissingPermissions(response), hasItem(OVERRIDE_PATRON_BLOCK_PERMISSION));
  }

  @Test
  public void canOverrideManualPatronBlockWhenBlockIsPresent() {
    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    userManualBlocksFixture.createManualPatronBlockForUser(steve.getId());

    final Response response = checkOutFixture.attemptCheckOutByBarcode(item, steve);

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(),
      hasErrorWith(hasMessage(PATRON_WAS_BLOCKED_MESSAGE)));

    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_PATRON_BLOCK_PERMISSION);
    JsonObject loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(steve)
        .at(UUID.randomUUID())
        .on(TEST_LOAN_DATE)
        .withOverrideBlocks(new BlockOverrides(
          null, new PatronBlockOverride(true), null, TEST_COMMENT)),
      okapiHeaders).getJson();

    item = itemsClient.get(item);
    assertThat(item, hasItemStatus(CHECKED_OUT));
    assertThat(loan.getString("actionComment"), is(TEST_COMMENT));
    assertThat(loan.getString("action"), is(CHECKED_OUT_THROUGH_OVERRIDE));
  }

  @Test
  public void canOverrideManualAndAutomationPatronBlocksWhenBlocksArePresent() {
    IndividualResource item = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    userManualBlocksFixture.createManualPatronBlockForUser(steve.getId());
    automatedPatronBlocksFixture.blockAction(steve.getId().toString(), true, false, false);

    final Response response = checkOutFixture.attemptCheckOutByBarcode(item, steve);

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(),
      hasErrorWith(hasMessage(PATRON_WAS_BLOCKED_MESSAGE)));
    assertThat(response.getJson(),
      hasErrorWith(hasMessage(MAX_NUMBER_OF_ITEMS_CHARGED_OUT_MESSAGE)));
    assertThat(response.getJson(),
      hasErrorWith(hasMessage(MAX_OUTSTANDING_FEE_FINE_BALANCE_MESSAGE)));

    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(
      OVERRIDE_PATRON_BLOCK_PERMISSION);
    JsonObject loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(steve)
        .at(UUID.randomUUID())
        .on(TEST_LOAN_DATE)
        .withOverrideBlocks(new BlockOverrides(
          null, new PatronBlockOverride(true), null, TEST_COMMENT)),
      okapiHeaders).getJson();

    item = itemsClient.get(item);
    assertThat(item, hasItemStatus(CHECKED_OUT));
    assertThat(loan.getString("actionComment"), is(TEST_COMMENT));
    assertThat(loan.getString("action"), is(CHECKED_OUT_THROUGH_OVERRIDE));
  }

  private IndividualResource prepareLoanPolicyWithItemLimit(int itemLimit) {
    return loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("Loan Policy with item limit")
        .withItemLimit(itemLimit)
        .rolling(months(2))
        .renewFromCurrentDueDate());
  }

  private IndividualResource prepareLoanPolicyWithItemLimitAndFixedDueDate(
    int itemLimit, UUID fixedDueDateScheduleId) {
    return loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("Loan Policy with item limit and fixed due date")
        .withItemLimit(itemLimit)
        .fixed(fixedDueDateScheduleId)
        .withClosedLibraryDueDateManagement(KEEP_THE_CURRENT_DUE_DATE.getValue()));
  }

  private IndividualResource prepareLoanPolicyWithoutItemLimit() {
    return loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("Loan Policy without item limit")
        .rolling(months(2))
        .renewFromCurrentDueDate());
  }

  private String createRules(String ruleCondition) {
    final String loanPolicyWithItemLimitId = prepareLoanPolicyWithItemLimit(1).getId().toString();
    final String loanPolicyWithoutItemLimitId = prepareLoanPolicyWithoutItemLimit().getId().toString();
    final String anyRequestPolicy = requestPoliciesFixture.allowAllRequestPolicy().getId().toString();
    final String anyNoticePolicy = noticePoliciesFixture.activeNotice().getId().toString();
    final String anyOverdueFinePolicy = overdueFinePoliciesFixture.facultyStandard().getId().toString();
    final String anyLostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard().getId().toString();

    return String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy: l " + loanPolicyWithoutItemLimitId + " r " + anyRequestPolicy + " n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy,
      ruleCondition + " : l " + loanPolicyWithItemLimitId + " r " + anyRequestPolicy + " n " + anyNoticePolicy  + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy);
  }

  private String createRulesWithFixedDueDateInLoanPolicy(String ruleCondition) {
    UUID fixedDueDateScheduleId = loanPoliciesFixture.createExampleFixedDueDateSchedule().getId();
    final String loanPolicyWithItemLimitAndFixedDueDateId = prepareLoanPolicyWithItemLimitAndFixedDueDate(
      1, fixedDueDateScheduleId).getId().toString();
    final String loanPolicyWithoutItemLimitId = prepareLoanPolicyWithoutItemLimit().getId().toString();
    final String anyRequestPolicy = requestPoliciesFixture.allowAllRequestPolicy().getId().toString();
    final String anyNoticePolicy = noticePoliciesFixture.activeNotice().getId().toString();
    final String anyOverdueFinePolicy = overdueFinePoliciesFixture.facultyStandard().getId().toString();
    final String anyLostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard().getId().toString();

    return String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy: l " + loanPolicyWithoutItemLimitId + " r " + anyRequestPolicy + " n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy,
      ruleCondition + " : l " + loanPolicyWithItemLimitAndFixedDueDateId + " r " + anyRequestPolicy + " n " + anyNoticePolicy  + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy);
  }

  private OkapiHeaders buildOkapiHeadersWithPermissions(String permissions) {
    return getOkapiHeadersFromContext()
      .withRequestId("override-check-out-by-barcode-request")
      .withOkapiPermissions("[\"" + permissions + "\"]");
  }

  private void setNotLoanablePolicy() {
    LoanPolicyBuilder notLoanablePolicy = new LoanPolicyBuilder()
      .withName("Not Loanable Policy")
      .withLoanable(false)
      .notRenewable();

    useFallbackPolicies(
      loanPoliciesFixture.create(notLoanablePolicy).getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.inactiveNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());
  }
}
