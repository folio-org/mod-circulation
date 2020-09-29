package org.folio.circulation.domain.representations.logs;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.DATE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.FEE_FINE_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ITEMS;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.NOTICE_POLICY_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.REQUEST_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.TEMPLATE_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.TRIGGERING_EVENT;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.USER_BARCODE;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
import java.util.Objects;

@AllArgsConstructor
@NoArgsConstructor
@With
@Setter
public class NoticeLogContext {
  private String userBarcode;
  private String userId;
  @Getter private List<NoticeLogContextItem> items = new ArrayList<>();
  private DateTime date;
  private String servicePointId;
  private String templateId;
  private String triggeringEvent;
  private String noticePolicyId;
  private String requestId;
  private String feeFineId;

  public static NoticeLogContext from(Loan loan) {
    return new NoticeLogContext()
      .withUser(loan.getUser())
      .withItems(Collections.singletonList(NoticeLogContextItem.from(loan)))
      .withServicePointId(loan.getCheckoutServicePointId());
  }

  public static NoticeLogContext from(Request request) {
    NoticeLogContext context = new NoticeLogContext()
      .withUser(request.getRequester())
      .withItems(Collections.singletonList(NoticeLogContextItem.from(request)));
    if (Objects.nonNull(request.getPickupServicePoint())) {
      context.setServicePointId(request.getPickupServicePoint().getId());
    }
    return context;
  }

  public static NoticeLogContext from(Item item, User user, Request request) {
    return new NoticeLogContext()
      .withUser(user)
      .withItems(Collections.singletonList(NoticeLogContextItem.from(item)))
      .withRequestId(request.getId())
      .withServicePointId(request.getPickupServicePoint().getId());
  }

  public static NoticeLogContext from(Loan loan, FeeFineAction action) {
    return NoticeLogContext.from(loan)
      .withFeeFineId(action.getId());
  }

  public NoticeLogContext withUser(User user) {
    userBarcode = user.getBarcode();
    userId = user.getId();
    return this;
  }

  public JsonObject asJson() {
    JsonObject json = new JsonObject();
    write(json, USER_BARCODE.value(), userBarcode);
    JsonArray itemsArray = items.stream()
      .map(NoticeLogContextItem::asJson)
      .collect(collectingAndThen(toList(), JsonArray::new));
    write(json, ITEMS.value(), itemsArray);
    write(json, DATE.value(), date);
    ofNullable(servicePointId).ifPresent(s ->
      write(json, SERVICE_POINT_ID.value(), servicePointId));
    write(json, TEMPLATE_ID.value(), templateId);
    write(json, TRIGGERING_EVENT.value(), triggeringEvent);
    write(json, NOTICE_POLICY_ID.value(), noticePolicyId);
    ofNullable(requestId).ifPresent(s ->
      write(json, REQUEST_ID.value(), requestId));
    ofNullable(feeFineId).ifPresent(s ->
      write(json, FEE_FINE_ID.value(), feeFineId));
    return json;
  }
}
