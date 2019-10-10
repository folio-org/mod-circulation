package org.folio.circulation.domain.notice;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PatronNoticePolicy {

  private final List<NoticeConfiguration> noticeConfigurations;

  public PatronNoticePolicy(
    List<NoticeConfiguration> noticeConfigurations) {
    this.noticeConfigurations = noticeConfigurations;
  }

  public List<NoticeConfiguration> getNoticeConfigurations() {
    return noticeConfigurations;
  }

  public Optional<NoticeConfiguration> lookupNoticeConfiguration(NoticeEventType eventType) {
    return noticeConfigurations.stream()
      .filter(d -> Objects.equals(d.getNoticeEventType(), eventType))
      .findFirst();
  }
}
