package org.folio.circulation.resources;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.stream.Stream;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.notice.ImmediatePatronNoticeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
    verify(immediatePatronNoticeService, times(0)).acceptNoticeEvent(any());
    verify(immediatePatronNoticeService, times(0)).sendNotice(any(), any());
  }

  @ParameterizedTest
  @MethodSource
  void sendNoticeOnMediatedRequestUpdate(User requester, int invokableTimes) {
    Request request = Request.from(new RequestBuilder().create()).withRequester(requester);
    RequestAndRelatedRecords records = new RequestAndRelatedRecords(request)
      .withRequestQueue(RequestQueue.requestQueueOf(request));

    requestNoticeSender.sendNoticeOnMediatedRequestCreated(request, records);
    verify(immediatePatronNoticeService, times(invokableTimes)).acceptNoticeEvent(any());
  }

  private static Stream<Arguments> sendNoticeOnMediatedRequestUpdate() {
    return Stream.of(
      Arguments.of(buildUser("Secure", "Patron"), 1),
      Arguments.of(buildUser("Secure", "Tramp"), 0),
      Arguments.of(buildUser("Donald", "Patron"), 0),
      Arguments.of(buildUser("Donald", "Trump"), 0),
      Arguments.of(buildUser(null, null), 0),
      Arguments.of(null, 0)
    );
  }

  private static User buildUser(String name, String lastName) {
    return User.from(new JsonObject()
      .put("personal", new JsonObject()
        .put("firstName", name)
        .put("lastName", lastName)));
  }
}
