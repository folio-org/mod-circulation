package api.support.http;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

public class CqlQuery implements QueryStringParameter {
  private final String query;

  public static CqlQuery queryFromTemplate(String queryTemplate,
      Object... parameters) {

    return new CqlQuery(String.format(queryTemplate, parameters));
  }

  public static CqlQuery noQuery() {
    return new CqlQuery(null);
  }

  private CqlQuery(String query) {
    this.query = query;
  }

  public String getQuery() {
    return query;
  }

  @Override
  public void collectInto(Map<String, String> queryStringParameters) {
    if (StringUtils.isNotBlank(getQuery())) {
      queryStringParameters.put("query", getQuery());
    }
  }
}
