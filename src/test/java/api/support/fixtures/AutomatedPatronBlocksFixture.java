package api.support.fixtures;

import java.util.Arrays;

import org.folio.circulation.support.http.client.IndividualResource;

import api.support.http.ResourceClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class AutomatedPatronBlocksFixture {
  public static final String MAX_NUMBER_OF_ITEMS_CHARGED_OUT_MESSAGE =
    "Patron has reached maximum allowed number of items charged out";
  public static final String MAX_OUTSTANDING_FEE_FINE_BALANCE_MESSAGE =
    "Patron has reached maximum allowed outstanding fee/fine balance for his/her patron group";

  private final ResourceClient automatedPatronBlocksClient;

  public AutomatedPatronBlocksFixture() {
    automatedPatronBlocksClient = ResourceClient.forAutomatedPatronBlocks();
  }

  public IndividualResource blockAction(String userId, boolean blockBorrowing,
    boolean blockRenewal, boolean blockRequest) {
    // Real automatedPatronBlocks object is generated on-the-fly and doesn't have it's own ID -
    // it's retrieved by user ID.
    // ID field is added here for compatibility with FakeStorageModule.
    JsonObject automatedPatronBlocks = new JsonObject()
      .put("id", userId)
      .put("automatedPatronBlocks", new JsonArray(Arrays.asList(
        new JsonObject().put("patronBlockConditionId", "2149fff5-a64c-4943-aa79-bb1d09511382")
          .put("blockBorrowing", blockBorrowing)
          .put("blockRenewal", blockRenewal)
          .put("blockRequest", blockRequest)
          .put("message", MAX_NUMBER_OF_ITEMS_CHARGED_OUT_MESSAGE),
        new JsonObject().put("patronBlockConditionId", "ac13a725-b25f-48fa-84a6-4af021d13afe")
          .put("blockBorrowing", blockBorrowing)
          .put("blockRenewal", blockRenewal)
          .put("blockRequest", blockRequest)
          .put("message", MAX_OUTSTANDING_FEE_FINE_BALANCE_MESSAGE)
      )));

    return automatedPatronBlocksClient.create(automatedPatronBlocks);
  }
}
