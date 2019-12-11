package api.support.matchers;

import static java.time.temporal.ChronoUnit.MILLIS;

import java.time.Instant;
import java.time.ZonedDateTime;

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

  public static Matcher<String> isEquivalentTo(ZonedDateTime expected) {
    return new TypeSafeMatcher<String>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a zoned date time matching: %s", expected.toString()));
      }

      @Override
      protected boolean matchesSafely(String textRepresentation) {
        //response representation might vary from request representation
        ZonedDateTime actual = ZonedDateTime.parse(textRepresentation);

        //The zoned date time could have a higher precision than milliseconds
        //This makes comparison to an ISO formatted date time using milliseconds
        //excessively precise and brittle
        //Discovered when using JDK 13.0.1 instead of JDK 1.8.0_202-b08
        return expected.truncatedTo(MILLIS).isEqual(actual);
      }
    };
  }

  public static Matcher<String> isEquivalentTo(Instant expected) {
    return new TypeSafeMatcher<String>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a zoned date time matching: %s", expected.toString()));
      }

      @Override
      protected boolean matchesSafely(String textRepresentation) {
        //response representation might vary from request representation
        Instant actual = Instant.parse(textRepresentation);

        //The zoned date time could have a higher precision than milliseconds
        //This makes comparison to an ISO formatted date time using milliseconds
        //excessively precise and brittle
        //Discovered when using JDK 13.0.1 instead of JDK 1.8.0_202-b08
        return expected.truncatedTo(MILLIS).equals(actual);
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

        return !actual.isBefore(after) &&
          Seconds.secondsBetween(after, actual).isLessThan(seconds);
      }
    };
  }
}
