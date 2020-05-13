package api.support.matchers;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.hamcrest.core.Is.is;
import org.hamcrest.Matcher;

import org.folio.circulation.domain.representations.LoanProperties;
import org.folio.circulation.services.EventPublishingService;

import io.vertx.core.json.JsonObject;

public class EventMatcher {

  public static Matcher<JsonObject> isCheckedOutEvent() {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventType", is(EventPublishingService.ITEM_CHECKED_OUT_EVENT_TYPE))
    );
  }

  public static Matcher<JsonObject> isValidCheckedOutEventPayload(JsonObject loan) {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("userId", is(loan.getString("userId"))),
      hasJsonPath("loanId", is(loan.getString("id"))),
      hasJsonPath("dueDate", is(loan.getString(LoanProperties.DUE_DATE)))
    );
  }

  public static Matcher<JsonObject> isCheckedInEvent() {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventType", is(EventPublishingService.ITEM_CHECKED_IN_EVENT_TYPE))
    );
  }

  public static Matcher<JsonObject> isValidCheckedInEventPayload(JsonObject loan) {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("userId", is(loan.getString("userId"))),
      hasJsonPath("loanId", is(loan.getString("id"))),
      hasJsonPath("returnDate", is(loan.getString(LoanProperties.RETURN_DATE)))
    );
  }

  public static Matcher<JsonObject> isDeclaredLostEvent() {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventType", is(EventPublishingService.ITEM_DECLARED_LOST_EVENT_TYPE))
    );
  }

  public static Matcher<JsonObject> isValidDeclaredLostEventPayload(JsonObject loan) {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("userId", is(loan.getString("userId"))),
      hasJsonPath("loanId", is(loan.getString("id")))
    );
  }

  public static Matcher<JsonObject> isDueDateChangedEvent() {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventType", is(EventPublishingService.LOAN_DUE_DATE_UPDATED_EVENT_TYPE))
    );
  }

  public static Matcher<JsonObject> isValidDueDateChangedEventPayload(JsonObject loan) {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("userId", is(loan.getString("userId"))),
      hasJsonPath("loanId", is(loan.getString("id"))),
      hasJsonPath("dueDate", is(loan.getString(LoanProperties.DUE_DATE))),
      hasJsonPath("dueDateChangedByRecall", is(getBooleanProperty(loan, "dueDateChangedByRecall")))
    );
  }

}
