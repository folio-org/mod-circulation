package org.folio.circulation.domain.policy.library;

import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTime;

public class LibraryTimetable {

  public static LibraryInterval findInterval(DateTime dateTime, LibraryInterval head) {
    for (LibraryInterval node = head; node != null; node = node.getNext()) {
      if (node.getInterval().contains(dateTime)) {
        return node;
      }
    }
    return null;
  }

  private static Pair<LibraryInterval, LibraryInterval> getHeadAndTail(List<LibraryInterval> intervalList) {
    if (intervalList.isEmpty()) {
      return new ImmutablePair<>(null, null);
    }

    LibraryInterval head = intervalList.get(0);
    LibraryInterval prevNode = head;
    for (LibraryInterval currNode : intervalList) {
      prevNode.setNext(currNode);
      currNode.setPrevious(prevNode);
      prevNode = currNode;
    }
    return new ImmutablePair<>(head, prevNode);
  }


  private final LibraryInterval head;

  private final LibraryInterval tail;

  public LibraryTimetable(LibraryInterval head) {
    this.head = head;
    this.tail = head;
  }

  public LibraryTimetable() {
    this.head = null;
    this.tail = null;
  }

  public LibraryTimetable(List<LibraryInterval> intervalList) {
    Pair<LibraryInterval, LibraryInterval> headAndTail = getHeadAndTail(intervalList);
    this.head = headAndTail.getKey();
    this.tail = headAndTail.getValue();
  }

  public LibraryInterval findInterval(DateTime dateTime) {
    return findInterval(dateTime, head);
  }

  public LibraryInterval getHead() {
    return head;
  }

  public LibraryInterval getTail() {
    return tail;
  }
}
