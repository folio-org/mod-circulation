package org.folio.circulation.domain.representations;

import org.joda.time.DateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChangeDueDateRequest {
  public static final String DUE_DATE = "dueDate";

  private final String loanId;
  private final DateTime dueDate;
}
