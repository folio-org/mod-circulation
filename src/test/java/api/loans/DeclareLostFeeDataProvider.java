package api.loans;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.iterableWithSize;

import org.folio.circulation.domain.FeeFine;
import org.hamcrest.Matcher;

import io.vertx.core.json.JsonObject;

@SuppressWarnings("unused")
public final class DeclareLostFeeDataProvider {
  public static Object[][] feeDataProvider() {
    return new Object[][] {
      {true, 10.0, itemCharge("anotherCost", 20.99), expectFee(expectProcessingFee(10.0), expectItemFee(20.99))},
      {true, null, itemCharge("anotherCost", 10.999), expectFee(expectItemFee(10.999))},
      {true, 0.0, itemCharge("anotherCost", 15.0), expectFee(expectItemFee(15.0))},
      {true, 10.0, null, expectFee(expectProcessingFee(10.0))},
      {true, 12.0, itemCharge("anotherCost", null), expectFee(expectProcessingFee(12.0))},
      {true, 13.0, itemCharge("anotherCost", 0.0),  expectFee(expectProcessingFee(13.0))},
      {true, 14.0, itemCharge("actualCost", 10.0),  expectFee(expectProcessingFee(14.0))},
      {true, 0.0, itemCharge("actualCost", 10.0), expectNoFee()},
      {false, 10.0, itemCharge("anotherCost", 12.0), expectFee(expectItemFee(12.0))},
      {false, 0.0, itemCharge("anotherCost", 0.0), expectNoFee()},
      {false, 0.0, itemCharge("actualCost", 10.0), expectNoFee()},
    };
  }

  private static JsonObject itemCharge(String type, Double amount) {
    return new JsonObject()
      .put("chargeType", type)
      .put("amount", amount);
  }

  @SafeVarargs
  private static Matcher<Iterable<JsonObject>> expectFee(Matcher<JsonObject>... matchers) {
    return allOf(iterableWithSize(matchers.length), hasItems(matchers));
  }

  private static Matcher<JsonObject> expectFee(String type, double amount) {
    return allOf(hasJsonPath("amount", amount),
      hasJsonPath("feeFineType", type));
  }

  private static Matcher<JsonObject> expectProcessingFee(double amount) {
    return expectFee(FeeFine.LOST_ITEM_PROCESSING_FEE, amount);
  }

  private static Matcher<JsonObject> expectItemFee(double amount) {
    return expectFee(FeeFine.LOST_ITEM_FEE, amount);
  }

  @SuppressWarnings("unchecked")
  private static Matcher<Iterable<JsonObject>> expectNoFee() {
    return anyOf(nullValue(), iterableWithSize(0));
  }
}
