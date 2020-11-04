package api.support.matchers;

import static java.time.temporal.ChronoUnit.MILLIS;

import java.time.Instant;
import java.time.ZonedDateTime;

import org.folio.circulation.support.ClockManager;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.joda.time.DateTime;
import org.joda.time.Seconds;

public class TextDateTimeMatcher {
  public static Matcher<String> isEquivalentTo(DateTime expected) {
    return new TypeSafeMatcher<>() {
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
    return isEquivalentTo(expected.toInstant());
  }

  public static Matcher<String> isEquivalentTo(Instant expected) {
    return new TypeSafeMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a date and time matching: %s", expected.toString()));
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
    return new TypeSafeMatcher<>() {
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

  public static Matcher<String> withinSecondsBefore(Seconds seconds, DateTime before) {
    return new TypeSafeMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a date time within %s seconds before %s",
          seconds.getSeconds(), before.toString()));
      }

      @Override
      protected boolean matchesSafely(String textRepresentation) {
        DateTime actual = DateTime.parse(textRepresentation);

        return actual.isBefore(before) &&
          Seconds.secondsBetween(actual, before).isLessThan(seconds);
      }
    };
  }

  public static Matcher<String> withinSecondsBeforeNow(Seconds seconds) {
    return withinSecondsBefore(seconds, ClockManager.getClockManager().getDateTime());
  }
}
