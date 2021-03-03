package api.support.matchers;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.core.Is.is;

import org.hamcrest.Matcher;

import io.vertx.core.json.JsonObject;

public class EventTypeMatchers {
  public static final String ITEM_CHECKED_OUT = "ITEM_CHECKED_OUT";
  public static final String ITEM_CHECKED_IN = "ITEM_CHECKED_IN";
  public static final String ITEM_DECLARED_LOST = "ITEM_DECLARED_LOST";
  public static final String ITEM_AGED_TO_LOST = "ITEM_AGED_TO_LOST";
  public static final String ITEM_CLAIMED_RETURNED = "ITEM_CLAIMED_RETURNED";
  public static final String LOAN_DUE_DATE_CHANGED = "LOAN_DUE_DATE_CHANGED";
  public static final String LOG_RECORD = "LOG_RECORD";

  public static Matcher<JsonObject> isItemCheckedOutEventType() {
    return isEventOfType(ITEM_CHECKED_OUT);
  }

  public static Matcher<JsonObject> isItemCheckedInEventType() {
    return isEventOfType(ITEM_CHECKED_IN);
  }

  public static Matcher<JsonObject> isItemDeclaredLostEventType() {
    return isEventOfType(ITEM_DECLARED_LOST);
  }

  public static Matcher<JsonObject> isItemAgedToLostEventType() {
    return isEventOfType(ITEM_AGED_TO_LOST);
  }

  public static Matcher<JsonObject> isItemClaimedReturnedEventType() {
    return isEventOfType(ITEM_CLAIMED_RETURNED);
  }

  public static Matcher<JsonObject> isLoanDueDateChangedEventType() {
    return isEventOfType(LOAN_DUE_DATE_CHANGED);
  }

  public static Matcher<JsonObject> isLogRecordEventType() {
    return isEventOfType(LOG_RECORD);
  }

  private static Matcher<JsonObject> isEventOfType(String eventType) {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventType", is(eventType)));
  }
}
