package api.loans.scenarios;

import static api.support.builders.FixedDueDateSchedule.wholeMonth;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static org.folio.circulation.domain.policy.DueDateManagement.KEEP_THE_CURRENT_DUE_DATE;
import static org.folio.circulation.domain.policy.Period.weeks;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.joda.time.DateTimeConstants.SEPTEMBER;

import java.util.UUID;

import api.support.http.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.ItemResource;

class CheckoutWithRequestScenarioTests extends APITests {

  @Test
  void canCheckoutPagedItem() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource charlotte = usersFixture.charlotte();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    requestsClient.create(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(charlotte));

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, charlotte);

    Response changedItem = itemsClient.getById(smallAngryPlanet.getId());

    assertThat(changedItem.getJson().getJsonObject("status").getString("name"),
      is("Checked out"));
  }

  @Test
  void checkingOutWithHoldRequestAppliesAlternatePeriod() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource james = usersFixture.james();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource policy = loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withName("Limited loan period for items with hold requests")
      .rolling(weeks(3))
      .withAlternateCheckoutLoanPeriod(weeks(1))
      .withClosedLibraryDueDateManagement(KEEP_THE_CURRENT_DUE_DATE.getValue()));

    useFallbackPolicies(
      policy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.inactiveNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.steve());

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

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    final DateTime loanDate = new DateTime(2019, 5, 5, 11, 32, 12, DateTimeZone.UTC);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder()
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
  void checkingOutWithHoldRequestAppliesAlternatePeriodAndScheduledForFixedPolicy() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource james = usersFixture.james();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

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

    useFallbackPolicies(
      policy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.inactiveNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    DateTime loanDate = new DateTime(2019, 9, 20, 11, 32, 12, DateTimeZone.UTC);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.steve(), loanDate);

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

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder()
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
