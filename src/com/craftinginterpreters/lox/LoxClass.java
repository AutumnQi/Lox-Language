package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LoxClass implements LoxCallable {//TODO: 增加静态方法
    final String name;
    final LoxClass superclass;
    Map<String, LoxFunction> methods = new HashMap<>();

    LoxClass(String name, LoxClass superclass ,Map<String, LoxFunction> methods){
        this.name = name;
        this.superclass = superclass;
        this.methods = methods;
    }

    public LoxFunction findMethod(String name) {
        if(methods.containsKey(name)){
            return methods.get(name);
        }
        if (superclass != null) {//在自身methods中没有找到时去superclass中找，同时解决了继承和重写问题！！妙啊！！然而无法解决在子类内部调用父类函数的问题，所以还是要做和this一样的处理
            return superclass.findMethod(name);
        }
        return null;
    }

    @Override
    public int arity() {
        LoxFunction initializer = findMethod("init");
        if (initializer == null) return 0;
        return initializer.arity();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {//实例化类的过程同样是一个调用过程，如 var time = Time() 的右侧被parse为一个callExpr，从而调用类的call方法
        LoxInstance instance = new LoxInstance(this);
        LoxFunction initializer = findMethod("init");
        if (initializer != null) {
            initializer.bind(instance).call(interpreter, arguments);//!!!妙啊
        }
        return instance;
    }
    
    @Override
    public String toString() {
        return "<class " + name + " >";
    }
    
}