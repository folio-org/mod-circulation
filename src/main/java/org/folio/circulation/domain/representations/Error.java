
package org.folio.circulation.domain.representations;

import java.util.ArrayList;
import java.util.List;
import org.folio.circulation.domain.representations.anonymization.Parameter;

public class Error {

  private String message;

  private List<Parameter> parameters = new ArrayList<>();

  public String getMessage() {
    return message;
  }

  public List<Parameter> getParameters() {
    return parameters;
  }

  public Error withMessage(String message) {
    this.message = message;
    return this;
  }

  public Error withParameters(List<Parameter> parameters) {
    this.parameters = parameters;
    return this;
  }
}
