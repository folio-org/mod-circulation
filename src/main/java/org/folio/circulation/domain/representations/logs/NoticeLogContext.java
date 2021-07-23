package org.folio.circulation.domain.representations.logs;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ACCOUNT_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.DATE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ERROR_MESSAGE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ITEMS;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.REQ_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.USER_BARCODE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.USER_ID;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@AllArgsConstructor
@NoArgsConstructor
@With
public class NoticeLogContext {
  private String userBarcode;
  private String userId;
  @Getter private List<NoticeLogContextItem> items = new ArrayList<>();
  private DateTime date;
  private String requestId;
  private String accountId;
  private String errorMessage;

  public static NoticeLogContext from(Loan loan) {
    return new NoticeLogContext()
      .withUser(loan.getUser())
      .withItems(singletonList(NoticeLogContextItem.from(loan)));
  }

  public static NoticeLogContext from(Request request) {
    return new NoticeLogContext()
      .withUserId(request.getUserId())
      .withUser(request.getRequester())
      .withItems(singletonList(NoticeLogContextItem.from(request)))
      .withRequestId(request.getId());
  }

  public static NoticeLogContext from(ScheduledNotice scheduledNotice) {
    return new NoticeLogContext()
      .withUserId(scheduledNotice.getRecipientUserId())
      .withRequestId(scheduledNotice.getRequestId())
      .withItems(singletonList(
        new NoticeLogContextItem()
          .withLoanId(scheduledNotice.getLoanId())
          .withTriggeringEvent(scheduledNotice.getTriggeringEvent().getRepresentation())
          .withTemplateId(scheduledNotice.getConfiguration().getTemplateId())
      ));
  }

  public NoticeLogContext withUser(User user) {
    if (user != null) {
      userBarcode = user.getBarcode();
      userId = user.getId();
    }
    return this;
  }

  public void setNoticePolicyId(String noticePolicyId) {
    items = items.stream()
      .map(item -> item.withNoticePolicyId(noticePolicyId))
      .collect(Collectors.toList());
  }

  public void setTemplateId(String templateId) {
    items = items.stream()
      .map(item -> item.withTemplateId(templateId))
      .collect(Collectors.toList());
  }

  public void setTriggeringEvent(String triggeringEvent) {
    items = items.stream()
      .map(item -> item.withTriggeringEvent(triggeringEvent))
      .collect(Collectors.toList());
  }

  public JsonObject asJson() {
    JsonObject json = new JsonObject();
    write(json, USER_ID.value(), userId);
    write(json, USER_BARCODE.value(), userBarcode);
    JsonArray itemsArray = items.stream()
      .map(NoticeLogContextItem::asJson)
      .collect(collectingAndThen(toList(), JsonArray::new));
    write(json, ITEMS.value(), itemsArray);
    write(json, DATE.value(), date);
    write(json, REQ_ID.value(), requestId);
    write(json, ACCOUNT_ID.value(), accountId);
    write(json, ERROR_MESSAGE.value(), errorMessage);
    return json;
  }
}
