package api.support.matchers;

import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTimeOptional;
import static org.folio.circulation.support.utils.DateFormatUtil.parseDateTimeOptional;
import static org.folio.circulation.support.utils.DateFormatUtil.parseInstantOptional;
import static org.folio.circulation.support.utils.DateTimeUtil.isAfterMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.isSameMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.millisBetween;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.folio.circulation.support.utils.ClockUtil;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class TextDateTimeMatcher {

  public static Matcher<String> isEquivalentTo(ZonedDateTime expected) {
    return new TypeSafeMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "an RFC-3339 formatted date and time with a UTC (zero) offset matching: %s", formatDateTimeOptional(expected)));
      }

      @Override
      protected boolean matchesSafely(String textRepresentation) {

        //response representation might vary from request representation
        return isSameMillis(expected, parseDateTimeOptional(textRepresentation));
      }
    };
  }

  public static Matcher<String> isEquivalentTo(Instant expected) {
    return new TypeSafeMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "an RFC-3339 formatted date and time with a UTC (zero) offset matching: %s", formatDateTimeOptional(expected)));
      }

      @Override
      protected boolean matchesSafely(String textRepresentation) {

        //response representation might vary from request representation
        return isSameMillis(expected, parseInstantOptional(textRepresentation));
      }
    };
  }

  public static Matcher<String> withinSecondsAfter(long seconds, ZonedDateTime after) {
    return new TypeSafeMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a date time within %l seconds after %s",
          seconds, formatDateTimeOptional(after)));
      }

      @Override
      protected boolean matchesSafely(String textRepresentation) {
        //response representation might vary from request representation
        final ZonedDateTime actual = ZonedDateTime.parse(textRepresentation)
          .truncatedTo(ChronoUnit.SECONDS);

        return !isBeforeMillis(actual, after.truncatedTo(ChronoUnit.SECONDS)) &&
          millisBetween(after.truncatedTo(ChronoUnit.SECONDS), actual) < seconds * 1000;
      }
    };
  }

  public static Matcher<String> withinSecondsBefore(long seconds, ZonedDateTime before) {
    return new TypeSafeMatcher<>() {
      @Override
      public void describeTo(Description description) {
        description.appendText(String.format(
          "a date time within %l seconds before %s",
          seconds, formatDateTimeOptional(before)));
      }

      @Override
      protected boolean matchesSafely(String textRepresentation) {
        final ZonedDateTime actual = ZonedDateTime.parse(textRepresentation)
          .truncatedTo(ChronoUnit.SECONDS);

        return !isAfterMillis(actual, before.truncatedTo(ChronoUnit.SECONDS)) &&
          millisBetween(before.truncatedTo(ChronoUnit.SECONDS), actual) < seconds * 1000;
      }
    };
  }

  public static Matcher<String> withinSecondsBeforeNow(long seconds) {
    return withinSecondsBefore(seconds, ClockUtil.getZonedDateTime());
  }
}
