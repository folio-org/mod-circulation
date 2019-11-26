package api.support.matchers;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

import io.vertx.core.json.JsonObject;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public class LoanMatchers {
  public static TypeSafeDiagnosingMatcher<JsonObject> hasOpenStatus() {
    return hasStatus("Open");
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> isClosed() {
    return hasStatus("Closed");
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> isAnonymized() {
    return doesNotHaveUserId();
  }

  public static Matcher<JsonObject> loanItemIsDeclaredLost() {
    return allOf(
      hasItemStatus("Declared lost"),
      hasLoanProperty("declaredLostDate")
      );
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> hasLoanProperty(
    String propertyName, String expectedValue) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Loan should have a ")
          .appendText(propertyName).
          appendText(" of ").appendText(expectedValue);
      }

      @Override
      protected boolean matchesSafely(JsonObject representation,
        Description description) {

        final String actualValue = representation.getString(propertyName);
        Matcher<String> objectMatcher = is(actualValue);
        objectMatcher.describeMismatch(expectedValue, description );
        return objectMatcher.matches(expectedValue);
      }
    };
  }

  public static TypeSafeDiagnosingMatcher<JsonObject> hasLoanProperty(
    String propertyName) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Loan item should have a ")
          .appendText(propertyName);
      }

      @Override
      protected boolean matchesSafely(JsonObject representation,
        Description description) {
        return notNullValue().matches(representation.getValue(propertyName));
      }
    };
  }

  private static TypeSafeDiagnosingMatcher<JsonObject> hasItemStatus(
    String expectedStatus) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Loan item should have a status of ")
          .appendText(expectedStatus);
      }

      @Override
      protected boolean matchesSafely(JsonObject representation,
        Description description) {

        final Matcher<String> itemStatusMatcher = is(expectedStatus);

        final String itemStatus = representation.getJsonObject("item")
          .getJsonObject("status").getString("name");

        itemStatusMatcher.describeMismatch(itemStatus, description);

        return itemStatusMatcher.matches(itemStatus);
      }
    };
  }

  private static TypeSafeDiagnosingMatcher<JsonObject> hasStatus(
    String status) {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Loan should have a status of ")
          .appendText(status);
      }

      @Override
      protected boolean matchesSafely(JsonObject representation,
        Description description) {
        if (!representation.containsKey("status")) {
          description.appendText("has no status property");
          return false;
        }

        final Matcher<String> statusMatcher = is(status);

        final String statusName = representation.getJsonObject("status")
          .getString("name");
        statusMatcher.describeMismatch(statusName, description);
        return statusMatcher.matches(statusName);
      }
    };
  }

  private static TypeSafeDiagnosingMatcher<JsonObject> doesNotHaveUserId() {
    return new TypeSafeDiagnosingMatcher<JsonObject>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("Anonymized loan should not have a userId");
      }

      @Override
      protected boolean matchesSafely(JsonObject representation,
        Description description) {
        this.describeMismatch(representation.getValue("userId"), description);
        return !representation.containsKey("userId") && !representation
          .containsKey("borrower");

      }
    };
  }
}
