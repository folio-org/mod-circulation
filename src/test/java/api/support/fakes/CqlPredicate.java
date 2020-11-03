package api.support.fakes;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getValueByPath;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.z3950.zing.cql.CQLBoolean;
import org.z3950.zing.cql.CQLBooleanNode;
import org.z3950.zing.cql.CQLNode;
import org.z3950.zing.cql.CQLParser;
import org.z3950.zing.cql.CQLTermNode;

import io.vertx.core.json.JsonObject;
import lombok.SneakyThrows;

public final class CqlPredicate implements Predicate<JsonObject> {
  private static final String MATCH_ALL_RECORDS_INDEX = "cql.allRecords";
  public static final String MATCH_ALL_RECORDS = MATCH_ALL_RECORDS_INDEX + "=1";

  private static final Map<CQLBoolean, CqlLogicalOperator> supportedLogicalOperators = initLogicalOperators();
  private static final Map<String, CqlBinaryOperator> supportedOperators = initOperatorsTable();
  private final CQLNode entryNode;

  @SneakyThrows
  public CqlPredicate(String cql) {
    Objects.requireNonNull(cql, "CQL query must be not null");
    this.entryNode = new CQLParser().parse(cql);
  }

  @SneakyThrows
  @Override
  public boolean test(JsonObject json) {
    return evaluate(json, entryNode);
  }

  private boolean evaluate(JsonObject json, CQLNode node) {
    if (isTerm(node)) {
      return evaluateTerm(json, (CQLTermNode) node);
    } else if (isLogicalNode(node)) {
      return evaluateLogicalOperation(json, (CQLBooleanNode) node);
    } else {
      throw new IllegalArgumentException("Undefined node type: " + node.getClass().getName());
    }
  }

  private boolean isLogicalNode(CQLNode node) {
    return node instanceof CQLBooleanNode;
  }

  private boolean evaluateTerm(JsonObject json, CQLTermNode node) {
    if (isMatchAllRecordsTerm(node)) {
      return true;
    }

    final CqlBinaryOperator cqlBinaryOperator = supportedOperators.get(node.getRelation().getBase());
    return cqlBinaryOperator.apply(json, node.getIndex(), node.getTerm());
  }

  private boolean evaluateLogicalOperation(JsonObject json, CQLBooleanNode node) {
    final CqlLogicalOperator operator = supportedLogicalOperators.get(node.getOperator());

    return operator.apply(evaluate(json, node.getRightOperand()),
      evaluate(json, node.getLeftOperand()));
  }

  private boolean isTerm(CQLNode node) {
    return node instanceof CQLTermNode;
  }

  private boolean isMatchAllRecordsTerm(CQLTermNode node) {
    return MATCH_ALL_RECORDS_INDEX.equals(node.getIndex());
  }

  private static Map<String, CqlBinaryOperator> initOperatorsTable() {
   return Map.of(
     "==", operator(Objects::equals),
    "=", operator((expected, actual) -> actual.contains(expected)),
    "<>", operator((expected, actual) -> !actual.contains(expected)),
    ">", operator((expected, actual) -> actual.compareTo(expected) > 0),
    "<", operator((expected, actual) -> actual.compareTo(expected) < 0),
    "<=", operator((expected, actual) -> actual.compareTo(expected) <= 0),
    ">=", operator((expected, actual) -> actual.compareTo(expected) >= 0));
  }

  private static CqlBinaryOperator operator(
    BiFunction<String, String, Boolean> compareOperator) {
    return (json, propertyName, expectedPropertyValue) -> {
      final Object actualPropertyValue = getValueByPath(json, propertyName.split("\\."));

      if (actualPropertyValue == null) {
        return false;
      }

      return compareOperator.apply(expectedPropertyValue, actualPropertyValue.toString());
    };
  }

  private static Map<CQLBoolean, CqlLogicalOperator> initLogicalOperators() {
    return Map.of(
      CQLBoolean.AND, (left, right) -> left && right,
      CQLBoolean.OR, (left, right) -> left || right,
      CQLBoolean.NOT, (left, right) -> left && !right);
  }

  @FunctionalInterface
  private interface CqlBinaryOperator {
    boolean apply(JsonObject record, String propertyName, String expectedValue);
  }

  @FunctionalInterface
  private interface CqlLogicalOperator {
    boolean apply(boolean prev, boolean current);
  }
}
