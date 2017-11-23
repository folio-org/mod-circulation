package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.ISODateTimeFormat;

import java.util.Random;
import java.util.UUID;

public class LoanRequestBuilder implements Builder {
  private final static String OPEN_LOAN_STATUS = "Open";
  private final static String CLOSED_LOAN_STATUS = "Closed";

  private final UUID id;
  private final UUID itemId;
  private final UUID userId;
  private final DateTime loanDate;
  private final String status;
  private DateTime returnDate;
  private final String action;
  private final DateTime dueDate;
  private final UUID proxyUserId;

  public LoanRequestBuilder() {
    this(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
      new DateTime(2017, 03, 06, 16, 04, 43), null, "Open", null, "checkedout",
      null);
  }

  private LoanRequestBuilder(
    UUID id,
    UUID itemId,
    UUID userId,
    DateTime loanDate,
    DateTime dueDate, String status,
    DateTime returnDate,
    String action,
    UUID proxyUserId) {

    this.id = id;
    this.itemId = itemId;
    this.userId = userId;
    this.proxyUserId = proxyUserId;
    this.loanDate = loanDate;
    this.status = status;
    this.returnDate = returnDate;
    this.action = action;
    this.dueDate = dueDate;
  }

  public JsonObject create() {

    JsonObject loanRequest = new JsonObject();

    if(id != null) {
      loanRequest.put("id", id.toString());
    }

    loanRequest
      .put("userId", userId.toString())
      .put("itemId", itemId.toString())
      .put("loanDate", loanDate.toString(ISODateTimeFormat.dateTime()))
      .put("status", new JsonObject().put("name", status));

    if(proxyUserId != null) {
      loanRequest.put("proxyUserId", proxyUserId.toString());
    }

    if(action != null) {
      loanRequest.put("action", action);
    }

    if(dueDate != null) {
      loanRequest.put("dueDate",
        dueDate.toString(ISODateTimeFormat.dateTime()));
    }

    if(status == CLOSED_LOAN_STATUS) {
      loanRequest.put("returnDate",
        returnDate.toString(ISODateTimeFormat.dateTime()));
    }

    return loanRequest;
  }

  public LoanRequestBuilder withRandomPastLoanDate() {
    Random random = new Random();

    return withLoanDate(DateTime.now().minusDays(random.nextInt(10)));
  }

  public LoanRequestBuilder withLoanDate(DateTime loanDate) {
    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      loanDate, this.dueDate, this.status, this.returnDate, this.action,
      this.proxyUserId);
  }

  public LoanRequestBuilder withUserId(UUID userId) {
    return new LoanRequestBuilder(this.id, this.itemId, userId,
      this.loanDate, this.dueDate, this.status, this.returnDate, this.action,
      this.proxyUserId);
  }

  public LoanRequestBuilder withStatus(String status) {

    DateTime defaultedReturnDate = this.returnDate != null
      ? this.returnDate
      : this.loanDate.plusDays(1).plusHours(4);

    String action = null;

    switch(status) {
      case OPEN_LOAN_STATUS:
        action = "checkedout";
        break;
      case CLOSED_LOAN_STATUS:
        action = "checkedin";
        break;
    }

    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      this.loanDate, this.dueDate, status, defaultedReturnDate, action,
      this.proxyUserId);
  }

  public LoanRequestBuilder open() {
    return withStatus(OPEN_LOAN_STATUS);
  }

  public LoanRequestBuilder closed() {
    return withStatus(CLOSED_LOAN_STATUS);
  }

  public LoanRequestBuilder withId(UUID id) {
    return new LoanRequestBuilder(id, this.itemId, this.userId,
      this.loanDate, this.dueDate, this.status, this.returnDate, this.action,
      this.proxyUserId);
  }

  public LoanRequestBuilder withItemId(UUID itemId) {
    return new LoanRequestBuilder(this.id, itemId, this.userId,
      this.loanDate, this.dueDate, this.status, this.returnDate, this.action,
      this.proxyUserId);
  }

  public LoanRequestBuilder withReturnDate(DateTime returnDate) {
    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      this.loanDate, this.dueDate, this.status, returnDate, this.action,
      this.proxyUserId);
  }

  public LoanRequestBuilder withItem(IndividualResource item) {
    return new LoanRequestBuilder(this.id, item.getId(), this.userId,
      this.loanDate, this.dueDate, this.status, this.returnDate, this.action,
      this.proxyUserId);
  }

  public LoanRequestBuilder withDueDate(DateTime dueDate) {
    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      this.loanDate, dueDate, this.status, this.returnDate, this.action,
      this.proxyUserId);
  }

  public LoanRequestBuilder withProxyUserId(UUID proxyUserId) {
    return new LoanRequestBuilder(this.id, this.itemId, this.userId,
      this.loanDate, dueDate, this.status, this.returnDate, this.action,
      proxyUserId);
  }

  public LoanRequestBuilder dueIn(Period period) {
    if(this.loanDate == null) {
      throw new IllegalStateException(
        "Cannot use period to specify due when no loan date specified");
    }

    DateTime calculatedDueDate = this.loanDate.plus(period);

    return withDueDate(calculatedDueDate);
  }

}
