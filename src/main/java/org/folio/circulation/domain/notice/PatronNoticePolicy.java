package org.folio.circulation.domain.notice;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PatronNoticePolicy {

  private final List<NoticeConfiguration> loanNoticeConfigurations;
  private final List<NoticeConfiguration> requestNoticeConfigurations;

  public PatronNoticePolicy(
    List<NoticeConfiguration> loanNoticeConfigurations,
    List<NoticeConfiguration> requestNoticeConfigurations) {
    this.loanNoticeConfigurations = loanNoticeConfigurations;
    this.requestNoticeConfigurations = requestNoticeConfigurations;
  }

  public List<NoticeConfiguration> lookupLoanNoticeDescriptor(
    NoticeEventType eventType, NoticeTiming timing) {
    return lookupNoticeDescriptor(loanNoticeConfigurations, eventType, timing);
  }

  public List<NoticeConfiguration> lookupRequestNoticeDescriptor(
    NoticeEventType eventType, NoticeTiming timing) {
    return lookupNoticeDescriptor(requestNoticeConfigurations, eventType, timing);
  }

  private List<NoticeConfiguration> lookupNoticeDescriptor(
    List<NoticeConfiguration> descriptorList, NoticeEventType eventType, NoticeTiming timing) {
    return descriptorList.stream()
      .filter(d -> Objects.equals(d.getNoticeEventType(), eventType))
      .filter(d -> Objects.equals(d.getTiming(), timing))
      .collect(Collectors.toList());
  }



}
