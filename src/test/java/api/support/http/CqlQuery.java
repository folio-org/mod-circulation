package api.support.http;

import java.util.HashMap;

import org.apache.commons.lang3.StringUtils;

public class CqlQuery implements QueryStringParameter {
  private final String query;

  public static CqlQuery query(String query) {
    return new CqlQuery(query);
  }

  public static CqlQuery queryFromTemplate(String queryTemplate,
      Object... parameters) {

    return query(String.format(queryTemplate, parameters));
  }

  public static CqlQuery noQuery() {
    return query(null);
  }

  private CqlQuery(String query) {
    this.query = query;
  }

  public String getQuery() {
    return query;
  }

  @Override
  public void collectInto(HashMap<String, String> queryStringParameters) {
    if (StringUtils.isNotBlank(getQuery())) {
      queryStringParameters.put("query", getQuery());
    }
  }
}
