package api.support.matchers;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.hamcrest.CoreMatchers.describedAs;
import static org.hamcrest.CoreMatchers.is;

import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsNull;

import io.vertx.core.json.JsonObject;

public final class RequestMatchers {

  private RequestMatchers() {}

  public static Matcher<JsonObject> isOpenAwaitingPickup() {
    return hasStatus("Open - Awaiting pickup");
  }

  public static Matcher<JsonObject> isOpenNotYetFilled() {
    return hasStatus("Open - Not yet filled");
  }

  public static Matcher<JsonObject> isOpenInTransit() {
    return hasStatus("Open - In transit");
  }

  public static Matcher<JsonObject> isClosedFilled() {
    return hasStatus("Closed - Filled");
  }

  public static Matcher<JsonObject> isItemLevel() {
    return hasLevel("Item");
  }

  public static Matcher<JsonObject> isTitleLevel() {
    return hasLevel("Title");
  }

  public static Matcher<JsonObject> hasPosition(int position) {
    return describedAs("Request with position [%0]",
      hasJsonPath("position", position), position);
  }

  private static Matcher<JsonObject> hasStatus(String status) {
    return describedAs("Request with status [%0]",
      hasJsonPath("status", status), status);
  }

  private static Matcher<JsonObject> hasLevel(String level) {
    return describedAs("Request with level [%0]",
      hasJsonPath("requestLevel", level), level);
  }
}
