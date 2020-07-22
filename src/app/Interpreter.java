package com.craftinginterpreters.lox;

import java.util.List;

import com.craftinginterpreters.lox.Expr.*;
import com.craftinginterpreters.lox.Stmt.*;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {//该类implement Expr&Stmt 类中定义的visitor接口
    //全局环境
    private Environment environment = new Environment();

    //外部调用接口
    void interpret(List<Stmt> statements){
        try{
            for(Stmt statement : statements){
                if(statement instanceof Stmt.Expression){//实现prompt中当输入的代码为expression时可以打印结果
                    Object value = evaluate(statement.expression);
                    System.out.println(stringify(value));
                }
                else execute(statement);
            }
        } catch (RuntimeError error){
            Lox.runtimeError(error);
        }
    }

    // void interpret(Expr expression) {
    //     try {
    //         Object value = evaluate(expression);
    //         System.out.println(stringify(value));
    //     } catch (RuntimeError error) {
    //         Lox.runtimeError(error);
    //     }
    // }

    //使用RUBY的语法，除了null和false对象都为true
    
    private Object evaluate(Expr expression) {//调用expr子类的accept计算值
        return expression.accept(this);
    }

    private void execute(Stmt statement) {
        statement.accept(this);
    }

    private boolean isTruthy(Object object) {
        if (object == null) return false;
        if (object instanceof Boolean) return (boolean)object;
        return true;
    }
    
    private boolean isEqual(Object a, Object b) {
        // nil is only equal to nil.
        if (a == null && b == null) return true;
        if (a == null) return false;
    
        return a.equals(b);
    }

    private String stringify(Object object) {//将objecr转化为string对象输出
        if (object == null)
            return "nil";

        // Hack. Work around Java adding ".0" to integer-valued doubles.
        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double)
            return;

        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    /********************************************* Visit Expression **************************************************/

    @Override
    public Object visitAssignExpr(Assign expr) {
        Object value = evaluate(expr.expression);
        Token name = expr.name;
        environment.assign(name,value);
        //assignment is an expression that can be nested inside other expressions 如 print a=2; //2
        return value;
    }

    @Override
    public Object visitVariableExpr(Variable expr) {
        return environment.get(expr.name);
    }

    @Override
    public Object visitLiteralExpr(Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitGroupingExpr(Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitUnaryExpr(Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right); //直接return了所以不用break
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double) right;
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitBinaryExpr(Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);
        /*
        TODO 1.增加不同类型的比较
             2.增加number和数字的相加
             3.增加➗0的处理
        */
        switch (expr.operator.type) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double) left > (double) right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left >= (double) right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left < (double) right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left <= (double)right;
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left - (double) right;
            case PLUS://string和number都有plus操作 #TODO 增加数字+字符串自动转化为字符串的功能
                if (left instanceof Double && right instanceof Double) {
                  return (double)left + (double)right;
                } 
                if (left instanceof String && right instanceof String) {
                  return (String)left + (String)right;
                }
                throw new RuntimeError(expr.operator,
                    "Operands must be two numbers or two strings.");
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double) left / (double) right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double) left * (double) right;
            case BANG_EQUAL: return !isEqual(left, right);
            case EQUAL_EQUAL: return isEqual(left, right);
        }

        // Unreachable.
        return null;
    }

    /********************************************* Visit Statement **************************************************/
    
    @Override
    public Void visitBlockStmt(Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));//执行block时需要新建一个scope
        return null;
    }

    private void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;//储存当前Interpreter的env
        try{
            this.environment = environment;//用新的scope覆盖当前的Interpreter的env
            for(Stmt statement : statements){
                execute(statement);
            }
        } catch (RuntimeError error){
            Lox.runtimeError(error);
        } finally {
            this.environment = previous;//恢复之前的env
        }
    }

    @Override
    public Void visitExpressionStmt(Expression stmt){
        evaluate(stmt.expression);
        return null;//#Question 这里为啥返回一个null，不指定接口的返回形式可以吗？不可以，因为之前的visitor定义了返回类型为Void，即必须要返回一个东西。这是因为返回后的值可能会经历一些判断，不能是空。
    }

    @Override
    public Void visitPrintStmt(Print stmt){
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitVarStmt(Var stmt){
        // 错误示范
        // Object value = evaluate(stmt.expression);
        // environment.define(stmt.name,value);
        // return null;
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme, value);
        return null;
    }

}