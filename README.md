# Lox Language

基于java的虚拟机和一些底层类型完成一门新语言，包含了

1. Scan
   1. 关于token的定义 [TokenType.java](./src/com/craftinginterpreters/Tokentype.java)&[Token.java](./src/com/craftinginterpreters/lox/Token.java)
   2. 一个返回token序列的  [scanner](./src/com/craftinginterpreters/lox/Scanner.java)
2. Parse
   1. 基于[Expr](./src/com/craftinginterpreters/Expr.java)和[Stmt](./src/com/craftinginterpreters/lox/Stmt.java)两类超类的AST节点类定义，每个节点类包括初始化方法和一个借助接口来执行的accept方法
   2. 一个解析tokens序列并将其组织成一棵完整的AST树的 [parser](./src/com/craftinginterpreters/lox/Parser.java)
3. Interpret
   1. 可调用对象超类的定义 [LoxCallable.java](./src/com/craftinginterpreters/lox/LoxCallable.java)
   2. 函数对象类的定义 [LoxFunction.java](./src/com/craftinginterpreters/lox/LoxFunctionjava)
   3. 类对象的定义 [LoxClass.java](./src/com/craftinginterpreters/lox/LoxClass.java)
   4. 实例对象的定义 [LoxInstance.java](./src/com/craftinginterpreters/lox/LoxInstance.java)
   5. 一个辅助执行，用来记录变量的环境类 [Environment.java](./src/com/craftinginterpreters/lox/Environment.java)
   6. 一个检验变量合法性，管理局部变量所在环境的 [resolver](./src/com/craftinginterpreters/lox/Resolver.java)
   7. 一个完善各个AST节点类内部接口，使用递归遍历AST的方式来执行的 [interpreter](./src/com/craftinginterpreters/lox/Interpreter.java)
4. Lox
   1. 外部调用的接口，可以执行文件和终端的输入 [Lox.java](./src/com/craftinginterpreters/lox/Lox.java)

后续采用C语言来编写独立的虚拟机，to be continued.....

更多的细节参考文档： [Lox Language](Lox%20Language.md)

​																																		reference: [Crafting Interpreters](http://craftinginterpreters.com/) 

​                                   																																2020.7-2020.8
