package com.craftinginterpreters.lox;

import java.util.List;

import java.util.ArrayList;
import java.util.Arrays;

// import jdk.nashorn.internal.parser.Token;
// import jdk.nashorn.internal.parser.TokenType;
// import sun.tools.tree.VarDeclarationStatement;
// import sun.tools.tree.WhileStatement;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {
    private static class ParseError extends RuntimeException{
        /**
         *
         */
        private static final long serialVersionUID = 1L;
    };
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
                default: break;
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
            } else if(expr instanceof Expr.Get) {
                Token name = ((Expr.Get) expr).name;
                Expr object = ((Expr.Get) expr).object;
                return new Expr.Set(object, name, value);
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
            Expr right = addition();
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
            return new Expr.Unary(operator, right);
        }
        return call();
    }

    private Expr call() {
        Expr expr = primary();
        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(DOT)) {
                Token name = consume(IDENTIFIER, "Expect property name after '.'.");
                expr = new Expr.Get(expr, name);
            } else
                break;
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do{
                if (arguments.size() >= 255) {
                    error(peek(), "Cannot have more than 255 arguments.");
                }
                arguments.add(expression());
            } while(match(COMMA));
        }
        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");//Question: 这里的paren作何用？在其他的callabel对象起作用吗？
        return new Expr.Call(callee,paren,arguments);
    }

    private Expr primary() {
        //primary → NUMBER | STRING | "false" | "true" | "nil" | "(" expression ")" | IDENTIFIER 
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);
        if (match(THIS)) return new Expr.This(previous());
        if (match(SUPER)) {//TODO: 将继承关系中的函数调用从super转化为inner的方式
            Token keyword = previous();
            consume(DOT, "Except '.' after super");
            Token method = consume(IDENTIFIER, "Expect superclass method name.");
            return new Expr.Super(keyword, method);
        }
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

        throw error(peek(), "Expect expression.");
    }

    /********************************************* Parse Statement **************************************************/

    private Stmt declaration() {
        try{
            if(match(CLASS)) return classDeclaration();
            if(match(FUN)) return function("function");
            if(match(VAR)) return varDeclaration();
            return statement();
        } catch(ParseError error){
            synchronize();
            return null;
        }
        
    }

    private Stmt classDeclaration(){
        Token name = consume(IDENTIFIER, "Except class name");
        Expr.Variable superclass = null;
        if(match(LESS)){//使用<作为继承的符号
            consume(IDENTIFIER, "Expect superclass name.");
            superclass = new Expr.Variable(previous());
        }
        List<Stmt.Function> methods = new ArrayList<>();
        consume(LEFT_BRACE, "Except '{' before class body");
        while(!match(RIGHT_BRACE)&&!isAtEnd()){
            methods.add(function("method"));
        }
        consume(RIGHT_BRACE, "Expect '}' after class body.");
        return new Stmt.Class(name, superclass,methods);
    }

    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");
        Expr initializer = null;//变量的声明不要放在if block中
        if(match(EQUAL)){
            initializer = expression();
        }
        consume(SEMICOLON, "Expect ';' after variable declaration.");
        return new Stmt.Var(name, initializer);

    }

    private Stmt.Function function(String kind) {//kind是用来干嘛的？在定义函数时，kind=function
        //TODO: 1.Add anonymous function syntax
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
        List<Token> parameters = new ArrayList<>();
        if(!check(RIGHT_PAREN)){
            do{
                if (parameters.size() >= 255) {
                    error(peek(), "Cannot have more than 255 parameters.");
                }

                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");
        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name, parameters, body);
    }

    private Stmt statement() {
        if (match(PRINT)) return printStatement();
        if (match(IF)) return ifStatement();
        if (match(WHILE)) return whileStatement();
        if (match(FOR)) return forStatement();
        if (match(LEFT_BRACE)) return new Stmt.Block(block());
        if (match(RETURN)) return returnStatement();
        return expressionStatement();
    }

    private List<Stmt> block(){
        List<Stmt> statements = new ArrayList<>();
        while(!isAtEnd()&&!check(RIGHT_BRACE)){
            statements.add(declaration());
        }
        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
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

    // 将for解析为blockStmt节点，实现desugaring，将varDeclaration和increment的stmt拼接到主体的block中，完成局部环境的构建
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

    private Stmt printStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(expr);
    }

    private Stmt returnStatement() {
        Token keyword = previous();//处理没有return的情况，和python一样默认返回null
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }

        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Expression(expr);
    }
}