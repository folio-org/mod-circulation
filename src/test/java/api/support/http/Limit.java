package api.support.http;

import static java.lang.Integer.MAX_VALUE;

import java.util.HashMap;

public class Limit implements QueryStringParameter {
  private final Integer limit;

  public static Limit limit(int limit) {
    return new Limit(limit);
  }

  public static Limit maximumLimit() {
    return limit(MAX_VALUE);
  }

  public static Limit noLimit() {
    return new Limit(null);
  }

  private Limit(Integer limit) {
    this.limit = limit;
  }

  public void collectInto(HashMap<String, String> queryStringParameters) {
    //TODO: Replace with null value pattern
    if (limit != null) {
      queryStringParameters.put("limit", limit.toString());
    }
  }
}
