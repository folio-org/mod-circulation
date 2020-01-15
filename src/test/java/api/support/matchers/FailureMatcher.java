package api.support.matchers;

import static org.hamcrest.core.StringContains.containsString;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public class FailureMatcher {
  public static <T> Matcher<Result<T>> hasValidationFailure(String expectedReason) {
    return new TypeSafeDiagnosingMatcher<Result<T>>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a validation failure: %s", expectedReason));
      }

      @Override
      protected boolean matchesSafely(Result<T> failedResult, Description description) {
        if(!failedResult.failed()) {
          return false;
        }
        else if(failedResult.cause() instanceof ValidationErrorFailure) {
          final ValidationErrorFailure cause = (ValidationErrorFailure) failedResult.cause();

          return cause.hasErrorWithReason(expectedReason);
        }
        else {
          return false;
        }
      }
    };
  }

  public static <T> Matcher<Result<T>> hasNumberOfFailureMessages(int numberFailureMsg) {
    return new TypeSafeDiagnosingMatcher<Result<T>>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "`%s` error messages", numberFailureMsg));
      }

      @Override
      protected boolean matchesSafely(Result<T> failedResult, Description description) {
        if (!failedResult.failed()) {
          return false;
        } else if (failedResult.cause() instanceof ValidationErrorFailure) {
          final ValidationErrorFailure cause = (ValidationErrorFailure) failedResult.cause();
          return cause.getErrors().size() == numberFailureMsg;
        } else {
          return false;
        }
      }
    };
  }

  public static <T> Matcher<Result<T>> isErrorFailureContaining(String expectedReason) {
    return new TypeSafeDiagnosingMatcher<Result<T>>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "an error failure: %s", expectedReason));
      }

      @Override
      protected boolean matchesSafely(Result<T> failedResult, Description description) {
        if(!failedResult.failed()) {
          description.appendText("but is a successful result");
          return false;
        }

        if(failedResult.cause() instanceof ServerErrorFailure) {
          final ServerErrorFailure cause = (ServerErrorFailure) failedResult.cause();

          final Matcher<String> matcher = containsString(expectedReason);

          matcher.describeMismatch(cause.reason, description);

          return matcher.matches(cause.reason);
        }
        else {
          description.appendText("but is not an error failure");
          return false;
        }
      }
    };
  }

  public static Matcher<HttpFailure> isFailureContaining(String expectedReason) {
    return new TypeSafeDiagnosingMatcher<HttpFailure>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format("a failure: %s", expectedReason));
      }

      @Override
      protected boolean matchesSafely(HttpFailure failure, Description description) {
        if(failure instanceof ServerErrorFailure) {
          final ServerErrorFailure cause = (ServerErrorFailure) failure;

          final Matcher<String> matcher = containsString(expectedReason);

          matcher.describeMismatch(cause.reason, description);

          return matcher.matches(cause.reason);
        }
        else {
          description.appendText("but is not a failure");
          return false;
        }
      }
    };
  }
}
