package api.support.matchers;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.withJsonPath;
import static org.hamcrest.Matchers.is;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.folio.circulation.domain.policy.Period;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.joda.time.base.BaseDuration;

import com.jayway.jsonpath.Predicate;

import io.vertx.core.json.JsonObject;

public class ScheduledNoticeMatchers {

  private static final String LOAN_ID = "loanId";
  private static final String NEXT_RUN_TIME = "nextRunTime";
  private static final String TIMING = "noticeConfig.timing";
  private static final String RECURRING_PERIOD = "noticeConfig.recurringPeriod";
  private static final String TEMPLATE_ID = "noticeConfig.templateId";
  private static final String FORMAT = "noticeConfig.format";
  private static final String SEND_IN_REAL_TIME = "noticeConfig.sendInRealTime";

  public static Matcher<JsonObject> hasScheduledLoanNoticeProperties(
    UUID expectedLoanId, DateTime expectedNextRunTime, String expectedTiming,
    UUID expectedTemplateId, Period expectedRecurringPeriod,
    boolean expectedSendInRealTime) {

    return hasScheduledLoanNoticeProperties(
      expectedLoanId, expectedNextRunTime, expectedTiming,
      expectedTemplateId, expectedRecurringPeriod,
      expectedSendInRealTime, "Email");
  }

  public static Matcher<JsonObject> hasScheduledLoanNoticeProperties(
    UUID expectedLoanId, DateTime expectedNextRunTime, String expectedTiming,
    UUID expectedTemplateId, Period expectedRecurringPeriod,
    boolean expectedSendInRealTime, String expectedFormat) {

    Long expectedRecurringPeriodInMillis =
      Optional.ofNullable(expectedRecurringPeriod)
        .map(Period::toTimePeriod)
        .map(org.joda.time.Period::toStandardDuration)
        .map(BaseDuration::getMillis)
        .orElse(null);

    return JsonObjectMatcher.allOfPaths(
      withJsonPath(LOAN_ID, UUIDMatcher.is(expectedLoanId)),
      withJsonPath(NEXT_RUN_TIME, is(expectedNextRunTime.getMillis())),
      withJsonPath(TIMING, is(expectedTiming)),
      withJsonPath(TEMPLATE_ID, UUIDMatcher.is(expectedTemplateId)),
      withJsonPath(RECURRING_PERIOD, pathEqualsTo(expectedRecurringPeriodInMillis)),
      withJsonPath(SEND_IN_REAL_TIME, is(expectedSendInRealTime)),
      withJsonPath(FORMAT, is(expectedFormat))
    );
  }

  private static Predicate pathEqualsTo(Object expectedValue) {
    return predicateContext -> Objects.equals(predicateContext.item(), expectedValue);
  }
}
