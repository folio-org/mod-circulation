package api.support.matchers;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.hamcrest.CoreMatchers.describedAs;

import org.hamcrest.Matcher;

import io.vertx.core.json.JsonObject;

public final class RequestMatchers {

  private RequestMatchers() {}

  public static Matcher<JsonObject> isOpenAwaitingPickup() {
    return hasStatus("Open - Awaiting pickup");
  }

  public static Matcher<JsonObject> isOpenInTransit() {
    return hasStatus("Open - In transit");
  }

  private static Matcher<JsonObject> hasStatus(String status) {
    return describedAs("Request with status [%0]",
      hasJsonPath("status", status), status);
  }
}
