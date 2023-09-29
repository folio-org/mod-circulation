package org.folio.circulation.domain.representations;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class AddInfoRequest {

  public static final String ACTION = "action";
  public static final String ACTION_COMMENT = "actionComment";

  private final String loanId;
  private final String action;
  private final String actionComment;

}
