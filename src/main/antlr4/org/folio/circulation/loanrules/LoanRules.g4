grammar LoanRules;

/*
 * The INDENT and DEDENT code (the code in @lexer::members) has been taken from
 * https://github.com/wevrem/wry/blob/master/grammars/Dent.g4
 * and has this author and copyright notice:
 *
 * Author: Mike Weaver
 *
 * The MIT License
 *
 * Copyright (c) 2017 Mike Weaver
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

tokens { INDENT, DEDENT }

@lexer::members {
  private boolean pendingDent = true;   // Setting this to true means we start out in `pendingDent`
    // state and any whitespace at the beginning of the file will trigger an INDENT, which will
    // probably be a syntax error, as it is in Python.
  private int indentCount = 0;
  private java.util.LinkedList<Token> tokenQueue = new java.util.LinkedList<>();
  private java.util.Stack<Integer> indentStack = new java.util.Stack<>();
  private Token initialIndentToken = null;
  private int getSavedIndent() { return indentStack.isEmpty() ? 0 : indentStack.peek(); }

  private CommonToken createToken(int type, String text, Token next) {
    CommonToken token = new CommonToken(type, text);
    if (null != initialIndentToken) {
      token.setStartIndex(initialIndentToken.getStartIndex());
      token.setLine(initialIndentToken.getLine());
      token.setCharPositionInLine(initialIndentToken.getCharPositionInLine());
      token.setStopIndex(next.getStartIndex()-1);
    }
    return token;
  }

  @Override
  public Token nextToken() {
    // Return tokens from the queue if it is not empty.
    if (!tokenQueue.isEmpty()) { return tokenQueue.poll(); }

    // Grab the next token and if nothing special is needed, simply return it.
    Token next = super.nextToken();
    // NOTE: This would be the appropriate spot to count whitespace or deal with NEWLINES, but it is
    // already handled with custom actions down in the lexer rules.
    if (pendingDent && null == initialIndentToken && NEWLINE != next.getType()) { initialIndentToken = next; }
    if (null == next || HIDDEN == next.getChannel() || NEWLINE == next.getType()) { return next; }

    // Handle EOF; in particular, handle an abrupt EOF that comes without an immediately preceding NEWLINE.
    if (next.getType() == EOF) {
      indentCount = 0;
      // EOF outside of `pendingDent` state means we did not have a final NEWLINE before the end of file.
      if (!pendingDent) {
        initialIndentToken = next;
        tokenQueue.offer(createToken(NEWLINE, "NEWLINE", next));
      }
    }

    // Before exiting `pendingDent` state we need to queue up proper INDENTS and DEDENTS.
    while (indentCount != getSavedIndent()) {
      if (indentCount > getSavedIndent()) {
        indentStack.push(indentCount);
        tokenQueue.offer(createToken(LoanRulesParser.INDENT, "INDENT" + indentCount, next));
      } else {
        indentStack.pop();
        tokenQueue.offer(createToken(LoanRulesParser.DEDENT, "DEDENT"+getSavedIndent(), next));
      }
    }
    pendingDent = false;
    tokenQueue.offer(next);
    return tokenQueue.poll();
  }
}

loanRulesFile
  : NEWLINE* 'priority' ':' 'first-line' ( NEWLINE | statement )*  NEWLINE* fallbackpolicy+ noStatementAfterFallbackPolicy EOF
  | NEWLINE* 'priority' ':' priority     NEWLINE* fallbackpolicy+   ( NEWLINE | statement )* EOF
  ;

priority
  : 'last-line'                                                # lastLinePriorities
  | criteriumPriority ',' linePriority                         # twoPriorities
  | criteriumPriority ',' criteriumPriority ',' linePriority   # threePriorities
  | sevenCriteriumLetters                                      # defaultPriorities
  ;

criteriumPriority
  : 'criterium' '(' sevenCriteriumLetters ')'
  | 'number-of-criteria'
  ;

linePriority
  : 'first-line'
  | 'last-line'
  ;

sevenCriteriumLetters
  : CRITERIUM_LETTER (','? CRITERIUM_LETTER)*
  ;

noStatementAfterFallbackPolicy
  :
  | { notifyErrorListeners("For 'priority: first-line' the fallback-policy must be after the last rule."); }  statement
  ;

statement
  : simpleStatement
  | blockStatements
  ;

simpleStatement : expr NEWLINE ;

blockStatements : expr NEWLINE indent statement+ dedent ;

indent : INDENT ;

dedent : DEDENT ;

expr: criterium ('+' criterium)* policies?
    ;

criterium : CRITERIUM_LETTER
            (   all
              | NAME+
              | NAME+ '!'        { notifyErrorListeners("A '!' must precede each name or no name."); } (NAME|'!')*
              | ('!' NAME)+
              | ('!' NAME)+ NAME { notifyErrorListeners("A '!' must precede each name or no name."); } (NAME|'!')*
              |                  { notifyErrorListeners("Name missing."); }
            )
          ;

all : 'all';

fallbackpolicy : 'fallback-policy' policies NEWLINE
               ;

policies : ':' policy+
         | ':' { notifyErrorListeners("Policy missing after ':'"); }
         ;

policy : POLICY_TYPE 
          ( NAME
          | { notifyErrorListeners("Name missing."); }
          )
       ;

CRITERIUM_LETTER: [tabcsmg];

POLICY_TYPE: [lrn];

NAME: [0-9a-zA-Z-]+;

LINE_COMMENT : ( '#' | '/' ) ~[\r\n]* -> channel(HIDDEN) ;

NEWLINE : ( '\r'? '\n' | '\r' ) { if (pendingDent) {
                                    setChannel(HIDDEN);
                                  }
                                  pendingDent = true;
                                  indentCount = 0;
                                  initialIndentToken = null;
                                } ;

WS : ' '+ { setChannel(HIDDEN); if (pendingDent) { indentCount += getText().length(); } } ;

TAB : '\t' { if (true) {  // "if (true)" prevents a compile error "unreachable code"
               throw new org.folio.circulation.loanrules.LoanRulesException(
                  "Tabulator character is not allowed, use spaces instead.",
                   getLine(), getCharPositionInLine()+1);
             }
           }
    ;
