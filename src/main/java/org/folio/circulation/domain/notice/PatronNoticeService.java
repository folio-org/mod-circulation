package org.folio.circulation.domain.notice;

import java.util.List;

import org.folio.circulation.domain.policy.PatronNoticePolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class PatronNoticeService {

  private static final Logger log = LoggerFactory.getLogger(PatronNoticeService.class);

  private PatronNoticePolicyRepository noticePolicyRepository;
  private CollectionResourceClient patronNoticeClient;

  public PatronNoticeService(PatronNoticePolicyRepository noticePolicyRepository, Clients clients) {
    this.noticePolicyRepository = noticePolicyRepository;
    this.patronNoticeClient = clients.patronNoticeClient();
  }

  public void acceptNoticeEvent(PatronNoticeEvent event) {
    noticePolicyRepository.lookupPolicy(event.getItem(), event.getUser())
      .thenAccept(r -> r.next(policy -> applyNoticePolicy(event, policy)));
  }

  private Result<PatronNoticePolicy> applyNoticePolicy(
    PatronNoticeEvent event, PatronNoticePolicy policy) {

    List<NoticeConfiguration> matchingNoticeConfiguration =
      policy.lookupNoticeConfiguration(event.getEventType(), event.getTiming());
    String recipientId = event.getUser().getId();

    sendPatronNotices(matchingNoticeConfiguration, recipientId, event.getNoticeContext());
    return Result.succeeded(policy);
  }

  private void sendPatronNotices(List<NoticeConfiguration> noticeConfigurations, String recipientId, JsonObject context) {
    noticeConfigurations.stream()
      .map(this::toPatronNotice)
      .map(n -> n.setRecipientId(recipientId))
      .map(n -> n.setContext(context))
      .forEach(this::sendNotice);
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
