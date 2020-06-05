package api.support.matchers;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.startsWith;

import org.folio.circulation.domain.FeeAmount;
import org.hamcrest.Matcher;

import io.vertx.core.json.JsonObject;

public final class AccountActionsMatchers {
  public static final String CANCELLED_ITEM_RETURNED = "Cancelled item returned";
  public static final String REFUNDED_FULLY = "Refunded fully";
  public static final String CREDITED_FULLY = "Credited fully";
  public static final String LOST_ITEM_FOUND = "Lost item found";
  public static final String REFUND_TO_BURSAR = "Refund to Bursar";
  public static final String REFUND_TO_PATRON = "Refund to patron";

  private AccountActionsMatchers() {}

  public static Matcher<Iterable<JsonObject>> arePaymentRefundActionsCreated(
    double remaining, double paymentAmount) {

    final FeeAmount creditAmount = new FeeAmount(remaining)
      .subtract(new FeeAmount(paymentAmount));
    return hasItems(
      allOf(
        hasJsonPath("createdAt", startsWith("Circ Desk")),
        hasJsonPath("source", "Admin, Admin"),
        hasJsonPath("amountAction", paymentAmount),
        hasJsonPath("balance", creditAmount.toDouble()),
        hasJsonPath("typeAction", CREDITED_FULLY),
        hasJsonPath("transactionInformation", REFUND_TO_PATRON),
        hasJsonPath("paymentMethod", LOST_ITEM_FOUND)),
      allOf(
        hasJsonPath("createdAt", startsWith("Circ Desk")),
        hasJsonPath("source", "Admin, Admin"),
        hasJsonPath("amountAction", paymentAmount),
        hasJsonPath("balance", remaining),
        hasJsonPath("typeAction", REFUNDED_FULLY),
        hasJsonPath("transactionInformation", REFUND_TO_PATRON),
        hasJsonPath("paymentMethod", LOST_ITEM_FOUND))
    );
  }

  public static Matcher<Iterable<JsonObject>> arePaymentRefundActionsCreated(double paymentAmount) {
    return arePaymentRefundActionsCreated(0.0, paymentAmount);
  }

  public static Matcher<Iterable<JsonObject>> areTransferRefundActionsCreated(double transferAmount) {
    return areTransferRefundActionsCreated(0.0, transferAmount);
  }

  public static Matcher<Iterable<JsonObject>> areTransferRefundActionsCreated(
    double remaining, double transferAmount) {

    final FeeAmount creditAmount = new FeeAmount(remaining)
      .subtract(new FeeAmount(transferAmount));
    return hasItems(
      allOf(
        hasJsonPath("createdAt", startsWith("Circ Desk")),
        hasJsonPath("source", "Admin, Admin"),
        hasJsonPath("amountAction", transferAmount),
        hasJsonPath("balance", creditAmount.toDouble()),
        hasJsonPath("typeAction", CREDITED_FULLY),
        hasJsonPath("transactionInformation", REFUND_TO_BURSAR),
        hasJsonPath("paymentMethod", LOST_ITEM_FOUND)),
      allOf(
        hasJsonPath("createdAt", startsWith("Circ Desk")),
        hasJsonPath("source", "Admin, Admin"),
        hasJsonPath("amountAction", transferAmount),
        hasJsonPath("balance", remaining),
        hasJsonPath("typeAction", REFUNDED_FULLY),
        hasJsonPath("transactionInformation", REFUND_TO_BURSAR),
        hasJsonPath("paymentMethod", LOST_ITEM_FOUND))
    );
  }

  public static Matcher<Iterable<JsonObject>> isCancelledItemReturnedActionCreated(double amount) {
    return hasItems(allOf(
      hasJsonPath("createdAt", startsWith("Circ Desk")),
      hasJsonPath("source", "Admin, Admin"),
      hasJsonPath("amountAction", amount),
      hasJsonPath("balance", 0.0),
      hasJsonPath("typeAction", CANCELLED_ITEM_RETURNED))
    );
  }
}
