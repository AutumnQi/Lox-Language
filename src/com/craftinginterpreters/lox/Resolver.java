package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import com.craftinginterpreters.lox.Expr.This;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {// 此处和interpreter不同，expr的visitor
    private final Interpreter interpreter;
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();// TODO:
                                                                     // 再加一个状态来反应变量是否在scope中被使用，scope结束时未使用则提出warning
                                                                     // Question：为什么这里使用String而非Expr等？Ans：因为有this
    private FunctionType currentFunction = FunctionType.NONE;// 用来检测一些不符合规范的语句，如出现在function body外的return语句
    private ClassType currentClass = ClassType.NONE;//用来检测this的非法使用

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    private enum FunctionType {
        NONE, FUNCTION, METHOD, INITIALIZER
    }
    private enum ClassType {
        NONE, CLASS
    }

    private void beginScope() {
        scopes.push(new HashMap<String, Boolean>());
    }

    private void endScope() {
        scopes.pop();
    }

    private void resolve(Expr expression) {// 不返回value，调用在resolve中定义的接口函数
        expression.accept(this);
    }

    private void resolve(Stmt statement) {
        statement.accept(this);// this指的是visitor
    }

    void resolve(List<Stmt> statements) {// 外部调用接口
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    private void resolveLocal(Expr expr, Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.lexeme)) {// 找到包含当前variable最近的一个scope
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
        // Not found. Assume it is global.
    }

    private void resolveFunction(Stmt.Function stmt, FunctionType type) {
        FunctionType enclosingFunction = currentFunction;// 暂存当前的FunctionType
        currentFunction = type;
        beginScope();
        for (Token param : stmt.params) {
            declare(param);
            define(param);
        }
        resolve(stmt.body);
        endScope();
        currentFunction = enclosingFunction;
    }

    private void declare(Token name) {
        if (scopes.isEmpty())
            return;
        Map<String, Boolean> scope = scopes.peek();
        scope.put(name.lexeme, false); // false表示该变量尚未初始化
    }

    private void define(Token name) {
        if (scopes.isEmpty())
            return;
        Map<String, Boolean> scope = scopes.peek();
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name, "Variable with this name already declared in this scope.");
        }
        scope.put(name.lexeme, true);
    }

    /**********************************************
     * Visit Expression
     **************************************************/
    @Override
    public Void visitThisExpr(This expr) {
        if(currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Cannot use 'this' outside of a class.");
            return null;
        }
        resolveLocal(expr, expr.keyword);//向上查找this指代的instance所在的scope
        return null;
    }
    
     @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.object);
        resolve(expr.value);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);
        for (Expr arg : expr.arguments) {
            resolve(arg);
        }
        return null;
    }

    @Override
    public Void visitLogicExpr(Expr.Logic expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        if (!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) { // Question:false和Boolean.FALSE有什么区别？
                                                                                         // 这种情况仅发生在visitVarStmt中已经declare完，resolve
                                                                                         // initializer时才会发生
            Lox.error(expr.name, "Cannot read local variable in its own initializer.");// 在variable没有初始化时调用则报错，但不中断，继续执行
        }
        resolveLocal(expr, expr.name);
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);// 判断要赋的值是否已经初始化
        resolveLocal(expr, expr.name);// 判断被赋值的variable是否已经被初始化，否则追溯到最近的一个scope
        return null;
    }

    /**********************************************
     * Visit Statement
     **************************************************/

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {// 显然每个function有自己的scope
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);// 访问完此节点后，在当前的scope中该变量已被赋值
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null)
            resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Cannot return from top-level code.");
        } else if(currentFunction == FunctionType.INITIALIZER) {
            Lox.error(stmt.keyword, "Cannot return from init function.");
        }
        if (stmt.value != null)
            resolve(stmt.value);
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;
        declare(stmt.name);
        define(stmt.name);// 和function一样不存在初始化的过程，可以直接调用 Qusetiong：？？？class不用实例化吗？
        beginScope();
        scopes.peek().put("this",Boolean.TRUE);
        for (Stmt.Function method : stmt.methods) {
            FunctionType declaration = FunctionType.METHOD;
            if (method.name.lexeme.equals("init")) {
                declaration = FunctionType.INITIALIZER;
              }
            resolveFunction(method, declaration);
        }
        endScope();
        currentClass = enclosingClass;
        return null;
    }
    
}