package org.folio.circulation.domain.notice;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.User;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class NoticeContextUtil {

  private static final String PATRON = "patron";
  private static final String ITEM = "item";
  private static final String DUE_DATE = "dueDate";

  private NoticeContextUtil() {
  }

  public static JsonObject createNoticeContextFromLoan(Loan loan) {
    return createLoanNoticeContext(loan, DateTimeZone.UTC);
  }

  public static JsonObject createLoanNoticeContext(Loan loan, DateTimeZone timeZone) {
    return createNoticeContextFromItemAndPatron(loan.getItem(), loan.getUser())
      .put(DUE_DATE, loan.getDueDate().withZone(timeZone).toString());
  }

  public static JsonObject createNoticeContextFromItemAndPatron(Item item, User user) {
    JsonObject patron = createPatronContext(user);
    JsonObject itemContext = createItemContext(item);

    return new JsonObject()
      .put(PATRON, patron)
      .put(ITEM, itemContext);
  }

  public static JsonObject createNoticeContextFromLoan(Loan loan, DateTimeZone timeZone) {
    User user = loan.getUser();
    Item item = loan.getItem();

    JsonObject patron = createPatronContext(user);
    JsonObject itemContext = createItemContext(item);

    return new JsonObject()
      .put(PATRON, patron)
      .put(ITEM, itemContext)
      .put(DUE_DATE, loan.getDueDate().withZone(timeZone).toString());
  }

  private static JsonObject createPatronContext(User user) {
    return new JsonObject()
      .put("firstName", user.getFirstName())
      .put("lastName", user.getLastName())
      .put("barcode", user.getBarcode());
  }

  private static JsonObject createItemContext(Item item) {
    return new JsonObject()
      .put("title", item.getTitle())
      .put("barcode", item.getBarcode())
      .put("status", item.getStatus());
  }
}
