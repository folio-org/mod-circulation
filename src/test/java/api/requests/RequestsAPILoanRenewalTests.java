package api.requests;

import static api.support.builders.FixedDueDateSchedule.wholeMonth;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.joda.time.DateTimeConstants.APRIL;

import java.net.MalformedURLException;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanPolicyBuilder;

import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;

public class RequestsAPILoanRenewalTests extends APITests {

  private static final String ITEMS_CANNOT_BE_RENEWED_MSG = "items cannot be renewed when there is an active recall request";
  private static final String EXPECTED_REASON_LOAN_IS_NOT_RENEWABLE = "loan is not renewable";
  private static final String EXPECTED_REASON_LOAN_IS_NOT_LOANABLE = "item is not loanable";

  @Test
  public void forbidRenewalLoanByBarcodeWhenFirstRequestInQueueIsRecall() throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

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
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    Response response = loansFixture.attemptRenewal(200, smallAngryPlanet, rebecca);
    assertThat(response.getJson().getString("action"), is("renewed"));
  }

  @Test
  public void forbidRenewalLoanByBarcodeWhenProfileIsRollingFirstRequestInQueueIsHoldAndRenewingIsDisallowedInLoanPolicy()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

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
  public void allowRenewalLoanByBarcodeWhenProfileIsFixedFirstRequestInQueueIsHoldAndRenewingIsAllowedInLoanPolicy()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();


    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateFixed().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId()
    );

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));

    IndividualResource response = loansFixture.renewLoan(smallAngryPlanet, rebecca);

    assertThat(response.getJson().getString("action"), is("renewed"));
  }

  @Test
  public void forbidRenewalLoanByBarcodeWhenLoanProfileIsFixedFirstRequestInQueueIsHoldAndRenewingIsDisallowedInLoanPolicy()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

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
  public void forbidRenewalLoanByIdWhenFirstRequestInQueueIsRecall() throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

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
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));


    IndividualResource response = loansFixture.renewLoanById(smallAngryPlanet, rebecca);

    assertThat(response.getJson().getString("action"), is("renewed"));
  }

  @Test
  public void forbidRenewalLoanByIdWhenLoanProfileIsRollingFirstRequestInQueueIsHoldAndRenewingIsDisallowedInLoanPolicy()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

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
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

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
  public void allowRenewalLoanByIdWhenLoanProfileIsFixedFirstRequestInQueueIsHoldAndRenewingIsAllowedInLoanPolicy()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateFixed().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId()
    );

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.charlotte()));

    IndividualResource response = loansFixture.renewLoanById(smallAngryPlanet, rebecca);

    assertThat(response.getJson().getString("action"), is("renewed"));
   }

  @Test
  public void allowRenewalOverrideWhenFirstRequestIsRecall()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

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
  public void forbidRenewalOverrideWhenFirstRequestIsNotRecall()
    throws MalformedURLException,
    InterruptedException,
    TimeoutException,
    ExecutionException {

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
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    LoanPolicyBuilder limitedRenewalsPolicy = new LoanPolicyBuilder()
      .withName("Non Renewable Policy")
      .rolling(Period.days(2))
      .notRenewable();

    UUID notRenewablePolicyId = loanPoliciesFixture
      .create(limitedRenewalsPolicy).getId();

    useLoanPolicyAsFallback(
      notRenewablePolicyId,
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId()
    );

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
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

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

    UUID notRenewablePolicyId = loanPoliciesFixture
      .create(limitedRenewalsPolicy).getId();

    useLoanPolicyAsFallback(
      notRenewablePolicyId,
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId()
    );

    Response response = loansFixture.attemptRenewal(smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(hasMessage(ITEMS_CANNOT_BE_RENEWED_MSG)));
    assertThat(response.getJson(), hasErrorWith(hasMessage(EXPECTED_REASON_LOAN_IS_NOT_LOANABLE)));
  }

  private void loanPolicyWithRollingProfileAndRenewingIsForbiddenWhenHoldIsPending()
    throws InterruptedException, ExecutionException, TimeoutException, MalformedURLException {

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Rolling. Renewing is forbidden with holds requests")
      .withDescription("Rolling profile with disallowed renewing a loan when a hold request is pending")
      .rolling(Period.weeks(3))
      .unlimitedRenewals()
      .renewFromSystemDate();

    useLoanPolicyAsFallback(
      loanPoliciesFixture.create(dueDateLimitedPolicy).getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId()
    );
  }

  private void loanPolicyWithFixedProfileAndRenewingIsForbiddenWhenHoldIsPending()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {
    LocalDate now = LocalDate.now();
    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("1 month - Fixed Due Date Schedule")
      .addSchedule(wholeMonth(now.getYear(), now.getMonthValue()));

    LoanPolicyBuilder dueDateLimitedPolicy = new LoanPolicyBuilder()
      .withName("Fixed Due Date Policy")
      .fixed(loanPoliciesFixture.createSchedule(fixedDueDateSchedules).getId())
      .renewFromSystemDate();

    useLoanPolicyAsFallback(
      loanPoliciesFixture.create(dueDateLimitedPolicy).getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId()
    );
  }
}
