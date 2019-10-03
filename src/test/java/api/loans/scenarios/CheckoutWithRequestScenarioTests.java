package api.loans.scenarios;

import static api.support.builders.FixedDueDateSchedule.wholeMonth;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static org.folio.circulation.domain.policy.DueDateManagement.KEEP_THE_CURRENT_DUE_DATE;
import static org.folio.circulation.domain.policy.Period.weeks;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.joda.time.DateTimeConstants.SEPTEMBER;

import java.net.MalformedURLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;

public class CheckoutWithRequestScenarioTests extends APITests {

  @After
  public void restoreClocks() {
    ClockManager.getClockManager().setClock(Clock.systemUTC());
  }

  @Test
  public void canCheckoutPagedItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource charlotte = usersFixture.charlotte();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    requestsClient.create(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(charlotte));

    loansFixture.checkOutByBarcode(smallAngryPlanet, charlotte);

    Response changedItem = itemsClient.getById(smallAngryPlanet.getId());

    assertThat(changedItem.getJson().getJsonObject("status").getString("name"),
      is("Checked out"));
  }

  @Test
  public void checkingOutWithHoldRequestAppliesAlternatePeriod()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource james = usersFixture.james();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    DateTime fakeNow = DateTime.now(DateTimeZone.UTC)
      .withYear(2019)
      .withMonthOfYear(SEPTEMBER)
      .withDayOfMonth(1);
    // Set the current date time
    ClockManager.getClockManager()
      .setClock(Clock.fixed(Instant.ofEpochMilli(fakeNow.getMillis()), ZoneOffset.UTC));

    final IndividualResource policy = loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withName("Limited loan period for items with hold requests")
      .rolling(weeks(3))
      .withAlternateCheckoutLoanPeriod(weeks(1))
      .withClosedLibraryDueDateManagement(KEEP_THE_CURRENT_DUE_DATE.getValue()));

    useLoanPolicyAsFallback(
      policy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.inactiveNotice().getId());

    loansFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.steve());

    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(charlotte));

    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(james));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    final DateTime loanDate = new DateTime(2019, 5, 5, 11, 32, 12, DateTimeZone.UTC);

    final IndividualResource loan = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder()
      .forItem(smallAngryPlanet)
      .to(charlotte)
      .at(pickupServicePointId)
      .on(loanDate));

    assertThat(loan.getJson().getString("dueDate"), isEquivalentTo(
      new DateTime(2019, 5, 12, 23, 59, 59, DateTimeZone.UTC)));

    Response changedItem = itemsClient.getById(smallAngryPlanet.getId());

    assertThat(changedItem.getJson().getJsonObject("status").getString("name"),
      is("Checked out"));
  }

  @Test
  public void checkingOutWithHoldRequestAppliesAlternatePeriodAndScheduledForFixedPolicy()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource james = usersFixture.james();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    DateTime now = new DateTime(DateTimeZone.UTC)
      .withYear(2019)
      .withMonthOfYear(9)
      .withDayOfMonth(1);

    ClockManager.getClockManager()
      .setClock(Clock.fixed(Instant.ofEpochMilli(now.getMillis()), ZoneOffset.UTC));

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2019, SEPTEMBER));

    UUID fixedDueDateSchedulesId = loanPoliciesFixture.createSchedule(
      fixedDueDateSchedules).getId();

    final IndividualResource policy = loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withName("Limited loan period for items with hold requests")
      .fixed(fixedDueDateSchedulesId)
      .withAlternateCheckoutLoanPeriod(weeks(3))
      .withClosedLibraryDueDateManagement(KEEP_THE_CURRENT_DUE_DATE.getValue()));

    useLoanPolicyAsFallback(
      policy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.inactiveNotice().getId());

    loansFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.steve());

    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(charlotte));

    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(james));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    final DateTime loanDate = new DateTime(2019, 9, 20, 11, 32, 12, DateTimeZone.UTC);

    final IndividualResource loan = loansFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder()
      .forItem(smallAngryPlanet)
      .to(charlotte)
      .at(pickupServicePointId)
      .on(loanDate));

    assertThat(loan.getJson().getString("dueDate"), isEquivalentTo(
      new DateTime(2019, 9, 30, 23, 59, 59, DateTimeZone.UTC)));

    Response changedItem = itemsClient.getById(smallAngryPlanet.getId());

    assertThat(changedItem.getJson().getJsonObject("status").getString("name"),
      is("Checked out"));
  }
}
