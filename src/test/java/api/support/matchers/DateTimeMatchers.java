package api.support.matchers;

import static org.folio.circulation.support.utils.DateTimeUtil.toOffsetDateTime;
import static org.hamcrest.CoreMatchers.is;
import static org.joda.time.DateTimeZone.UTC;

import java.time.OffsetDateTime;

import org.hamcrest.Matcher;
import org.joda.time.DateTime;

class DateTimeMatchers {
  private DateTimeMatchers() { }

  public static Matcher<OffsetDateTime> isEquivalentTo(DateTime expected) {
    // All date times produced by the APIs should be in UTC
    return is(toOffsetDateTime(expected.withZone(UTC)));
  }
}
