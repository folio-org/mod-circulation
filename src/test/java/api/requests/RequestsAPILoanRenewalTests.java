package api.requests;

import static api.support.builders.FixedDueDateSchedule.wholeMonth;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.joda.time.DateTimeConstants.APRIL;

import java.net.MalformedURLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Seconds;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.FixedDueDateSchedule;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class RequestsAPILoanRenewalTests extends APITests {

  private static final String ITEMS_CANNOT_BE_RENEWED_MSG = "items cannot be renewed when there is an active recall request";
  private static final String EXPECTED_REASON_LOAN_IS_NOT_RENEWABLE = "loan is not renewable";
  private static final String EXPECTED_REASON_LOAN_IS_NOT_LOANABLE = "item is not loanable";
  private static final int DEFAULT_HOLD_RENEWAL_PERIOD = 4;

  @Test
  public void forbidRenewalLoanByBarcodeWhenFirstRequestInQueueIsRecall() {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);
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
  public void allowRenewalLoanByBarcodeWhenProfileIsRollingFirstRequestInQueueIsHoldAndRenewingIsAllowedInLoanPolicy()
    throws InterruptedException, TimeoutException, ExecutionException {

    final DateTime expectedDueDate = DateTime.now(DateTimeZone.UTC)
      .plusWeeks(DEFAULT_HOLD_RENEWAL_PERIOD);
    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

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

    assertThat(response.getJson().getString("dueDate"),
      is(withinSecondsAfter(Seconds.seconds(15), expectedDueDate)));
  }

  @Test
  public void forbidRenewalLoanByBarcodeWhenProfileIsRollingFirstRequestInQueueIsHoldAndRenewingIsDisallowedInLoanPolicy()
    throws InterruptedException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loanPolicyWithRollingProfileAndRenewingIsForbiddenWhenHoldIsPending();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);
    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));

    Response response = loansFixture.attemptRenewalById(smallAngryPlanet, rebecca);

    String message = response.getJson().getJsonArray("errors").getJsonObject(0).getString("message");
    assertThat(message, is(LoanPolicy.CAN_NOT_RENEW_ITEM_ERROR));
  }

  @Test
  public void allowRenewalWithHoldsWhenProfileIsRollingUseLoanPeriod() throws Exception {
    final int renewalPeriod = 90;
    final DateTime expectedDueDate = DateTime.now(DateTimeZone.UTC).plusWeeks(renewalPeriod);
    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

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
      is(withinSecondsAfter(Seconds.seconds(15), expectedDueDate)));
  }

  @Test
  public void allowRenewalWithHoldsWhenProfileIsRollingUseRenewalPeriod() throws Exception {
    final int renewalPeriod = 60;
    final DateTime expectedDueDate = DateTime.now(DateTimeZone.UTC).plusWeeks(renewalPeriod);
    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

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
      is(withinSecondsAfter(Seconds.seconds(15), expectedDueDate)));
  }


  @Test
  public void forbidRenewalLoanByBarcodeWhenLoanProfileIsFixedFirstRequestInQueueIsHoldAndRenewingIsDisallowedInLoanPolicy()
    throws InterruptedException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    loanPolicyWithFixedProfileAndRenewingIsForbiddenWhenHoldIsPending();

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));


    Response response = loansFixture.attemptRenewal(smallAngryPlanet, rebecca);

    String message = response.getJson().getJsonArray("errors").getJsonObject(0).getString("message");
    assertThat(message, is(LoanPolicy.CAN_NOT_RENEW_ITEM_ERROR));
  }

  @Test
  public void forbidRenewalLoanByIdWhenFirstRequestInQueueIsRecall() {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    Response response = loansFixture.attemptRenewal(smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(hasMessage(ITEMS_CANNOT_BE_RENEWED_MSG)));
  }

  @Test
  public void allowRenewalLoanByIdWhenProfileIsRollingFirstRequestInQueueIsHoldAndRenewingIsAllowedInLoanPolicy()
    throws InterruptedException, TimeoutException, ExecutionException {

    final DateTime expectedDueDate = DateTime.now(DateTimeZone.UTC)
      .plusWeeks(DEFAULT_HOLD_RENEWAL_PERIOD);
    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

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

    assertThat(response.getJson().getString("dueDate"),
      is(withinSecondsAfter(Seconds.seconds(15), expectedDueDate)));
  }

  @Test
  public void forbidRenewalLoanByIdWhenLoanProfileIsRollingFirstRequestInQueueIsHoldAndRenewingIsDisallowedInLoanPolicy()
    throws InterruptedException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    loanPolicyWithRollingProfileAndRenewingIsForbiddenWhenHoldIsPending();

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));


    Response response = loansFixture.attemptRenewalById(smallAngryPlanet, rebecca);

    String message = response.getJson().getJsonArray("errors").getJsonObject(0).getString("message");
    assertThat(message, is(LoanPolicy.CAN_NOT_RENEW_ITEM_ERROR));
  }

  @Test
  public void forbidRenewalLoanByIdWhenLoanProfileIsFixedFirstRequestInQueueIsHoldAndRenewingIsDisallowedInLoanPolicy()
    throws InterruptedException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    loanPolicyWithFixedProfileAndRenewingIsForbiddenWhenHoldIsPending();

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));


    Response response = loansFixture.attemptRenewalById(smallAngryPlanet, rebecca);

    String message = response.getJson().getJsonArray("errors").getJsonObject(0).getString("message");
    assertThat(message, is(LoanPolicy.CAN_NOT_RENEW_ITEM_ERROR));
  }

  @Test
  public void allowRenewalWithHoldsWhenProfileIsFixedUseRenewalSchedule()
    throws InterruptedException, TimeoutException, ExecutionException {
    final DateTime from = DateTime.now(DateTimeZone.UTC).minusMonths(3);
    final DateTime to = DateTime.now(DateTimeZone.UTC).plusMonths(3);
    final DateTime dueDate = to.plusDays(15);

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

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
      is(withinSecondsAfter(Seconds.seconds(15), dueDate)));
   }

  @Test
  public void allowRenewalWithHoldsWhenProfileIsFixedUseLoanSchedule()
    throws InterruptedException, TimeoutException, ExecutionException {
    final DateTime from = DateTime.now(DateTimeZone.UTC).minusMonths(3);
    final DateTime to = DateTime.now(DateTimeZone.UTC).plusMonths(3);
    final DateTime dueDate = to.plusDays(15);

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

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
      is(withinSecondsAfter(Seconds.seconds(15), dueDate)));
  }

  @Test
  public void allowRenewalOverrideWhenFirstRequestIsRecall() {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    DateTime loanDate = new DateTime(2018, APRIL, 21, 11, 21, 43);
    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca, loanDate);

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

    IndividualResource response = loansFixture.overrideRenewalByBarcode(
      smallAngryPlanet,
      rebecca,
      "Renewal override",
      "2018-12-21T13:30:00Z");

    assertThat(response.getJson().getString("action"), is("renewedThroughOverride"));
  }

  @Test
  public void forbidRenewalOverrideWhenFirstRequestIsNotRecall() {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    DateTime loanDate = new DateTime(2018, APRIL, 21, 11, 21, 43);
    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca, loanDate);

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.steve()));

    requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));

    Response overrideResponse = loansFixture.attemptOverride(
      smallAngryPlanet,
      rebecca,
      "Renewal override",
      "2018-12-21T13:30:00Z");

    assertThat(overrideResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Override renewal does not match any of expected cases: " +
        "item is not loanable, " +
        "item is not renewable, " +
        "reached number of renewals limit," +
        "renewal date falls outside of the date ranges in the loan policy, " +
        "items cannot be renewed when there is an active recall request"))));
  }

  @Test
  public void multipleRenewalFailuresWhenItemHasOpenRecallRequestAndLoanIsNotRenewable()
    throws InterruptedException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

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
  public void multipleRenewalFailuresWhenItemHasOpenRecallRequestAndLoanIsNotLoanable()
    throws InterruptedException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

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

    assertThat(response.getJson(), hasErrorWith(hasMessage(ITEMS_CANNOT_BE_RENEWED_MSG)));
    assertThat(response.getJson(), hasErrorWith(hasMessage(EXPECTED_REASON_LOAN_IS_NOT_LOANABLE)));
  }

  @Test
  public void validationErrorWhenRenewalPeriodForHoldsSpecifiedForFixedPolicy()
    throws InterruptedException, TimeoutException, ExecutionException {
    final DateTime from = DateTime.now(DateTimeZone.UTC).minusMonths(3);
    final DateTime to = DateTime.now(DateTimeZone.UTC).plusMonths(3);
    final DateTime dueDate = to.plusDays(15);

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

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

    Response response = loansFixture.attemptRenewal(500, smallAngryPlanet, rebecca);

    assertThat(
      response.getBody(),
      is("Item's loan policy has fixed profile but alternative renewal period for holds is specified")
    );
  }

  @Test
  public void validationErrorWhenRenewalPeriodSpecifiedForFixedPolicy()
    throws InterruptedException, TimeoutException, ExecutionException {
    final DateTime from = DateTime.now(DateTimeZone.UTC).minusMonths(3);
    final DateTime to = DateTime.now(DateTimeZone.UTC).plusMonths(3);
    final DateTime dueDate = to.plusDays(15);

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

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

    Response response = loansFixture.attemptRenewal(500, smallAngryPlanet, rebecca);

    assertThat(
      response.getBody(),
      is("Item's loan policy has fixed profile but renewal period is specified")
    );
  }

  private void loanPolicyWithRollingProfileAndRenewingIsForbiddenWhenHoldIsPending()
    throws InterruptedException, ExecutionException, TimeoutException {

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Rolling. Renewing is forbidden with holds requests")
      .withDescription("Rolling profile with disallowed renewing a loan when a hold request is pending")
      .rolling(Period.weeks(3))
      .unlimitedRenewals()
      .renewFromSystemDate();

    useWithActiveNotice(dueDateLimitedPolicy);
  }

  private void loanPolicyWithFixedProfileAndRenewingIsForbiddenWhenHoldIsPending()
    throws InterruptedException, TimeoutException, ExecutionException {
    LocalDate now = LocalDate.now();
    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("1 month - Fixed Due Date Schedule")
      .addSchedule(wholeMonth(now.getYear(), now.getMonthValue()));

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Fixed Due Date Policy")
      .fixed(loanPoliciesFixture.createSchedule(fixedDueDateSchedules).getId())
      .renewFromSystemDate();

    useWithActiveNotice(dueDateLimitedPolicy);
  }

  private void useRollingPolicyWithRenewingAllowedForHoldingRequest()
    throws InterruptedException, TimeoutException, ExecutionException {
    JsonObject holds = new JsonObject();
    holds.put("alternateRenewalLoanPeriod", Period
      .weeks(DEFAULT_HOLD_RENEWAL_PERIOD).asJson());
    holds.put("renewItemsWithRequest", true);

    final LoanPolicyBuilder rollingPolicy = new LoanPolicyBuilder()
      .withName("Can Circulate Rolling with holding renewal")
      .withDescription("Can circulate item")
      .withHolds(holds)
      .rolling(Period.weeks(3))
      .unlimitedRenewals()
      .renewFromSystemDate();

    useWithActiveNotice(rollingPolicy);
  }
}
