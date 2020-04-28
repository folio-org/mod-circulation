package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.LostItemFeePolicyBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class LostItemFeePoliciesFixture {
  private final RecordCreator lostItemFeePolicyRecordCreator;
  private final FeeFineTypeFixture feeFineTypeFixture;
  private final FeeFineOwnerFixture feeFineOwnerFixture;

  public LostItemFeePoliciesFixture(ResourceClient lostItemFeePoliciesClient,
    FeeFineOwnerFixture feeFineOwnerFixture, FeeFineTypeFixture feeFineTypeFixture) {

    lostItemFeePolicyRecordCreator = new RecordCreator(lostItemFeePoliciesClient,
      reason -> getProperty(reason, "name"));
    this.feeFineOwnerFixture = feeFineOwnerFixture;
    this.feeFineTypeFixture = feeFineTypeFixture;
  }

  public IndividualResource facultyStandard() {
    return create(facultyStandardPolicy());
  }

  public IndividualResource chargeFee() {
    createReferenceData();

    return create(chargeFeePolicy());
  }

  public LostItemFeePolicyBuilder facultyStandardPolicy() {
    JsonObject itemAgedLostOverdue = new JsonObject();
    itemAgedLostOverdue.put("duration", 12);
    itemAgedLostOverdue.put("intervalId", "Months");

    JsonObject patronBilledAfterAgedLost = new JsonObject();
    patronBilledAfterAgedLost.put("duration", 12);
    patronBilledAfterAgedLost.put("intervalId", "Months");

    return new LostItemFeePolicyBuilder()
      .withName("Undergrad standard")
      .withDescription("This is description for undergrad standard")
      .withItemAgedLostOverdue(itemAgedLostOverdue)
      .withPatronBilledAfterAgedLost(patronBilledAfterAgedLost)
      .withNoChargeAmountItem()
      .doNotChargeProcessingFee()
      .withChargeAmountItemSystem(true)
      .withReturnedLostItemProcessingFee(true)
      .withReplacedLostItemProcessingFee(true)
      .withReplacementAllowed(true)
      .withLostItemReturned("Charge");
  }

  private LostItemFeePolicyBuilder chargeFeePolicy() {
    JsonObject itemAgedLostOverdue = new JsonObject();
    itemAgedLostOverdue.put("duration", 12);
    itemAgedLostOverdue.put("intervalId", "Months");

    JsonObject patronBilledAfterAgedLost = new JsonObject();
    patronBilledAfterAgedLost.put("duration", 12);
    patronBilledAfterAgedLost.put("intervalId", "Months");

    return new LostItemFeePolicyBuilder()
      .withName("No lost item fees policy")
      .withItemAgedLostOverdue(itemAgedLostOverdue)
      .withPatronBilledAfterAgedLost(patronBilledAfterAgedLost)
      .withSetCost(10.00)
      .chargeProcessingFee()
      .withLostItemProcessingFee(5.00)
      .withChargeAmountItemSystem(true)
      .withReturnedLostItemProcessingFee(true)
      .withReplacedLostItemProcessingFee(true)
      .withReplacementAllowed(true)
      .withLostItemReturned("Charge");
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
