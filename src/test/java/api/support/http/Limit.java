package api.support.http;

import static java.lang.Integer.MAX_VALUE;

public class Limit {
  private final int limit;

  public static Limit limit(int limit) {
    return new Limit(limit);
  }

  public static Limit maximumLimit() {
    return limit(MAX_VALUE);
  }

  private Limit(int limit) {
    this.limit = limit;
  }


  public int getLimit() {
    return limit;
  }
}
