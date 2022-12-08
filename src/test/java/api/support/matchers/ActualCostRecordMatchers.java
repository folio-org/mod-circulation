package api.support.matchers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.ItemLossType;
import org.hamcrest.Matcher;
import org.hamcrest.core.Is;

import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.UserResource;
import static api.support.matchers.JsonObjectMatcher.allOfPaths;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.JsonObjectMatcher.toStringMatcher;
import static api.support.matchers.UUIDMatcher.is;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import static org.hamcrest.Matchers.allOf;

public class ActualCostRecordMatchers {
  private ActualCostRecordMatchers() {
  }

  public static Matcher<JsonObject> isActualCostRecord(IndividualResource loan, ItemResource item,
    UserResource user, ItemLossType itemLossType, String permanentLocationName,
    IndividualResource feeFineOwner, IndividualResource feeFine, String identifierType,
    IndividualResource patronGroup) {

    JsonObject instanceJson = item.getInstance().getJson();
    JsonObject itemJson = item.getJson();
    JsonArray identifiers = instanceJson.getJsonArray("identifiers");

    List<Matcher<? super String>> identifierMatchers = IntStream.range(0, identifiers.size())
      .mapToObj(i -> {
        Map<String, Matcher<String>> matchers = new HashMap<>();
        String currentIdentifierString = String.format("instance.identifiers[%d]", i);
        JsonObject currentIdentifierObject = (JsonObject) identifiers.getValue(i);
        matchers.put(currentIdentifierString + ".identifierTypeId",
          Is.is(currentIdentifierObject.getString("identifierTypeId")));
        matchers.put(currentIdentifierString + ".value",
          Is.is(currentIdentifierObject.getString("value")));
        matchers.put(currentIdentifierString + ".identifierType",
          Is.is(identifierType));

        return toStringMatcher(matchers);
      })
      .collect(Collectors.toList());

    JsonObject effectiveCallNumberComponents = itemJson.getJsonObject(
      "effectiveCallNumberComponents");
    return allOf(hasJsonPath("user.id", is(user.getId())),
      hasJsonPath("user.barcode", user.getBarcode()),
      hasJsonPath("user.patronGroupId", user.getJson().getString("patronGroup")),
      hasJsonPath("user.patronGroup", patronGroup.getJson().getString("group")),
      hasJsonPath("loan.id", is(loan.getId())),
      hasJsonPath("lossType", itemLossType.getValue()),
      hasJsonPath("lossDate", loan.getJson().getJsonObject("agedToLostDelayedBilling")
        .getString("agedToLostDate")),
      hasJsonPath("instance.title", instanceJson.getString("title")),
      hasJsonPath("item.barcode", item.getBarcode()),
      hasJsonPath("item.materialTypeId", itemJson.getString("materialTypeId")),
      hasJsonPath("item.materialType", "Book"),
      hasJsonPath("item.loanTypeId", itemJson.getString("permanentLoanTypeId")),
      hasJsonPath("item.loanType", "Can Circulate"),
      hasJsonPath("item.effectiveCallNumberComponents.callNumber",
        effectiveCallNumberComponents.getString("callNumber")),
      allOfPaths(identifierMatchers),
      hasJsonPath("item.effectiveCallNumberComponents.prefix",
        effectiveCallNumberComponents.getString("prefix")),
      hasJsonPath("item.effectiveCallNumberComponents.suffix",
        effectiveCallNumberComponents.getString("suffix")),
      hasJsonPath("item.permanentLocation", permanentLocationName),
      hasJsonPath("item.volume", itemJson.getString("volume")),
      hasJsonPath("item.chronology", itemJson.getString("chronology")),
      hasJsonPath("item.enumeration", itemJson.getString("enumeration")),
      hasJsonPath("item.copyNumber", itemJson.getString("copyNumber")),
      hasJsonPath("feeFine.ownerId", is(feeFineOwner.getId())),
      hasJsonPath("feeFine.owner", feeFineOwner.getJson().getString("owner")),
      hasJsonPath("feeFine.typeId", is(feeFine.getId())),
      hasJsonPath("feeFine.type", feeFine.getJson().getString("feeFineType")));
  }

  public static Matcher<JsonObject> isInStatus(ActualCostRecord.Status status) {
    return hasJsonPath("status", status.getValue());
  }

}
