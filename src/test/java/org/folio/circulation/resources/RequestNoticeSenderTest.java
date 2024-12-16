package org.folio.circulation.resources;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.notice.ImmediatePatronNoticeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class RequestNoticeSenderTest {

  @Mock
  private ImmediatePatronNoticeService immediatePatronNoticeService;
  @InjectMocks
  private RequestNoticeSender requestNoticeSender;

  @Test
  void shouldNotSendNotificationWhenIsDcbCancellationTrue() {
    JsonObject representation = new RequestBuilder().create();
    representation.put("isDcbReRequestCancellation", true);
    requestNoticeSender.sendNoticeOnRequestCancelled(
      new RequestAndRelatedRecords(Request.from(representation)));
    Mockito.verify(immediatePatronNoticeService, times(0)).acceptNoticeEvent(any());
    Mockito.verify(immediatePatronNoticeService, times(0)).sendNotice(any(), any());
  }
}
