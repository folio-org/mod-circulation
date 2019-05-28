package org.folio.circulation.domain.notice.schedule;

import static org.folio.circulation.support.JsonPropertyFetcher.getBooleanProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getLongProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.notice.NoticeFormat;
import org.folio.circulation.domain.notice.NoticeTiming;

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

  public static ScheduledNotice mapFromJson(JsonObject jsonObject) {
    return new ScheduledNoticeBuilder()
      .setId(getProperty(jsonObject, ID))
      .setLoanId(getProperty(jsonObject, LOAN_ID))
      .setRequestId(getProperty(jsonObject, REQUEST_ID))
      .setNextRunTime(getLongProperty(jsonObject, NEXT_RUN_TIME))
      .setNoticeConfig(mapJsonToConfig(jsonObject.getJsonObject(NOTICE_CONFIG)))
      .build();
  }

  private static ScheduledNoticeConfig mapJsonToConfig(JsonObject jsonObject) {
    return new ScheduledNoticeConfigBuilder()
      .setTemplateId(getProperty(jsonObject, TEMPLATE_ID))
      .setTiming(NoticeTiming.from(getProperty(jsonObject, TIMING)))
      .setFormat(NoticeFormat.from(getProperty(jsonObject, FORMAT)))
      .setRecurringPeriod(getLongProperty(jsonObject, RECURRING_PERIOD))
      .setSendInRealTime(getBooleanProperty(jsonObject, SEND_IN_REAL_TIME))
      .build();
  }

  public static JsonObject mapToJson(ScheduledNotice notice) {
    return new JsonObject()
      .put(ID, notice.getId())
      .put(LOAN_ID, notice.getLoanId())
      .put(REQUEST_ID, notice.getRequestId())
      .put(NEXT_RUN_TIME, notice.getNextRunTime())
      .put(NOTICE_CONFIG, mapConfigToJson(notice.getNoticeConfig()));
  }

  private static JsonObject mapConfigToJson(ScheduledNoticeConfig config) {
    return new JsonObject()
      .put(TIMING, config.getTiming().getRepresentation())
      .put(RECURRING_PERIOD,
        config.isRecurring() ? config.getRecurringPeriod() : null)
      .put(TEMPLATE_ID, config.getTemplateId())
      .put(FORMAT, config.getFormat().getRepresentation())
      .put(SEND_IN_REAL_TIME, config.sendInRealTime());
  }

}
