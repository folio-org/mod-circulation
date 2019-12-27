package org.folio.circulation.domain.representations.changeduedate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.joda.time.DateTime;

public class BatchChangeDueDateRequest {

  private final Set<String> loanIds;
  private final DateTime dueDate;

  public BatchChangeDueDateRequest(List<String> loanIds,
    DateTime dueDate) {

    this.loanIds = new HashSet<>(loanIds);
    this.dueDate = dueDate;
  }

  public Set<String> getLoanIds() {
    return loanIds;
  }

  public DateTime getDueDate() {
    return dueDate;
  }
}
