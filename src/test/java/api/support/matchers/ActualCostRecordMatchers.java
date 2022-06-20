package api.support.matchers;

import org.folio.circulation.domain.ItemLossType;
import org.hamcrest.Matcher;

import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.UUIDMatcher.is;
import io.vertx.core.json.JsonObject;
import static org.hamcrest.Matchers.allOf;

public class ActualCostRecordMatchers {
  private ActualCostRecordMatchers() {}

  public static Matcher<JsonObject> isActualCostRecord(IndividualResource loan, ItemResource item,
    UserResource user, ItemLossType itemLossType, String permanentLocationName, IndividualResource feeFineOwner,
    IndividualResource feeFine) {
    JsonObject instanceJson = item.getInstance().getJson();
    JsonObject itemJson = item.getJson();

    JsonObject effectiveCallNumberComponents = itemJson.getJsonObject(
      "effectiveCallNumberComponents");
    return allOf(hasJsonPath("userId", is(user.getId())),
      hasJsonPath("userBarcode", user.getBarcode()),
      hasJsonPath("loanId", is(loan.getId())),
      hasJsonPath("itemLossType", itemLossType.getValue()),
      hasJsonPath("dateOfLoss", loan.getJson().getJsonObject("agedToLostDelayedBilling")
        .getString("agedToLostDate")),
      hasJsonPath("title", instanceJson.getString("title")),
      hasJsonPath("itemBarcode", item.getBarcode()),
      hasJsonPath("loanType", "Can Circulate"),
      hasJsonPath("effectiveCallNumberComponents.callNumber", effectiveCallNumberComponents.getString("callNumber")),
      hasJsonPath("effectiveCallNumberComponents.prefix", effectiveCallNumberComponents.getString("prefix")),
      hasJsonPath("effectiveCallNumberComponents.suffix", effectiveCallNumberComponents.getString("suffix")),
      hasJsonPath("permanentItemLocation", permanentLocationName),
      hasJsonPath("feeFineOwnerId", is(feeFineOwner.getId())),
      hasJsonPath("feeFineOwner", feeFineOwner.getJson().getString("owner")),
      hasJsonPath("feeFineTypeId", is(feeFine.getId())),
      hasJsonPath("feeFineType", feeFine.getJson().getString("feeFineType")));
  }

}
