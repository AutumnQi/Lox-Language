package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

import com.craftinginterpreters.lox.Expr.*;
import com.craftinginterpreters.lox.Stmt.*;

class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {//该类implement Expr&Stmt 类中定义的visitor接口
    //全局环境
    final Environment globals = new Environment();
    private Environment environment = globals;//当前运行的环境
    private final Map<Expr, Integer> locals = new HashMap<>();//管理当前的局部变量 通过local中的distance，从变量最近一次被声明/赋值的env中进行取值，包含variable和this
                                                            // Question：env不是一脉相承的吗，为什么要搞这么麻烦？Ans：在进入block后新建的env内无数据的复制，想要查找enclsing内的数据需要一个distance
                                                            //Question：这里为什么要用Expr而不是String？Ans：String可能存在重名?

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

    //使用RUBY的语法，除了null和false对象都为true
    
    private Object evaluate(Expr expression) {//调用expr子类的accept计算值
        return expression.accept(this);
    }

    private void execute(Stmt statement) {
        statement.accept(this);
    }

    void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    private Object lookUpVariable(Token name, Expr expr) {
        Integer distance = locals.get(expr);
        if (distance != null) {
            return environment.getAt(distance, name.lexeme);
        } 
        return globals.get(name);
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
        Object value = evaluate(expr.value);
        Token name = expr.name;
        Integer distance = locals.get(expr);
        if(distance!=null){
            environment.assignAt(distance, name, value);
        }
        else environment.assign(name,value);
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
        return lookUpVariable(expr.name, expr);
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
            default: break;
        }

        // Unreachable.
        return null;
    }

    @Override
    public Object visitCallExpr(Call expr) {
        Object callee = evaluate(expr.callee);//在environment中寻找function对象
        if (!(callee instanceof LoxCallable)) {// 运行时检查并抛出Exception
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");
        }
        List<Object> arguments = new ArrayList<>();//argument是实际传入的Object
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }
        //检查参数数量是否匹配，TODO 4.有什么方式可以避免检查参数数量？Smalltalk语言为什么没有这种问题？
        LoxCallable function = (LoxCallable) callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren,
                    "Expected " + function.arity() + " arguments but got " + arguments.size() + ".");
        }
        return function.call(this, arguments);//真正执行
    }

    @Override
    public Object visitSetExpr(Set expr) {
        Object instance = evaluate(expr.object);
        if (instance instanceof LoxInstance) {// 运行时检查并抛出Exception
            Object value = evaluate(expr.value);
            ((LoxInstance)instance).set(expr.name,value);
            return value;
        }
        throw new RuntimeError(expr.name, "Only instances have properties.");
    }

    @Override
    public Object visitGetExpr(Get expr) {
        Object instance = evaluate(expr.object);
        if (instance instanceof LoxInstance) {// 运行时检查并抛出Exception
            return ((LoxInstance)instance).get(expr.name);
        }
        throw new RuntimeError(expr.name, "Only instances have properties.");
    }

    @Override
    public Object visitThisExpr(This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    @Override
    public Object visitSuperExpr(Super expr) {
        int distance = locals.get(expr);
        LoxClass superclass = (LoxClass) environment.getAt(distance, "super");
        // "this" is always one level nearer than "super"'s environment.
        LoxInstance object = (LoxInstance) environment.getAt(distance - 1, "this");
        LoxFunction method = superclass.findMethod(expr.method.lexeme);
        if (method == null) {
            throw new RuntimeError(expr.method, "Undefined property '" + expr.method.lexeme + "'.");
        }
        return method.bind(object);
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
            case PLUS://string和number都有plus操作 #TODO 2.增加数字+字符串自动转化为字符串的功能
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
            default: break;
        }

        // Unreachable.
        return null;
    }

    /********************************************* Visit Statement **************************************************/
    
    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        Object superclass = null;
        if (stmt.superclass != null) {
            superclass = evaluate(stmt.superclass);
            if (!(superclass instanceof LoxClass)) {
                throw new RuntimeError(stmt.superclass.name, "Superclass must be a class.");
            }
        }
        environment.define(stmt.name.lexeme, null);

        if (stmt.superclass != null) {//存在继承则新建一个env在其中定义super，该env对应class的内部，外部无法使用super 
                                    //Question: 为什么this没有这个需求？Ans: 因为这里class的定义需要用到外部环境中的superclass，类似执行block的过程，而普通的class定义是完全不涉及到对env的交互的，包括this的使用
            environment = new Environment(environment);
            environment.define("super", superclass);
        }

        Map<String, LoxFunction> methods = new HashMap<>();
        for (Stmt.Function method : stmt.methods) {
            LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("init"));//这里多个instance的func不是同名了吗？Ans: LoxFunction是一个接口对象，此处的method不同于func，是定义在class的filed内的而非env
            methods.put(method.name.lexeme, function);
        }
        LoxClass klass = new LoxClass(stmt.name.lexeme, (LoxClass)superclass,methods);
        
        if (superclass != null) {//回退到上一个env
            environment = environment.enclosing;
        }
        environment.assign(stmt.name, klass);
        return null;
    }
    
    @Override
    public Void visitFunctionStmt(Function stmt) {//此处并非调用函数，而是定义函数的过程，将函数对象新增到环境中
        LoxFunction function = new LoxFunction(stmt, environment, false);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitBlockStmt(Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));//执行block时需要新建一个env
        return null;
    }

    public void executeBlock(List<Stmt> statements, Environment environment) {
        Environment previous = this.environment;//储存当前Interpreter的env
        try{
            this.environment = environment;//用新的env覆盖当前的Interpreter的env
            for(Stmt statement : statements){
                //TODO 3.处理break的情况
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
    public Void visitWhileStmt(While stmt) {
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
    public Void visitReturnStmt(Stmt.Return stmt) {
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