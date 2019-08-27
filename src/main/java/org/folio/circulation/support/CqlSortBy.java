package org.folio.circulation.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

public class CqlSortBy {

  private final List<CqlOrder> orders;

  private CqlSortBy(List<CqlOrder> orders) {
    this.orders = orders;
  }

  public static CqlSortBy sortBy(CqlOrder... orders) {
    return CqlSortBy.sortBy(Arrays.asList(orders));
  }

  public static CqlSortBy sortBy(List<CqlOrder> orders) {
    return new CqlSortBy(orders);
  }

  public static CqlSortBy ascending(String index) {
    return CqlSortBy.sortBy(CqlOrder.asc(index));
  }

  public static CqlSortBy descending(String index) {
    return CqlSortBy.sortBy(CqlOrder.desc(index));
  }

  public static CqlSortBy none() {
    return CqlSortBy.sortBy(Collections.emptyList());
  }

  public String applyTo(String query) {
    if (orders.isEmpty()) {
      return query;
    }
    String sortBy = orders.stream()
      .map(CqlOrder::asText)
      .collect(Collectors.joining(
        StringUtils.SPACE, " sortBy ", StringUtils.EMPTY));
    return query.concat(sortBy);
  }
}
