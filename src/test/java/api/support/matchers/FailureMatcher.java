package api.support.matchers;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
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

          return cause.reason.equals(expectedReason);
        }
        else {
          return false;
        }
      }
    };
  }

  public static <T> Matcher<HttpResult<T>> isServerErrorFailure(String expectedReason) {
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
        else if(failedResult.cause() instanceof ServerErrorFailure) {
          final ServerErrorFailure cause = (ServerErrorFailure) failedResult.cause();

          return cause.reason.equals(expectedReason);
        }
        else {
          return false;
        }
      }
    };
  }
}
