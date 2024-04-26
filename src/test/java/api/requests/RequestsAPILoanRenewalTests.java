package api.requests;

import static api.support.builders.FixedDueDateSchedule.wholeMonth;
import static api.support.matchers.DateTimeMatchers.isEquivalentTo;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasErrors;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static api.support.utl.BlockOverridesUtils.OVERRIDE_RENEWAL_PERMISSION;
import static api.support.utl.BlockOverridesUtils.buildOkapiHeadersWithPermissions;
import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.domain.RequestType.HOLD;
import static org.folio.circulation.resources.RenewalValidator.CAN_NOT_RENEW_ITEM_ERROR;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.utils.ClockUtil.getLocalDate;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.TlrFeatureStatus;
import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.CheckOutResource;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.OkapiHeaders;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;

class RequestsAPILoanRenewalTests extends APITests {

  private static final String ITEMS_CANNOT_BE_RENEWED_MSG = "items cannot be renewed when there is an active recall request";
  private static final String EXPECTED_REASON_LOAN_IS_NOT_RENEWABLE = "loan is not renewable";
  private static final String EXPECTED_REASON_LOAN_IS_NOT_LOANABLE = "item is not loanable";
  private static final int DEFAULT_LOAN_PERIOD_WEEKS = 3;
  private static final int ALTERNATE_RENEWAL_LOAN_PERIOD_WEEKS = 4;

  @Test
  void forbidRenewalLoanByBarcodeWhenFirstRequestInQueueIsRecall() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    Response response = loansFixture.attemptRenewal(smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(hasMessage(ITEMS_CANNOT_BE_RENEWED_MSG)));
  }

  @Test
  void allowRenewalLoanByBarcodeWhenProfileIsRollingFirstRequestInQueueIsHoldAndRenewingIsAllowedInLoanPolicy() {
    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    CheckOutResource initialLoan = checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    useRollingPolicyWithRenewingAllowedForHoldingRequest();

    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    Response response = loansFixture.attemptRenewal(200, smallAngryPlanet, rebecca);
    assertThat(response.getJson().getString("action"), is("renewed"));
    // Assert no validation issues, so the renewal is allowed
    assertThat(response.getJson().getJsonArray("errors"), nullValue());

    assertThat(getDateTimeProperty(response.getJson(), "dueDate"),
      isEquivalentTo(initialLoan.getDueDate().plusWeeks(ALTERNATE_RENEWAL_LOAN_PERIOD_WEEKS)));
  }

  @Test
  void forbidRenewalLoanByBarcodeWhenProfileIsRollingFirstRequestInQueueIsHoldAndRenewingIsDisallowedInLoanPolicy() {
    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loanPolicyWithRollingProfileAndRenewingIsForbiddenWhenHoldIsPending();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);
    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));

    Response response = loansFixture.attemptRenewalById(smallAngryPlanet, rebecca);

    String message = response.getJson().getJsonArray("errors").getJsonObject(0).getString("message");
    assertThat(message, is(CAN_NOT_RENEW_ITEM_ERROR));
  }

  @Test
  void allowRenewalWithHoldsWhenProfileIsRollingUseLoanPeriod() {
    final int renewalPeriod = 90;
    final ZonedDateTime expectedDueDate = getZonedDateTime().plusWeeks(renewalPeriod);
    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    final LoanPolicyBuilder rollingPolicy = new LoanPolicyBuilder()
      .withName("Can Circulate Rolling with holding renewal only")
      .withDescription("Can circulate item")
      .withHolds(new JsonObject().put("renewItemsWithRequest", true))
      .rolling(Period.weeks(renewalPeriod))
      .unlimitedRenewals()
      .renewFromSystemDate();

    useWithActiveNotice(rollingPolicy);

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));

    IndividualResource response = loansFixture.renewLoan(smallAngryPlanet, rebecca);

    assertThat(response.getJson().getString("action"), is("renewed"));
    // Assert no validation issues, so the renewal is allowed
    assertThat(response.getJson().getJsonArray("errors"), nullValue());

    assertThat(response.getJson().getString("dueDate"),
      is(withinSecondsAfter(15, expectedDueDate)));
  }

  @Test
  void allowRenewalWithHoldsWhenProfileIsRollingUseRenewalPeriod() {
    final int renewalPeriod = 60;
    final ZonedDateTime expectedDueDate = getZonedDateTime().plusWeeks(renewalPeriod);
    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    final LoanPolicyBuilder rollingPolicy = new LoanPolicyBuilder()
      .withName("Can Circulate Rolling with holding renewals policy")
      .withDescription("Can circulate item")
      .withHolds(new JsonObject().put("renewItemsWithRequest", true))
      .rolling(Period.weeks(90))
      .unlimitedRenewals()
      .renewFromSystemDate()
      .renewWith(Period.weeks(renewalPeriod));

    useWithActiveNotice(rollingPolicy);

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));

    IndividualResource response = loansFixture.renewLoan(smallAngryPlanet, rebecca);

    assertThat(response.getJson().getString("action"), is("renewed"));
    // Assert no validation issues, so the renewal is allowed
    assertThat(response.getJson().getJsonArray("errors"), nullValue());

    assertThat(response.getJson().getString("dueDate"),
      is(withinSecondsAfter(15, expectedDueDate)));
  }


  @Test
  void forbidRenewalLoanByBarcodeWhenLoanProfileIsFixedFirstRequestInQueueIsHoldAndRenewingIsDisallowedInLoanPolicy() {
    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    loanPolicyWithFixedProfileAndRenewingIsForbiddenWhenHoldIsPending();

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));


    Response response = loansFixture.attemptRenewal(smallAngryPlanet, rebecca);

    String message = response.getJson().getJsonArray("errors").getJsonObject(0).getString("message");
    assertThat(message, is(CAN_NOT_RENEW_ITEM_ERROR));
  }

  @Test
  void forbidRenewalLoanByIdWhenFirstRequestInQueueIsRecall() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    Response response = loansFixture.attemptRenewal(smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(hasMessage(ITEMS_CANNOT_BE_RENEWED_MSG)));
  }

  @Test
  void allowRenewalLoanByIdWhenProfileIsRollingFirstRequestInQueueIsHoldAndRenewingIsAllowedInLoanPolicy() {
    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    CheckOutResource initialLoan = checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    useRollingPolicyWithRenewingAllowedForHoldingRequest();

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));


    IndividualResource response = loansFixture.renewLoanById(smallAngryPlanet, rebecca);

    assertThat(response.getJson().getString("action"), is("renewed"));
    // Assert no validation issues, so the renewal is allowed
    assertThat(response.getJson().getJsonArray("error"), nullValue());

    assertThat(getDueDate(response),
      isEquivalentTo(initialLoan.getDueDate().plusWeeks(ALTERNATE_RENEWAL_LOAN_PERIOD_WEEKS)));
  }

  @Test
  void forbidRenewalLoanByIdWhenLoanProfileIsRollingFirstRequestInQueueIsHoldAndRenewingIsDisallowedInLoanPolicy() {
    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    loanPolicyWithRollingProfileAndRenewingIsForbiddenWhenHoldIsPending();

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));


    Response response = loansFixture.attemptRenewalById(smallAngryPlanet, rebecca);

    String message = response.getJson().getJsonArray("errors").getJsonObject(0).getString("message");
    assertThat(message, is(CAN_NOT_RENEW_ITEM_ERROR));
  }

  @Test
  void forbidRenewalLoanByIdWhenLoanProfileIsFixedFirstRequestInQueueIsHoldAndRenewingIsDisallowedInLoanPolicy() {
    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    loanPolicyWithFixedProfileAndRenewingIsForbiddenWhenHoldIsPending();

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));


    Response response = loansFixture.attemptRenewalById(smallAngryPlanet, rebecca);

    String message = response.getJson().getJsonArray("errors").getJsonObject(0).getString("message");
    assertThat(message, is(CAN_NOT_RENEW_ITEM_ERROR));
  }

  @Test
  void allowRenewalWhenFirstRequestInQueueIsItemLevelHoldForDifferentItemOfSameInstance() {
    settingsFixture.enableTlrFeature();
    loanPolicyWithRollingProfileAndRenewingIsForbiddenWhenHoldIsPending();
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    ItemResource itemForLoan = items.get(0);
    ItemResource itemForRequest = items.get(1);
    UserResource borrower = usersFixture.charlotte();
    checkOutFixture.checkOutByBarcode(itemForLoan, borrower);
    checkOutFixture.checkOutByBarcode(itemForRequest, usersFixture.jessica()); // to allow Hold
    requestsFixture.placeItemLevelHoldShelfRequest(itemForRequest, usersFixture.steve());
    loansFixture.renewLoan(itemForLoan, borrower);
  }

  @Test
  void allowRenewalWhenFirstRequestInQueueIsTitleLevelHoldForDifferentItemOfSameInstance() {
    settingsFixture.enableTlrFeature();
    loanPolicyWithRollingProfileAndRenewingIsForbiddenWhenHoldIsPending();
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    ItemResource itemForLoan = items.get(0);
    ItemResource itemForRequest = items.get(1);
    UUID instanceId = itemForLoan.getInstanceId(); // same for both items
    UserResource borrower = usersFixture.charlotte();
    checkOutFixture.checkOutByBarcode(itemForLoan, borrower);
    checkOutFixture.checkOutByBarcode(itemForRequest, usersFixture.jessica()); // to allow Hold
    IndividualResource hold = requestsFixture.placeTitleLevelRequest(HOLD, instanceId, usersFixture.steve());
    checkInFixture.checkInByBarcode(itemForRequest);
    assertThat(requestsFixture.getById(hold.getId()).getJson().getString("itemId"),
      is(itemForRequest.getId().toString()));
    loansFixture.renewLoan(itemForLoan, borrower);
  }

  @Test
  void forbidRenewalWhenFirstRequestInQueueIsTitleLevelHoldWithoutItemId() {
    settingsFixture.enableTlrFeature();
    loanPolicyWithRollingProfileAndRenewingIsForbiddenWhenHoldIsPending();
    ItemResource item = itemsFixture.basedUponNod();
    UserResource borrower = usersFixture.charlotte();
    checkOutFixture.checkOutByBarcode(item, borrower);
    requestsFixture.placeTitleLevelRequest(HOLD, item.getInstanceId(), usersFixture.steve());
    Response renewalResponse = loansFixture.attemptRenewal(422, item, borrower);

    assertThat(renewalResponse.getJson(), hasErrorWith(hasMessage(
      "Items with this loan policy cannot be renewed when there is an active, pending hold request")));
  }

  @Test
  void alternateLoanPeriodIsNotUsedWhenFirstRequestInQueueIsItemLevelHoldForDifferentItemOfSameInstance() {
    settingsFixture.enableTlrFeature();
    useRollingPolicyWithRenewingAllowedForHoldingRequest(); // base loan period - 3 weeks, alternate - 4 weeks
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    ItemResource itemForLoan = items.get(0);
    ItemResource itemForRequest = items.get(1);
    UserResource borrower = usersFixture.charlotte();
    CheckOutResource initialLoan = checkOutFixture.checkOutByBarcode(itemForLoan, borrower);
    checkOutFixture.checkOutByBarcode(itemForRequest, usersFixture.jessica()); // to allow Hold
    requestsFixture.placeItemLevelHoldShelfRequest(itemForRequest, usersFixture.steve());
    IndividualResource renewedLoan = loansFixture.renewLoan(itemForLoan, borrower);
    assertThat(getDueDate(renewedLoan),
      isEquivalentTo(initialLoan.getDueDate().plusWeeks(DEFAULT_LOAN_PERIOD_WEEKS)));
  }

  @Test
  void alternateLoanPeriodIsNotUsedForRenewalWhenFirstRequestInQueueIsTitleLevelHoldForDifferentItemOfSameInstance() {
    settingsFixture.enableTlrFeature();
    useRollingPolicyWithRenewingAllowedForHoldingRequest();
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    ItemResource itemForLoan = items.get(0);
    ItemResource itemForRequest = items.get(1);
    UUID instanceId = itemForLoan.getInstanceId(); // same for both items
    UserResource borrower = usersFixture.charlotte();
    CheckOutResource initialLoan = checkOutFixture.checkOutByBarcode(itemForLoan, borrower);
    checkOutFixture.checkOutByBarcode(itemForRequest, usersFixture.jessica()); // to allow Hold
    IndividualResource hold = requestsFixture.placeTitleLevelRequest(HOLD, instanceId, usersFixture.steve());
    checkInFixture.checkInByBarcode(itemForRequest);
    assertThat(requestsFixture.getById(hold.getId()).getJson().getString("itemId"),
      is(itemForRequest.getId().toString()));
    IndividualResource renewedLoan = loansFixture.renewLoan(itemForLoan, borrower);
    assertThat(getDueDate(renewedLoan),
      isEquivalentTo(initialLoan.getDueDate().plusWeeks(DEFAULT_LOAN_PERIOD_WEEKS)));
  }

  @Test
  void alternateLoanPeriodIsUsedForRenewalWhenFirstRequestInQueueIsTitleLevelHoldWithoutItemId() {
    settingsFixture.enableTlrFeature();
    useRollingPolicyWithRenewingAllowedForHoldingRequest(); // base loan period - 3 weeks, alternate - 4 weeks
    ItemResource item = itemsFixture.basedUponNod();
    UUID instanceId = item.getInstanceId();
    UserResource borrower = usersFixture.charlotte();
    UserResource requester = usersFixture.steve();
    ZonedDateTime loanDate = getZonedDateTime();
    CheckOutResource initialLoan = checkOutFixture.checkOutByBarcode(item, borrower, loanDate);
    requestsFixture.placeTitleLevelRequest(HOLD, instanceId, requester);
    IndividualResource renewedLoan = loansFixture.renewLoan(item, borrower);
    assertThat(getDueDate(renewedLoan),
      isEquivalentTo(initialLoan.getDueDate().plusWeeks(ALTERNATE_RENEWAL_LOAN_PERIOD_WEEKS)));
  }

  @Test
  void forbidRenewalWhenTitleLevelRecallRequestExistsForSameItem() {
    settingsFixture.enableTlrFeature();
    ItemResource item = itemsFixture.basedUponNod();
    UserResource borrower = usersFixture.james();
    checkOutFixture.checkOutByBarcode(item, borrower);
    // create a hold request so that the recall we create next does not end up at the top of the queue,
    // just to make sure we traverse the whole queue when looking for existing recalls
    requestsFixture.placeItemLevelHoldShelfRequest(item, usersFixture.steve());
    IndividualResource titleLevelRecall = requestsFixture.placeTitleLevelRecallRequest(
      item.getInstanceId(), usersFixture.jessica());
    Response renewalResponse = loansFixture.attemptRenewal(422, item, borrower);

    assertThat(renewalResponse.getJson(), hasErrorWith(allOf(
      hasMessage("items cannot be renewed when there is an active recall request"),
      hasUUIDParameter("requestId", titleLevelRecall.getId()))));
  }

  @Test
  void allowRenewalWhenTitleLevelRecallRequestExistsForDifferentItemOfSameInstance() {
    settingsFixture.enableTlrFeature();
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    ItemResource itemForLoan = items.get(0);
    ItemResource itemForRequest = items.get(1);
    UUID instanceId = itemForLoan.getInstanceId(); // same for both items
    UserResource borrower = usersFixture.james();
    checkOutFixture.checkOutByBarcode(itemForLoan, borrower);
    checkOutFixture.checkOutByBarcode(itemForRequest, usersFixture.rebecca(),
      getZonedDateTime().minusMonths(1)); // so that this loan is recalled first
    IndividualResource titleLevelRecall = requestsFixture.placeTitleLevelRecallRequest(
      instanceId, usersFixture.jessica());
    // make sure that recall was placed on the second item
    assertThat(requestsFixture.getById(titleLevelRecall.getId()).getJson().getString("itemId"),
      is(itemForRequest.getId().toString()));
    loansFixture.renewLoan(itemForLoan, borrower);
  }

  @Test
  void allowRenewalWithHoldsWhenProfileIsFixedUseRenewalSchedule() {
    final ZonedDateTime from = getZonedDateTime().minusMonths(3);
    final ZonedDateTime to = getZonedDateTime().plusMonths(3);
    final ZonedDateTime dueDate = to.plusDays(15);

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    IndividualResource schedule = loanPoliciesFixture.createSchedule(new FixedDueDateSchedulesBuilder()
      .withId(UUID.randomUUID())
      .withName("Can circulate schedule")
      .withDescription("descr")
      .addSchedule(new FixedDueDateSchedule(from, to, dueDate))
    );

    JsonObject holds = new JsonObject()
      .put("renewItemsWithRequest", true);

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .fixed(loanPoliciesFixture.createExampleFixedDueDateSchedule().getId())
      .renewWith(schedule.getId())
      .withName("Fixed with holds")
      .withDescription("Fixed policy with holds")
      .withHolds(holds);

    useWithActiveNotice(loanPolicy);

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));

    IndividualResource response = loansFixture.renewLoanById(smallAngryPlanet, rebecca);

    assertThat(response.getJson().getString("action"), is("renewed"));
    assertThat(response.getJson().getString("dueDate"),
      is(withinSecondsAfter(15, dueDate)));
   }

  @Test
  void allowRenewalWithHoldsWhenProfileIsFixedUseLoanSchedule() {
    final ZonedDateTime from = getZonedDateTime().minusMonths(3);
    final ZonedDateTime to = getZonedDateTime().plusMonths(3);
    final ZonedDateTime dueDate = to.plusDays(15);

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    IndividualResource schedule = loanPoliciesFixture.createSchedule(new FixedDueDateSchedulesBuilder()
      .withId(UUID.randomUUID())
      .withName("Can circulate schedule")
      .withDescription("descr")
      .addSchedule(new FixedDueDateSchedule(from, to, dueDate))
    );

    JsonObject holds = new JsonObject()
      .put("renewItemsWithRequest", true);

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .fixed(schedule.getId())
      .withName("Fixed with holds")
      .withDescription("Fixed policy with holds")
      .withHolds(holds);

    useWithActiveNotice(loanPolicy);

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));

    IndividualResource response = loansFixture.renewLoanById(smallAngryPlanet, rebecca);

    assertThat(response.getJson().getString("action"), is("renewed"));
    assertThat(response.getJson().getString("dueDate"),
      is(withinSecondsAfter(15, dueDate)));
  }

  @Test
  void allowRenewalOverrideWhenFirstRequestIsRecall() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 4, 21, 11, 21, 43, 0, UTC);
    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca, loanDate);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.steve()));

    loansFixture.attemptRenewalById(smallAngryPlanet, rebecca);

    final OkapiHeaders okapiHeaders = buildOkapiHeadersWithPermissions(OVERRIDE_RENEWAL_PERMISSION);
    IndividualResource response = loansFixture.overrideRenewalByBarcode(
      smallAngryPlanet,
      rebecca,
      "Renewal override",
      "2018-12-21T13:30:00Z", okapiHeaders);

    assertThat(response.getJson().getString("action"), is("renewedThroughOverride"));
  }

  @Test
  void forbidRenewalOverrideWhenRecallIsForDifferentItemOfSameInstance() {
    reconfigureTlrFeature(TlrFeatureStatus.ENABLED);
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    ItemResource firstItem = items.get(0);
    ItemResource secondItem = items.get(1);
    UUID instanceId = firstItem.getInstanceId();
    final IndividualResource rebecca = usersFixture.rebecca();

    ZonedDateTime loanDate = ZonedDateTime.of(2018, 4, 21, 11, 21, 43, 0, UTC);
    checkOutFixture.checkOutByBarcode(firstItem, rebecca, loanDate);
    checkOutFixture.checkOutByBarcode(secondItem, usersFixture.steve(), loanDate.minusDays(1));

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(firstItem)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.steve()));

    IndividualResource recall = requestsFixture.placeTitleLevelRequest(
      RequestType.RECALL, instanceId, usersFixture.charlotte());

    // make sure that recall was created for a DIFFERENT item of the same instance
    assertThat(recall.getJson().getString("itemId"), is(secondItem.getId().toString()));

    Response overrideResponse = loansFixture.attemptOverride(
      firstItem,
      rebecca,
      "Renewal override",
      "2018-12-21T13:30:00Z");

    assertThat(overrideResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Override renewal does not match any of expected cases: " +
        "item is not loanable, " +
        "item is not renewable, " +
        "reached number of renewals limit," +
        "renewal date falls outside of the date ranges in the loan policy, " +
        "items cannot be renewed when there is an active recall request, " +
        "item is Declared lost, item is Aged to lost, " +
        "renewal would not change the due date, " +
        "loan has reminder fees"))));
  }

  @Test
  void forbidRenewalOverrideWhenTitleLevelRecallRequestExistsForDifferentItemOfSameInstance() {
    settingsFixture.enableTlrFeature();
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    ItemResource itemForLoan = items.get(0);
    ItemResource itemForRequest = items.get(1);
    UUID instanceId = itemForLoan.getInstanceId(); // same for both items
    UserResource borrower = usersFixture.james();
    checkOutFixture.checkOutByBarcode(itemForLoan, borrower);
    checkOutFixture.checkOutByBarcode(itemForRequest, usersFixture.rebecca(),
      getZonedDateTime().minusMonths(1)); // so that this loan is recalled first
    IndividualResource titleLevelRecall = requestsFixture.placeTitleLevelRecallRequest(
      instanceId, usersFixture.jessica());
    // make sure that recall was placed on the second item
    assertThat(requestsFixture.getById(titleLevelRecall.getId()).getJson().getString("itemId"),
      is(itemForRequest.getId().toString()));

    Response overrideResponse = loansFixture.attemptOverride(itemForLoan, borrower,
      "Renewal override", "2018-12-21T13:30:00Z");

    assertThat(overrideResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Override renewal does not match any of expected cases: " +
        "item is not loanable, " +
        "item is not renewable, " +
        "reached number of renewals limit," +
        "renewal date falls outside of the date ranges in the loan policy, " +
        "items cannot be renewed when there is an active recall request, " +
        "item is Declared lost, item is Aged to lost, " +
        "renewal would not change the due date, " +
        "loan has reminder fees"))));
  }

  @Test
  void multipleRenewalFailuresWhenItemHasOpenRecallRequestAndLoanIsNotRenewable() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .rolling(Period.days(2))
      .notRenewable();

    useWithActiveNotice(limitedRenewalsPolicy);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    Response response = loansFixture.attemptRenewal(smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(hasMessage(ITEMS_CANNOT_BE_RENEWED_MSG)));
    assertThat(response.getJson(), hasErrorWith(hasMessage(EXPECTED_REASON_LOAN_IS_NOT_RENEWABLE)));
  }

  @Test
  void multipleRenewalFailuresWhenItemHasOpenRecallRequestAndLoanIsNotLoanable() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .rolling(Period.days(2))
      .withLoanable(false);

    useWithActiveNotice(limitedRenewalsPolicy);

    Response response = loansFixture.attemptRenewal(smallAngryPlanet, rebecca);
    JsonObject renewalResponse = response.getJson();

    assertThat(renewalResponse, hasErrors(2));
    assertThat(renewalResponse, hasErrorWith(hasMessage(ITEMS_CANNOT_BE_RENEWED_MSG)));
    assertThat(renewalResponse, hasErrorWith(hasMessage(EXPECTED_REASON_LOAN_IS_NOT_LOANABLE)));
  }

  @Test
  void validationErrorWhenRenewalPeriodForHoldsSpecifiedForFixedPolicy() {
    final ZonedDateTime from = getZonedDateTime().minusMonths(3);
    final ZonedDateTime to = getZonedDateTime().plusMonths(3);
    final ZonedDateTime dueDate = to.plusDays(15);

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    IndividualResource schedule = loanPoliciesFixture.createSchedule(new FixedDueDateSchedulesBuilder()
      .withId(UUID.randomUUID())
      .withName("Can circulate schedule")
      .withDescription("descr")
      .addSchedule(new FixedDueDateSchedule(from, to, dueDate))
    );

    JsonObject holds = new JsonObject()
      .put("renewItemsWithRequest", true)
      .put("alternateRenewalLoanPeriod", Period.weeks(10).asJson());

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .fixed(schedule.getId())
      .withName("Fixed with holds")
      .withDescription("Fixed policy with holds")
      .withHolds(holds);

    loanPoliciesFixture.create(loanPolicy);

    useWithActiveNotice(loanPolicy);

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));

    Response response = loansFixture.attemptRenewal(422, smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(
      hasMessage("Item's loan policy has fixed profile but alternative renewal period for holds is specified")));
  }

  @Test
  void validationErrorWhenRenewalPeriodSpecifiedForFixedPolicy() {
    final ZonedDateTime from = getZonedDateTime().minusMonths(3);
    final ZonedDateTime to = getZonedDateTime().plusMonths(3);
    final ZonedDateTime dueDate = to.plusDays(15);

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    IndividualResource schedule = loanPoliciesFixture.createSchedule(new FixedDueDateSchedulesBuilder()
      .withId(UUID.randomUUID())
      .withName("Can circulate schedule")
      .withDescription("descr")
      .addSchedule(new FixedDueDateSchedule(from, to, dueDate))
    );

    JsonObject holds = new JsonObject()
      .put("renewItemsWithRequest", true);

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .fixed(schedule.getId())
      .withName("Fixed with holds")
      .withDescription("Fixed policy with holds")
      .renewWith(Period.weeks(2))
      .withHolds(holds);

    useWithActiveNotice(loanPolicy);

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));

    Response response = loansFixture.attemptRenewal(422, smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(
      hasMessage("Item's loan policy has fixed profile but renewal period is specified")));
  }

  private void loanPolicyWithRollingProfileAndRenewingIsForbiddenWhenHoldIsPending() {

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Rolling. Renewing is forbidden with holds requests")
      .withDescription("Rolling profile with disallowed renewing a loan when a hold request is pending")
      .rolling(Period.weeks(3))
      .unlimitedRenewals()
      .renewFromSystemDate();

    useWithActiveNotice(dueDateLimitedPolicy);
  }

  private void loanPolicyWithFixedProfileAndRenewingIsForbiddenWhenHoldIsPending() {
    final LocalDate now = getLocalDate();
    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("1 month - Fixed Due Date Schedule")
      .addSchedule(wholeMonth(now.getYear(), now.getMonthValue()));

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Fixed Due Date Policy")
      .fixed(loanPoliciesFixture.createSchedule(fixedDueDateSchedules).getId())
      .renewFromSystemDate();

    useWithActiveNotice(dueDateLimitedPolicy);
  }

  private void useRollingPolicyWithRenewingAllowedForHoldingRequest() {
    JsonObject holds = new JsonObject();
    holds.put("alternateRenewalLoanPeriod", Period
      .weeks(ALTERNATE_RENEWAL_LOAN_PERIOD_WEEKS).asJson());
    holds.put("renewItemsWithRequest", true);

    final LoanPolicyBuilder rollingPolicy = new LoanPolicyBuilder()
      .withName("Can Circulate Rolling with holding renewal")
      .withDescription("Can circulate item")
      .withHolds(holds)
      .rolling(Period.weeks(DEFAULT_LOAN_PERIOD_WEEKS))
      .unlimitedRenewals()
      .renewFromCurrentDueDate();

    useWithActiveNotice(rollingPolicy);
  }

  private static ZonedDateTime getDueDate(IndividualResource loan) {
    return getDateTimeProperty(loan.getJson(), "dueDate");
  }
}
