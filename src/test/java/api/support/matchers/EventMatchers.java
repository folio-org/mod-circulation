package api.support.matchers;

import static api.support.matchers.EventActionMatchers.isItemRenewedEventAction;
import static api.support.matchers.EventTypeMatchers.isItemAgedToLostEventType;
import static api.support.matchers.EventTypeMatchers.isItemCheckedInEventType;
import static api.support.matchers.EventTypeMatchers.isItemCheckedOutEventType;
import static api.support.matchers.EventTypeMatchers.isItemClaimedReturnedEventType;
import static api.support.matchers.EventTypeMatchers.isItemDeclaredLostEventType;
import static api.support.matchers.EventTypeMatchers.isLoanClosedEventType;
import static api.support.matchers.EventTypeMatchers.isLoanDueDateChangedEventType;
import static api.support.matchers.EventTypeMatchers.isLogRecordEventType;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static java.util.Optional.ofNullable;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.utils.DateFormatUtil.parseDateTime;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.core.Is.is;

import org.folio.circulation.domain.representations.logs.LogEventType;
import org.hamcrest.Matcher;

import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

public class EventMatchers {
  public static Matcher<JsonObject> isValidItemCheckedOutEvent(JsonObject loan,
    IndividualResource loanPolicy) {

    return allOf(JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventPayload", allOf(
        hasJsonPath("userId", is(loan.getString("userId"))),
        hasJsonPath("loanId", is(loan.getString("id"))),
        hasJsonPath("dueDate", is(loan.getString("dueDate"))),
        buildGracePeriodMatcher(loanPolicy)))),
      isItemCheckedOutEventType());
  }

  private static Matcher<Object> buildGracePeriodMatcher(IndividualResource loanPolicy) {
    return ofNullable(loanPolicy)
      .map(IndividualResource::getJson)
      .map(lp -> lp.getJsonObject("loansPolicy"))
      .map(lp -> lp.getJsonObject("gracePeriod"))
      .map(EventMatchers::buildGracePeriodMatcher)
      .orElse(hasNoJsonPath("gracePeriod"));
  }

  private static Matcher<Object> buildGracePeriodMatcher(JsonObject gracePeriodJson) {
    return hasJsonPath("gracePeriod", allOf(
      hasJsonPath("duration", is(gracePeriodJson.getInteger("duration"))),
      hasJsonPath("intervalId", is(gracePeriodJson.getString("intervalId")))));
  }

  public static Matcher<JsonObject> isValidCheckOutLogEvent(JsonObject checkedOutLoan, LogEventType logEventType) {
    return allOf(JsonObjectMatcher.allOfPaths(
        hasJsonPath("eventPayload", allOf(
          hasJsonPath("logEventType", is(logEventType.value())),
          hasJsonPath("servicePointId", is(checkedOutLoan.getString("checkoutServicePointId"))),
          hasJsonPath("loanId", is(checkedOutLoan.getString("id"))),
          hasJsonPath("isLoanClosed", is(checkedOutLoan.getJsonObject("status").getString("name").equals("Closed"))),
          hasJsonPath("dueDate", is(checkedOutLoan.getString("dueDate"))),
          hasJsonPath("userId", is(checkedOutLoan.getString("userId"))),
          hasJsonPath("itemId", is(checkedOutLoan.getString("itemId"))),
          hasJsonPath("source", is("Admin, Admin")),
          hasJsonPath("itemBarcode", is(checkedOutLoan.getJsonObject("item").getString("barcode"))),
          hasJsonPath("itemStatusName", is(checkedOutLoan.getJsonObject("item").getJsonObject("status").getString("name")))
        ))),
      isLogRecordEventType());
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

  public static Matcher<JsonObject> doesNotContainUserBarcode() {
    return allOf(JsonObjectMatcher.allOfPaths(
        hasJsonPath("eventPayload", allOf(
          hasNoJsonPath("userBarcode")
        ))),
      isItemCheckedInEventType());
  }
  public static Matcher<JsonObject> isValidCheckInLogEvent(JsonObject checkedInLoan) {
    return allOf(JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventPayload", allOf(
        hasJsonPath("logEventType", is("CHECK_IN_EVENT")),
        hasJsonPath("servicePointId", is(checkedInLoan.getString("checkinServicePointId"))),
        hasJsonPath("returnDate", is(checkedInLoan.getString("returnDate"))),
        hasJsonPath("loanId", is(checkedInLoan.getString("id"))),
        hasJsonPath("isLoanClosed", is(checkedInLoan.getJsonObject("status").getString("name").equals("Closed"))),
        hasJsonPath("systemReturnDate", is(checkedInLoan.getString("systemReturnDate"))),
        hasJsonPath("returnDate", is(checkedInLoan.getString("returnDate"))),
        hasJsonPath("source", is("Admin, Admin")),
        hasJsonPath("dueDate", is(checkedInLoan.getString("dueDate"))),
        hasJsonPath("userId", is(checkedInLoan.getString("userId"))),
        hasJsonPath("itemId", is(checkedInLoan.getString("itemId"))),
        hasJsonPath("zoneId", is("Z")),
        hasJsonPath("itemBarcode", is(checkedInLoan.getJsonObject("item").getString("barcode"))),
        hasJsonPath("itemStatusName", is(checkedInLoan.getJsonObject("item").getJsonObject("status").getString("name"))),
        hasJsonPath("userBarcode", is(checkedInLoan.getJsonObject("borrower").getString("barcode")))
      ))),
      isLogRecordEventType());
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

  public static Matcher<JsonObject> isValidLoanClosedEvent(JsonObject loan) {
    return allOf(JsonObjectMatcher.allOfPaths(
        hasJsonPath("eventPayload", allOf(
          hasJsonPath("userId", is(loan.getString("userId"))),
          hasJsonPath("loanId", is(loan.getString("id")))
        ))),
      isLoanClosedEventType());
  }

  public static Matcher<JsonObject> isValidItemAgedToLostEvent(JsonObject loan) {
    return allOf(JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventPayload", allOf(
        hasJsonPath("userId", is(loan.getString("userId"))),
        hasJsonPath("loanId", is(loan.getString("id")))
      ))),
      isItemAgedToLostEventType());
  }

  public static Matcher<JsonObject> isValidLoanDueDateChangedEvent(JsonObject loan) {
    return allOf(JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventPayload", allOf(
        hasJsonPath("userId", is(loan.getString("userId"))),
        hasJsonPath("loanId", is(loan.getString("id"))),
        hasJsonPath("dueDate", isEquivalentTo(parseDateTime(loan.getString("dueDate")))),
        hasJsonPath("dueDateChangedByRecall",
          is(getBooleanProperty(loan, "dueDateChangedByRecall")))
      ))),
      isLoanDueDateChangedEventType());
  }

  public static Matcher<JsonObject> isValidRenewedEvent(JsonObject loan) {
    return allOf(JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventPayload", allOf(
        hasJsonPath("payload", allOf(
          hasJsonPath("userId", is(loan.getString("userId"))),
          hasJsonPath("loanId", is(loan.getString("id")))
        ))))),
      isItemRenewedEventAction());
  }

  public static Matcher<JsonObject> isValidLoanLogRecordEvent(JsonObject loanCtx) {
    return allOf(JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventPayload", allOf(
        hasJsonPath("logEventType", is("LOAN")),
        hasJsonPath("payload", allOf(
          hasJsonPath("loanId", is(loanCtx.getString("id"))),
          hasJsonPath("userId", is(loanCtx.getString("userId"))),
          hasJsonPath("itemId", is(loanCtx.getString("itemId"))),
          hasJsonPath("itemBarcode", is(loanCtx.getJsonObject("item").getString("barcode"))),
          hasJsonPath("instanceId", is(loanCtx.getJsonObject("item").getString("instanceId"))),
          hasJsonPath("holdingsRecordId", is(loanCtx.getJsonObject("item").getString("holdingsRecordId")))
        ))
      ))),
      isLogRecordEventType());
  }

  public static Matcher<JsonObject> isValidAnonymizeLoansLogRecordEvent(JsonObject loanCtx) {
    return allOf(JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventPayload", allOf(
        hasJsonPath("logEventType", is("LOAN")),
        hasJsonPath("payload", allOf(
          hasJsonPath("loanId", is(loanCtx.getString("id"))),
          hasJsonPath("itemId", is(loanCtx.getString("itemId"))),
          hasJsonPath("updatedByUserId", is(loanCtx.getJsonObject("metadata").getString("updatedByUserId"))),
          hasJsonPath("action", is("Anonymize"))
        ))
      ))),
      isLogRecordEventType());
  }


  public static Matcher<JsonObject> isValidNoticeLogRecordEvent(JsonObject notice) {
    return allOf(JsonObjectMatcher.allOfPaths(
        hasJsonPath("eventPayload", allOf(
          hasJsonPath("logEventType", is("NOTICE")),
          hasJsonPath("payload", allOf(
            hasJsonPath("userId", is(notice.getString("recipientId"))),
            hasJsonPath("userBarcode", is(notice.getJsonObject("context").getJsonObject("user").getString("barcode")))
          ))
        ))),
      isLogRecordEventType());
  }
}
