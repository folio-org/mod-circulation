package api.support.http;

public class CqlQuery {
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
}
