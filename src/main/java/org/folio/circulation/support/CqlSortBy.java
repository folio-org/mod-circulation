package org.folio.circulation.support;

public abstract class CqlSortBy {
  public static CqlSortBy ascending(String index) {
    return new AscendingCqlSortBy(index);
  }

  public static CqlSortBy none() {
    return new NoCqlSortBy();
  }

  protected abstract String applyTo(String query);

  private static class AscendingCqlSortBy extends CqlSortBy {
    private final String index;

    AscendingCqlSortBy(String index) {
      this.index = index;
    }

    @Override
    protected String applyTo(String query) {
      return String.format("%s sortBy %s/sort.ascending", query, index);
    }
  }

  private static class NoCqlSortBy extends CqlSortBy {
    @Override
    protected String applyTo(String query) {
      return query;
    }
  }
}
