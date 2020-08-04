
package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// import jdk.nashorn.internal.parser.TokenType;
// import sun.tools.java.Identifier;

import static com.craftinginterpreters.lox.TokenType.*;

class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0; //每次匹配的开始位置
    private int current = 0; //当前匹配的偏移
    private int line = 1;
    private static final Map<String,TokenType> keywords;

    static{
        keywords = new HashMap<>();
        keywords.put("and", AND);
        keywords.put("class", CLASS);
        keywords.put("else", ELSE);
        keywords.put("false", FALSE);
        keywords.put("for", FOR);
        keywords.put("fun", FUN);
        keywords.put("if", IF);
        keywords.put("nil", NIL);
        keywords.put("or", OR);
        keywords.put("print", PRINT);
        keywords.put("return", RETURN);
        keywords.put("super", SUPER);
        keywords.put("this", THIS);
        keywords.put("true", TRUE);
        keywords.put("var", VAR);
        keywords.put("while", WHILE);
    }

    Scanner(String source) {
        this.source = source;
    }

    List<Token> scanTokens() {
        while (!isAtEnd()) {
            // We are at the beginning of the next lexeme.
            start = current;
            scanToken();
        }

        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    private boolean isAtEnd() {
        return current >= source.length();
      }
    
    private char advance() {
        current++;
        return source.charAt(current - 1);
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

    private boolean match(char expected) {
        if(isAtEnd()) return false;
        if(source.charAt(current) != expected) return false;

        current++;
        return true;
    }

    private char peek(){//peek current位置的字符
        if(isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private char peekNext(){//peek current+1 位置的字符
        if(current+1>=source.length()) return '\0';
        return source.charAt(current+1);
    }

    private void String() {//处理string的函数
        while (peek() != '"' && isAtEnd()) {
            if (peek() == '\n')
                line++;
            advance();
        }

        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }

        // 跳出最后一个‘“’
        advance();

        String value = source.substring(start + 1, current - 1);
        addToken(STRING, value);
    }

    private boolean isDigit(char c){
        return c>='0'&&c<='9';
    }

    private boolean isAlpha(char c){
        return c>='a'&&c<='z'||c>='A'&&c<='Z'||c=='_';
    }

    private boolean isAlphaNumeric(char c){
        return isAlpha(c)||isDigit(c);
    }

    private void number(){//处理数字
        while(isDigit(peek())) advance();
        if(peek()=='.') {
            advance();
            while(isDigit(peek())) advance();
        }
        addToken(NUMBER, Double.parseDouble(source.substring(start,current)));
    }

    private void identifier(){
        while (isAlphaNumeric(peek()))
            advance();
        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if (type == null)
            type = IDENTIFIER;
        addToken(type);
    }

    private void comment(){
        while(peek()!='*'&&peekNext()!='/'){
            if(peek()=='\n') line++;
        }
        //跳出*/
        advance();
        advance();
    }

    private void scanToken() {
        char c = advance();
        //相当于一个DFA
        switch (c) {
            //数值运算
            case '(': addToken(LEFT_PAREN); break;
            case ')': addToken(RIGHT_PAREN); break;
            case '{': addToken(LEFT_BRACE); break;
            case '}': addToken(RIGHT_BRACE); break;
            case ',': addToken(COMMA); break;
            case '.': addToken(DOT); break;
            case '-': addToken(MINUS); break;
            case '+': addToken(PLUS); break;
            case ';': addToken(SEMICOLON); break;
            case '*': addToken(STAR); break;
            case '!': addToken(match('=') ? BANG_EQUAL : BANG); break;
            case '=': addToken(match('=') ? EQUAL_EQUAL : EQUAL); break;
            case '<': addToken(match('=') ? LESS_EQUAL : LESS); break;
            case '>': addToken(match('=') ? GREATER_EQUAL : GREATER); break;
            //逻辑运算
            case 'o': if(peek()=='r') addToken(OR);break;
            case 'a':
            //注释
            case '/':
                if(match('/')){
                    while(peek()!='\n'&&!isAtEnd()) advance(); 
                }
                else if(match('*')){
                    comment();
                }
                else addToken(SLASH);
                break;
            //空字符，换行字符
            case ' ':break;
            case '\r':break;
            case '\t':break;
            case '\n':line++;break;
            //string
            case '"':String();break;
            default: 
            if(isDigit(c)) number();
            else if(isAlpha(c)) identifier();
            else
            Lox.error(line, "Unexcepted character."); break;//每次scan都会遍历全部的长度，每次遇到不规范的字符报一次错
        }
    }
    
    public static void main(String[] args) {
        String source= "nt i = 0;/n i=i+1; //jiuij";
        Scanner s=new Scanner(source);
        s.scanTokens();
        for (Token token : s.tokens) {
            System.out.println(token.toString());
        }
    }
}

