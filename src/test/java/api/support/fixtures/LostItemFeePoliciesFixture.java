package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.builders.LostItemFeePolicyBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class LostItemFeePoliciesFixture {
  private final RecordCreator lostItemFeePolicyRecordCreator;

  public LostItemFeePoliciesFixture(ResourceClient lostItemFeePoliciesClient) {
    lostItemFeePolicyRecordCreator = new RecordCreator(lostItemFeePoliciesClient,
      reason -> getProperty(reason, "name"));
  }

  public IndividualResource facultyStandard() {
    return create(facultyStandardPolicy());
  }

  public IndividualResource chargeSetCostFee() {
    return create(chargeSetCostFeePolicy());
  }

  public LostItemFeePolicyBuilder facultyStandardPolicy() {
    JsonObject itemAgedLostOverdue = new JsonObject();
    itemAgedLostOverdue.put("duration", 12);
    itemAgedLostOverdue.put("intervalId", "Months");

    JsonObject patronBilledAfterAgedLost = new JsonObject();
    patronBilledAfterAgedLost.put("duration", 12);
    patronBilledAfterAgedLost.put("intervalId", "Months");

    JsonObject lostItemChargeFeeFine = new JsonObject();
    lostItemChargeFeeFine.put("duration", "6");
    lostItemChargeFeeFine.put("intervalId", "Months");

    return new LostItemFeePolicyBuilder()
      .withName("Undergrad standard")
      .withDescription("This is description for undergrad standard")
      .withItemAgedLostOverdue(itemAgedLostOverdue)
      .withPatronBilledAfterAgedLost(patronBilledAfterAgedLost)
      .withNoChargeAmountItem()
      .doNotChargeProcessingFee()
      .withChargeAmountItemSystem(true)
      .withLostItemChargeFeeFine(lostItemChargeFeeFine)
      .withReturnedLostItemProcessingFee(true)
      .withReplacedLostItemProcessingFee(true)
      .withReplacementAllowed(true)
      .withLostItemReturned("Charge");
  }

  public LostItemFeePolicyBuilder chargeSetCostFeePolicy() {
    JsonObject itemAgedLostOverdue = new JsonObject();
    itemAgedLostOverdue.put("duration", 12);
    itemAgedLostOverdue.put("intervalId", "Months");

    JsonObject patronBilledAfterAgedLost = new JsonObject();
    patronBilledAfterAgedLost.put("duration", 12);
    patronBilledAfterAgedLost.put("intervalId", "Months");

    JsonObject lostItemChargeFeeFine = new JsonObject();
    lostItemChargeFeeFine.put("duration", "6");
    lostItemChargeFeeFine.put("intervalId", "Months");

    return new LostItemFeePolicyBuilder()
      .withName("No lost item fees policy")
      .withItemAgedLostOverdue(itemAgedLostOverdue)
      .withPatronBilledAfterAgedLost(patronBilledAfterAgedLost)
      .withChargeAmountItem("anotherCost", 10.00)
      .chargeProcessingFee()
      .withLostItemProcessingFee(5.00)
      .withChargeAmountItemSystem(true)
      .withLostItemChargeFeeFine(lostItemChargeFeeFine)
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
}
