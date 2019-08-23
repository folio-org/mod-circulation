package org.folio.circulation.support;

import static java.util.Collections.singletonList;

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
    return new CqlSortBy(Arrays.asList(orders));
  }

  public static CqlSortBy ascending(String index) {
    return new CqlSortBy(singletonList(CqlOrder.asc(index)));
  }

  public static CqlSortBy descending(String index) {
    return new CqlSortBy(singletonList(CqlOrder.desc(index)));
  }

  public static CqlSortBy none() {
    return new CqlSortBy(Collections.emptyList());
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
