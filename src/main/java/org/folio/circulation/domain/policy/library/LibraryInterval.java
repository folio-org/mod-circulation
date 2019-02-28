package org.folio.circulation.domain.policy.library;

import org.joda.time.DateTime;
import org.joda.time.Interval;

public class LibraryInterval {

  private final Interval interval;
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

  public DateTime getStartTime() {
    return interval.getStart();
  }

  public DateTime getEndTime() {
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
