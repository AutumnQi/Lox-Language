package com.craftinginterpreters.lox;

import java.util.List;

interface LoxCallable {//callabble的对象可以是class和function等，具体的call函数要在具体的对象文件中实现
    int arity();//需要的参数的数量
    Object call(Interpreter interpreter, List<Object> arguments);
    String toString();//在print时被调用
}