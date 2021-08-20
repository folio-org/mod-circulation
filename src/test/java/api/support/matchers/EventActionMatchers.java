package api.support.matchers;

import io.vertx.core.json.JsonObject;
import org.hamcrest.Matcher;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.core.Is.is;

public class EventActionMatchers {
  public static final String ITEM_RENEWED = "Renewed";

  public static Matcher<JsonObject> isItemRenewedEventAction() {
    return isEventOfAction(ITEM_RENEWED);
  }

  private static Matcher<JsonObject> isEventOfAction(String eventAction) {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventPayload", allOf(
        hasJsonPath("payload", allOf(
          hasJsonPath("action", is(eventAction)))))));
  }
}
