package com.craftinginterpreters.lox;

import java.util.List;
import java.util.ArrayList;

import com.craftinginterpreters.lox.Expr.*;
import com.craftinginterpreters.lox.Stmt.*;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {//该类implement Expr&Stmt 类中定义的visitor接口
    //全局环境
    final Environment globals = new Environment();
    private Environment environment = globals;

    //构造函数，在globals中加入一个名为clock的函数对象，在构造函数中的函数称为natives functions 即内建函数
    Interpreter() {
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double) System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() {
                return "<native fn>";
            }
        });
    }

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
    public Object visitLogicExpr(Logic expr) {
        Object left = evaluate(expr.left);
        // if(expr.operator.type==AND){
        //     if(!isTruthy(value)) return false;
        //     Object right = evaluate(expr.right);
        //     return isTruthy(left)&&isTruthy(right);
        // } 
        // else {
        //     if(isTruthy(left)) return true;
        //     Object right = evaluate(expr.right);
        //     return isTruthy(left)||isTruthy(right);
        // }
        
        //妙啊，此处选择返回真实值
        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left))
                return left;
        } else {
            if (!isTruthy(left))
                return left;
        }

        return evaluate(expr.right);
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
    public Object visitCallExpr(Call expr) {
        Object callee = evaluate(expr.callee);
        if (!(callee instanceof LoxCallable)) {// 运行时检查并抛出Exception
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");
        }
        List<Object> arguments = new ArrayList<>();//argument是实际传入的Object
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        LoxCallable function = (LoxCallable) callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren,
                    "Expected " + function.arity() + " arguments but got " + arguments.size() + ".");
        }
        return function.call(this, arguments);
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
    public Void visitFunctionStmt(Function stmt) {//此处并非调用函数，而是定义函数的过程，将函数对象新增到环境中
        LoxFunction function = new LoxFunction(stmt, environment);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

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
    public Void visitIfStmt(If stmt) {
        if(isTruthy(evaluate(stmt.condition))){
            execute(stmt.thenBranch);
        }
        else if(stmt.elseBranch != null){
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Viod visitWhileStmt(While stmt) {
        while(isTruthy(evaluate(stmt.condition))){
            execute(stmt.body);
        }
        return null;
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
    public Void visitReturnStmt(Return stmt) {
        Object value  = null;
        if(stmt.value!=null){
            value = evaluate(stmt.value);
        }
        throw new Return(value);//使用runtimeException来跳出当前的block
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