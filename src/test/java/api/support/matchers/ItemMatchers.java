package api.support.matchers;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.hamcrest.CoreMatchers.describedAs;

import org.hamcrest.Matcher;

import io.vertx.core.json.JsonObject;

public final class ItemMatchers {

  private ItemMatchers() {}

  public static Matcher<JsonObject> isLostAndPaid() {
    return hasStatus("Lost and paid");
  }

  public static Matcher<JsonObject> isDeclaredLost() {
    return hasStatus("Declared lost");
  }

  public static Matcher<JsonObject> isCheckedOut() {
    return hasStatus("Checked out");
  }

  public static Matcher<JsonObject> isWithdrawn() {
    return hasStatus("Withdrawn");
  }

  public static Matcher<JsonObject> isLostAndPaid() {
    return hasStatus("Lost and paid");
  }

  public static Matcher<JsonObject> isAvailable() {
    return hasStatus("Available");
  }

  public static Matcher<JsonObject> isInTransit() {
    return hasStatus("In transit");
  }

  public static Matcher<JsonObject> isAwaitingPickup() {
    return hasStatus("Awaiting pickup");
  }

  private static Matcher<JsonObject> hasStatus(String status) {
    return describedAs("Item with status [%0]",
      hasJsonPath("status.name", status), status);
  }
}
