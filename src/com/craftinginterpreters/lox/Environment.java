package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

import javax.management.RuntimeErrorException;

class Environment {
    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    Environment() {
        enclosing = null;//一个新的全局环境
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;//上一层级的环境作为enclosing，但不会将values进行复制，而是借助resolver得到的locals来查找变量所在的环境
    }

    Environment ancestor(int distance) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }

        return environment;
    }

    void define(String name, Object value) {
        values.put(name, value);// 不检查变量是否已经存在，直接赋值,Question: 这里为什么要用String而非Assign中的Token？ Ans: 为了方便匿名函数、内建函数的实现，实现在没有token时也可以完成对象的建立
    }

    Object get(Token name){
        if (values.containsKey(name.lexeme)) {
            if(values.get(name.lexeme)==null){
                throw new RuntimeError(name, "Unassigned variable '" + name.lexeme +"'.");
            }
            return values.get(name.lexeme);
        }
        //此处用RuntimeError来处理找不到变量的错误而非静态Parsing时报错，是为了实现全局同时parsing，避免声明在当前行后面的变量无法被识别，从而避免递归函数的失效
        //现代语言都会在parsing前先进行全局扫描找到所有的变量名后，再进行parsing
        //It’s OK to refer to a variable before it’s defined as long as you don’t evaluate the reference. 
        //refer是可以的，但直接evaluate则应该报错，即不允许在声明前取值
        if (enclosing != null) return enclosing.get(name);//在当前的scope中找不到，递归往上查阅
        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }
        if (enclosing != null) {// #Question: 退出当前scope后如何恢复之前的状态？不恢复全局变量的状态
            enclosing.assign(name, value);
            return;
        }
        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    void assignAt(int distance, Token name, Object value) {
        ancestor(distance).values.put(name.lexeme, value);
    }
}