package api.loans;

import api.support.APITests;
import api.support.builders.*;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static api.support.fixtures.ItemExamples.basedUponSmallAngryPlanet;
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

    final UUID servicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      item -> item.withPrimaryServicePoint(servicePointId));

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
    final LoanPolicyBuilder forUseAtLocationPolicyBuilder = new LoanPolicyBuilder()
      .withName("Reading room loans")
      .withDescription("Policy for items to be used at location")
      .rolling(Period.days(30))
      .withForUseAtLocation(true)
      .withHoldShelfExpiryPeriodForUseAtLocation(Period.from(5, "DAYS"));

    use(forUseAtLocationPolicyBuilder);

    checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(borrower)
        .at(servicePointsFixture.cd1()));

    Response holdResponse = holdForUseAtLocationFixture.holdForUseAtLocation(
      new HoldByBarcodeRequestBuilder(item.getBarcode()));

    JsonObject forUseAtLocation = holdResponse.getJson().getJsonObject("forUseAtLocation");

    assertThat("loan.forUseAtLocation",
      forUseAtLocation, notNullValue());
    assertThat("loan.forUseAtLocation.status",
      forUseAtLocation.getString("status"), Is.is("Held"));
    assertThat("loan.forUseAtLocation.holdShelfExpirationDate",
      forUseAtLocation.getString("holdShelfExpirationDate"), notNullValue());
  }

  @Test
  void holdWillFailWithDifferentItem() {
    final LoanPolicyBuilder forUseAtLocationPolicyBuilder = new LoanPolicyBuilder()
      .withName("Reading room loans")
      .withDescription("Policy for items to be used at location")
      .rolling(Period.days(30))
      .withForUseAtLocation(true)
      .withHoldShelfExpiryPeriodForUseAtLocation(Period.from(5, "DAYS"));

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
      .withHoldShelfExpiryPeriodForUseAtLocation(Period.from(5, "DAYS"));

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
      .withHoldShelfExpiryPeriodForUseAtLocation(Period.from(5, "DAYS"));

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
      .withHoldShelfExpiryPeriodForUseAtLocation(Period.from(5, "DAYS"));

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
      .withHoldShelfExpiryPeriodForUseAtLocation(Period.from(5, "DAYS"));

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
