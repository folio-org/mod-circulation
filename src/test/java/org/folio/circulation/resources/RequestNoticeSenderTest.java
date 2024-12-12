package org.folio.circulation.resources;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.notice.ImmediatePatronNoticeService;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.support.StringUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.shaded.org.bouncycastle.cert.ocsp.Req;

import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
public class RequestNoticeSenderTest {

  @Mock
  private ImmediatePatronNoticeService immediatePatronNoticeService;
  @InjectMocks
  private RequestNoticeSender requestNoticeSender;

  @ParameterizedTest
  @MethodSource("requestAndRelatedRecords")
  void shouldNotSendNotificationWhenIsDcbCancellationTrue(RequestAndRelatedRecords records,
    int invocationTimes) {

    requestNoticeSender.sendNoticeOnRequestCancelled(records);
    Mockito.verify(immediatePatronNoticeService, times(invocationTimes)).acceptNoticeEvent(any(
      PatronNoticeEvent.class));
  }

  static Stream<Arguments> requestAndRelatedRecords() {
    return Stream.of(
      Arguments.of(getRequest(true, null), 0)
//      Arguments.of(getRequest(false, null), 1),
//      Arguments.of(getRequest(false, UUID.randomUUID()), 1)
    );
  }

  private static RequestAndRelatedRecords getRequest(boolean isDcbReRequestCancellation, UUID itemId) {
    JsonObject representation = new RequestBuilder()
      .withItemId(itemId)
      .create();
    representation.put("isDcbReRequestCancellation", isDcbReRequestCancellation);
    return new RequestAndRelatedRecords(Request.from(representation));
  }
}
