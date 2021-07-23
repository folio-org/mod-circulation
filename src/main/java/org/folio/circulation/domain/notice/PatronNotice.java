package org.folio.circulation.domain.notice;

import org.folio.circulation.domain.notice.schedule.ScheduledNoticeConfig;

import io.vertx.core.json.JsonObject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class PatronNotice {
  private final String recipientId;
  private final String templateId;
  private final String deliveryChannel;
  private final String outputFormat;
  private final JsonObject context;

  public PatronNotice(String recipientId, JsonObject context, ScheduledNoticeConfig config) {
    this(recipientId, context, config.getTemplateId(), config.getFormat());
  }

  public PatronNotice(String recipientId, JsonObject context, NoticeConfiguration config) {
    this(recipientId, context, config.getTemplateId(), config.getNoticeFormat());
  }

  private PatronNotice(String recipientId, JsonObject context, String templateId, NoticeFormat format) {
    this(recipientId, templateId, format.getDeliveryChannel(), format.getOutputFormat(), context);
  }

  @Override
  public String toString() {
    return "PatronNotice{" +
      "recipientId='" + recipientId + '\'' +
      ", templateId='" + templateId + '\'' +
      '}';
  }
}
