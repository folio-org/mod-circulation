package api.support.matchers;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.folio.circulation.domain.ItemLossType;
import org.hamcrest.Matcher;
import org.hamcrest.core.Is;

import com.jayway.jsonpath.matchers.JsonPathMatchers;

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
    IndividualResource feeFineOwner, IndividualResource feeFine) {
    JsonObject instanceJson = item.getInstance().getJson();
    JsonObject itemJson = item.getJson();
    JsonArray identifiers = instanceJson.getJsonArray("identifiers");

    List<Matcher<? super String>> identifierMatchers = IntStream.range(0, identifiers.size())
      .mapToObj(i -> {
        Map<String, Matcher<String>> matchers = new HashMap<>();
        String currentIdentifierString = String.format("identifiers[%d]", i);
        JsonObject currentIdentifierObject = (JsonObject) identifiers.getValue(i);
        matchers.put(currentIdentifierString + ".identifierTypeId",
          Is.is(currentIdentifierObject.getString("identifierTypeId")));
        matchers.put(currentIdentifierString + ".value",
          Is.is(currentIdentifierObject.getString("value")));

        return toStringMatcher(matchers);
      })
      .collect(Collectors.toList());

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
      hasJsonPath("effectiveCallNumberComponents.callNumber",
        effectiveCallNumberComponents.getString("callNumber")),
      allOfPaths(identifierMatchers),
      hasJsonPath("effectiveCallNumberComponents.prefix",
        effectiveCallNumberComponents.getString("prefix")),
      hasJsonPath("effectiveCallNumberComponents.suffix",
        effectiveCallNumberComponents.getString("suffix")),
      hasJsonPath("permanentItemLocation", permanentLocationName),
      hasJsonPath("feeFineOwnerId", is(feeFineOwner.getId())),
      hasJsonPath("feeFineOwner", feeFineOwner.getJson().getString("owner")),
      hasJsonPath("feeFineTypeId", is(feeFine.getId())),
      hasJsonPath("feeFineType", feeFine.getJson().getString("feeFineType")));
  }

}
