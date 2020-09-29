package org.folio.circulation.domain.notice;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.User;

import io.vertx.core.json.JsonObject;

public class PatronNoticeEventBuilder {

  private Item item;
  private User user;
  private NoticeEventType eventType;
  private JsonObject noticeContext;

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

  public PatronNoticeEvent build() {
    return new PatronNoticeEvent(item, user, eventType, noticeContext);
  }
}
