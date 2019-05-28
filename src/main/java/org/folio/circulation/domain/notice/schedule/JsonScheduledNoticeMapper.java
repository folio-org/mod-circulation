package org.folio.circulation.domain.notice.schedule;

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

  public static JsonObject mapToJson(ScheduledNotice notice) {
    ScheduledNoticeConfig noticeConfig = notice.getNoticeConfig();
    JsonObject noticeConfigRepresentation =
      new JsonObject()
        .put(TIMING, noticeConfig.getTiming().getRepresentation())
        .put(RECURRING_PERIOD,
          noticeConfig.isRecurring() ? noticeConfig.getRecurringPeriod() : null)
        .put(TEMPLATE_ID, noticeConfig.getTemplateId())
        .put(FORMAT, noticeConfig.getFormat().getRepresentation())
        .put(SEND_IN_REAL_TIME, noticeConfig.sendInRealTime());
    return new JsonObject()
      .put(ID, notice.getId())
      .put(LOAN_ID, notice.getLoanId())
      .put(REQUEST_ID, notice.getRequestId())
      .put(NEXT_RUN_TIME, notice.getNextRunTime())
      .put(NOTICE_CONFIG, noticeConfigRepresentation);
  }

}
