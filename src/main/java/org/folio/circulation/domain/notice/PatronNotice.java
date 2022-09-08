package org.folio.circulation.domain.notice;

import static org.folio.circulation.domain.notice.NoticeFormat.EMAIL;

import java.util.UUID;

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

  public static PatronNotice buildEmail(String recipientId, UUID templateId, JsonObject context) {
    return buildEmail(recipientId, templateId.toString(), context);
  }

  public static PatronNotice buildEmail(String recipientId, String templateId, JsonObject context) {
    return new PatronNotice(recipientId, context, templateId, EMAIL);
  }

  @Override
  public String toString() {
    return "PatronNotice{" +
      "recipientId='" + recipientId + '\'' +
      ", templateId='" + templateId + '\'' +
      '}';
  }
}
