package api.support.fixtures;

import static api.support.http.ResourceClient.forLostItemFeePolicies;
import static org.folio.circulation.domain.policy.Period.minutes;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.util.UUID;

import org.folio.circulation.domain.policy.Period;
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
    feeFineTypeFixture.lostItemFee();
    return create(chargeFeePolicy(10.0, 5.0));
  }

  public IndividualResource chargeFeeWithActualCost() {
    createReferenceData();
    feeFineTypeFixture.lostItemActualCostFee();
    return create(chargeActualCostFeePolicy( 10.0, 5.0));
  }

  public IndividualResource chargeFeeWithZeroLostItemFee() {
    createReferenceData();
    feeFineTypeFixture.lostItemFee();
    return create(chargeFeePolicy(0.0, 5.0));
  }

  public IndividualResource chargeFeeWithZeroLostItemProcessingFee() {
    createReferenceData();
    feeFineTypeFixture.lostItemFee();
    return create(chargeFeePolicy(10.0, 0.0));
  }

  public IndividualResource chargeActualCostFeeWithZeroLostItemProcessingFee() {
    createReferenceData();
    feeFineTypeFixture.lostItemActualCostFee();
    return create(chargeActualCostFeePolicy(10.0, 0.0));
  }

  public IndividualResource ageToLostAfterOneMinute() {
    createReferenceData();
    feeFineTypeFixture.lostItemFee();
    return create(ageToLostAfterOneMinutePolicy());
  }

  public IndividualResource ageToLostAfterOneWeek() {
    createReferenceData();
    feeFineTypeFixture.lostItemFee();
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

  private LostItemFeePolicyBuilder chargeFeePolicy(double lostItemFeeCost,
    double lostItemProcessingFeeCost) {

    return chargePolicy()
      .withName("No lost item fees policy")
      .withSetCost(lostItemFeeCost)
      .chargeProcessingFeeWhenDeclaredLost(lostItemProcessingFeeCost);
  }

  private LostItemFeePolicyBuilder chargeActualCostFeePolicy(double lostItemFeeCost,
    double lostItemProcessingFeeCost) {

    return chargePolicy()
      .withName("No lost item actual cost fees policy")
      .withActualCost(lostItemFeeCost)
      .chargeProcessingFeeWhenDeclaredLost(lostItemProcessingFeeCost);
  }

  private LostItemFeePolicyBuilder chargePolicy() {
    Period itemAgedLostOverdue = Period.months(12);
    Period patronBilledAfterAgedLost = Period.months(12);

    return new LostItemFeePolicyBuilder()
      .withName("No lost item fees policy")
      .withItemAgedToLostAfterOverdue(itemAgedLostOverdue)
      .withPatronBilledAfterItemAgedToLost(patronBilledAfterAgedLost)
      .withChargeAmountItemSystem(true)
      .refundProcessingFeeWhenReturned()
      .withReplacedLostItemProcessingFee(true)
      .withReplacementAllowed(true)
      .chargeOverdueFineWhenReturned();
  }

  public LostItemFeePolicyBuilder ageToLostAfterOneMinutePolicy() {
    return chargeFeePolicy(10.0, 5.00)
      .withName("Age to lost after one minute overdue")
      .withItemAgedToLostAfterOverdue(minutes(1))
      .withPatronBilledAfterItemAgedToLost(minutes(5))
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
    feeFineOwnerFixture.cd1Owner();
  }
}
