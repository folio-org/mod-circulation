package org.folio.circulation.domain.notice;

import java.util.List;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class PatronNoticeService {

  private static final Logger log = LoggerFactory.getLogger(PatronNoticeService.class);

  private static final DateTimeFormatter NOTICE_DATE_TIME_FORMATTER =
    DateTimeFormat.forPattern("MM/dd/yyyy hh:mm a");

  private CollectionResourceClient patronNoticeClient;

  public PatronNoticeService(Clients clients) {
    this.patronNoticeClient = clients.patronNoticeClient();
  }

  public void sendPatronNotice(List<NoticeDescriptor> noticeDescriptors, String recipientId, JsonObject context) {
    noticeDescriptors.stream()
      .map(this::toPatronNotice)
      .map(n -> n.setRecipientId(recipientId))
      .map(n -> n.setContext(context))
      .forEach(this::sendNotice);
  }

  public JsonObject createNoticeContextFromLoan(Loan loan) {
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
      .put("dueDate", NOTICE_DATE_TIME_FORMATTER.print(loan.getDueDate()));
  }

  private PatronNotice toPatronNotice(NoticeDescriptor noticeDescriptor) {
    PatronNotice patronNotice = new PatronNotice();
    patronNotice.setTemplateId(noticeDescriptor.getTemplateId());
    patronNotice.setDeliveryChannel(noticeDescriptor.getNoticeFormat().getDeliveryChannel());
    patronNotice.setOutputFormat(noticeDescriptor.getNoticeFormat().getOutputFormat());
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
