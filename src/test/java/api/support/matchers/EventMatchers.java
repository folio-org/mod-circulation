package api.support.matchers;

import static api.support.matchers.EventTypeMatchers.isItemCheckedInEventType;
import static api.support.matchers.EventTypeMatchers.isItemCheckedOutEventType;
import static api.support.matchers.EventTypeMatchers.isItemDeclaredLostEventType;
import static api.support.matchers.EventTypeMatchers.isLoanDueDateChangedEventType;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.core.Is.is;

import org.hamcrest.Matcher;

import io.vertx.core.json.JsonObject;

public class EventMatchers {

  public static Matcher<JsonObject> isValidItemCheckedOutEvent(JsonObject loan) {
    return allOf(JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventPayload", allOf(
        hasJsonPath("userId", is(loan.getString("userId"))),
        hasJsonPath("loanId", is(loan.getString("id"))),
        hasJsonPath("dueDate", is(loan.getString("dueDate")))
      ))),
      isItemCheckedOutEventType());
  }

  public static Matcher<JsonObject> isValidItemCheckedInEvent(JsonObject loan) {
    return allOf(JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventPayload", allOf(
        hasJsonPath("userId", is(loan.getString("userId"))),
        hasJsonPath("loanId", is(loan.getString("id"))),
        hasJsonPath("returnDate", is(loan.getString("returnDate")))
      ))),
      isItemCheckedInEventType());
  }

  public static Matcher<JsonObject> isValidItemDeclaredLostEvent(JsonObject loan) {
    return allOf(JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventPayload", allOf(
        hasJsonPath("userId", is(loan.getString("userId"))),
        hasJsonPath("loanId", is(loan.getString("id")))
      ))),
      isItemDeclaredLostEventType());
  }

  public static Matcher<JsonObject> isValidLoanDueDateChangedEvent(JsonObject loan) {
    return allOf(JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventPayload", allOf(
        hasJsonPath("userId", is(loan.getString("userId"))),
        hasJsonPath("loanId", is(loan.getString("id"))),
        hasJsonPath("dueDate", is(loan.getString("dueDate"))),
        hasJsonPath("dueDateChangedByRecall",
          is(getBooleanProperty(loan, "dueDateChangedByRecall")))
      ))),
      isLoanDueDateChangedEventType());
  }

}
