package api.support.matchers;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.joda.time.DateTime;
import org.joda.time.Seconds;

public class TextDateTimeMatcher {
  public static Matcher<String> isEquivalentTo(DateTime expected) {
    return new TypeSafeMatcher<String>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a date time matching: %s", expected.toString()));
      }

      @Override
      protected boolean matchesSafely(String textRepresentation) {
        //response representation might vary from request representation
        DateTime actual = DateTime.parse(textRepresentation);

        return expected.isEqual(actual);
      }
    };
  }

  public static Matcher<String> withinSecondsAfter(Seconds seconds, DateTime after) {
    return new TypeSafeMatcher<String>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a date time within %s seconds after %s",
          seconds.getSeconds(), after.toString()));
      }

      @Override
      protected boolean matchesSafely(String textRepresentation) {
        //response representation might vary from request representation
        DateTime actual = DateTime.parse(textRepresentation);

        return actual.isAfter(after) &&
          Seconds.secondsBetween(after, actual).isLessThan(seconds);
      }
    };
  }
}
