package org.folio.circulation.domain.representations.logs;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.DATE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.FEE_FINE_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ITEMS;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.REQ_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.USER_BARCODE;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.With;
import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Collections;
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
  private String feeFineId;

  public static NoticeLogContext from(Loan loan) {
    return new NoticeLogContext()
      .withUser(loan.getUser())
      .withItems(Collections.singletonList(NoticeLogContextItem.from(loan)));
  }

  public static NoticeLogContext from(Request request) {
    return new NoticeLogContext()
      .withUser(request.getRequester())
      .withItems(Collections.singletonList(NoticeLogContextItem.from(request)));
  }

  public static NoticeLogContext from(Item item, User user, Request request) {
    return new NoticeLogContext()
      .withUser(user)
      .withItems(Collections.singletonList(NoticeLogContextItem.from(item)
        .withServicePointId(request.getPickupServicePoint().getId())))
      .withRequestId(request.getId());
  }

  public NoticeLogContext withUser(User user) {
    userBarcode = user.getBarcode();
    userId = user.getId();
    return this;
  }

  public NoticeLogContext withFeeFineAction(FeeFineAction action) {
    feeFineId = action.getId();
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
    write(json, USER_BARCODE.value(), userBarcode);
    JsonArray itemsArray = items.stream()
      .map(NoticeLogContextItem::asJson)
      .collect(collectingAndThen(toList(), JsonArray::new));
    write(json, ITEMS.value(), itemsArray);
    write(json, DATE.value(), date);
    ofNullable(requestId).ifPresent(s ->
      write(json, REQ_ID.value(), requestId));
    ofNullable(feeFineId).ifPresent(s ->
      write(json, FEE_FINE_ID.value(), feeFineId));
    return json;
  }
}
