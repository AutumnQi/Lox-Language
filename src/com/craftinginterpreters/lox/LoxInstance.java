package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class LoxInstance {
    private LoxClass klass;
    private final Map<String, Object> fields = new HashMap<>();

    LoxInstance(LoxClass klass) {
        this.klass = klass;
    }

	public Object get(Token name) {
        if(fields.containsKey(name.lexeme)){
            return fields.get(name.lexeme);
        }
        LoxFunction method = klass.findMethod(name.lexeme);
        if (method != null) return method.bind(this);

        throw new RuntimeError(name, "Undefined property '" + name.lexeme + "'.");
    }
    
    public void set(Token name,Object value) {
        // if(fields.containsKey(name.lexeme)){
        //     fields.put(name.lexeme, value);
        // }
        // throw new RuntimeError(name, "No such property '" + name.lexeme + "'.");
        fields.put(name.lexeme, value);//在init函数中被调用
    }

    @Override
    public String toString() {
        return "<instance of class " + klass.name + " >";
    }
}