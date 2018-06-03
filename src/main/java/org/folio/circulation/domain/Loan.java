package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.*;
import org.joda.time.DateTime;

import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;

public class Loan implements ItemRelatedRecord, UserRelatedRecord {
  private static final String STATUS_PROPERTY_NAME = "status";

  private final JsonObject representation;
  private Item item;
  private User user;

  public Loan(JsonObject representation) {
    this.representation = representation;
  }

  public static Loan from(JsonObject representation) {
    return new Loan(representation);
  }

  public static Loan from(JsonObject representation, Item item) {
    return from(representation, item, null);
  }

  public static Loan from(JsonObject representation, Item item, User user) {
    final Loan loan = new Loan(representation);

    loan.setItem(item);
    loan.setUser(user);

    return loan;
  }

  JsonObject asJson() {
    return representation.copy();
  }

  public void changeDueDate(DateTime dueDate) {
    JsonPropertyWriter.write(representation, "dueDate", dueDate);
  }

  private void changeAction(String action) {
    representation.put("action", action);
  }

  public void changeProxyUser(String userId) {
    representation.put("proxyUserId", userId);
  }

  public HttpResult<Void> isValidStatus() {
    if(!representation.containsKey(STATUS_PROPERTY_NAME)) {
      return HttpResult.failure(new ServerErrorFailure(
        "Loan does not have a status"));
    }

    switch(getStatus()) {
      case "Open":
      case "Closed":
        return HttpResult.success(null);

      default:
        return HttpResult.failure(new ValidationErrorFailure(
          "Loan status must be \"Open\" or \"Closed\"", STATUS_PROPERTY_NAME, getStatus()));
    }
  }

  boolean isClosed() {
    return StringUtils.equals(getStatus(), "Closed");
  }

  private String getStatus() {
    return getNestedStringProperty(representation, STATUS_PROPERTY_NAME, "name");
  }

  @Override
  public String getItemId() {
    return representation.getString("itemId");
  }

  public DateTime getLoanDate() {
    return DateTime.parse(representation.getString("loanDate"));
  }

  @Override
  public String getUserId() {
    return representation.getString("userId");
  }

  @Override
  public String getProxyUserId() {
    return representation.getString("proxyUserId");
  }

  public Item getItem() {
    return item;
  }

  private void setItem(Item newItem) {
    //TODO: Refuse if ID does not match property in representation
    if(newItem != null) {
      representation.put("itemId", newItem.getItemId());
    }
    this.item = newItem;
  }

  public User getUser() {
    return user;
  }

  private void setUser(User newUser) {
    //TODO: Refuse if ID does not match property in representation
    if(newUser != null) {
      representation.put("userId", newUser.getString("id"));
    }
    this.user = newUser;
  }

  void renew() {
    changeAction("renewed");
    changeDueDate(DateTime.now().plusWeeks(3));
  }
}
