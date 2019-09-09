package org.folio.circulation.domain.notice;

import static org.folio.circulation.support.http.CommonResponseInterpreters.mapToRecordInterpreter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.notice.schedule.ScheduledNoticeConfig;
import org.folio.circulation.domain.policy.PatronNoticePolicyRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.Result;

import io.vertx.core.json.JsonObject;

public class PatronNoticeService {
  public static PatronNoticeService using(Clients clients) {
    return new PatronNoticeService(new PatronNoticePolicyRepository(clients), clients);
  }

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

  public CompletableFuture<Result<Void>> acceptScheduledNoticeEvent(
    ScheduledNoticeConfig noticeConfig, String recipientId, JsonObject context) {
    PatronNotice patronNotice = toPatronNotice(noticeConfig);
    patronNotice.setRecipientId(recipientId);
    patronNotice.setContext(context);
    return sendNotice(patronNotice).thenApply(r -> r.map(n -> null));
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

  private PatronNotice toPatronNotice(ScheduledNoticeConfig noticeConfig) {
    PatronNotice patronNotice = new PatronNotice();
    patronNotice.setTemplateId(noticeConfig.getTemplateId());
    patronNotice.setDeliveryChannel(noticeConfig.getFormat().getDeliveryChannel());
    patronNotice.setOutputFormat(noticeConfig.getFormat().getOutputFormat());
    return patronNotice;
  }


  private CompletableFuture<Result<PatronNotice>> sendNotice(PatronNotice patronNotice) {
    JsonObject body = JsonObject.mapFrom(patronNotice);

    return patronNoticeClient.post(body)
      .thenApply(mapToRecordInterpreter(patronNotice, 200, 201)::apply);
  }
}
