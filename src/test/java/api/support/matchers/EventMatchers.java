package api.support.matchers;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.hamcrest.core.Is.is;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import io.vertx.core.json.JsonObject;

public class EventMatchers {

  public static Matcher<JsonObject> isValidItemCheckedOutEvent(JsonObject loan) {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventType", is("ITEM_CHECKED_OUT")),
      hasJsonPath("eventPayload", isValidItemCheckedOutEventPayload(loan))
    );
  }

  private static TypeSafeDiagnosingMatcher<String> isValidItemCheckedOutEventPayload(
    JsonObject loan) {

    return new TypeSafeDiagnosingMatcher<String>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("eventPayload should be a valid ITEM_CHECKED_OUT event payload");
      }

      @Override
      protected boolean matchesSafely(String representation, Description description) {
        JsonObject payload = new JsonObject(representation);

        return is(loan.getString("userId")).matches(payload.getString("userId")) &&
          is(loan.getString("id")).matches(payload.getString("loanId")) &&
          is(loan.getString("dueDate")).matches(payload.getString("dueDate"));
      }
    };
  }

  public static Matcher<JsonObject> isValidItemCheckedInEvent(JsonObject loan) {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventType", is("ITEM_CHECKED_IN")),
      hasJsonPath("eventPayload", isValidItemCheckedInEventPayload(loan))
    );
  }

  private static TypeSafeDiagnosingMatcher<String> isValidItemCheckedInEventPayload(
    JsonObject loan) {

    return new TypeSafeDiagnosingMatcher<String>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("eventPayload should be a valid ITEM_CHECKED_IN event payload");
      }

      @Override
      protected boolean matchesSafely(String representation, Description description) {
        JsonObject payload = new JsonObject(representation);

        return is(loan.getString("userId")).matches(payload.getString("userId")) &&
          is(loan.getString("id")).matches(payload.getString("loanId")) &&
          is(loan.getString("returnDate")).matches(payload.getString("returnDate"));
      }
    };
  }

  public static Matcher<JsonObject> isValidItemDeclaredLostEvent(JsonObject loan) {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventType", is("ITEM_DECLARED_LOST")),
      hasJsonPath("eventPayload", isValidItemDeclaredLostEventPayload(loan))
    );
  }

  private static TypeSafeDiagnosingMatcher<String> isValidItemDeclaredLostEventPayload(
    JsonObject loan) {

    return new TypeSafeDiagnosingMatcher<String>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("eventPayload should be a valid ITEM_DECLARED_LOST event payload");
      }

      @Override
      protected boolean matchesSafely(String representation, Description description) {
        JsonObject payload = new JsonObject(representation);

        return is(loan.getString("userId")).matches(payload.getString("userId")) &&
          is(loan.getString("id")).matches(payload.getString("loanId"));
      }
    };
  }

  public static Matcher<JsonObject> isValidLoanDueDateChangedEvent(JsonObject loan) {
    return JsonObjectMatcher.allOfPaths(
      hasJsonPath("eventType", is("LOAN_DUE_DATE_CHANGED")),
      hasJsonPath("eventPayload", isValidLoanDueDateChangedEventPayload(loan))
    );
  }

  private static TypeSafeDiagnosingMatcher<String> isValidLoanDueDateChangedEventPayload(
    JsonObject loan) {

    return new TypeSafeDiagnosingMatcher<String>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("eventPayload should be a valid LOAN_DUE_DATE_CHANGED event " +
          "payload");
      }

      @Override
      protected boolean matchesSafely(String representation, Description description) {
        JsonObject payload = new JsonObject(representation);

        return is(loan.getString("userId")).matches(payload.getString("userId")) &&
          is(loan.getString("id")).matches(payload.getString("loanId")) &&
          is(loan.getString("dueDate")).matches(payload.getString("dueDate")) &&
          is(getBooleanProperty(loan, "dueDateChangedByRecall"))
            .matches(payload.getBoolean("dueDateChangedByRecall"));
      }
    };
  }

}
