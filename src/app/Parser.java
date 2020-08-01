package com.craftinginterpreters.lox;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;
import sun.tools.tree.VarDeclarationStatement;
import sun.tools.tree.WhileStatement;

import static com.craftinginterpreters.lox.TokenType.*;

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

        Expr expr = or();
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

    private Expr or() {
        Expr expr = and();
        while(match(OR)){
            Token operator = previous();
            Expr tmp = and();
            expr = new Expr.Logic(expr, operator, tmp);
        }
        return expr;
    }

    private Expr and() {
        Expr expr = equality();
        while(match(AND)) {
            Token operator = previous();
            Expr tmp = equality();
            expr = new Expr.Logic(expr, operator, tmp);
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
        Expr expr = addition();

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
        if (match(IF)) return ifStatement();
        if (match(WHILE)) return whileStatement();
        if (match(FOR)) return forStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        if (match(VAR)) return varDeclarationStatement();
        return expressionStatement();
    }

    private Stmt ifStatement(){
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");
        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if(match(ELSE)) elseBranch = statement();//此处后一个else直接绑定前一个if，除非在有 } 的情况下
        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after while condition.");
        Stmt body = statement();
        return new Stmt.While(condition,body);
    }

    // 新建一个for ast node的parse方法
    // private Stmt forStatement() {
    //     consume(LEFT_PAREN, "Expect '(' after 'for'.");
    //     Stmt initializer = statement();
    //     Expr condition = expression();
    //     consume(LEFT_PAREN, "Expect ';' after 'for condition'.");
    //     Expr increment = expression();
    //     consume(RIGHT_PAREN, "Expect ')' after for condition.");
    //     Stmt body = statement();
    //     return new Stmt.For(initializer,condition,increment,body);
    // }

    // 将for解析为whileStmt节点，实现desugaring
    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");
        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        Stmt body = statement();

        if (condition == null) condition = new Expr.Literal(true);
        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        }
        body = new Stmt.While(condition, body);

        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer,body));
        }
              
        return body;
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