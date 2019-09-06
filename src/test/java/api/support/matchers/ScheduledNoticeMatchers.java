package api.support.matchers;

import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;

import java.util.UUID;

import org.folio.circulation.domain.policy.Period;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class ScheduledNoticeMatchers {

  private static final String LOAN_ID = "loanId";
  private static final String NEXT_RUN_TIME = "nextRunTime";
  private static final String TIMING = "noticeConfig.timing";
  private static final String RECURRING_PERIOD_INTERVAL = "noticeConfig.recurringPeriod";
  private static final String INTERVAL_ID = "intervalId";
  private static final String DURATION = "duration";
  private static final String TEMPLATE_ID = "noticeConfig.templateId";
  private static final String FORMAT = "noticeConfig.format";
  private static final String SEND_IN_REAL_TIME = "noticeConfig.sendInRealTime";

  public static Matcher<JsonObject> hasScheduledLoanNotice(
    UUID expectedLoanId, DateTime expectedNextRunTime, String expectedTiming,
    UUID expectedTemplateId, Period expectedRecurringPeriod,
    boolean expectedSendInRealTime) {

    return hasScheduledLoanNotice(
      expectedLoanId, expectedNextRunTime, expectedTiming,
      expectedTemplateId, expectedRecurringPeriod,
      expectedSendInRealTime, "Email");
  }

  public static Matcher<JsonObject> hasScheduledLoanNotice(
    UUID expectedLoanId, DateTime expectedNextRunTime, String expectedTiming,
    UUID expectedTemplateId, Period expectedRecurringPeriod,
    boolean expectedSendInRealTime, String expectedFormat) {

    return JsonObjectMatcher.allOfPaths(
      hasJsonPath(LOAN_ID, UUIDMatcher.is(expectedLoanId)),
      hasJsonPath(NEXT_RUN_TIME, isEquivalentTo(expectedNextRunTime)),
      hasJsonPath(TIMING, is(expectedTiming)),
      hasJsonPath(TEMPLATE_ID, UUIDMatcher.is(expectedTemplateId)),
      hasJsonPath(RECURRING_PERIOD_INTERVAL, isPeriod(expectedRecurringPeriod)),
      hasJsonPath(SEND_IN_REAL_TIME, is(expectedSendInRealTime)),
      hasJsonPath(FORMAT, is(expectedFormat))
    );
  }

  private static Matcher<Object> isPeriod(Period period) {
    if (period == null) {
      return Matchers.nullValue();
    }
    return allOf(
      hasJsonPath(INTERVAL_ID, is(period.asJson().getString(INTERVAL_ID))),
      hasJsonPath(DURATION, is(period.asJson().getInteger(DURATION)))
    );
  }
}
