package api.support.fixtures;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import api.support.builders.LostItemFeePolicyBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class LostItemFeePoliciesFixture {
  private final RecordCreator lostItemFeePolicyRecordCreator;

  public LostItemFeePoliciesFixture(ResourceClient lostItemFeePoliciesClient) {
    lostItemFeePolicyRecordCreator = new RecordCreator(lostItemFeePoliciesClient,
      reason -> getProperty(reason, "name"));
  }

  public IndividualResource facultyStandard() {

    JsonObject itemAgedLostOverdue = new JsonObject();
    itemAgedLostOverdue.put("duration", 12);
    itemAgedLostOverdue.put("intervalId", "Months");

    JsonObject patronBilledAfterAgedLost = new JsonObject();
    patronBilledAfterAgedLost.put("duration", 12);
    patronBilledAfterAgedLost.put("intervalId", "Months");

    JsonObject chargeAmountItem = new JsonObject();
    chargeAmountItem.put("chargeType", "Actual cost");
    chargeAmountItem.put("amount", 5.00);

    JsonObject lostItemChargeFeeFine = new JsonObject();
    lostItemChargeFeeFine.put("duration", "6");
    lostItemChargeFeeFine.put("intervalId", "Months");

    final LostItemFeePolicyBuilder undergradStandard = new LostItemFeePolicyBuilder()
      .withName("Undergrad standard")
      .withDescription("This is description for undergrad standard")
      .withItemAgedLostOverdue(itemAgedLostOverdue)
      .withPatronBilledAfterAgedLost(patronBilledAfterAgedLost)
      .withChargeAmountItem(chargeAmountItem)
      .withLostItemProcessingFee(5.00)
      .withChargeAmountItemPatron(true)
      .withChargeAmountItemSystem(true)
      .withLostItemChargeFeeFine(lostItemChargeFeeFine)
      .withReturnedLostItemProcessingFee(true)
      .withReplacedLostItemProcessingFee(true)
      .withReplacementAllowed(true)
      .withLostItemReturned("Charge");

    return lostItemFeePolicyRecordCreator.createIfAbsent(undergradStandard);
  }

  public IndividualResource create(UUID id, String name) {
    return lostItemFeePolicyRecordCreator.createIfAbsent(new LostItemFeePolicyBuilder()
      .withId(id)
      .withName(name));
  }

  public void create(List<String> ids) {
    ids.stream()
      .map(id -> new LostItemFeePolicyBuilder()
        .withId(UUID.fromString(id)))
//        .withName("Example LostItemFeePolicy " + id))
      .forEach(lostItemFeePolicyRecordCreator::createIfAbsent);
  }

  public void cleanUp() {
    lostItemFeePolicyRecordCreator.cleanUp();
  }
}
