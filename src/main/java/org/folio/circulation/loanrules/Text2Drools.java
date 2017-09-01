package org.folio.circulation.loanrules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.text.StrMatcher;
import org.apache.commons.lang3.text.StrTokenizer;

public class Text2Drools {
  /* Example drools file:

  package loanrules
  import org.folio.circulation.loanrules.*
  global LoanPolicy loanPolicy

  // fallback-policy
  rule "line 0"
    salience 0
    when
    then
      loanPolicy.id = "ffffffff-2222-4b5e-a7bd-064b8d177231";
      drools.halt();
  end

  rule "line 1"
    salience 1
    when
      ItemType(id == "aaaaaaaa-1111-4b5e-a7bd-064b8d177231")
    then
      loanPolicy.id = "ffffffff-3333-4b5e-a7bd-064b8d177231";
      drools.halt();
  end

  rule "line 2"
    salience 2
    when
      ItemType(id == "aaaaaaaa-1111-4b5e-a7bd-064b8d177231")
      PatronGroup(id == "cccccccc-1111-4b5e-a7bd-064b8d177231")
    then
      loanPolicy.id = "ffffffff-4444-4b5e-a7bd-064b8d177231";
      drools.halt();
  end

  */

  private StringBuilder drools = new StringBuilder(
      "package loanrules\n" +
      "import org.folio.circulation.loanrules.*\n" +
      "global LoanPolicy loanPolicy\n" +
      "\n"
      );

  private enum MatcherType {
    PatronGroup, ItemType, LoanType
  }

  private class Matcher {
    int indentation;
    MatcherType type;
    StringBuilder matcher;
    public Matcher(int indentation, MatcherType type, StringBuilder matcher) {
      this.indentation = indentation;
      this.type = type;
      this.matcher = matcher;
    }
  }
  private LinkedList<Matcher> stack = new LinkedList<>();

  /** Private constructor to be invoked from convert(String) only. */
  private Text2Drools() {
  }

  /**
   * Convert loan rules from FOLIO text format into a Drools file.
   * @param text String with a loan rules file in FOLIO syntax.
   * @return Drools file
   */
  public static String convert(String text) {
    Text2Drools text2drools = new Text2Drools();

    String [] lines = text.split("[\n\r]+");

    for (int i=0; i<lines.length; i++) {
      text2drools.convertLine(i, lines[i]);
    }

    return text2drools.drools.toString();
  }

  /**
   * Convert line to Drools syntax and add to drools.
   * Use lineNumber for error reporting.
   * @param lineNumber position of line in the input
   * @param line String to convert
   */
  private void convertLine(int lineNumber, String line) {
    int indentation = indentation(line);
    popObsoleteMatchers(indentation);
    LinkedList<String> tokens = parse(line);
    boolean firstToken = true;
    while (! tokens.isEmpty()) {
      String token = tokens.removeFirst();
      switch (token) {
      case "g":
        convertNames(indentation, MatcherType.PatronGroup, tokens);
        break;
      case "m":
        convertNames(indentation, MatcherType.ItemType, tokens);
        break;
      case "t":
        convertNames(indentation, MatcherType.LoanType, tokens);
        break;
      case "fallback-policy":
        if (! stack.isEmpty()) {
          throw new IllegalArgumentException(
              "fallback-policy must be at top level but it is indented in line " + lineNumber);
        }
        if (! firstToken) {
          throw new IllegalArgumentException(
              "fallback-policy must be the first token in line " + lineNumber);
        }
        drools.append("// fallback-policy\n");
        break;
      case "+":
        // nothing to be done
        break;
      case ":":
        String loanPolicy = tokens.removeFirst();
        if (! tokens.isEmpty()) {
          throw new IllegalArgumentException("Unexpected token after loan policy in line "
              + lineNumber + ": " + tokens.removeFirst());
        }
        drools.append("rule \"line ").append(lineNumber).append("\"\n");
        drools.append("  salience ").append(stack.size()).append("\n");
        drools.append("  when\n");
        stack.descendingIterator().forEachRemaining(matcher -> drools.append(matcher.matcher));
        drools.append("  then\n");
        drools.append("    loanPolicy.id = ");
        appendQuotedString(drools, loanPolicy);
        drools.append(";\n");
        drools.append("    drools.halt();\n");
        drools.append("end\n\n");
        break;
      case "#":
        tokens.clear();  // skip comment
        break;
      default:
        throw new IllegalArgumentException(
            "Expected f or m or t or fallback-policy or : or + or # but found \"" + token
            + "\" in line " + lineNumber + ": " + line);
      }

      firstToken = false;
    }
  }

  /**
   * Pop all matchers whose indentation is >= the parameter.
   * @param indentation - minimum indentation for deletion
   */
  private void popObsoleteMatchers(int indentation) {
    while (! stack.isEmpty()) {
      Matcher matcher = stack.peek();
      if (matcher.indentation < indentation) {
        return;
      }
      stack.pop();
    }
  }

  private void convertNames(int indentation, MatcherType type, LinkedList<String> tokens) {
    List<String> names = new ArrayList<>();

    while (! tokens.isEmpty()) {
      String peek = tokens.peekFirst();
      if (peek.equals("+") || peek.equals(":")) {
        break;
      }
      tokens.removeFirst();
      names.add(peek);
    }

    if (names.isEmpty()) {
      return;
    }

    StringBuilder matcher = new StringBuilder("    ").append(type).append("(id ");

    if (names.size() == 1) {
      matcher.append("== ");
      appendQuotedString(matcher, names.get(0));
    } else {
      matcher.append("in (");
      boolean first = true;
      for (String name : names) {
        if (first) {
          first = false;
        } else {
          matcher.append(", ");
        }
        appendQuotedString(matcher, name);
      }
      matcher.append(")");
    }
    matcher.append(")\n");
    stack.push(new Matcher(indentation, type, matcher));
  }

  /**
   * Append name as quoted String to sb.
   * @param sb - where to add
   * @param name - the String to quote
   */
  private void appendQuotedString(StringBuilder sb, String name) {
    sb.append('"');
    sb.append(StringEscapeUtils.escapeJava(name));
    sb.append('"');
  }

  /**
   * Indentation of s.
   * @param s - String
   * @return number of space characters at the beginning of s
   */
  private static int indentation(String s) {
    int i;
    for (i=0; i<s.length(); i++) {
      if (s.charAt(i) != ' ') {
        break;
      }
    }
    return i;
  }

  /**
   * Parse line into tokens.
   * @param line
   * @return array of tokens.
   */
  public static LinkedList<String> parse(String line) {
    String [] tokenArray = new StrTokenizer(line)
      .setDelimiterMatcher(StrMatcher.spaceMatcher())
      .setQuoteChar('"')
      .getTokenArray();
    LinkedList<String> token = new LinkedList<>(Arrays.asList(tokenArray));

    // make colon at end of token before circulation policy a token of its own.
    // "t" "foobar:" "my-circulation-policy"
    // becomes
    // "t" "foobar" ":" "my-circulation-policy"

    if (token.size() < 2) {
      return token;
    }
    String beforeLast = token.get(token.size()-2);
    if (! beforeLast.endsWith(":") || beforeLast.equals(":")) {
      return token;
    }
    String last = token.removeLast();
    beforeLast = token.removeLast();
    // remove : from end of String
    beforeLast = beforeLast.substring(0, beforeLast.length()-1);
    token.add(beforeLast);
    token.add(":");
    token.add(last);
    return token;
  }
}
