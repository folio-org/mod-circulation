package org.folio.circulation.domain.notice.combiner;

import static java.util.stream.Collectors.collectingAndThen;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import org.folio.circulation.domain.mapper.UserMapper;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;

import io.vertx.core.json.JsonObject;

public class LoanNoticeContextCombiner implements NoticeContextCombiner {

  @Override
  public JsonObject buildCombinedNoticeContext(Collection<PatronNoticeEvent> events) {
    return events.stream()
      .map(PatronNoticeEvent::getNoticeContext)
      .filter(Objects::nonNull)
      .collect(collectingAndThen(
        Collectors.toList(),
        contexts -> new JsonObject()
          .put("user", UserMapper.createUserContext(events.iterator().next().getUser()))
          .put("loans", contexts)
      ));
  }

  @Override
  public NoticeLogContext buildCombinedNoticeLogContext(Collection<PatronNoticeEvent> events) {
    return events.stream()
      .map(PatronNoticeEvent::getNoticeLogContext)
      .filter(Objects::nonNull)
      .map(NoticeLogContext::getItems)
      .flatMap(Collection::stream)
      .filter(Objects::nonNull)
      .collect(collectingAndThen(
        Collectors.toList(),
        items -> new NoticeLogContext()
          .withUser(events.iterator().next().getUser())
          .withItems(items)
      ));
  }
}
