package org.folio.circulation.domain.anonymization;

import static org.folio.circulation.support.http.client.PageLimit.limit;

import org.folio.circulation.support.http.client.PageLimit;

public class LoanAnonymization {
  public static final PageLimit FETCH_LOANS_PAGE_LIMIT = limit(5000);
}
