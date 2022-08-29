package api.support.fixtures;

import static api.support.http.ResourceClient.forLostItemFeePolicies;
import static org.folio.circulation.domain.policy.Period.minutes;
import static org.folio.circulation.domain.policy.lostitem.ChargeAmountType.ACTUAL_COST;
import static org.folio.circulation.domain.policy.lostitem.ChargeAmountType.SET_COST;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.UUID;

import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.domain.policy.lostitem.ChargeAmountType;

import api.support.http.IndividualResource;

import api.support.builders.LostItemFeePolicyBuilder;

public class LostItemFeePoliciesFixture {
  private final RecordCreator lostItemFeePolicyRecordCreator;
  private final FeeFineTypeFixture feeFineTypeFixture;
  private final FeeFineOwnerFixture feeFineOwnerFixture;

  public LostItemFeePoliciesFixture() {

    lostItemFeePolicyRecordCreator = new RecordCreator(forLostItemFeePolicies(),
      reason -> getProperty(reason, "name"));
    this.feeFineOwnerFixture = new FeeFineOwnerFixture();
    this.feeFineTypeFixture = new FeeFineTypeFixture();
  }

  public IndividualResource facultyStandard() {
    return create(facultyStandardPolicy());
  }

  public IndividualResource chargeFee() {
    createReferenceData();

    return create(chargeSetCostFeePolicy(10.0, 5.0));
  }

  public IndividualResource chargeFeeWithZeroLostItemFee() {
    createReferenceData();

    return create(chargeSetCostFeePolicy(0.0, 5.0));
  }

  public IndividualResource chargeFeeWithZeroLostItemProcessingFee() {
    createReferenceData();

    return create(chargeSetCostFeePolicy(10.0, 0.0));
  }

  public IndividualResource ageToLostAfterOneMinute() {
    createReferenceData();

    return create(ageToLostAfterOneMinutePolicy());
  }

  public IndividualResource ageToLostAfterOneMinute(ChargeAmountType costType) {
    createReferenceData();

    return create(ageToLostAfterOneMinutePolicy(costType));
  }

  public IndividualResource ageToLostAfterOneWeek() {
    createReferenceData();

    return create(ageToLostAfterOneWeekPolicy());
  }


  public LostItemFeePolicyBuilder facultyStandardPolicy() {
    final Period itemAgedLostOverdue = Period.months(12);
    final Period patronBilledAfterAgedLost = Period.months(12);

    return new LostItemFeePolicyBuilder()
      .withName("Undergrad standard")
      .withDescription("This is description for undergrad standard")
      .withItemAgedToLostAfterOverdue(itemAgedLostOverdue)
      .withPatronBilledAfterItemAgedToLost(patronBilledAfterAgedLost)
      .withNoChargeAmountItem()
      .doNotChargeProcessingFeeWhenDeclaredLost()
      .withChargeAmountItemSystem(true)
      .refundProcessingFeeWhenReturned()
      .withReplacedLostItemProcessingFee(true)
      .withReplacementAllowed(true)
      .chargeOverdueFineWhenReturned();
  }

  private LostItemFeePolicyBuilder chargeSetCostFeePolicy(double lostItemFeeCost,
    double lostItemProcessingFeeCost) {

    return chargeFeePolicy(lostItemFeeCost, lostItemProcessingFeeCost, SET_COST);
  }

  private LostItemFeePolicyBuilder chargeActualCostFeePolicy(double lostItemFeeCost,
    double lostItemProcessingFeeCost) {

    return chargeFeePolicy(lostItemFeeCost, lostItemProcessingFeeCost, ACTUAL_COST);
  }

  private LostItemFeePolicyBuilder chargeFeePolicy(double lostItemFeeCost,
    double lostItemProcessingFeeCost, ChargeAmountType costType) {

    Period itemAgedLostOverdue = Period.months(12);
    Period patronBilledAfterAgedLost = Period.months(12);

    return new LostItemFeePolicyBuilder()
      .withName("No lost item fees policy")
      .withItemAgedToLostAfterOverdue(itemAgedLostOverdue)
      .withPatronBilledAfterItemAgedToLost(patronBilledAfterAgedLost)
      .withChargeAmountItem(costType.getValue(), lostItemFeeCost)
      .chargeProcessingFeeWhenDeclaredLost(lostItemProcessingFeeCost)
      .withChargeAmountItemSystem(true)
      .refundProcessingFeeWhenReturned()
      .withReplacedLostItemProcessingFee(true)
      .withReplacementAllowed(true)
      .withRecalledItemAgedToLostAfterOverdue(Period.months(6))
      .chargeOverdueFineWhenReturned();
  }

  public LostItemFeePolicyBuilder ageToLostAfterOneMinutePolicy() {
    return ageToLostAfterOneMinutePolicy(SET_COST);
  }

  public LostItemFeePolicyBuilder ageToLostAfterOneMinutePolicy(ChargeAmountType costType) {
    return chargeFeePolicy(10.0, 5.00, costType)
      .withName("Age to lost after one minute overdue")
      .withItemAgedToLostAfterOverdue(minutes(1))
      .withPatronBilledAfterItemAgedToLost(minutes(5))
      .withPatronBilledAfterRecalledItemAgedLost(minutes(5))
      // disable lost item processing fee
      .withChargeAmountItemPatron(false)
      .withChargeAmountItemSystem(true);
  }

  public LostItemFeePolicyBuilder ageToLostAfterOneWeekPolicy() {
    Period itemAgedLostOverdue = Period.weeks(1);

    return new LostItemFeePolicyBuilder()
      .withName("lost item allows for overdues")
      .withItemAgedToLostAfterOverdue(itemAgedLostOverdue)
      .billPatronImmediatelyWhenAgedToLost()
      .withSetCost(10.00)
      .doNotChargeProcessingFeeWhenAgedToLost()
      .withChargeAmountItemSystem(true)
      .refundFeesWithinWeeks(1)
      .chargeOverdueFineWhenReturned();
  }

  public IndividualResource create(UUID id, String name) {
    return lostItemFeePolicyRecordCreator.createIfAbsent(new LostItemFeePolicyBuilder()
      .withId(id)
      .withName(name));
  }

  public IndividualResource create(LostItemFeePolicyBuilder lostItemFeePolicyBuilder) {
    return lostItemFeePolicyRecordCreator.createIfAbsent(lostItemFeePolicyBuilder);
  }

  public void cleanUp() {
    lostItemFeePolicyRecordCreator.cleanUp();
  }

  private void createReferenceData() {
    feeFineTypeFixture.lostItemProcessingFee();
    feeFineTypeFixture.lostItemFee();
    feeFineTypeFixture.lostItemActualCostFee();
    feeFineOwnerFixture.cd1Owner();
  }
}
