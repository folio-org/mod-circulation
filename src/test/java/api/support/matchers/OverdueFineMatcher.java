package api.support.matchers;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getUUIDProperty;
import static org.hamcrest.core.Is.is;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.representations.LoanProperties;
import org.hamcrest.Matcher;

import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

public class OverdueFineMatcher {

  public static Matcher<JsonObject> isValidOverdueFine(JsonObject loan,
    IndividualResource item, String location, UUID ownerId, UUID feeFineId, Double amount) {

    List<Matcher<? super String>> matchers = new ArrayList<>(Arrays.asList(
      hasJsonPath("ownerId", UUIDMatcher.is(ownerId)),
      hasJsonPath("feeFineId", is(feeFineId.toString())),
      hasJsonPath("amount", is(amount)),
      hasJsonPath("remaining", is(amount)),
      hasJsonPath("feeFineType", is("Overdue fine")),
      hasJsonPath("feeFineOwner", is("fee-fine-owner")),
      hasJsonPath("title", is(loan.getJsonObject("item").getString("title"))),
      hasJsonPath("barcode", is(item.getJson().getString("barcode"))),
      hasJsonPath("callNumber", is(item.getJson().getJsonObject("effectiveCallNumberComponents")
        .getString("callNumber"))),
      hasJsonPath("location", is(location)),
      hasJsonPath("materialTypeId", is(item.getJson().getString("materialTypeId"))),
      hasJsonPath("materialType", is(loan.getJsonObject("item")
        .getJsonObject("materialType").getString("name"))),
      hasJsonPath("loanId", UUIDMatcher.is(getUUIDProperty(loan, "id"))),
      hasJsonPath("userId", is(loan.getString("userId"))),
      hasJsonPath("itemId", UUIDMatcher.is(item.getId())),
      hasJsonPath("dueDate", is(loan.getString(LoanProperties.DUE_DATE))),
      hasJsonPath("loanPolicyId", is(loan.getString("loanPolicyId"))),
      hasJsonPath("overdueFinePolicyId", is(loan.getString("overdueFinePolicyId"))),
      hasJsonPath("lostItemFeePolicyId", is(loan.getString("lostItemPolicyId")))
    ));

    if (loan.getString(LoanProperties.RETURN_DATE) != null) {
      matchers.add(hasJsonPath("returnedDate", is(loan.getString(LoanProperties.RETURN_DATE))));
    }
    else {
      matchers.add(hasNoJsonPath("returnedDate"));
    }

    return JsonObjectMatcher.allOfPaths(matchers);
  }

}
