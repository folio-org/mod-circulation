package org.folio.circulation.domain.notice.schedule;

import static java.lang.String.format;
import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.Result.succeeded;

import org.folio.circulation.domain.notice.NoticeFormat;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class JsonScheduledNoticeMapper {

  private static final String ID = "id";
  private static final String LOAN_ID = "loanId";
  private static final String REQUEST_ID = "requestId";
  private static final String NEXT_RUN_TIME = "nextRunTime";
  private static final String NOTICE_CONFIG = "noticeConfig";
  private static final String TIMING = "timing";
  private static final String RECURRING_PERIOD = "recurringPeriod";
  private static final String TEMPLATE_ID = "templateId";
  private static final String FORMAT = "format";
  private static final String SEND_IN_REAL_TIME = "sendInRealTime";

  private JsonScheduledNoticeMapper() {
  }

  public static Result<ScheduledNotice> mapFromJson(JsonObject jsonObject) {
    return succeeded(new ScheduledNoticeBuilder())
      .map(b -> b.setId(getProperty(jsonObject, ID)))
      .map(b -> b.setLoanId(getProperty(jsonObject, LOAN_ID)))
      .map(b -> b.setRequestId(getProperty(jsonObject, REQUEST_ID)))
      .map(b -> b.setNextRunTime(getDateTimeProperty(jsonObject, NEXT_RUN_TIME)))
      .combine(mapJsonToConfig(jsonObject.getJsonObject(NOTICE_CONFIG)),
        ScheduledNoticeBuilder::setNoticeConfig)
      .map(ScheduledNoticeBuilder::build);
  }

  private static Result<ScheduledNoticeConfig> mapJsonToConfig(JsonObject jsonObject) {
    return succeeded(new ScheduledNoticeConfigBuilder())
      .map(b -> b.setTemplateId(getProperty(jsonObject, TEMPLATE_ID)))
      .map(b -> b.setTiming(NoticeTiming.from(getProperty(jsonObject, TIMING))))
      .map(b -> b.setFormat(NoticeFormat.from(getProperty(jsonObject, FORMAT))))
      .combine(getPeriod(getObjectProperty(jsonObject, RECURRING_PERIOD)),
        ScheduledNoticeConfigBuilder::setRecurringPeriod)
      .map(b -> b.setSendInRealTime(getBooleanProperty(jsonObject, SEND_IN_REAL_TIME)))
      .map(ScheduledNoticeConfigBuilder::build);
  }

  private static Result<Period> getPeriod(JsonObject jsonObject) {
    if (jsonObject == null) {
      return succeeded(null);
    }
    return Period.from(jsonObject,
      () -> getParsingFailure("the loan period is not recognised"),
      interval -> getParsingFailure(format("the interval \"%s\" is not recognised", interval)),
      duration -> getParsingFailure(format("the duration \"%s\"  is invalid", duration)));
  }

  private static HttpFailure getParsingFailure(String message) {
    return new ServerErrorFailure("Unable to parse scheduled notice: " + message);
  }

  public static JsonObject mapToJson(ScheduledNotice notice) {
    return new JsonObject()
      .put(ID, notice.getId())
      .put(LOAN_ID, notice.getLoanId())
      .put(REQUEST_ID, notice.getRequestId())
      .put(NEXT_RUN_TIME, notice.getNextRunTime().withZone(DateTimeZone.UTC).toString())
      .put(NOTICE_CONFIG, mapConfigToJson(notice.getNoticeConfig()));
  }

  private static JsonObject mapConfigToJson(ScheduledNoticeConfig config) {
    return new JsonObject()
      .put(TIMING, config.getTiming().getRepresentation())
      .put(RECURRING_PERIOD,
        config.isRecurring() ? config.getRecurringPeriod().asJson() : null)
      .put(TEMPLATE_ID, config.getTemplateId())
      .put(FORMAT, config.getFormat().getRepresentation())
      .put(SEND_IN_REAL_TIME, config.sendInRealTime());
  }

}
