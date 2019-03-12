package org.folio.circulation.domain.notice;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PatronNoticePolicy {

  private final List<NoticeDescriptor> loanNoticeDescriptors;
  private final List<NoticeDescriptor> requestNoticeDescriptors;

  public PatronNoticePolicy(
    List<NoticeDescriptor> loanNoticeDescriptors,
    List<NoticeDescriptor> requestNoticeDescriptors) {
    this.loanNoticeDescriptors = loanNoticeDescriptors;
    this.requestNoticeDescriptors = requestNoticeDescriptors;
  }

  public List<NoticeDescriptor> lookupLoanNoticeDescriptor(
    NoticeEventType eventType, NoticeTiming timing) {
    return lookupNoticeDescriptor(loanNoticeDescriptors, eventType, timing);
  }

  public List<NoticeDescriptor> lookupRequestNoticeDescriptor(
    NoticeEventType eventType, NoticeTiming timing) {
    return lookupNoticeDescriptor(requestNoticeDescriptors, eventType, timing);
  }

  private List<NoticeDescriptor> lookupNoticeDescriptor(
    List<NoticeDescriptor> descriptorList, NoticeEventType eventType, NoticeTiming timing) {
    return descriptorList.stream()
      .filter(d -> Objects.equals(d.getNoticeEventType(), eventType))
      .filter(d -> Objects.equals(d.getTiming(), timing))
      .collect(Collectors.toList());
  }



}
