package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.utils.BigDecimalUtil.roundTaxValue;

import java.math.BigDecimal;
import java.util.UUID;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;

import io.vertx.core.json.JsonObject;

public class AccountStorageRepresentation extends JsonObject {
  public AccountStorageRepresentation(Loan loan, Item item, FeeFineOwner feeFineOwner,
    FeeFine feeFine, BigDecimal amount) {
    super();

    this.put("id", UUID.randomUUID().toString());
    this.put("ownerId", feeFineOwner.getId());
    this.put("feeFineId", feeFine.getId());
    this.put("amount", roundTaxValue(amount).toString());
    this.put("remaining", roundTaxValue(amount).toString());
    this.put("feeFineType", feeFine.getFeeFineType());
    this.put("feeFineOwner", feeFineOwner.getOwner());
    this.put("title", item.getTitle());
    this.put("barcode", item.getBarcode());
    this.put("callNumber", item.getCallNumber());
    this.put("location", item.getLocation().getName());
    this.put("materialType", item.getMaterialTypeName());
    this.put("materialTypeId", item.getMaterialTypeId());
    this.put("loanId", loan.getId());
    this.put("userId", loan.getUserId());
    this.put("itemId", item.getItemId());
    write(this, "dueDate", loan.getDueDate());
    write(this, "returnedDate", loan.getReturnDate());

    this.put("paymentStatus", createNamedObject("Outstanding"));
    this.put("status", createNamedObject("Open"));
  }

  private AccountStorageRepresentation(JsonObject json) {
    super(json.getMap());
  }

  public String getId() {
    return getString("id");
  }

  public static AccountStorageRepresentation fromAccount(Account account) {
    return new AccountStorageRepresentation(account.toJson());
  }

  private void setStatus(String status) {
    put("status", createNamedObject(status));
  }

  private void setPaymentStatus(FeeFinePaymentAction paymentStatus) {
    put("paymentStatus", createNamedObject(paymentStatus.getValue()));
  }

  private void setRemaining(BigDecimal remaining) {
    put("remaining", roundTaxValue(remaining).toString());
  }

  public void close(FeeFinePaymentAction paymentAction) {
    setStatus("Closed");
    setPaymentStatus(paymentAction);
    setRemaining(BigDecimal.ZERO);
  }

  private JsonObject createNamedObject(String status) {
    return new JsonObject().put("name", status);
  }
}
