package api.support.matchers;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.UUID;

public class UUIDMatcher {
  public static TypeSafeDiagnosingMatcher<String> is(UUID expected) {
    return new TypeSafeDiagnosingMatcher<String>() {
      @Override
      public void describeTo(Description description) {
        description.appendText("is ").appendValue(expected.toString());
      }

      @Override
      protected boolean matchesSafely(String value, Description description) {
        final Matcher<Object> matcher = Matchers.is(expected);

        matcher.describeMismatch(value, description);

        return matcher.matches(UUID.fromString(value));
      }
    };
  }
}
