package org.folio.circulation.domain.notice;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.User;

import io.vertx.core.json.JsonObject;

public class PatronNoticeEvent {

  private final Item item;
  private final User user;
  private final NoticeEventType eventType;
  private final NoticeTiming timing;
  private final JsonObject noticeContext;

  public PatronNoticeEvent(
    Item item, User user, NoticeEventType eventType,
    NoticeTiming timing, JsonObject noticeContext) {
    this.item = item;
    this.user = user;
    this.eventType = eventType;
    this.timing = timing;
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

  public NoticeTiming getTiming() {
    return timing;
  }

  public JsonObject getNoticeContext() {
    return noticeContext;
  }
}
