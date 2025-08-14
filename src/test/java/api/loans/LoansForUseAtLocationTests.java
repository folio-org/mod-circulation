package api.loans;

import api.support.APITests;
import api.support.builders.*;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.policy.ExpirationDateManagement;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.UUID;

import static api.support.fixtures.CalendarExamples.*;
import static api.support.fixtures.ItemExamples.basedUponSmallAngryPlanet;
import static api.support.http.ResourceClient.forServicePoints;
import static java.lang.Boolean.TRUE;
import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.nullValue;

public class LoansForUseAtLocationTests extends APITests {
  private ItemResource item;
  private UserResource borrower;

  @BeforeEach
  void beforeEach() {

    HoldingBuilder holdingsBuilder = itemsFixture.applyCallNumberHoldings(
      "CN",
      "Prefix",
      "Suffix",
      Collections.singletonList("CopyNumbers"));

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(UUID.fromString(CASE_FIRST_DAY_OPEN_SECOND_CLOSED_THIRD_OPEN)));

    ItemBuilder itemBuilder = basedUponSmallAngryPlanet(
      materialTypesFixture.book().getId(),
      loanTypesFixture.canCirculate().getId())
      .withPermanentLocation(homeLocation);

    item = itemsFixture.basedUponSmallAngryPlanet(itemBuilder, holdingsBuilder);

    borrower = usersFixture.steve();
  }

  @Test
  void willSetAtLocationUsageStatusToInUseOnCheckout() {
    final LoanPolicyBuilder forUseAtLocationPolicyBuilder = new LoanPolicyBuilder()
      .withName("Reading room loans")
      .withDescription("Policy for items to be used at location")
      .rolling(Period.days(30))
      .withForUseAtLocation(true);

    use(forUseAtLocationPolicyBuilder);

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .at(servicePointsFixture.cd1()));

    JsonObject loan = loansFixture.getLoanById(response.getId()).getJson();
    JsonObject forUseAtLocation = loan.getJsonObject("forUseAtLocation");
    assertThat("loan.forUseAtLocation",
      forUseAtLocation, notNullValue());
    assertThat("loan.forUseAtLocation.status",
      forUseAtLocation.getString("status"), Is.is("In use"));
  }

  @Test
  void willMarkItemHeldByBarcode() {
    // Check item out and put it on hold at 2020-10-27 (two days before mock calendar starts)
    ZonedDateTime dateOfHold = atStartOfDay(FIRST_DAY_OPEN.minusDays(2), UTC).plusHours(10).plusSeconds(10);
    mockClockManagerToReturnFixedDateTime(dateOfHold);

    Period holdShelfExpiryPeriod = Period.from(3, "Days");

    forServicePoints().create(new ServicePointBuilder("Reading room", "RR",
        "Circulation Desk -- Reading room").withPickupLocation(TRUE)
        .withId(UUID.fromString(CASE_FIRST_DAY_OPEN_SECOND_CLOSED_THIRD_OPEN))
        .withholdShelfClosedLibraryDateManagement(ExpirationDateManagement.KEEP_THE_CURRENT_DUE_DATE.name()));

    final LoanPolicyBuilder forUseAtLocationPolicyBuilder = new LoanPolicyBuilder()
      .withName("Reading room loans")
      .withDescription("Policy for items to be used at location")
      .rolling(Period.days(30))
      .withForUseAtLocation(true)
      .withHoldShelfExpiryPeriodForUseAtLocation(holdShelfExpiryPeriod);
    use(forUseAtLocationPolicyBuilder);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .at(CASE_FIRST_DAY_OPEN_SECOND_CLOSED_THIRD_OPEN));

    Response holdResponse = holdForUseAtLocationFixture.holdForUseAtLocation(
      new HoldByBarcodeRequestBuilder(item.getBarcode()));

    JsonObject forUseAtLocation = holdResponse.getJson().getJsonObject("forUseAtLocation");

    assertThat("loan.forUseAtLocation",
      forUseAtLocation, notNullValue());
    assertThat("loan.forUseAtLocation.status",
      forUseAtLocation.getString("status"), Is.is("Held"));
    assertThat("loan.forUseAtLocation.holdShelfExpirationDate",
      forUseAtLocation.getString("holdShelfExpirationDate"), notNullValue());
    assertThat("loan.forUseAtLocation.holdShelfExpirationDate",
      forUseAtLocation.getString("holdShelfExpirationDate").replaceAll("\\.000",""),
      Is.is(atEndOfDay(holdShelfExpiryPeriod.plusDate(dateOfHold),UTC).toString()));
  }

  @Test
  void willSetHoldShelfExpiryToEndOfDayBeforeClosedDayOnPutOnHold() {
    // Check item out and put it on hold at 2020-10-27 (two days before mock calendar starts)
    ZonedDateTime dateOfHold = atStartOfDay(FIRST_DAY_OPEN.minusDays(2), UTC).plusHours(10).plusSeconds(10);
    mockClockManagerToReturnFixedDateTime(dateOfHold);

    Period holdShelfExpiryPeriod = Period.from(3, "Days");

    forServicePoints().create(new ServicePointBuilder("Reading room", "RR",
      "Circulation Desk -- Reading room").withPickupLocation(TRUE)
      .withId(UUID.fromString(CASE_FIRST_DAY_OPEN_SECOND_CLOSED_THIRD_OPEN))
      .withholdShelfClosedLibraryDateManagement(ExpirationDateManagement.MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY.name()));

    final LoanPolicyBuilder forUseAtLocationPolicyBuilder = new LoanPolicyBuilder()
      .withName("Reading room loans")
      .withDescription("Policy for items to be used at location")
      .rolling(Period.days(30))
      .withForUseAtLocation(true)
      .withHoldShelfExpiryPeriodForUseAtLocation(holdShelfExpiryPeriod);
    use(forUseAtLocationPolicyBuilder);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .at(CASE_FIRST_DAY_OPEN_SECOND_CLOSED_THIRD_OPEN));

    JsonObject forUseAtLocation = holdForUseAtLocationFixture.holdForUseAtLocation(
      new HoldByBarcodeRequestBuilder(item.getBarcode())).getJson().getJsonObject("forUseAtLocation");

    ZonedDateTime expectedExpiryDateTime =
      atEndOfDay(holdShelfExpiryPeriod
        .plusDate(dateOfHold)
        .minusDays(1),UTC);  // move back one day

    assertThat("loan.forUseAtLocation.holdShelfExpirationDate",
      forUseAtLocation.getString("holdShelfExpirationDate").replaceAll("\\.000",""),
      Is.is(expectedExpiryDateTime.toString()));
  }

  @Test
  void willSetHoldShelfExpiryToEndOfDayAfterClosedDayOnPutOnHold() {
    // Check item out and put it on hold at 2020-10-27 (two days before mock calendar starts)
    ZonedDateTime dateOfHold = atStartOfDay(FIRST_DAY_OPEN.minusDays(2), UTC).plusHours(10).plusSeconds(10);
    mockClockManagerToReturnFixedDateTime(dateOfHold);

    Period holdShelfExpiryPeriod = Period.from(3, "Days");

    forServicePoints().create(new ServicePointBuilder("Reading room", "RR",
      "Circulation Desk -- Reading room").withPickupLocation(TRUE)
      .withId(UUID.fromString(CASE_FIRST_DAY_OPEN_SECOND_CLOSED_THIRD_OPEN))
      .withholdShelfClosedLibraryDateManagement(ExpirationDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY.name()));

    final LoanPolicyBuilder forUseAtLocationPolicyBuilder = new LoanPolicyBuilder()
      .withName("Reading room loans")
      .withDescription("Policy for items to be used at location")
      .rolling(Period.days(30))
      .withForUseAtLocation(true)
      .withHoldShelfExpiryPeriodForUseAtLocation(holdShelfExpiryPeriod);
    use(forUseAtLocationPolicyBuilder);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .at(CASE_FIRST_DAY_OPEN_SECOND_CLOSED_THIRD_OPEN));

    JsonObject forUseAtLocation = holdForUseAtLocationFixture.holdForUseAtLocation(
      new HoldByBarcodeRequestBuilder(item.getBarcode())).getJson().getJsonObject("forUseAtLocation");

    ZonedDateTime expectedExpiryDateTime =
      atEndOfDay(holdShelfExpiryPeriod
        .plusDate(dateOfHold)
        .plusDays(1),UTC);  // move forward one day

    assertThat("loan.forUseAtLocation.holdShelfExpirationDate",
      forUseAtLocation.getString("holdShelfExpirationDate").replaceAll("\\.000",""),
      Is.is(expectedExpiryDateTime.toString()));
  }

  @Test
  void willSetNoHoldShelfExpirationIfPolicyNotDefined() {
    ZonedDateTime dateOfHold = atStartOfDay(FIRST_DAY_OPEN.minusDays(2), UTC).plusHours(10).plusSeconds(10);
    mockClockManagerToReturnFixedDateTime(dateOfHold);

    forServicePoints().create(new ServicePointBuilder("Reading room", "RR",
      "Circulation Desk -- Reading room").withPickupLocation(TRUE)
      .withId(UUID.fromString(CASE_FIRST_DAY_OPEN_SECOND_CLOSED_THIRD_OPEN))
      .withholdShelfClosedLibraryDateManagement(ExpirationDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY.name()));

    final LoanPolicyBuilder forUseAtLocationPolicyBuilder = new LoanPolicyBuilder()
      .withName("Reading room loans")
      .withDescription("Policy for items to be used at location")
      .rolling(Period.days(30))
      .withForUseAtLocation(true);
    use(forUseAtLocationPolicyBuilder);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .at(CASE_FIRST_DAY_OPEN_SECOND_CLOSED_THIRD_OPEN));

    JsonObject forUseAtLocation = holdForUseAtLocationFixture.holdForUseAtLocation(
      new HoldByBarcodeRequestBuilder(item.getBarcode())).getJson().getJsonObject("forUseAtLocation");

    assertThat("loan.forUseAtLocation.holdShelfExpirationDate",
      forUseAtLocation.getString("holdShelfExpirationDate"), nullValue());
  }

  @Test
  void holdWillFailWithDifferentItem() {
    final LoanPolicyBuilder forUseAtLocationPolicyBuilder = new LoanPolicyBuilder()
      .withName("Reading room loans")
      .withDescription("Policy for items to be used at location")
      .rolling(Period.days(30))
      .withForUseAtLocation(true)
      .withHoldShelfExpiryPeriodForUseAtLocation(Period.from(5, "Days"));

    use(forUseAtLocationPolicyBuilder);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .at(servicePointsFixture.cd1()));

    holdForUseAtLocationFixture.holdForUseAtLocation(
      new HoldByBarcodeRequestBuilder("different-item"), 400);
  }

  @Test
  void holdWillFailIfLoanIsNotForUseAtLocation() {
    final LoanPolicyBuilder homeLoansPolicyBuilder = new LoanPolicyBuilder()
      .withName("Home loans")
      .withDescription("Policy for items that can be taken home")
      .rolling(Period.days(30));

    use(homeLoansPolicyBuilder);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .at(servicePointsFixture.cd1()));

    holdForUseAtLocationFixture.holdForUseAtLocation(
      new HoldByBarcodeRequestBuilder(item.getBarcode()), 400);
  }

  @Test
  void holdWillFailWithIncompleteRequest() {
    final LoanPolicyBuilder forUseAtLocationPolicyBuilder = new LoanPolicyBuilder()
      .withName("Reading room loans")
      .withDescription("Policy for items to be used at location")
      .rolling(Period.days(30))
      .withForUseAtLocation(true)
      .withHoldShelfExpiryPeriodForUseAtLocation(Period.from(5, "Days"));

    use(forUseAtLocationPolicyBuilder);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .at(servicePointsFixture.cd1()));

    holdForUseAtLocationFixture.holdForUseAtLocation(
      new HoldByBarcodeRequestBuilder(null), 422);
  }


  @Test
  void willMarkItemInUseByBarcode() {
    final LoanPolicyBuilder forUseAtLocationPolicyBuilder = new LoanPolicyBuilder()
      .withName("Reading room loans")
      .withDescription("Policy for items to be used at location")
      .rolling(Period.days(30))
      .withForUseAtLocation(true)
      .withHoldShelfExpiryPeriodForUseAtLocation(Period.from(5, "Days"));

    use(forUseAtLocationPolicyBuilder);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .at(servicePointsFixture.cd1()));

    Response pickupResponse = pickupForUseAtLocationFixture.pickupForUseAtLocation(
      new PickupByBarcodeRequestBuilder(item.getBarcode(), borrower.getBarcode()));

    JsonObject forUseAtLocation = pickupResponse.getJson().getJsonObject("forUseAtLocation");
    assertThat("loan.forUseAtLocation",
      forUseAtLocation, notNullValue());
    assertThat("loan.forUseAtLocation.status",
      forUseAtLocation.getString("status"), Is.is("In use"));
  }

  @Test
  void pickupWillFailWithDifferentItemOrDifferentUser() {
    final LoanPolicyBuilder forUseAtLocationPolicyBuilder = new LoanPolicyBuilder()
      .withName("Reading room loans")
      .withDescription("Policy for items to be used at location")
      .rolling(Period.days(30))
      .withForUseAtLocation(true);

    use(forUseAtLocationPolicyBuilder);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .at(servicePointsFixture.cd1()));

    pickupForUseAtLocationFixture.pickupForUseAtLocation(
      new PickupByBarcodeRequestBuilder("different-item", borrower.getBarcode()), 400);

    pickupForUseAtLocationFixture.pickupForUseAtLocation(
      new PickupByBarcodeRequestBuilder(item.getBarcode(), "different-user"), 400);

  }

  @Test
  void pickupWillFailWithIncompleteRequestObject() {
    final LoanPolicyBuilder forUseAtLocationPolicyBuilder = new LoanPolicyBuilder()
      .withName("Reading room loans")
      .withDescription("Policy for items to be used at location")
      .rolling(Period.days(30))
      .withForUseAtLocation(true)
      .withHoldShelfExpiryPeriodForUseAtLocation(Period.from(5, "Days"));

    use(forUseAtLocationPolicyBuilder);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .at(servicePointsFixture.cd1()));

    pickupForUseAtLocationFixture.pickupForUseAtLocation(
      new PickupByBarcodeRequestBuilder(null, borrower.getBarcode()), 422);

    pickupForUseAtLocationFixture.pickupForUseAtLocation(
      new PickupByBarcodeRequestBuilder(item.getBarcode(), null), 422);
  }

  @Test
  void pickupWillFailIfLoanIsNotForUseAtLocation() {
    final LoanPolicyBuilder homeLoansPolicyBuilder = new LoanPolicyBuilder()
      .withName("Home loans")
      .withDescription("Policy for items that can be taken home")
      .rolling(Period.days(30));

    use(homeLoansPolicyBuilder);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .at(servicePointsFixture.cd1()));

    pickupForUseAtLocationFixture.pickupForUseAtLocation(
      new PickupByBarcodeRequestBuilder(item.getBarcode(), borrower.getBarcode()), 400);
  }

  @Test
  void willSetAtLocationUsageStatusToReturnedOnCheckIn() {
    final LoanPolicyBuilder forUseAtLocationPolicyBuilder = new LoanPolicyBuilder()
      .withName("Reading room loans")
      .withDescription("Policy for items to be used at location")
      .rolling(Period.days(30))
      .withForUseAtLocation(true)
      .withHoldShelfExpiryPeriodForUseAtLocation(Period.from(5, "Days"));

    use(forUseAtLocationPolicyBuilder);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .at(servicePointsFixture.cd1()));

    final IndividualResource response = checkInFixture.checkInByBarcode(item);

    JsonObject loan = loansFixture.getLoanById(
      UUID.fromString(response.getJson().getJsonObject("loan").getString("id")))
      .getJson();
    JsonObject forUseAtLocation = loan.getJsonObject("forUseAtLocation");
    assertThat("loan.forUseAtLocation",
      forUseAtLocation, notNullValue());
    assertThat("loan.forUseAtLocation.status",
      forUseAtLocation.getString("status"), Is.is("Returned"));
    assertThat("loan.forUseAtLocation.holdShelfExpirationDate",
      forUseAtLocation.getString("holdShelfExpirationDate"), nullValue());
  }

}
