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
  @Parameters(method = "allowedPeriods")
  public void shouldNotAgeItemToLostIfDueDateAfterNow(String interval) {
    final Random random = new Random();
    final int duration = random.nextInt(1000);
    final Period period = from(duration, interval);
    final LostItemPolicy lostItemPolicy = lostItemPolicyWithAgePeriod(period);

    final DateTime loanDueDate = now(UTC).plus(period.timePeriod())
      .plus(minutes(random.nextInt(3)).timePeriod());

    assertFalse(lostItemPolicy.canAgeLoanToLost(loanDueDate));
  }

  @Test
  @Parameters(method = "allowedPeriods")
  public void shouldAgeItemToLostIfDueDateBeforeNow(String interval) {
    final Random random = new Random();
    final int duration = random.nextInt(1000);
    final Period period = from(duration, interval);
    final LostItemPolicy lostItemPolicy = lostItemPolicyWithAgePeriod(period);

    final DateTime loanDueDate = now(UTC).minus(period.timePeriod())
      .minus(minutes(random.nextInt(3)).timePeriod());

    assertTrue(lostItemPolicy.canAgeLoanToLost(loanDueDate));
  }

  @Test
  @Parameters(method = "allowedPeriods")
  public void shouldAgeItemToLostIfDueDateIsNow(String interval) {
    final Random random = new Random();
    final int duration = random.nextInt(1000);
    final Period period = from(duration, interval);
    final LostItemPolicy lostItemPolicy = lostItemPolicyWithAgePeriod(period);

    final DateTime loanDueDate = now(UTC).minus(period.timePeriod());

    assertTrue(lostItemPolicy.canAgeLoanToLost(loanDueDate));
  }

  @Test
  public void shouldNotAgeItemToLostIfPeriodIsMissingInPolicy() {
    final LostItemPolicy lostItemPolicy = lostItemPolicyWithAgePeriod(null);

    assertFalse(lostItemPolicy.canAgeLoanToLost(now(UTC)));
  }

  private LostItemPolicy lostItemPolicyWithAgePeriod(Period period) {
    final JsonObject representation = new JsonObject();
    if (period != null) {
      representation.put("itemAgedLostOverdue", period.asJson());
    }

    return LostItemPolicy.from(representation);
  }

  // Used as parameter source
  @SuppressWarnings("unused")
  private String[] allowedPeriods() {
    return new String[] {
      "Minutes",
      "Hours",
      "Days",
      "Weeks",
      "Months",
    };
  }
}
