package org.folio.circulation.domain.notice;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.User;

import io.vertx.core.json.JsonObject;

public class PatronNoticeEvent {

  private final Item item;
  private final User user;
  private final NoticeEventType eventType;
  private final JsonObject noticeContext;

  public PatronNoticeEvent(
    Item item, User user, NoticeEventType eventType,
    JsonObject noticeContext) {
    this.item = item;
    this.user = user;
    this.eventType = eventType;
    this.noticeContext = noticeContext;
  }

  public Item getItem() {
    return item;
  }

  public User getUser() {
    return user;
  }

  public NoticeEventType getEventType() {
    return eventType;
  }

  public JsonObject getNoticeContext() {
    return noticeContext;
  }
}
