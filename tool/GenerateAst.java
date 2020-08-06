package com.craftinginterpreters.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import javax.management.RuntimeErrorException;

public class GenerateAst {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: generate_ast <output directory>");
            System.exit(64);
        }
        String outputDir = args[0];
        defineAst(outputDir, "Expr", Arrays.asList(//Expr AST的节点
            "Assign   : Token name, Expr value",
            "Call     : Expr callee, Token paren, List<Expr> arguments",
            "This     : Token keyword",//用以指代当前的instance
            "Super    : Token keyword, Token method",//和this不同，super指代的是一个抽象的类没有实际的fields，只能被调用method
            "Get      : Expr object, Token name",
            "Set      : Expr object, Token name, Expr value",
            "Logic    : Expr left, Token operator, Expr right",
            "Binary   : Expr left, Token operator, Expr right",
            "Grouping : Expr expression", 
            "Literal  : Object value", 
            "Unary    : Token operator, Expr right",
            "Variable : Token name"//变量
        ));
        
        defineAst(outputDir, "Stmt", Arrays.asList(
            "Class      : Token name, Expr.Variable superclass, List<Stmt.Function> methods",//Question: 没有field感觉很变扭....
            "If         : Expr condition, Stmt thenBranch, Stmt elseBranch",
            //"For        : Stmt initializer, Expr condition, Expr increment, Stmt body",
            "Function   : Token name, List<Token> params, List<Stmt> body",
            "While      : Expr condition, Stmt body",
            "Block      : List<Stmt> statements",
            "Expression : Expr expression",
            "Print      : Expr expression",
            "Return     : Token keyword, Expr value",
            "Var        : Token name, Expr initializer"//变量的声明节点
        ));
    }

    //自动创建一个Expr类，自动定义不同type的Expr类
    private static void defineAst(String outputDir, String baseName, List<String> types) throws IOException {
        String path = outputDir + "/" + baseName + ".java";
        PrintWriter writer = new PrintWriter(path, "UTF-8");

        writer.println("package com.craftinginterpreters.lox;");
        writer.println();
        writer.println("import java.util.List;");
        writer.println();
        writer.println("abstract class " + baseName + " {");
        
        defineVisitor(writer, baseName, types);

        // The AST classes.
        for (String type : types) {
            String className = type.split(":")[0].trim();
            String fields = type.split(":")[1].trim(); 
            defineType(writer, baseName, className, fields);
        }

        // The base accept() method.
        writer.println();
        writer.println("  abstract <R> R accept(Visitor<R> visitor);");

        writer.println("}");

        writer.close();
    }
    
    //定义不同type的类
    private static void defineType(PrintWriter writer, String baseName, String className, String fieldList) {
        writer.println("  static class " + className + " extends " + baseName + " {");

        // Constructor.
        writer.println("    " + className + "(" + fieldList + ") {");

        // Store parameters in fields.
        String[] fields = fieldList.split(", ");
        for (String field : fields) {
            String name = field.split(" ")[1];
            writer.println("      this." + name + " = " + name + ";");
        }
        writer.println("    }");
        
        // Visitor pattern.
        writer.println();
        writer.println("    @Override");
        writer.println("    <R> R accept(Visitor<R> visitor) {");
        writer.println("      return visitor.visit" + className + baseName + "(this);");
        writer.println("    }");

        // Fields.
        writer.println();
        for (String field : fields) {
            writer.println("    final " + field + ";");
        }

        writer.println("  }");
    }

    //定义vistor接口，均为抽象类，具体实现在之后的implement的类中
    private static void defineVisitor(PrintWriter writer, String baseName, List<String> types) {
        writer.println("  interface Visitor<R> {");

        for (String type : types) {
            String typeName = type.split(":")[0].trim();
            writer.println("    R visit" + typeName + baseName + "(" + typeName + " " + baseName.toLowerCase() + ");");
        }

        writer.println("  }");
    }
}