package org.folio.circulation.domain.policy.library;

import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.junit.Assert;
import org.junit.Test;

public class KeepCurrentStrategyTest {

  @Test
  public void testKeepCurrentStrategy() {
    KeepCurrentStrategy keepCurrentStrategy = new KeepCurrentStrategy();
    DateTime requestDate = new DateTime(2019, DateTimeConstants.JANUARY, 1, 0, 0);
    DateTime calculatedDateTime = keepCurrentStrategy.calculateDueDate(requestDate, null);
    Assert.assertEquals(requestDate, calculatedDateTime);
  }
}
