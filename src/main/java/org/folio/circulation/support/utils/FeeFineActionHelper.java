package org.folio.circulation.support.utils;

import static org.apache.commons.lang3.StringUtils.defaultString;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.circulation.domain.FeeFineAction;

public class FeeFineActionHelper {
  public static final String PATRON_COMMENTS_KEY = "PATRON";
  public static final String STAFF_COMMENTS_KEY = "STAFF";
  private static final String COMMENT_KEY_VALUE_SEPARATOR = " : ";
  private static final String COMMENTS_SEPARATOR = " \n ";

  private FeeFineActionHelper() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static String getStaffInfoFromComment(FeeFineAction action) {
    return defaultString(parseFeeFineComments(action.getComments()).get(STAFF_COMMENTS_KEY));
  }

  public static String getPatronInfoFromComment(FeeFineAction action) {
    return defaultString(parseFeeFineComments(action.getComments()).get(PATRON_COMMENTS_KEY));
  }

  private static Map<String, String> parseFeeFineComments(String comments) {
    return Arrays.stream(defaultString(comments).split(COMMENTS_SEPARATOR))
      .map(s -> s.split(COMMENT_KEY_VALUE_SEPARATOR))
      .filter(arr -> arr.length == 2)
      .map(strings -> Pair.of(strings[0], strings[1]))
      .collect(Collectors.toMap(Pair::getKey, Pair::getValue, (s, s2) -> s));
  }
}
