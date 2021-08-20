package api.support.http;

import java.util.Map;

class Offset implements QueryStringParameter {
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
  public void collectInto(Map<String, String> queryStringParameters) {
    if (offset != null) {
      queryStringParameters.put("offset", offset.toString());
    }
  }
}
