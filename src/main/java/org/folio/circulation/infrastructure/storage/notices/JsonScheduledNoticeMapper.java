package org.folio.circulation.infrastructure.storage.notices;

import static java.lang.String.format;
import static org.folio.circulation.domain.notice.schedule.TriggeringEvent.from;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getObjectProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;

import java.lang.invoke.MethodHandles;
import java.time.ZoneOffset;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.notice.NoticeFormat;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticeBuilder;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticeConfig;
import org.folio.circulation.domain.notice.schedule.ScheduledNoticeConfigBuilder;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class JsonScheduledNoticeMapper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final String ID = "id";
  private static final String SESSION_ID = "sessionId";
  public static final String LOAN_ID = "loanId";
  private static final String REQUEST_ID = "requestId";
  private static final String FEE_FINE_ACTION_ID = "feeFineActionId";
  private static final String RECIPIENT_USER_ID = "recipientUserId";
  private static final String NEXT_RUN_TIME = "nextRunTime";
  public static final String TRIGGERING_EVENT = "triggeringEvent";
  public static final String NOTICE_CONFIG = "noticeConfig";
  public static final String TIMING = "timing";
  private static final String RECURRING_PERIOD = "recurringPeriod";
  private static final String TEMPLATE_ID = "templateId";
  private static final String FORMAT = "format";
  private static final String SEND_IN_REAL_TIME = "sendInRealTime";

  private JsonScheduledNoticeMapper() { }

  public static Result<ScheduledNotice> mapFromJson(JsonObject jsonObject) {
    return succeeded(new ScheduledNoticeBuilder())
      .map(b -> b.setId(getProperty(jsonObject, ID)))
      .map(b -> b.setSessionId(getProperty(jsonObject, SESSION_ID)))
      .map(b -> b.setLoanId(getProperty(jsonObject, LOAN_ID)))
      .map(b -> b.setRequestId(getProperty(jsonObject, REQUEST_ID)))
      .map(b -> b.setFeeFineActionId(getProperty(jsonObject, FEE_FINE_ACTION_ID)))
      .map(b -> b.setRecipientUserId(getProperty(jsonObject, RECIPIENT_USER_ID)))
      .map(b -> b.setNextRunTime(getDateTimeProperty(jsonObject, NEXT_RUN_TIME)))
      .map(b -> b.setTriggeringEvent(from(getProperty(jsonObject, TRIGGERING_EVENT))))
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
    log.debug("getPeriod:: processing period from json");
    if (jsonObject == null) {
      log.info("getPeriod:: jsonObject is null, returning null period");
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
    log.debug("mapToJson:: parameters notice id: {}", notice != null ? notice.getId() : "null");
    JsonObject result = new JsonObject()
      .put(ID, notice.getId())
      .put(SESSION_ID, notice.getSessionId())
      .put(LOAN_ID, notice.getLoanId())
      .put(REQUEST_ID, notice.getRequestId())
      .put(FEE_FINE_ACTION_ID, notice.getFeeFineActionId())
      .put(RECIPIENT_USER_ID, notice.getRecipientUserId())
      .put(TRIGGERING_EVENT, notice.getTriggeringEvent().getRepresentation())
      .put(NEXT_RUN_TIME, formatDateTime(notice.getNextRunTime().withZoneSameInstant(ZoneOffset.UTC)))
      .put(NOTICE_CONFIG, mapConfigToJson(notice.getConfiguration()));
    log.info("mapToJson:: result: scheduled notice mapped to json");
    return result;
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
