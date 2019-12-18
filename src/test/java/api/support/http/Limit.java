package api.support.http;

public class Limit {
  private final int limit;

  public static Limit limit(int limit) {
    return new Limit(limit);
  }

  private Limit(int limit) {
    this.limit = limit;
  }

  public int getLimit() {
    return limit;
  }
}
