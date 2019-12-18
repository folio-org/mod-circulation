package api.support.http;

import java.util.HashMap;
public interface QueryStringParameter {
  void collectInto(HashMap<String, String> queryStringParameters);
}
