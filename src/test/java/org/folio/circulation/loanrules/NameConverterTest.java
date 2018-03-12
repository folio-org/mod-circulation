package org.folio.circulation.loanrules;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.junit.Test;

public class NameConverterTest {
  private Map<String,Map<String,String>> name2uuid = new HashMap<>();
  private Map<String,Map<String,String>> uuid2name = new HashMap<>();
  {
    Map<String,String> m = new HashMap<>();
    m.put("book", "123");
    m.put("dvd", "987");
    name2uuid.put("m", m);
    Map<String,String> policy = new HashMap<>();
    policy.put("fallback", "0");
    policy.put("policy-x", "1");
    name2uuid.put("policy", policy);

    for (Entry<String,Map<String,String>> typeMap : name2uuid.entrySet()) {
      String type = typeMap.getKey();
      Map<String,String> map = typeMap.getValue();
      Map<String,String> inverted = new HashMap<>();
      for (Entry<String,String> entry : map.entrySet()) {
        inverted.put(entry.getValue(), entry.getKey());
      }
      uuid2name.put(type, inverted);
    }
  }

  private static final String NL = "\n";

  private String loanRulesNames =
      "fallback-policy: fallback"
      + NL + "m book withoutreplacement dvd + t withoutreplacement: policy-x"
      + NL;
  private String loanRulesUuids =
      "fallback-policy: 0"
      + NL + "m 123 withoutreplacement 987 + t withoutreplacement: 1"
      + NL;

  private String convertNames(String loanRules, Map<String,Map<String,String>> replacements) {
    CharStream inputStream = CharStreams.fromString(loanRules);
    LoanRulesLexer lexer = new LoanRulesLexer(inputStream);
    CommonTokenStream tokenStream = new CommonTokenStream(lexer);
    LoanRulesParser parser = new LoanRulesParser(tokenStream);
    ParseTree parseTree = parser.loanRulesFile();

    ParseTreeWalker walker = new ParseTreeWalker();
    NameConverter nameConverter = new NameConverter(tokenStream, replacements);
    walker.walk(nameConverter, parseTree);
    return nameConverter.getText();
  }

  @Test
  public void replaceNames() {
    assertEquals(convertNames(loanRulesNames, name2uuid), loanRulesUuids);
  }

  @Test
  public void replaceUuids() {
    assertEquals(convertNames(loanRulesUuids, uuid2name), loanRulesNames);
  }
}
