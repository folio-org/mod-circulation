package org.folio.circulation.domain.notice;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;

import io.vertx.core.json.JsonObject;

public class PatronNoticeEventBuilder {

  private Item item;
  private User user;
  private NoticeEventType eventType;
  private JsonObject noticeContext;
  private NoticeLogContext noticeLogContext;
  private String patronNoticePolicyId;
  private String recipientId;

  public PatronNoticeEventBuilder withItem(Item item) {
    this.item = item;
    return this;
  }

  public PatronNoticeEventBuilder withUser(User user) {
    this.user = user;
    return this;
  }

  public PatronNoticeEventBuilder withEventType(NoticeEventType eventType) {
    this.eventType = eventType;
    return this;
  }

  public PatronNoticeEventBuilder withNoticeContext(JsonObject noticeContext) {
    this.noticeContext = noticeContext;
    return this;
  }

  public PatronNoticeEventBuilder withNoticeLogContext(NoticeLogContext noticeLogContext) {
    this.noticeLogContext = noticeLogContext;
    return this;
  }

  public PatronNoticeEventBuilder withPatronNoticePolicyId(String patronNoticePolicyId) {
    this.patronNoticePolicyId = patronNoticePolicyId;
    return this;
  }

  public PatronNoticeEventBuilder withRecipientId(String recipientId) {
    this.recipientId = recipientId;
    return this;
  }

  public PatronNoticeEvent build() {
    return new PatronNoticeEvent(item, user, eventType, noticeContext, noticeLogContext, patronNoticePolicyId, recipientId);
  }
}
