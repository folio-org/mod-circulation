package org.folio.circulation.domain.policy.library;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.junit.Assert;
import org.junit.Test;

import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.END_OF_A_DAY;

public class KeepCurrentStrategyTest {

  @Test
  public void testKeepCurrentDateStrategy() {
    ClosedLibraryStrategy keepCurrentStrategy = new KeepCurrentDateStrategy(DateTimeZone.UTC);
    DateTime requestDate =
      new DateTime(2019, DateTimeConstants.JANUARY, 1, 0, 0)
        .withZoneRetainFields(DateTimeZone.UTC);
    DateTime calculatedDateTime = keepCurrentStrategy.calculateDueDate(requestDate, null);

    DateTime expectedDate = requestDate.withTime(END_OF_A_DAY);
    Assert.assertEquals(expectedDate, calculatedDateTime);
  }

  @Test
  public void testKeepCurrentDateTimeStrategy() {
    ClosedLibraryStrategy keepCurrentStrategy = new KeepCurrentDateTimeStrategy();
    DateTime requestDate =
      new DateTime(2019, DateTimeConstants.JANUARY, 1, 0, 0)
        .withZoneRetainFields(DateTimeZone.UTC);
    DateTime calculatedDateTime = keepCurrentStrategy.calculateDueDate(requestDate, null);

    Assert.assertEquals(requestDate, calculatedDateTime);
  }
}
