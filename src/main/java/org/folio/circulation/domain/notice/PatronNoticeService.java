package org.folio.circulation.domain.notice;

import java.util.List;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class PatronNoticeService {

  private static final Logger log = LoggerFactory.getLogger(PatronNoticeService.class);

  private CollectionResourceClient patronNoticeClient;

  public PatronNoticeService(Clients clients) {
    this.patronNoticeClient = clients.patronNoticeClient();
  }

  public void sendPatronNotice(List<NoticeConfiguration> noticeConfigurations, String recipientId, JsonObject context) {
    noticeConfigurations.stream()
      .map(this::toPatronNotice)
      .map(n -> n.setRecipientId(recipientId))
      .map(n -> n.setContext(context))
      .forEach(this::sendNotice);
  }

  public JsonObject createNoticeContextFromLoan(Loan loan) {
    return createNoticeContextFromLoan(loan, DateTimeZone.UTC);
  }

  public JsonObject createNoticeContextFromLoan(Loan loan, DateTimeZone timeZone) {
    User user = loan.getUser();
    Item item = loan.getItem();

    JsonObject patron = new JsonObject()
      .put("firstName", user.getFirstName())
      .put("lastName", user.getLastName())
      .put("barcode", user.getBarcode());

    JsonObject itemContext = new JsonObject()
      .put("title", item.getTitle())
      .put("barcode", item.getBarcode());

    return new JsonObject()
      .put("patron", patron)
      .put("item", itemContext)
      .put("dueDate", loan.getDueDate().withZone(timeZone).toString());
  }

  private PatronNotice toPatronNotice(NoticeConfiguration noticeConfiguration) {
    PatronNotice patronNotice = new PatronNotice();
    patronNotice.setTemplateId(noticeConfiguration.getTemplateId());
    patronNotice.setDeliveryChannel(noticeConfiguration.getNoticeFormat().getDeliveryChannel());
    patronNotice.setOutputFormat(noticeConfiguration.getNoticeFormat().getOutputFormat());
    return patronNotice;
  }


  private void sendNotice(PatronNotice patronNotice) {
    JsonObject body = JsonObject.mapFrom(patronNotice);

    patronNoticeClient.post(body).thenAccept(response -> {
      if (response.getStatusCode() != 200) {
        log.error("Failed to send patron notice. Status: {} Body: {}",
          response.getStatusCode(),
          response.getBody());
      }
    });
  }
}
