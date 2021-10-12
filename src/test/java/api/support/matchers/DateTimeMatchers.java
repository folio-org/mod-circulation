package api.support.matchers;

import static java.time.ZoneOffset.UTC;
import static org.hamcrest.CoreMatchers.is;

import java.time.ZonedDateTime;

import org.hamcrest.Matcher;

public class DateTimeMatchers {
  private DateTimeMatchers() { }

  public static Matcher<ZonedDateTime> isEquivalentTo(ZonedDateTime expected) {
    // All date times produced by the APIs should be in UTC
    return is(expected.withZoneSameInstant(UTC));
  }
}
