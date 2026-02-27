package org.folio.circulation.domain.notice.combiner;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static java.util.stream.Collectors.collectingAndThen;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import org.folio.circulation.domain.mapper.UserMapper;
import org.folio.circulation.domain.notice.PatronNoticeEvent;
import org.folio.circulation.domain.representations.logs.NoticeLogContext;

import io.vertx.core.json.JsonObject;

public class LoanNoticeContextCombiner implements NoticeContextCombiner {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public JsonObject buildCombinedNoticeContext(Collection<PatronNoticeEvent> events) {
    log.debug("buildCombinedNoticeContext:: building combined notice context for {} events",
      events.size());

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
    log.debug("buildCombinedNoticeLogContext:: building combined log context for {} events",
      events.size());
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
