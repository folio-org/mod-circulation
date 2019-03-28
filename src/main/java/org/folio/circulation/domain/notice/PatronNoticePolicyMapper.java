package org.folio.circulation.domain.notice;

import static org.folio.circulation.support.JsonPropertyFetcher.getNestedObjectProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.Result.succeeded;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.joda.time.Period;

import io.vertx.core.json.JsonObject;

public class PatronNoticePolicyMapper implements Function<JsonObject, Result<PatronNoticePolicy>> {

  private static final String LOAN_NOTICES = "loanNotices";
  private static final String REQUEST_NOTICES = "requestNotices";

  private static final String TEMPLATE_ID = "templateId";
  private static final String FORMAT = "format";
  private static final String SEND_OPTIONS = "sendOptions";
  private static final String SEND_WHEN = "sendWhen";
  private static final String SEND_HOW = "sendHow";
  private static final String SEND_BY = "sendBy";
  private static final String FREQUENCY = "frequency";
  private static final String SEND_EVERY = "sendEvery";

  private static final String DURATION = "duration";
  private static final String INTERVAL_ID = "intervalId";

  private static final String RECURRING_FREQUENCY = "Recurring";

  @Override
  public Result<PatronNoticePolicy> apply(JsonObject representation) {
    try {
      List<NoticeConfiguration> loanNoticeConfigurations =
        JsonArrayHelper.mapToList(representation, LOAN_NOTICES, this::toNoticeConfiguration);
      List<NoticeConfiguration> requestNoticeConfigurations =
        JsonArrayHelper.mapToList(representation, REQUEST_NOTICES, this::toNoticeConfiguration);

      List<NoticeConfiguration> allNoticeConfigurations = new ArrayList<>(loanNoticeConfigurations);
      allNoticeConfigurations.addAll(requestNoticeConfigurations);

      return succeeded(new PatronNoticePolicy(allNoticeConfigurations));
    } catch (IllegalArgumentException ex) {
      return failed(new ServerErrorFailure("Unable to parse patron notice policy:" + ex.getMessage()));
    }
  }

  private NoticeConfiguration toNoticeConfiguration(JsonObject representation) {
    NoticeConfigurationBuilder builder = new NoticeConfigurationBuilder();

    String templateId = representation.getString(TEMPLATE_ID);
    if (templateId == null) {
      throw new IllegalArgumentException("templateId must not be null");
    }
    builder.setTemplateId(representation.getString(TEMPLATE_ID));

    NoticeFormat noticeFormat = NoticeFormat.from(representation.getString(FORMAT));
    if (noticeFormat == NoticeFormat.UNKNOWN) {
      throw new IllegalArgumentException("unexpected notice format");
    }
    builder.setNoticeFormat(noticeFormat);

    builder.setNoticeEventType(
      NoticeEventType.from(
        getNestedStringProperty(representation, SEND_OPTIONS, SEND_WHEN)));

    NoticeTiming noticeTiming = NoticeTiming.from(
      getNestedStringProperty(representation, SEND_OPTIONS, SEND_HOW));
    builder.setTiming(noticeTiming);

    if (noticeTiming.requiresPeriod()) {
      builder.setTimingPeriod(
        getNestedPeriodProperty(representation, SEND_OPTIONS, SEND_BY));
    }

    boolean recurring =
      Objects.equals(representation.getString(FREQUENCY), RECURRING_FREQUENCY);
    builder.setRecurring(recurring);
    if (recurring) {
      builder.setRecurringPeriod(
        getNestedPeriodProperty(representation, SEND_OPTIONS, SEND_EVERY));
    }

    return builder.build();
  }

  private Period getNestedPeriodProperty(
    JsonObject representation,
    String objectName,
    String propertyName) {
    return mapToPeriod(getNestedObjectProperty(representation, objectName, propertyName));
  }

  private Period mapToPeriod(JsonObject jsonObject) {
    if (jsonObject == null) {
      return Period.millis(0);
    }
    LoanPolicyPeriod intervalId =
      LoanPolicyPeriod.getProfileByName(jsonObject.getString(INTERVAL_ID));
    Integer duration = jsonObject.getInteger(DURATION, 0);
    return LoanPolicyPeriod.calculatePeriod(intervalId, duration);
  }
}
