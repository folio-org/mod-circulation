package api.support.matchers;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.hamcrest.CoreMatchers.describedAs;

import org.hamcrest.Matcher;

import io.vertx.core.json.JsonObject;

public final class ItemMatchers {

  private ItemMatchers() {}

  public static Matcher<JsonObject> checkedOut() {
    return hasStatus("Checked out");
  }

  public static Matcher<JsonObject> withdrawn() {
    return hasStatus("Withdrawn");
  }

  public static Matcher<JsonObject> available() {
    return hasStatus("Available");
  }

  public static Matcher<JsonObject> inTransit() {
    return hasStatus("In transit");
  }

  public static Matcher<JsonObject> awaitingPickup() {
    return hasStatus("Awaiting pickup");
  }

  private static Matcher<JsonObject> hasStatus(String status) {
    return describedAs("Item with status [%0]",
      hasJsonPath("status.name", status), status);
  }
}
