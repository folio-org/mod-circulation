package api.support.matchers;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class FailureMatcher {
  public static <T> Matcher<HttpResult<T>> isValidationFailure(String expectedReason) {
    return new TypeSafeMatcher<HttpResult<T>>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a validation failure: %s", expectedReason));
      }

      @Override
      protected boolean matchesSafely(HttpResult<T> failedResult) {
        if(!failedResult.failed()) {
          return false;
        }
        else if(failedResult.cause() instanceof ValidationErrorFailure) {
          final ValidationErrorFailure cause = (ValidationErrorFailure) failedResult.cause();

          return cause.getReason().equals(expectedReason);
        }
        else {
          return false;
        }
      }
    };
  }
}
