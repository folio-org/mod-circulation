package api.support.matchers;

import static org.folio.circulation.support.utils.DateTimeUtil.toOffsetDateTime;

import java.time.OffsetDateTime;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;

public class DateTimeMatchers {
  private DateTimeMatchers() { }

  public static Matcher<OffsetDateTime> isEquivalentTo(DateTime expected) {
    return CoreMatchers.is(toOffsetDateTime(expected));
  }
}
