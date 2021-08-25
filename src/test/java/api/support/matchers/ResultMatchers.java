package api.support.matchers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItems;

import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.hamcrest.TypeSafeMatcher;

public final class ResultMatchers {
  private ResultMatchers() {}

  public static TypeSafeDiagnosingMatcher<Result<?>> succeeded() {
    return new TypeSafeDiagnosingMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description
          .appendText("a successful result");
      }

      @Override
      protected boolean matchesSafely(Result<?> result, Description description) {
        final Matcher<Boolean> successMatcher = is(true);

        description.appendText("is a " + result.toString());

        return successMatcher.matches(result.succeeded());
      }
    };
  }

  public static <T> Matcher<Result<T>> hasValidationErrors(
    Matcher<Iterable<ValidationError>> errorMatcher) {

    return new TypeSafeMatcher<>() {
      @Override
      protected boolean matchesSafely(Result<T> item) {
        if (item.failed() && item.cause() instanceof ValidationErrorFailure) {
          final var validationFailure = (ValidationErrorFailure) item.cause();

          return errorMatcher.matches(validationFailure.getErrors());
        }

        return false;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("Result has validation errors matching: ")
          .appendDescriptionOf(errorMatcher);
      }
    };
  }

  @SafeVarargs
  public static <T> Matcher<Result<T>> hasValidationErrors(Matcher<ValidationError>... errorMatchers) {
    return hasValidationErrors(hasItems(errorMatchers));
  }

  public static <T> Matcher<Result<T>> hasValidationError(Matcher<ValidationError> errorMatcher) {
    return hasValidationErrors(errorMatcher);
  }
}
