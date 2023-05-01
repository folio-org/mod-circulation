package org.folio.circulation.domain.policy.library;

import java.time.ZonedDateTime;

import org.folio.circulation.support.Interval;

import lombok.ToString;

@ToString(onlyExplicitlyIncluded = true)
public class LibraryInterval {
  @ToString.Include
  private final Interval interval;
  @ToString.Include
  private final boolean open;

  private LibraryInterval previous;
  private LibraryInterval next;

  public LibraryInterval(Interval interval, boolean open) {
    this.interval = interval;
    this.open = open;
  }

  public Interval getInterval() {
    return interval;
  }

  public ZonedDateTime getStartTime() {
    return interval.getStart();
  }

  public ZonedDateTime getEndTime() {
    return interval.getEnd();
  }

  public boolean isOpen() {
    return open;
  }

  public LibraryInterval getPrevious() {
    return previous;
  }

  public void setPrevious(LibraryInterval previous) {
    this.previous = previous;
  }

  public LibraryInterval getNext() {
    return next;
  }

  public void setNext(LibraryInterval next) {
    this.next = next;
  }
}
