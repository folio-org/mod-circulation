package api.support.matchers;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.hamcrest.CoreMatchers.allOf;

import org.hamcrest.Matcher;

import io.vertx.core.json.JsonObject;

public final class AccountMatchers {
  private AccountMatchers() {}

  public static Matcher<JsonObject> isOpen(double amount) {
    return allOf(
      hasJsonPath("status.name", "Open"),
      hasJsonPath("remaining", amount),
      hasJsonPath("amount", amount));
  }

  public static Matcher<JsonObject> isRefundedFully(double amount) {
    return allOf(
      hasJsonPath("amount", amount),
      hasJsonPath("remaining", 0.0),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", "Refunded fully"));
  }

  public static Matcher<JsonObject> isClosedCancelledItemReturned(double amount) {
    return allOf(
      hasJsonPath("amount", amount),
      hasJsonPath("remaining", 0.0),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", "Cancelled item returned"));
  }

  public static Matcher<JsonObject> isTransferredFully(double amount) {
    return allOf(
      hasJsonPath("amount", amount),
      hasJsonPath("remaining", 0.0),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", "Transferred fully"));
  }

  public static Matcher<JsonObject> isPaidFully(double amount) {
    return allOf(
      hasJsonPath("amount", amount),
      hasJsonPath("remaining", 0.0),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", "Paid fully"));
  }
}
