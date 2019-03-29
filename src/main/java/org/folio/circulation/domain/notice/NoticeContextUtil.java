package org.folio.circulation.domain.notice;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.User;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class NoticeContextUtil {

  private NoticeContextUtil() {
  }

  public static JsonObject createNoticeContextFromLoan(Loan loan) {
    return createNoticeContextFromLoan(loan, DateTimeZone.UTC);
  }

  public static JsonObject createNoticeContextFromLoan(Loan loan, DateTimeZone timeZone) {
    User user = loan.getUser();
    Item item = loan.getItem();

    JsonObject patron = createPatron(user);
    JsonObject itemContext = createItemContext(item);

    return new JsonObject()
      .put("patron", patron)
      .put("item", itemContext)
      .put("dueDate", loan.getDueDate().withZone(timeZone).toString());
  }

  public static JsonObject createNoticeContextFromItem(Item item, User user) {
    JsonObject patron = createPatron(user);
    JsonObject itemContext = createItemContext(item);

    return new JsonObject()
      .put("patron", patron)
      .put("item", itemContext);
  }

  private static JsonObject createPatron(User user) {
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
