package org.folio.circulation.domain.notice;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

@AllArgsConstructor
@Getter
@With
public class PatronNoticeEvent {
  private final Item item;
  private final User user;
  private final NoticeEventType eventType;
  private final JsonObject noticeContext;
  private final NoticeLogContext noticeLogContext;
  private final String patronNoticePolicyId;
  private final String recipientId;
}
