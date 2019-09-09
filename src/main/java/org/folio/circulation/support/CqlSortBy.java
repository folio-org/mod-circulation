package org.folio.circulation.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

public class CqlSortBy {

  private final List<CqlSortClause> orders;

  private CqlSortBy(List<CqlSortClause> orders) {
    this.orders = orders;
  }

  public static CqlSortBy sortBy(CqlSortClause... orders) {
    return CqlSortBy.sortBy(Arrays.asList(orders));
  }

  public static CqlSortBy sortBy(List<CqlSortClause> orders) {
    return new CqlSortBy(orders);
  }

  public static CqlSortBy ascending(String index) {
    return CqlSortBy.sortBy(CqlSortClause.ascending(index));
  }

  public static CqlSortBy descending(String index) {
    return CqlSortBy.sortBy(CqlSortClause.descending(index));
  }

  public static CqlSortBy none() {
    return new CqlSortBy(Collections.emptyList());
  }

  public String applyTo(String query) {
    if (orders.isEmpty()) {
      return query;
    }
    String sortBy = orders.stream()
      .map(CqlSortClause::asText)
      .collect(Collectors.joining(
        StringUtils.SPACE, " sortBy ", StringUtils.EMPTY));
    return query.concat(sortBy);
  }
}
