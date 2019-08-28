package api.support.matchers;

import static org.hamcrest.CoreMatchers.is;

import org.folio.circulation.support.Result;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public class ResultMatchers {
  public static TypeSafeDiagnosingMatcher<Result> succeeded() {
    return new TypeSafeDiagnosingMatcher<Result>() {
      @Override
      public void describeTo(Description description) {
        description
          .appendText("a successful result");
      }

      @Override
      protected boolean matchesSafely(Result result, Description description) {
        final Matcher<Boolean> successMatcher = is(true);

        description.appendText("is a " + result.toString());

        return successMatcher.matches(result.succeeded());
      }
    };
  }
}
