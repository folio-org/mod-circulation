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

    return create(chargeFeePolicy());
  }

  public IndividualResource ageToLostAfterOneMinute() {
    createReferenceData();

    return create(ageToLostAfterOneMinutePolicy());
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
      .withPatronBilledAfterAgedLost(patronBilledAfterAgedLost)
      .withNoChargeAmountItem()
      .doNotChargeProcessingFeeWhenDeclaredLost()
      .withChargeAmountItemSystem(true)
      .refundProcessingFeeWhenReturned()
      .withReplacedLostItemProcessingFee(true)
      .withReplacementAllowed(true)
      .chargeOverdueFineWhenReturned();
  }

  private LostItemFeePolicyBuilder chargeFeePolicy() {
    Period itemAgedLostOverdue = Period.months(12);
    Period patronBilledAfterAgedLost = Period.months(12);

    return new LostItemFeePolicyBuilder()
      .withName("No lost item fees policy")
      .withItemAgedToLostAfterOverdue(itemAgedLostOverdue)
      .withPatronBilledAfterAgedLost(patronBilledAfterAgedLost)
      .withSetCost(10.00)
      .chargeProcessingFeeWhenDeclaredLost(5.00)
      .withChargeAmountItemSystem(true)
      .refundProcessingFeeWhenReturned()
      .withReplacedLostItemProcessingFee(true)
      .withReplacementAllowed(true)
      .chargeOverdueFineWhenReturned();
  }

  public LostItemFeePolicyBuilder ageToLostAfterOneMinutePolicy() {
    return chargeFeePolicy()
      .withName("Age to lost after one minute overdue")
      .withItemAgedToLostAfterOverdue(minutes(1))
      .withPatronBilledAfterAgedLost(minutes(5))
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
      .refundFeesWithinMinutes(10)
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
    feeFineOwnerFixture.cd1Owner();
  }
}
