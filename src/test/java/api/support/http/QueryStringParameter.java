package api.support.http;

import java.util.Map;

public interface QueryStringParameter {
  void collectInto(Map<String, String> queryStringParameters);
}
