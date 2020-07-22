package com.craftinginterpreters.lox;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;
import sun.tools.tree.VarDeclarationStatement;

import static com.craftinginterpreters.lox.TokenType.*;

/*
program     → declaration* EOF ;

declaration → varDecl
          | statement ;

varDecl → "var" IDENTIFIER ( "=" expression )? ";" ;

statement → exprStmt | printStmt ;
exprStmt  → expression ";" ;
printStmt → "print" expression ";" ;

primary → "true" | "false" | "nil"
        | NUMBER | STRING
        | "(" expression ")"
        | IDENTIFIER ;

expression → literal
           | unary
           | binary
           | grouping ;
           | assignment ;

assignment → IDENTIFIER "=" assignment
           | equality ;

literal    → NUMBER | STRING | "false" | "true" | "nil" ;
grouping   → "(" expression ")" ;
unary      → ( "-" | "!" ) expression ;
binary     → expression operator expression ;
operator   → "==" | "!=" | "<" | "<=" | ">" | ">="
           | "+"  | "-"  | "*" | "/" ;
*/

class Parser {
    private static class ParseError extends RuntimeException{};
    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            //statements.add(statement());
            statements.add(declaration());
        }

        return statements;
    }

    // Expr parse() {
    //     try {
    //         return expression();
    //     } catch (ParseError error) {
    //         return null;
    //     }
    // }

    private Token advance(){
        if(!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private ParseError error(Token token, String message) {
        //returns it instead of throwing because we want to let the caller decide whether to unwind or not
        Lox.error(token, message);
        return new ParseError();
    }

    private Token consume(TokenType type, String message) {//若当前token满足条件则advance并返回当前的token，否则报错
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private void synchronize() {//处理出错后的discard和重启parse过程
        advance();

        while (!isAtEnd()) {
            //Discard Tokens Until we’re right at the beginning of the next statement
            if (previous().type == SEMICOLON)
                return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

    private boolean check(TokenType type){
        if(isAtEnd()) return false;
        return peek().type==type;
    }

    //...代表可变参数，后面传入的参数是一个list或空
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    /********************************************* Parse Expression **************************************************/

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        // 以下为没有考虑到parser在parse l-value时的不可预知性
        // Token name = peek();
        // if(match(INDENTIFIER, EQUAL)){
        //     Expr value = assignment();
        //     return new Expr.Assign(name,value);
        // }
        // return equality();

        Expr expr = equality();
        if (match(EQUAL)) {
            Token equals = previous();
            //不使用while，即不允许多重赋值
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr equality() {
        // equality → comparison ( ( "!=" | "==" ) comparison )*
        Expr expr = comparison();

        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }
        
        // 完成对语法三元表达式 ?: 的支持
        // if(match(QES_MARK)){
        //     Expr left=comparison();
        //     if(not match(types))
        // }
        return expr;
    }

    private Expr comparison() {
        // comparison → addition ( ( ">" | ">=" | "<" | "<=" ) addition )*
        Expr expr = addtion();

        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = addtion();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr addition() {
        //addition → multiplication ( ( "-" | "+" ) multiplication )* 
        Expr expr = multiplication();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = multiplication();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr multiplication() {
        //multiplication → unary ( ( "/" | "*" ) unary )*
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary() {
        //unary → ( "!" | "-" ) unary | primary
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Unary(operator, right);
        }

        return primary();
    }

    private Expr primary() {
        //primary → NUMBER | STRING | "false" | "true" | "nil" | "(" expression ")" | IDENTIFIER | block
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);
        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            while (match(COMMA)){
                expr=expression();
            }
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        if(match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_BRACE)) return new Stmt.Block(block());

        throw error(peek(), "Expect expression.");
    }

    /********************************************* Parse Statement **************************************************/

    /*
    program   → statement* EOF ;

    declaration → varDecl
                | statement ;

    varDecl → "var" IDENTIFIER ( "=" expression )? ";" ;

    statement → exprStmt
            | printStmt ;

    exprStmt  → expression ";" ;
    printStmt → "print" expression ";" ;
    */

    private Stmt declaration() {
        try{
            if(match(VAR)) return varDeclarationStatement();
            return statement();
        } catch(ParseError error){
            synchronize();
            return null;
        }
        
    }

    private List<Stmt> block(){
        List<Stmt> statements = new ArrayList<>();
        while(!isAtEnd()&&!check(RIGHT_BRACE)){
            statements.add(declaration());
        }
        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    } 

    private Stmt statement() {
        if (match(PRINT)) return printStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        if (match(VAR)) return varDeclarationStatement();
        return expressionStatement();
    }

    private Stmt varDeclarationStatement() {
        Token name = consume(IDENTIFIER, "Expect variable name.");
        Expr initializer = null;//变量的声明不要放在if block中
        if(match(EQUAL)){
            initializer = expression();
        }
        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);

    }

    private Stmt printStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(expr);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Expression(expr);
    }
}