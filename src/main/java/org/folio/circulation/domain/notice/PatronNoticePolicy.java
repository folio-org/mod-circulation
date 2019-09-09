package org.folio.circulation.domain.notice;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PatronNoticePolicy {

  private final List<NoticeConfiguration> noticeConfigurations;

  public PatronNoticePolicy(
    List<NoticeConfiguration> noticeConfigurations) {
    this.noticeConfigurations = noticeConfigurations;
  }

  public List<NoticeConfiguration> getNoticeConfigurations() {
    return noticeConfigurations;
  }

  public List<NoticeConfiguration> lookupNoticeConfiguration(NoticeEventType eventType, NoticeTiming timing) {
    return noticeConfigurations.stream()
      .filter(d -> Objects.equals(d.getNoticeEventType(), eventType))
      .filter(d -> Objects.equals(d.getTiming(), timing))
      .collect(Collectors.toList());
  }
}
