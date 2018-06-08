package org.folio.circulation.domain;

import org.folio.circulation.support.ValidationErrorFailure;

public interface UserRelatedQuery {
  boolean userMatches(User user);

  ValidationErrorFailure userDoesNotMatchError();
}
