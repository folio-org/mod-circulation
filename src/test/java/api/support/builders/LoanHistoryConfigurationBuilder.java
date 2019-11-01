package api.support.builders;

import io.vertx.core.json.JsonObject;

public class LoanHistoryConfigurationBuilder extends JsonBuilder implements Builder {

  private boolean exceptionForFeesAndFines;
  private String loanClosingType;
  private String feeFineClosingType;
  private Integer loanCloseDuration;
  private String loanCloseIntervalId;
  private Integer feeFineCloseDuration;
  private String feeFineCloseIntervalId;

  public LoanHistoryConfigurationBuilder loanCloseAnonymizeImmediately() {
    loanClosingType = "immediately";
    return this;
  }

  public LoanHistoryConfigurationBuilder loanCloseAnonymizeAfterXInterval(Integer duration, String intervalId) {
    loanClosingType = "interval";
    loanCloseDuration = duration;
    loanCloseIntervalId = intervalId;
    return this;
  }

  public LoanHistoryConfigurationBuilder feeFineCloseAnonymizeAfterXInterval(Integer duration, String intervalId) {
    feeFineClosingType = "interval";
    feeFineCloseDuration = duration;
    feeFineCloseIntervalId = intervalId;
    exceptionForFeesAndFines = true;
    return this;
  }

  public LoanHistoryConfigurationBuilder loanCloseAnonymizeNever() {
    loanClosingType = "never";
    return this;
  }

  public LoanHistoryConfigurationBuilder feeFineCloseAnonymizeImmediately() {
    exceptionForFeesAndFines = true;
    feeFineClosingType = "immediately";
    return this;
  }

  public LoanHistoryConfigurationBuilder feeFineCloseAnonymizeNever() {
    exceptionForFeesAndFines = true;
    loanClosingType = "never";
    return this;
  }

  @Override
  public JsonObject create() {
    JsonObject config = new JsonObject();

    JsonObject closingType = new JsonObject();

    put(closingType, "loan", loanClosingType);
    put(closingType, "feeFine", feeFineClosingType);

    JsonObject feeFine = new JsonObject();

    put(feeFine, "intervalId", feeFineCloseIntervalId);
    put(feeFine, "duration", feeFineCloseDuration);

    JsonObject loan = new JsonObject();

    put(loan, "intervalId", loanCloseIntervalId);
    put(loan, "duration", loanCloseDuration);

    put(config, "closingType", closingType);
    put(config, "treatEnabled", exceptionForFeesAndFines);
    put(config, "feeFine", feeFine);
    put(config, "loan", loan);

    return config;
  }
}
