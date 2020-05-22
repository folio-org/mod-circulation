package api.support.matchers;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.core.Is.is;

import org.hamcrest.Matcher;

import io.vertx.core.json.JsonObject;

public class EventTypeMatchers {
  public static Matcher<JsonObject> isItemCheckedOutEventType() {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventType", is("ITEM_CHECKED_OUT"))
    );
  }

  public static Matcher<JsonObject> isItemCheckedInEventType() {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventType", is("ITEM_CHECKED_IN"))
    );
  }

  public static Matcher<JsonObject> isItemDeclaredLostEventType() {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventType", is("ITEM_DECLARED_LOST"))
    );
  }

  public static Matcher<JsonObject> isLoanDueDateChangedEventType() {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventType", is("LOAN_DUE_DATE_CHANGED"))
    );
  }
}
