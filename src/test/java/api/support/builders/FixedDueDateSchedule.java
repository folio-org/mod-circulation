package api.support.builders;

import org.joda.time.DateTime;

public class FixedDueDateSchedule {

  final DateTime from;
  final DateTime to;
  final DateTime due;

  public FixedDueDateSchedule(DateTime from, DateTime to, DateTime due) {
    this.from = from;
    this.to = to;
    this.due = due;
  }
}
