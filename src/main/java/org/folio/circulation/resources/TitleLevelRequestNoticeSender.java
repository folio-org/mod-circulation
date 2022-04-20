package org.folio.circulation.resources;

import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.notice.NoticeConfiguration;
import org.folio.circulation.domain.notice.NoticeEventType;
import org.folio.circulation.domain.notice.NoticeFormat;
import org.folio.circulation.domain.notice.PatronNotice;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

public class TitleLevelRequestNoticeSender extends RequestNoticeSender {
  public TitleLevelRequestNoticeSender(Clients clients) {
    super(clients);
  }

  @Override
  public Result<RequestAndRelatedRecords> sendNoticeOnRequestCreated(
    RequestAndRelatedRecords relatedRecords) {

    Request request = relatedRecords.getRequest();
    TlrSettingsConfiguration tlrSettings = request.getTlrSettingsConfiguration();
    NoticeEventType eventType = requestTypeToEventMap.getOrDefault(request.getRequestType(),
      NoticeEventType.UNKNOWN);

    if (tlrSettings.isTitleLevelRequestsFeatureEnabled()
      && tlrSettings.getConfirmationPatronNoticeTemplateId() != null) {

      locationRepository.loadCampus(request.getItem().getLocation())
        .thenCompose(r -> r.after(locationRepository::loadInstitution))
        .thenApply(r -> r.map(request.getItem()::withLocation))
        .thenApply(r -> r.map(request::withItem))
        .thenApply(r -> r.map(requestResult -> createPatronNoticeEvent(requestResult, eventType)))
        .thenAccept(r -> r.after(patronNotice -> applyTlrConfirmationNotice(request.getTlrSettingsConfiguration(), patronNotice)));
    }

    return Result.succeeded(relatedRecords);
  }

  @Override
  protected Result<Void> sendNoticeOnRequestCancelled(RequestAndRelatedRecords relatedRecords) {
    Request request = relatedRecords.getRequest();

    if (request.getTlrSettingsConfiguration().isTitleLevelRequestsFeatureEnabled()
      && request.getTlrSettingsConfiguration().getCancellationPatronNoticeTemplateId() != null) {

      PatronNoticeEvent requestCancelledEvent = createPatronNoticeEvent(
        request, NoticeEventType.REQUEST_CANCELLATION);
      applyTlrCancellationNotice(request.getTlrSettingsConfiguration(), requestCancelledEvent);
    }

    return Result.succeeded(null);
  }

  private CompletableFuture<Result<Void>> applyTlrConfirmationNotice(
    TlrSettingsConfiguration tlrSettings, PatronNoticeEvent patronNoticeEvent) {

    UUID confirmationTemplateId = tlrSettings.getConfirmationPatronNoticeTemplateId();
    if (confirmationTemplateId != null) {
      NoticeLogContext noticeLogContext = patronNoticeEvent.getNoticeLogContext()
        .withTriggeringEvent(patronNoticeEvent.getEventType().getRepresentation())
        .withTemplateId(confirmationTemplateId.toString());
      NoticeConfiguration noticeConfiguration = buildTlrNoticeConfiguration(patronNoticeEvent,
        confirmationTemplateId);
      PatronNotice patronNotice = new PatronNotice(patronNoticeEvent.getUser().getId(),
        patronNoticeEvent.getNoticeContext(), noticeConfiguration);

      return patronNoticeService.sendNotice(patronNotice, noticeLogContext);
    }

    return ofAsync(() -> null);
  }

  private CompletableFuture<Result<Void>> applyTlrCancellationNotice(
    TlrSettingsConfiguration tlrSettings, PatronNoticeEvent patronNoticeEvent) {

    UUID cancellationTemplateId = tlrSettings.getCancellationPatronNoticeTemplateId();
    if (cancellationTemplateId != null) {
      NoticeLogContext noticeLogContext = patronNoticeEvent.getNoticeLogContext()
        .withTriggeringEvent(patronNoticeEvent.getEventType().getRepresentation())
        .withTemplateId(cancellationTemplateId.toString());
      NoticeConfiguration noticeConfiguration = buildTlrNoticeConfiguration(patronNoticeEvent,
        cancellationTemplateId);
      PatronNotice patronNotice = new PatronNotice(patronNoticeEvent.getUser().getId(),
        patronNoticeEvent.getNoticeContext(), noticeConfiguration);

      return patronNoticeService.sendNotice(patronNotice, noticeLogContext);
    }

    return ofAsync(() -> null);
  }

  private NoticeConfiguration buildTlrNoticeConfiguration(PatronNoticeEvent patronNoticeEvent,
    UUID cancellationTemplateId) {
    return new NoticeConfiguration(cancellationTemplateId.toString(), NoticeFormat.EMAIL,
      patronNoticeEvent.getEventType(), null, null, false, null, false);
  }
}
