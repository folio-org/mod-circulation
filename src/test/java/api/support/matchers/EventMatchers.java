package api.support.matchers;

import static api.support.matchers.EventTypeMatchers.isItemCheckedInEventType;
import static api.support.matchers.EventTypeMatchers.isItemCheckedOutEventType;
import static api.support.matchers.EventTypeMatchers.isItemClaimedReturnedEventType;
import static api.support.matchers.EventTypeMatchers.isItemDeclaredLostEventType;
import static api.support.matchers.EventTypeMatchers.isLoanDueDateChangedEventType;
import static api.support.matchers.EventTypeMatchers.isLogRecordEventType;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.core.Is.is;

import io.vertx.core.json.JsonArray;
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

  public static Matcher<JsonObject> isValidItemClaimedReturnedEvent(JsonObject loan) {
    return allOf(JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventPayload", allOf(
        hasJsonPath("userId", is(loan.getString("userId"))),
        hasJsonPath("loanId", is(loan.getString("id")))
      ))),
      isItemClaimedReturnedEventType());
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

  public static Matcher<JsonObject> isValidNoticeLogRecordEvent(JsonObject notice) {
    return allOf(JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventPayload", allOf(
        hasJsonPath("logEventType", is("NOTICE")),
        hasJsonPath("userBarcode", is(notice.getString("userBarcode"))),
        hasJsonPath("userId", is(notice.getString("userId"))),
        hasJsonPath("items", allOf(
          hasJsonPath("itemId", is(notice.getString("itemId"))),
          hasJsonPath("itemBarcode", is(notice.getString("barcode"))),
          hasJsonPath("instanceId", is(notice.getString("instanceId"))),
          hasJsonPath("holdingsRecordId", is(notice.getString("holdingsRecordId")))
        )),
        hasJsonPath("date", is(notice.getString("date"))),
        hasJsonPath("servicePointId", is(notice.getString("servicePointId"))),
        hasJsonPath("templateId", is(notice.getString("templateId"))),
        hasJsonPath("triggeringEvent", is(notice.getString("triggeringEvent"))),
        hasJsonPath("noticePolicyId", is(notice.getString("noticePolicyId")))
      ))),
      isLogRecordEventType());
  }
}
