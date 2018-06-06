package api.support.matchers;

import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ValidationErrorFailure;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public class FailureMatchers {
  public static <T> TypeSafeDiagnosingMatcher<HttpResult<T>> errorResultFor(
    String propertyName,
    String reason) {
    return new TypeSafeDiagnosingMatcher<HttpResult<T>>() {
      @Override
      public void describeTo(Description description) {
        description.appendValue(reason)
          .appendText(" for ").appendValue(propertyName);
      }

      @Override
      protected boolean matchesSafely(HttpResult<T> failure, Description description) {
        if(failure.succeeded()) {
          description.appendText("is not a failed result");
          return false;
        }
        if(failure.cause() instanceof ValidationErrorFailure) {
          ValidationErrorFailure error = (ValidationErrorFailure) failure.cause();

          if(!error.hasErrorForKey(propertyName)) {
            description.appendText("not for ").appendValue(propertyName).appendText(" property");
            return false;
          }

          description.appendValue(error.toString());

          return error.hasErrorWithReason(reason);
        }
        else {
          description.appendText("is not a validation error failure");
          return false;
        }
      }
    };
  }
}
