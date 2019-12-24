package api.support.http.api.support;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Map;

import api.support.http.QueryStringParameter;

public class NamedQueryStringParameter implements QueryStringParameter {
  private final String name;
  private final String value;

  public static NamedQueryStringParameter namedParameter(String name, String value) {
    return new NamedQueryStringParameter(name, value);
  }

  private NamedQueryStringParameter(String name, String value) {
    if(isBlank(name)) {
      throw new IllegalArgumentException("name must not be empty");
    }

    if(isBlank(value)) {
      throw new IllegalArgumentException("value must not be empty");
    }

    this.name = name;
    this.value = value;
  }



  @Override
  public void collectInto(Map<String, String> queryStringParameters) {
    queryStringParameters.put(name, value);
  }
}
