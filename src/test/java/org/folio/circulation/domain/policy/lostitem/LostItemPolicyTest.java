package org.folio.circulation.domain.policy.lostitem;

import static org.folio.circulation.domain.policy.Period.from;
import static org.folio.circulation.domain.policy.Period.minutes;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.folio.circulation.domain.policy.Period;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class LostItemPolicyTest {

  @Test
  @Parameters( {
    "Minutes",
    "Hours",
    "Days",
    "Weeks",
    "Months",
  })
  public void shouldNotAgeItemToLostIfDueDateAfterNow(String interval) {
    final Random random = new Random();
    final int duration = random.nextInt(1000);
    final Period period = from(duration, interval);
    final LostItemPolicy lostItemPolicy = ageLostItemToLostPolicy(period);

    final DateTime loanDueDate = now(UTC).plus(period.timePeriod())
      .plus(minutes(random.nextInt(3)).timePeriod());

    assertFalse(lostItemPolicy.canAgeLoanToLost(loanDueDate));
  }

  @Test
  @Parameters( {
    "Minutes",
    "Hours",
    "Days",
    "Weeks",
    "Months",
  })
  public void shouldAgeItemToLostIfDueDateBeforeNow(String interval) {
    final Random random = new Random();
    final int duration = random.nextInt(1000);
    final Period period = from(duration, interval);
    final LostItemPolicy lostItemPolicy = ageLostItemToLostPolicy(period);

    final DateTime loanDueDate = now(UTC).minus(period.timePeriod())
      .minus(minutes(random.nextInt(3)).timePeriod());

    assertTrue(lostItemPolicy.canAgeLoanToLost(loanDueDate));
  }

  @Test
  @Parameters( {
    "Minutes",
    "Hours",
    "Days",
    "Weeks",
    "Months",
  })
  public void shouldAgeItemToLostIfDueDateIsNow(String interval) {
    final Random random = new Random();
    final int duration = random.nextInt(1000);
    final Period period = from(duration, interval);
    final LostItemPolicy lostItemPolicy = ageLostItemToLostPolicy(period);

    final DateTime loanDueDate = now(UTC).minus(period.timePeriod());

    assertTrue(lostItemPolicy.canAgeLoanToLost(loanDueDate));
  }

  public void shouldAgeItemToLostIfPeriodIsMissingInPolicy() {
    final LostItemPolicy lostItemPolicy = ageLostItemToLostPolicy(null);

    assertTrue(lostItemPolicy.canAgeLoanToLost(now(UTC)));
  }

  public LostItemPolicy ageLostItemToLostPolicy(Period period) {
    final JsonObject representation = new JsonObject();
    if (period != null) {
      representation.put("itemAgedLostOverdue", period.asJson());
    }

    return LostItemPolicy.from(representation);
  }
}
