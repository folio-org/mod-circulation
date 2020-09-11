package api.loans.agetolost;

import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.LoanAccountActionsMatcher.hasLostItemFeeCreatedBySystemAction;
import static api.support.matchers.LoanAccountActionsMatcher.hasLostItemProcessingFeeCreatedBySystemAction;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFees;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasNoLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasNoLostItemProcessingFee;
import static api.support.matchers.LoanHistoryMatcher.hasLoanHistory;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static java.lang.Boolean.TRUE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.iterableWithSize;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;

import api.support.builders.FeeFineOwnerBuilder;
import api.support.builders.ItemBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.builders.ServicePointBuilder;
import api.support.spring.SpringApiTest;
import io.vertx.core.json.JsonObject;
import lombok.val;

public class ScheduledAgeToLostFeeChargingApiTest extends SpringApiTest {

  public ScheduledAgeToLostFeeChargingApiTest() {
    super(true, true);
  }

  @Before
  public void createOwnersAndFeeFineTypes() {
    feeFineOwnerFixture.cd1Owner();
    feeFineTypeFixture.lostItemFee();
    feeFineTypeFixture.lostItemProcessingFee();
  }

  @Test
  @SuppressWarnings("unchecked")
  public void shouldChargeItemFee() {
    final double expectedSetCost = 12.88;

    val policy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(expectedSetCost)
      .doNotChargeProcessingFeeWhenAgedToLost();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    assertThat(result.getLoan().getJson(), isLostItemHasBeenBilled());
    assertThat(result.getLoan(), hasLostItemFee(isOpen(expectedSetCost)));
    assertThat(result.getLoan(), hasLostItemFeeCreatedBySystemAction());

    assertThat(result.getLoan(), hasLoanHistory(containsInRelativeOrder(
      hasJsonPath("loan.action", ""),
      hasJsonPath("loan.action", "itemAgedToLost"),
      hasJsonPath("loan.action", "checkedout")
    )));
  }

  @Test
  public void shouldChargeItemProcessingFee() {
    final double expectedProcessingFee = 99.54;

    val policy = new LostItemFeePolicyBuilder()
      .withName("shouldChargeItemProcessingFee")
      .withItemAgedToLostAfterOverdue(Period.weeks(1))
      .withPatronBilledAfterAgedLost(Period.weeks(2))
      .withNoChargeAmountItem()
      .doNotChargeProcessingFeeWhenDeclaredLost()
      .chargeProcessingFeeWhenAgedToLost(expectedProcessingFee);

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    assertThat(result.getLoan().getJson(), isLostItemHasBeenBilled());
    assertThat(result.getLoan(), hasLostItemProcessingFee(isOpen(expectedProcessingFee)));
    assertThat(result.getLoan(), hasLostItemProcessingFeeCreatedBySystemAction());
  }

  @Test
  public void shouldChargeItemAndProcessingFee() {
    final double expectedItemFee = 99.54;
    final double expectedProcessingFee = 99.54;

    val policy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(expectedItemFee)
      .chargeProcessingFeeWhenAgedToLost(expectedProcessingFee);

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    assertThat(result.getLoan().getJson(), isLostItemHasBeenBilled());

    assertThat(result.getLoan(), hasLostItemFee(isOpen(expectedItemFee)));
    assertThat(result.getLoan(), hasLostItemFeeCreatedBySystemAction());

    assertThat(result.getLoan(), hasLostItemProcessingFee(isOpen(expectedProcessingFee)));
    assertThat(result.getLoan(), hasLostItemProcessingFeeCreatedBySystemAction());
  }

  @Test
  public void shouldNotChargeFeesIfStatedByPolicy() {
    val policy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withNoChargeAmountItem()
      .doNotChargeProcessingFeeWhenAgedToLost();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    assertThat(result.getLoan().getJson(), isLostItemHasBeenBilled());

    assertThat(result.getLoan(), hasNoLostItemFee());
    assertThat(result.getLoan(), hasNoLostItemProcessingFee());
  }

  @Test
  public void shouldSkipChargingFeesWhenActualCostFeeShouldBeIssued() {
    val policy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(10.00)
      .doNotChargeProcessingFeeWhenAgedToLost();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    assertThat(result.getLoan().getJson(), isLostItemHasBeenBilled());
  }

  @Test
  public void shouldChargeFeesToMultipleItems() {
    val loanToFeeMap = checkoutTenItems();

    ageToLostFixture.ageToLostAndChargeFees();

    loanToFeeMap.forEach((loan, expectedFee) -> {
      val loanFromStorage = loansStorageClient.get(loan);

      assertThat(loanFromStorage.getJson(), isLostItemHasBeenBilled());

      assertThat(loanFromStorage, hasLostItemFee(isOpen(expectedFee)));
      assertThat(loanFromStorage, hasLostItemFeeCreatedBySystemAction());
    });
  }

  @Test
  public void shouldContinueToProcessLoansAfterChargingFeesForSomeHaveFailed() {
    val loanToFeeMap = checkoutTenItems();
    val loansExpectedToBeFailed = loanToFeeMap.entrySet().stream()
      .limit(2)
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    val loansExpectedToBeProcessedSuccessfully = loanToFeeMap.entrySet().stream()
      .skip(2)
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    val firstOwner = feeFineOwnerFixture.getExistingRecord("Owner 1");
    val secondOwner = feeFineOwnerFixture.getExistingRecord("Owner 2");

    feeFineOwnerFixture.delete(firstOwner);
    feeFineOwnerFixture.delete(secondOwner);

    ageToLostFixture.ageToLostAndAttemptChargeFees();

    loansExpectedToBeFailed.forEach((loan, fee) ->
        assertThat(loansStorageClient.get(loan).getJson(), isLostItemHasNotBeenBilled()));

    loansExpectedToBeProcessedSuccessfully.forEach((loan, fee) -> {
      val loanFromStorage = loansStorageClient.get(loan);
      assertThat(loanFromStorage.getJson(), isLostItemHasBeenBilled());

      assertThat(loanFromStorage, hasLostItemFee(isOpen(fee)));
      assertThat(loanFromStorage, hasLostItemFeeCreatedBySystemAction());
    });
  }

  @Test
  public void cannotChargeFeesWhenNoProcessingFeeType() {
    feeFineTypeFixture.delete(feeFineTypeFixture.lostItemProcessingFee());

    val policy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withNoChargeAmountItem()
      .chargeProcessingFeeWhenAgedToLost(10.0);

    useLostItemPolicy(lostItemFeePoliciesFixture.create(policy).getId());

    val item = itemsFixture.basedUponNod(ItemBuilder::withRandomBarcode);
    checkOutFixture.checkOutByBarcode(item, usersFixture.steve());

    val response = ageToLostFixture.ageToLostAndAttemptChargeFees();

    assertThat(response.getJson(), hasErrorWith(
      hasMessage("No automated Lost item processing fee type found")));
  }

  @Test
  public void cannotChargeFeesWhenNoItemFeeType() {
    feeFineTypeFixture.delete(feeFineTypeFixture.lostItemFee());

    val policy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(11.00)
      .doNotChargeProcessingFeeWhenAgedToLost();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(policy).getId());

    val item = itemsFixture.basedUponNod(ItemBuilder::withRandomBarcode);
    checkOutFixture.checkOutByBarcode(item, usersFixture.steve());

    val response = ageToLostFixture.ageToLostAndAttemptChargeFees();

    assertThat(response.getJson(), hasErrorWith(
      hasMessage("No automated Lost item fee type found")));
  }

  @Test
  public void shouldNotChargeFeesWhenDelayedBillingPeriodHasNotPassed() {
    val policy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(11.00)
      .withPatronBilledAfterAgedLost(Period.months(7));

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    assertThat(result.getLoan().getJson(), isLostItemHasNotBeenBilled());
    assertThat(result.getLoan(), hasNoLostItemFee());
  }

  @Test
  public void shouldNotChargeFeesMoreThanOnce() {
    val policy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withSetCost(11.00);

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    assertThat(result.getLoan().getJson(), isLostItemHasBeenBilled());
    assertThat(result.getLoan(), hasLostItemFee(isOpen(11.00)));

    ageToLostFixture.ageToLostAndChargeFees();

    assertThat(result.getLoan(), hasLostItemFees(iterableWithSize(1)));
  }

  @Test
  public void shouldCloseLoanWhenNoFeesToCharge() {
    val policy = lostItemFeePoliciesFixture
      .ageToLostAfterOneMinutePolicy()
      .withPatronBilledAfterAgedLost(Period.weeks(1))
      .withNoChargeAmountItem()
      .doNotChargeProcessingFeeWhenAgedToLost();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    assertThat(result.getLoan().getJson(), isLostItemHasBeenBilled());
    assertThat(result.getLoan().getJson(), isClosed());
    assertThat(result.getItem().getJson(), isLostAndPaid());
  }

  private Map<IndividualResource, Double> checkoutTenItems() {
    val loanToFeeMap = new LinkedHashMap<IndividualResource, Double>();

    for (int itemIndex = 0; itemIndex < 10; itemIndex++) {
      val itemIndexFinal = itemIndex;
      val servicePoint = createServicePointForItemIndex(itemIndex);

      val location = locationsFixture.basedUponExampleLocation(builder -> builder
        .withName("Location for sp " + itemIndexFinal)
        .withCode("agl-loc-" + itemIndexFinal)
        .withPrimaryServicePoint(servicePoint.getId()));

      val item = itemsFixture.basedUponNod(itemBuilder -> itemBuilder
        .withRandomBarcode()
        .withTemporaryLocation(location.getId()));

      val setCostFee = 10.0 + itemIndex;
      val policyBuilder = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .withName("Age to lost item " + itemIndex)
        .doNotChargeProcessingFeeWhenAgedToLost()
        .withSetCost(setCostFee);

      useLostItemPolicy(lostItemFeePoliciesFixture.create(policyBuilder).getId());
      feeFineOwnerFixture.create(new FeeFineOwnerBuilder()
        .withOwner("Owner " + (itemIndex + 1))
        .withServicePointOwner(servicePoint.getId()));

      val loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());
      loanToFeeMap.put(loan, setCostFee);
    }

    return loanToFeeMap;
  }

  private IndividualResource createServicePointForItemIndex(int itemIndex) {
    return servicePointsFixture.create(new ServicePointBuilder(
      "Age to lost service point " + itemIndex, "agl-sp-" + itemIndex,
      "Age to lost service point " + itemIndex)
      .withPickupLocation(TRUE));
  }

  private Matcher<JsonObject> isLostItemHasBeenBilled() {
    return hasJsonPath("agedToLostDelayedBilling.lostItemHasBeenBilled", true);
  }

  private Matcher<JsonObject> isLostItemHasNotBeenBilled() {
    return hasJsonPath("agedToLostDelayedBilling.lostItemHasBeenBilled", false);
  }
}
