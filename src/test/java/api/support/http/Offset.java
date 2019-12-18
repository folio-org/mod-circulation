package api.support.http;

import java.util.HashMap;

public class Offset implements QueryStringParameter {
  private final Integer offset;

  public static Offset offset(int offset) {
    return new Offset(offset);
  }

  public static Offset noOffset() {
    return new Offset(null);
  }

  private Offset(Integer offset) {
    this.offset = offset;
  }

  @Override
  public void collectInto(HashMap<String, String> queryStringParameters) {
    if (offset != null) {
      queryStringParameters.put("offset", offset.toString());
    }
  }
}
