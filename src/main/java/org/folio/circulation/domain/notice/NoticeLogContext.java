package org.folio.circulation.domain.notice;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.User;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class NoticeLogContext {
  private String userBarcode;
  private String userId;
  private List<NoticeLogContextItem> items = new ArrayList<>();
  private DateTime date;
  private String servicePointId;
  private String templateId;
  private String triggeringEvent;
  private String noticePolicyId;

  public NoticeLogContext() {
  }

  public static NoticeLogContext from(Loan loan) {
    return new NoticeLogContext()
      .withUser(loan.getUser())
      .withItems(Collections.singletonList(NoticeLogContextItem.from(loan)));
  }

  public static NoticeLogContext from(Request request) {
    NoticeLogContext context = new NoticeLogContext()
      .withUser(request.getRequester())
      .withItems(Collections.singletonList(
        NoticeLogContextItem.from(request.getItem())
          .withRequestId(request.getId())));
    if (Objects.nonNull(request.getPickupServicePoint())) {
      context.setServicePointId(request.getPickupServicePoint().getId());
    }
    return context;
  }

  public static NoticeLogContext from(Item item, User user, Request request) {
    return new NoticeLogContext()
      .withUser(user)
      .withItems(Collections.singletonList(
        NoticeLogContextItem.from(item)
          .withRequestId(request.getId())))
      .withServicePointId(request.getPickupServicePoint().getId());
  }

  public static NoticeLogContext from(Loan loan, Account account) {
    NoticeLogContext noticeLogContext = NoticeLogContext.from(loan);
    noticeLogContext.getItems().stream()
      .findFirst()
      .ifPresent(noticeLogContextItem -> noticeLogContextItem.setFeeFineId(account.getFeeFineId()));
    return noticeLogContext;
  }

  public NoticeLogContext withUser(User user) {
    userBarcode = user.getBarcode();
    userId = user.getId();
    return this;
  }

  public NoticeLogContext withItems(List<NoticeLogContextItem> items) {
    this.items = items;
    return this;
  }

  public NoticeLogContext withServicePointId(String servicePointId) {
    this.servicePointId = servicePointId;
    return this;
  }

  public NoticeLogContext withTemplateId(String templateId) {
    this.templateId = templateId;
    return this;
  }

  public NoticeLogContext withTriggeringEvent(String triggeringEvent) {
    this.triggeringEvent = triggeringEvent;
    return this;
  }

  public NoticeLogContext withNoticePolicyId(String noticePolicyId) {
    this.noticePolicyId = noticePolicyId;
    return this;
  }

  public NoticeLogContext withDate(DateTime date) {
    this.date = date;
    return this;
  }

  public String getUserBarcode() {
    return userBarcode;
  }

  public String getUserId() {
    return userId;
  }

  public List<NoticeLogContextItem> getItems() {
    return items;
  }

  public void setItems(List<NoticeLogContextItem> items) {
    this.items = items;
  }

  public void setDate(DateTime date) {
    this.date = date;
  }

  public DateTime getDate() {
    return date;
  }

  public String getServicePointId() {
    return servicePointId;
  }

  public void setServicePointId(String servicePointId) {
    this.servicePointId = servicePointId;
  }

  public String getTemplateId() {
    return templateId;
  }

  public void setTemplateId(String templateId) {
    this.templateId = templateId;
  }

  public String getTriggeringEvent() {
    return triggeringEvent;
  }

  public void setTriggeringEvent(String triggeringEvent) {
    this.triggeringEvent = triggeringEvent;
  }

  public String getNoticePolicyId() {
    return noticePolicyId;
  }

  public void setNoticePolicyId(String noticePolicyId) {
    this.noticePolicyId = noticePolicyId;
  }

  public JsonObject asJson() {
    JsonObject json = new JsonObject();
    write(json, "userBarcode", userBarcode);
    write(json, "userId", userId);

    JsonArray itemsJson = new JsonArray();
    items.forEach(item -> itemsJson.add(item.asJson()));
    write(json, "items", itemsJson);

    write(json, "date", date);
    if (Objects.nonNull(servicePointId)) {
      write(json, "servicePointId", servicePointId);
    }
    write(json, "noticePolicyId", noticePolicyId);
    write(json, "triggeringEvent", triggeringEvent);
    write(json, "templateId", templateId);
    return json;
  }
}
