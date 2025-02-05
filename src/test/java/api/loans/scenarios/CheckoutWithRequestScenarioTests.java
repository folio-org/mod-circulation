package api.loans.scenarios;

import static api.support.builders.FixedDueDateSchedule.wholeMonth;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.domain.policy.DueDateManagement.KEEP_THE_CURRENT_DUE_DATE;
import static org.folio.circulation.domain.policy.Period.days;
import static org.folio.circulation.domain.policy.Period.weeks;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.CqlQuery;
import api.support.http.IndividualResource;
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

    final ZonedDateTime loanDate = ZonedDateTime.of(2019, 5, 5, 11, 32, 12, 0, UTC);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(new CheckOutByBarcodeRequestBuilder()
      .forItem(smallAngryPlanet)
      .to(charlotte)
      .at(pickupServicePointId)
      .on(loanDate));

    assertThat(loan.getJson().getString("dueDate"), isEquivalentTo(
      ZonedDateTime.of(2019, 5, 12, 23, 59, 59, 0, UTC)));

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
      .addSchedule(wholeMonth(2019, 9));

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

    ZonedDateTime loanDate = ZonedDateTime.of(2019, 9, 20, 11, 32, 12, 0, UTC);

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
      ZonedDateTime.of(2019, 9, 30, 23, 59, 59, 0, UTC)));

    Response changedItem = itemsClient.getById(smallAngryPlanet.getId());

    assertThat(changedItem.getJson().getJsonObject("status").getString("name"),
      is("Checked out"));
  }

  @Test
  void alternatePeriodShouldBeAppliedWhenRequestQueueContainsHoldTlr() {
    settingsFixture.enableTlrFeature();
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    var firstItem = items.get(0);
    var secondItem = items.get(1);
    var instanceId = firstItem.getInstanceId();

    var james = usersFixture.james();
    var charlotte = usersFixture.charlotte();
    var steve = usersFixture.steve();
    var pickupServicePointId = servicePointsFixture.cd1().getId();

    var loanPolicy = loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withName("Limited loan period for items with hold requests")
      .rolling(days(5))
      .withAlternateCheckoutLoanPeriod(days(1))
      .withClosedLibraryDueDateManagement(KEEP_THE_CURRENT_DUE_DATE.getValue()));
    useFallbackPolicies(
      loanPolicy.getId(),
      requestPoliciesFixture.allowPageAndHoldRequestPolicy().getId(),
      noticePoliciesFixture.inactiveNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    requestsClient.create(new RequestBuilder()
      .page()
      .titleRequestLevel()
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withInstanceId(instanceId)
      .withPickupServicePointId(pickupServicePointId)
      .by(charlotte));
    requestsClient.create(new RequestBuilder()
      .page()
      .titleRequestLevel()
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withInstanceId(instanceId)
      .withPickupServicePointId(pickupServicePointId)
      .by(james));
    requestsClient.create(new RequestBuilder()
      .hold()
      .titleRequestLevel()
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withInstanceId(instanceId)
      .withPickupServicePointId(pickupServicePointId)
      .by(steve));

    checkInFixture.checkInByBarcode(firstItem);
    checkInFixture.checkInByBarcode(secondItem);

    String firstRequesterBarcode = requestsClient.getMany(CqlQuery.exactMatch(
      "itemId", firstItem.getId().toString())).getFirst().getJsonObject("requester")
      .getString("barcode");
    ZonedDateTime loanDate = ZonedDateTime.of(2024, 1, 1, 11, 0, 0, 0, UTC);
    final IndividualResource firstLoan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(firstItem)
        .to(firstRequesterBarcode)
        .at(pickupServicePointId)
        .on(loanDate));
    assertThat(firstLoan.getJson().getString("dueDate"), isEquivalentTo(
      ZonedDateTime.of(2024, 1, 2, 23, 59, 59, 0, UTC)));

    String secondRequesterBarcode = requestsClient.getMany(CqlQuery.exactMatch(
        "itemId", secondItem.getId().toString())).getFirst().getJsonObject("requester")
      .getString("barcode");
    loanDate = ZonedDateTime.of(2024, 1, 10, 11, 0, 0, 0, UTC);
    final IndividualResource secondLoan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(secondItem)
        .to(secondRequesterBarcode)
        .at(pickupServicePointId)
        .on(loanDate));
    assertThat(secondLoan.getJson().getString("dueDate"), isEquivalentTo(
      ZonedDateTime.of(2024, 1, 11, 23, 59, 59, 0, UTC)));
  }

  @Test
  void alternatePeriodShouldNotBeAppliedWhenRequestQueueContainsHoldIlrForDifferentItem() {
    settingsFixture.enableTlrFeature();
    List<ItemResource> items = itemsFixture.createMultipleItemsForTheSameInstance(2);
    var firstItem = items.get(0);
    var secondItem = items.get(1);
    var instanceId = firstItem.getInstanceId();

    var james = usersFixture.james();
    var charlotte = usersFixture.charlotte();
    var pickupServicePointId = servicePointsFixture.cd1().getId();

    var loanPolicy = loanPoliciesFixture.create(new LoanPolicyBuilder()
      .withName("Limited loan period for items with hold requests")
      .rolling(days(5))
      .withAlternateCheckoutLoanPeriod(days(1))
      .withClosedLibraryDueDateManagement(KEEP_THE_CURRENT_DUE_DATE.getValue()));
    useFallbackPolicies(
      loanPolicy.getId(),
      requestPoliciesFixture.allowPageAndHoldRequestPolicy().getId(),
      noticePoliciesFixture.inactiveNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    checkOutFixture.checkOutByBarcode(secondItem);
    requestsClient.create(new RequestBuilder()
      .page()
      .titleRequestLevel()
      .withNoItemId()
      .withNoHoldingsRecordId()
      .withInstanceId(instanceId)
      .withPickupServicePointId(pickupServicePointId)
      .by(charlotte));
    requestsClient.create(new RequestBuilder()
      .hold()
      .itemRequestLevel()
      .withItemId(secondItem.getId())
      .withInstanceId(secondItem.getInstanceId())
      .withPickupServicePointId(pickupServicePointId)
      .by(james));

    checkInFixture.checkInByBarcode(firstItem);

    String firstRequesterBarcode = requestsClient.getMany(CqlQuery.exactMatch(
        "itemId", firstItem.getId().toString())).getFirst().getJsonObject("requester")
      .getString("barcode");
    ZonedDateTime loanDate = ZonedDateTime.of(2024, 1, 1, 11, 0, 0, 0, UTC);
    final IndividualResource firstLoan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(firstItem)
        .to(firstRequesterBarcode)
        .at(pickupServicePointId)
        .on(loanDate));
    assertThat(firstLoan.getJson().getString("dueDate"), isEquivalentTo(
      ZonedDateTime.of(2024, 1, 6, 23, 59, 59, 0, UTC)));
  }
}
