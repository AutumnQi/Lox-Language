# Lox Language

基于java的虚拟机和一些底层类型完成一门新语言，包含了

1. Scan
   1. 关于token的定义 [TokenType.java](./src/com/craftinginterpreters/Tokentype.java)&[Token.java](./src/com/craftinginterpreters/Token.java)
   2. 一个返回token序列的  [scanner](./src/com/craftinginterpreters/Scanner.java)
2. Parse
   1. 基于[Expr](./src/com/craftinginterpreters/Expr.java)和[Stmt](./src/com/craftinginterpreters/Stmt.java)两类超类的AST节点类定义，每个节点类包括初始化方法和一个借助接口来执行的accept方法
   2. 一个解析tokens序列并将其组织成一棵完整的AST树的 [parser](./src/com/craftinginterpreters/Parser.java)
3. Interpret
   1. 可调用对象超类的定义 [LoxCallable.java](./src/com/craftinginterpreters/LoxCallable.java)
   2. 函数对象类的定义 [LoxFunction.java](./src/com/craftinginterpreters/.LoxFunctionjava)
   3. 类对象的定义 [LoxClass.java](./src/com/craftinginterpreters/LoxClass.java)
   4. 实例对象的定义 [LoxInstance.java](./src/com/craftinginterpreters/LoxInstance.java)
   5. 一个辅助执行，用来记录变量的环境类 [Environment.java](./src/com/craftinginterpreters/Environment.java)
   6. 一个检验变量合法性，管理局部变量所在环境的 [resolver](./src/com/craftinginterpreters/Resolver.java)
   7. 一个完善各个AST节点类内部接口，使用递归遍历AST的方式来执行的 [interpreter](./src/com/craftinginterpreters/Interpreter.java)
4. Lox
   1. 外部调用的接口，可以执行文件和终端的输入 [Lox.java](./src/com/craftinginterpreters/Lox.java)

后续采用C语言来编写独立的虚拟机，to be continued.....

## 0. Lox.java

- `main`
- `runPrompt`, `runFile`
- `run`
- `error`, `report`
  - 可能的错误来自于三个过程，不同过程的错误处理方式也不同
    - 静态过程（static）：
      - 1.scan 的时候发现不规范的字符导致的此法错误；
      - 2.pasre 的时候发现无法闭合规则导致的语法错误；
      - 3.reslove 的时候变量的不合法使用导致的语法错误；
    - 动态过程（Runtime Error）：运行时产生的计算错误，如整数和字符串相加
      - 只会在interpreter执行时抛出



## 1 Token 定义& Scanner 的实现

### 1.1 TokenType.java

- 定义了一系列 TokenType

```java
enum TokenType {
  // Single-character tokens.
  LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE, COMMA, DOT, MINUS, PLUS, SEMICOLON, SLASH, STAR, COLON, 

  // One or two character tokens.
  BANG, BANG_EQUAL, EQUAL, EQUAL_EQUAL, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,

  // Literals.
  IDENTIFIER, STRING, NUMBER,

  // Keywords.
  AND, CLASS, ELSE, FALSE, FUN, FOR, IF, NIL, OR, PRINT, RETURN, SUPER, THIS, TRUE, VAR, WHILE, BREAK,

  EOF
}
```

### 1.2 Token.java

- 定义了 Token 类，包含了 type，原文 lexeme，值 literal 和所在的行数 line （在Error中用到）

```java
class Token {
    final TokenType type;
    final String lexeme;
    final Object literal;
    final int line;

    Token(TokenType type, String lexeme, Object literal, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
    }

    public String toString() {
        return type + " " + lexeme + " " + literal;
    }
}
```

### 1.3 Scanner.java

- Scanner 类的属性

  - `source`
  - `List<Token> tokens`
  - `current`, `start`, `line`
  - `keywords`

- 外部调用

  - `scanToken`

  ```java
    List<Token> scanTokens() {
        while (!isAtEnd()) {
            // We are at the beginning of the next lexeme.
            start = current;
            scanToken();
        }
        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }
  ```

> 功能函数实现对全局 token 序列的读取和分析，维护当前的读取位置，生成 Token 对象保存到 list 中

- 功能函数
  - `advance`
  - `peek`, `peekNext`
  - `match`
  - `addToken`
  - `isAtEnd`, `isDigt`, `isAlpha`, `isAlphaNumeric`

> `scan`不采用正则表达式而用 swith case 的方式处理各种 lexeme

- 核心函数
  - **`scan`**
  - `string`, `number`, `comment`, `identifier` 分别负责处理各自的Token

```java
switch (c) {
        //数值运算
        case '(': addToken(LEFT_PAREN); break;
        case ')': addToken(RIGHT_PAREN); break;
        case '{': addToken(LEFT_BRACE); break;
        case '}': addToken(RIGHT_BRACE); break;
        case ',': addToken(COMMA); break;
        case '.': addToken(DOT); break;
        case '-': addToken(MINUS); break;
        case '+': addToken(PLUS); break;
        case ';': addToken(SEMICOLON); break;
        case '*': addToken(STAR); break;
        case '!': addToken(match('=') ? BANG_EQUAL : BANG); break;
        case '=': addToken(match('=') ? EQUAL_EQUAL : EQUAL); break;
        case '<': addToken(match('=') ? LESS_EQUAL : LESS); break;
        case '>': addToken(match('=') ? GREATER_EQUAL : GREATER); break;
        //逻辑运算
        case 'o': if(peek()=='r') addToken(OR);break;
        case 'a':
        //注释
        case '/':
            if(match('/')){
                while(peek()!='\n'&&!isAtEnd()) advance();
            }
            else if(match('*')){
                comment();
            }
            else addToken(SLAH);
            break;
        //空字符，换行字符
        case ' ':break;
        case '\r':break;
        case '\t':break;
        case '\n':line++;break;
        //string
        case '"':string();break;
        default:
        if(isDigit(c)) number();
        else if(isAlpha(c)) identifier();
        else
        Lox.error(line, "Unexcepted character."); break;//每次scan都会遍历全部的长度，将所有不规范的字符都进行报错
    }
}
```


## 2 AST 节点定义& Parser的实现

### 2.1 Expr.java

> abstrct class，使用工厂模式生产 Expr 的各个子类

- 属性

  - interface **`visitor`**

- 子类
- > 每个子类中包含自己的构造函数和一个重载的 accept 函数，accept 的参数为 visitor，在其中调用 visitor 对应不同 expr 子类的方法。该方式为 java 的一种设计模式——Visitor Pattern。

```java
//Expr的子类
defineAst(outputDir, "Expr", Arrays.asList(
            "Assign   : Token name, Expr value",
            "Call     : Expr callee, Token paren, List<Expr> arguments",
            "This     : Token keyword",//用以指代当前的instance
            "Get      : Expr object, Token name",
            "Set      : Expr object, Token name, Expr value",
            "Logic    : Expr left, Token operator, Expr right",
            "Binary   : Expr left, Token operator, Expr right",
            "Grouping : Expr expression",
            "Literal  : Object value",
            "Unary    : Token operator, Expr right",
            "Variable : Token name"//变量
));

interface Visitor<R> {
    R visitBinaryExpr(Binary expr);
    ...
}

abstract <R> R accept(Visitor<R> visitor);

//子类中的accept重载示例
@Override
<R> R accept(Visitor<R> visitor) {
    return visitor.visitBinaryExpr(this);
}
```

### 2.2 Stmt.java

> abstract class，使用工厂模式生产 Stmt 的各个子类，

- 属性
  - interface **`visitor`**
- 子类

```java
defineAst(outputDir, "Stmt", Arrays.asList(
            "Class      : Token name,Expr.Variable superclass, List<Stmt.Function> methods",
            "If         : Expr condition, Stmt thenBranch, Stmt elseBranch",
            //"For        : Stmt initializer, Expr condition, Expr increment, Stmt body",
            "Function   : Token name, List<Token> params, List<Stmt> body",
            "While      : Expr condition, Stmt body",
            "Block      : List<Stmt> statements",
            "Expression : Expr expression",//如i++;等可执行的一条statement
            "Print      : Expr expression",
            "Return     : Token keyword, Expr value",
            "Var        : Token name, Expr initializer"//变量的声明节点
));

interface Visitor<R> {
    R visitExpressionStmt(Expression stmt);
    R visitPrintStmt(Print stmt);
  	.....
}

abstract <R> R accept(Visitor<R> visitor);

//子类中的accept重载示例
@Override
<R> R accept(Visitor<R> visitor) {
    return visitor.visitExpressionStmt(this);
}
```

### 2.3 Paeser.java

> **自底向上规则**：
>
> ```java
> program     → declaration* EOF ;
>
> //------------------------------------ Decl ------------------------------------
>declaration → varDecl | funDecl	| classDecl |statement ;
> 
>classDecl   → "class" IDENTIFIER ( "<" IDENTIFIER )? "{" function* "}" ;
> 
>funDecl  → "func" function ;
> function → IDENTIFIER "(" parameters? ")" block ;
> parameters → IDENTIFIER ( "," IDENTIFIER )* ;
> 
>varDecl  → "var" IDENTIFIER ( "=" expression )? ";" ;
> 
>//------------------------------------ Stmt ------------------------------------
> 
>statement → exprStmt | printStmt | block | ifStmt | whileStmt | returnStmt ;
> exprStmt  → expression ";" ;
> printStmt → "print" expression ";" ;
> ifStmt    → "if" "(" expression ")" statement ("else" statemnt); //不支持else if
> whileStmt → "while" "(" expression ")" statement ;
> //在这里使用desugar将其parse为包含block的一个block，甚至无需在interpretre中新写函数
> forStmt   → "for" "(" ( varDecl | exprStmt | ";" )
>              expression? ";"
>                 expression? ")" statement ;
>    block     → "{" declaration* "}" ;
> returnStmt → "return" expression? ";" ;
> 
>//------------------------------------ Expr ------------------------------------
> expression → assignment ;
> 
>//若该语句是assignment，则等式的左边一定是Token而不是Expr，即左边为变量右侧为可计算值的Expr
> //但有时候左边是复杂的表达式如 makeList().head.next = node; 中，parser在遇到‘=’前都不知道
> //在parse一个l-value，如何解决这个问题？先用Expr来计算l-value，若得到Expr.Valiable则赋值
> assignment     → ( call "." )? IDENTIFIER "=" assignment | logic_or ;
> 
>logic_or       → logic_and ( "or" logic_and )* ;
> logic_and      → equality ( "and" equality )* ;
> equality       → comparison ( ( "!=" | "==" ) comparison )* ;
> comparison     → addition ( ( ">" | ">=" | "<" | "<=" ) addition )* ;
> addition       → multiplication ( ( "-" | "+" ) multiplication )* ;
> multiplication → unary ( ( "/" | "*" ) unary )* ;
> unary          → ( "!" | "-" ) unary | call ;
> //call的优先级在unary之上，在primary之后
> call           → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;//这里使用primary而不是IDENTIFIER是为什么？
> arguments      → expression ( "," expression )* ;
> primary        → NUMBER | STRING | "false" | "true" | "nil"	| "(" expression ")" | IDENTIFIER 								|"super" "." IDENTIFIER ;;
> 
>```

- 属性

  - `tokens`
  - `current`
  - `List<Stmt> statements`

- 调用方法 - 返回 List\<`Stmt`> 调用顺序为`declaration`->`XXXstatemnt`->`XXXexpression`

```java
List<Stmt> parse() {
    List<Stmt> statements = new ArrayList<>();
    while (!isAtEnd()) {
        statements.add(declaration());
    }

    return statements;
}
```

> 功能函数和 scanner 的类似，实现对全局 token 序列的读取和分析，多了一个特殊的 ParserError 类(extends RuntimeException)用来处理 parse 过程中遇到的错误

- 功能函数
  - `advance`
  - `peek`, `previous`
  - `check`, `match`
  - `isAtEnd`
  - **`error`**
  - **`synchronize`**, `consume`

    - 实现对错误的处理，discard 当前的 tokens 直到下一个条语句的开头(暂时无法处理 for 语句内部的 token 错误)

```java
private ParseError error(Token token, String message) {
    //returns it instead of throwing because we want to
    //let the caller decide whether to unwind or not
    Lox.error(token, message);
    return new ParseError();
}
```

```java
private void synchronize() {
    advance();

    while (!isAtEnd()) {
        //Discard Tokens Until we’re right at the beginning of the next statement
        if (previous().type == SEMICOLON)
            return;

        switch (peek().type) {
            case CLASS:
            case FUN:
            case VAR:
            case FOR:
            case IF:
            case WHILE:
            case PRINT:
            case RETURN:
                return;
        }

        advance();
    }
}
```

> 每个核心函数暂时只能处理一个现规则下的 expr，调用 Expr 类中某个子类的构造函数生成该子类并返回，parser.parse()返回一个 Expr 子类对象，随后调用 Interpreter 类求值。**最后得到的 expr 对象实际上是一颗 AST 树**。

- 核心函数
  - 处理`Expr`类
    - `expression`
      - `assignment`
      - `or` && `and`
      - `equality`
      - `comparision`
      - `addtion`
      - `mutiplication`
      - `unary`
      - `primary`
      - `call`
        ```java
        //调用方法，不支持将函数作为对象？funcA(**args)(funcB(**args))
            private Expr call() {
                Expr expr = primary();
                while(true){
                    if (match(LEFT_PAREN)) {
                        //第一次执行玩call的结果作为第二次的callee
                        expr = finishCall(expr);
                    }
                    else break;
                }

                return expr;
            }

            private Expr finishCall(Expr callee) {
                List<Expr> arguments = new ArrayList<>();
                if (!check(RIGHT_PAREN)) {
                    do{
                        arguments.add(expression());
                    } while(match(COMMA));
                }
                Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");
                return new Expr.Call(callee,paren,arguments);
            }
        ```
  - 处理`Stmt`类
    - `varDeclarationStatementn`
    - `function`
    - `statement`
      - `block`
      - `ifStatement`
      - `whileStatement`
      - `forStatement`
      - `printStatement`
      - `expressionStatement`
      - `varDeclarationStatement`

## 3 Function和Class对象的实现

### 3.1 LoxCallable.java

>  可调用接口的抽象接口 #Question: 这里为什么要设计成接口？

- 属性
  - int arity();//需要的参数的数量
  - Object call(Interpreter interpreter, List<\Object> arguments);
  - String toString();//在 print 时被调用

### 3.2 LoxFunction.java

> 对 LoxCallable 抽象接口的一种 implement

- 属性
  - private final Stmt.Function `declaration` AST 节点
  - private final Environment `closure` 上层函数的**作用域**
    - **每个LoxFunction对象负责管理自己的env！在bind函数中实现！**
- 构造方法
  - LoxFunction(Stmt.Function `declaration`, Environment `closure`, boolean` isInitializer`) 
- 方法
  - `arity` 返回参数个数
  - `toString` 返回函数名称
  - `call`(interpreter, arguments) 调用传入的 interpreter，根据 arguments 更新 closure 后调用 executeBlock 来执行。当函数为initializer时，返回一个instance对象否则返回Loxfuntion对象。

```java
		@Override
    public Object call(Interpreter interpreter, List<Object> arguments) {//arguments是param在evaluate之后的Object
        Environment environment = new Environment(closure);//使用上层函数传递的closure而不是global作为当前执行的环境
        //在当前Interpreter中绑定function.params中的各个Token和其Object
        for (int i = 0; i < declaration.params.size(); i++) {
            environment.define(declaration.params.get(i).lexeme, arguments.get(i));
        }

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {
            return returnValue.value;
        }
        if (isInitializer) return closure.getAt(0, "this");
        return null;//函数没有返回值则返回null
    }
```

### 3.3 LoxClass.java

> 继承 LoxCallable 类，用来生成可调用的 class 对象，实现 instance 的生成

- 属性

  - final String `name`;
  - final LoxClass `superclass`;
  - Map<String, LoxFunction> `methods` = new HashMap<>();

- 构造方法

  - LoxClass(String `name`, LoxClass `superclass` ,Map<String, LoxFunction> `methods`)

- 方法

  - `arity` 返回参数个数
  - `toString` 返回函数名称
  - `call`(interpreter, arguments) 调用传入的 interpreter，根据 arguments 更新 closure 后调用 executeBlock 来执行。new一个Instance对象，调用initialize函数来对其进行初始化（绑定this和该对象），返回该对象

  ```java
  @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {//实例化类的过程同样是一个调用过程，如 var time = Time() 的右侧被parse为一个callExpr，从而调用类的call方法
          LoxInstance instance = new LoxInstance(this);
          LoxFunction initializer = findMethod("init");
          if (initializer != null) {
              initializer.bind(instance).call(interpreter, arguments);//!!!妙啊
          }
          return instance;
      }
  ```

### 3.4 LoxInstance.java

> 独立的 Instance对象类

- 属性

  - private LoxClass `klass`;
  - private final Map<String, Object> `fields` = new HashMap<>(); 包含该instance的一些属性值

- 构造方法

  - LoxInstance(LoxClass `klass`) 指向自己的抽象类，用于环境管理

- 方法

  - `set` 用来对fields进行修改，被initializer调用
  - `get` 获取fields中的一些属性
    - instance函数的调用也通过get来实现，但并非在fields中寻找，而是去LoxClass对象的methods中寻找，并返回一个绑定了this的LoxFunciton对象

  ```java
  public Object get(Token name) {
          if(fields.containsKey(name.lexeme)){
              return fields.get(name.lexeme);
          }
          LoxFunction method = klass.findMethod(name.lexeme);
          if (method != null) return method.bind(this);
  
          throw new RuntimeError(name, "Undefined property '" + name.lexeme + "'.");
      }
  ```

## 4  局部变量合法性检查

### 4.1 [Resolver.java](./src/com/craftinginterpreters/lox/Resolver.java)

> 用一个 mini-interpreter 来对 AST 进行剪枝，主要解决变量绑定的问题，和 interpreter 一样是对 visitor 接口的 implement，但只专注变量的管理，通过一个栈来管理当前的 scope，在新的 scope 中，每个新声明的变量都有一个 boolean 值来确认其是否已经初始化。如此一来即可在 parse 阶段完成多次声明后的冲突。

- 属性

  - private final Interpreter `interpreter`
  - private final Stack<Map<String,Boolean>> `scopes` = new Stack<>()
    - 每一个 scope 包含在其中声明的 variable，以完成变量的合法性检查
  - private FunctionType `currentFunction` = FunctionType.NONE.
    - type包含：NONE, FUNCTION, METHOD, INITIALIZER
    - 指示当前处于那类 function 体中，用来检测一些语法错误如：不在 function 内的 return 语句
  - private ClassType `currentClass` = ClassType.NONE;
    - types包含：NONE, CLASS, SUBCLASS
    - 用来检测this和super的非法使用

- 构造方法

  - Resolver(Interpreter interpreter) {this.interpreter = interpreter;} 绑定 interpreter

- 调用方法

  -  `resolve(List<\Stmt> statements)`

- 辅助函数

  - `beginScope` `endScope` 分别表示在scopes栈中压入一个scope和弹出

  - `declare` 在当前scopes栈顶的scope中添加某个对象，值设置为false，代表为初始化

  - `define` 在当前scopes栈顶的scope中将某个对象的设置为true，代表初始化完成

  - 三种`resolve` 分别对应输入参数为 Expr、Stmt 和 List<\Stmt>，通过 accept 调用自身子类的 visit 接口，完成变量名的检查
  
- `resolveFunction` 新建一个 scope，将函数声明中的参数分别`declare`、`define`后，再对 body 进行 resolve，完成函数内部的变量合法性检查
  
- 核心函数

  - `resolveLocal` 在 scopes 栈中依次向前寻找包含该变量的 scope，随后调用 Interpreter 的 resolve 方法在interpreter的Local中put(Expr， distance)
   ```java
  private void resolveLocal(Expr expr, Token name) {
            for (int i = scopes.size() - 1; i >= 0; i--) {
                if (scopes.get(i).containsKey(name.lexeme)) {// 找到包含当前variable最近的一个scope
                    interpreter.resolve(expr, scopes.size() - 1 - i);
                    return;
                }
            }
            // Not found. Assume it is global.
        }
   ```

- 重载 visitXXX 接口

  - 在不涉及到变量赋值、声明和 block 时，直接调用 resolve 来进行变量检查。

    - 1. 在变量的声明中，首先`declare`变量，随后通过`resolve`检查 initializer，没有问题则`define`变量，结束后

    - 2. 在函数的声明中，首先`declare`、`define`函数名，随后通过`resolveFunction`检查 body 中的变量使用

  3. 一旦遇到for/while block,  function, class时，首先`beginScope`，将局部变量添加到该scope中，通过`resolve`检查 body 中所有的 statements 后`endScope`

- `Expr`类

  - `visitLogicExpr`
  - `visitLiteralExpr`
  - ....

- `Stmt`类

  - `visitBlockStmt`
  - .....


## 5 解释执行 AST

### 5.1 Interpreter.java

> implements Expr.Visitor\<Object>, Stmt.Visitor\<Void> 完善之前定义类的 visit 接口
>
> void 不是函数，是方法的修饰符，void 的意思是该方法没有返回值，意思就是方法只会运行方法中的语句，但是不返回任何东西。 java.lang.Void 是一种类型。例如给 Void 引用赋值 null。通过 Void 类的源代码可以看到，Void 类型不可以继承与实例化。
> [其他用法参考此博文](https://blog.csdn.net/f641385712/article/details/80409211)

- 属性

  - final Environment `globals` = new Environment()
  - private Environment `environment` = globals

- 辅助函数

  - `isTruthy`, `isEqual` ...

- 调用方法
  - **`interpret`**
- 核心函数

  - **`execute`**

    - 调用 Stmt 对象的 accept 方法中的 visit 接口来执行该节点

  - **`evaluate`**
  - 调用 expr 对象的 accept 方法计算单个 expressiong 的值，**返回Object**
    
  - 总体的调用顺序为: **`interpret`**→`execute`→`evaluate`→`visitXXXX`
- 重载 visitXXX 接口
  - `Expr`类
    - `visitLogicExpr`
    - `visitLiteralExpr`
    - `visitGroupingExpr`
    - `visitUnaryExpr`
    - `visitBinaryExpr` 根据 left、right 和 operator 来做运算，本质上是一个 Post Order 遍历树的过程
    - `visitVariableExpr`
  - `Stmt`类
    - `visitBlockStmt`
      - `executeBlock` 新建一个 local 环境来执行当前的 block
    - `visitExpressionStmt`
    - `visitPrintStmt`
    - `visitVarStmt`
    - `visitIfStmt`
    - `visitWhileStmt`

```java
@Override
public Object visitBinaryExpr(Expr.Binary expr) {
    Object left = evaluate(expr.left);
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {
        case GREATER:
            return (double) left > (double) right;
        case GREATER_EQUAL:
            return (double) left >= (double) right;
        case LESS:
            return (double) left < (double) right;
        case LESS_EQUAL:
            return (double) left <= (double)right;
        case MINUS:
            checkNumberOperand(expr.operator, right);//处理runtime error
            return (double) left - (double) right;
        case PLUS://string和number都有plus操作
            if (left instanceof Double && right instanceof Double) {
            return (double)left + (double)right;
            }
            if (left instanceof String && right instanceof String) {
            return (String)left + (String)right;
            }
        case SLASH:
            return (double) left / (double) right;
        case STAR:
            return (double) left * (double) right;
        case BANG_EQUAL: return !isEqual(left, right);
        case EQUAL_EQUAL: return isEqual(left, right);
    }

    // Unreachable.
    return null;
}
```

- `executeBlock`函数
  - 当前的解决方案是把 interpreter 的 environment 进行替换，保存之前的环境作为 previous，最后再进行恢复。如此一来没有 local 和 global 之分，interpreter 始终在 global 中执行。这种解决方案的缺点在于相当浪费内存，而且效率不高。
  - 另一种更优雅的方式为：在每个 visitXXX 接口中将当前的 environment 作为一个参数输入，不采用是因为有些麻烦 👀

```java
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
```

### 5.2 Return.java

> 继承 RuntimeException 的类，实现 return 跳出当前的页帧并返回值到上一个页帧（默认没有 return 则返回 null）

- 属性
  - final Object `value`
- 构造方法
  - Return(Object value){ super(null, null, false, false); this.value = `value`; }

## 5 令人头疼的环境管理

### 5.1 Environment.java

> 全局变量和局部变量的覆盖关系如上图所示，local scope 中的变量继承上一层级，在该 scope 中发生的修改会在该 scope 中生效（即覆盖 shadow），但在该 scope 结束后恢复到进入前的状态。
>
> 要实现上述的目标，理想的形式是使用链状的数据结构来管理所有的环境变量，最终会生成一个巨大的单向图结构

- 属性

  - Environment `enclosing`
  - Hashmap `values`

- 构造方法

  - Environment ( ) { enclosing = null; }
  - Environment (Environment `enclosing`) { this.enclosing = enclosing; } 

- 调用方法

  - > #Question 为什么这里一个用 String，一个用 Token？?

  - `define(String name,Object value)`

  - `ancestor (int distance)` 根据 distance 顺着 enclosing 链表向上找到目标 enclosing

  - `get(Token token)`

  - `getAt（int distance, Token name）`

  - `assign`

### 5.2 环境的生成过程

<img src="pics/scope.png" alt="全局变量和局部变量的关系" style="zoom:35%;" />

- #### environment 树的生长

  - 1.作为**主干**的**全局环境**的生长

    - 在实例化 Interpreter 时进行初始化：

    ```java
     final Environment globals = new Environment();
    ```

    - 在最外层的 visitClassStmt, visitFunctionStmt, visitVarStmt 时对全局环境进行修改

    ```java
    //visitClassStmt
     environment.define(stmt.name.lexeme, null);
     environment.assign(stmt.name, klass);
     //visitFunctionStmt
     environment.define(stmt.name.lexeme, function);
     //visitVarStmt
     environment.define(stmt.name.lexeme, value);
    ```

  * 2.作为**分支**的各种**局部环境**的生长

    - Interpreter 中

      - 在 visiteClassStmt 时，若该类是继承某超类的，新建一个包含“super”对象的环境：

      ```java
       environment = new Environment(environment);
       environment.define("super", superclass);
      ```

      - 在 visitBlock 时，新建一个局部环境，在其中执行 stmt，不干扰全局环境（主要是为了保持一致）：

      ```java
       executeBlock(stmt.statements, new Environment(environment));
      ```

    - LoxFunction 中

      - 在调用`bind`方法绑定 Function 对象和 Instance 对象时，更新 function 对象的 environment，新建一个包含“this”对象的环境，this 指向传入的 instance 对象：

      ```java
       Environment environment = new Environment(closure);
       environment.define("this", instance);
       return new LoxFunction(declaration, environment,isInitializer)
      ```

      - 在调用 function 对象的 call 方法时，新建一个 environment 将 argument 和 parameters 进行绑定，再丢入 interpreter 的 executeBlock 中进行执行

      ```java
       Environment environment = new Environment(closure)
       for (int i = 0; i < declaration.params.size(); i++) {
       environment.define(declaration.params.get(i).lexeme,arguments.get(i));
       }
      ```

- #### environment 树的调用

  - `executeBlock(List<Stmt> statements, Environment environment)`

  - 新建一个当前 interpreter 的 env 的备份 previous，在执行时用传入的 env 将当前的 env 覆盖后执行 block 内部，保证**执行时可用到全局环境**且**执行完毕后不干扰全局环境**

    ```java
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
    ```

## 6 问题总结

### 6.1 为什么临时变量在不同对象中的key不同？

- Environment: private final Map<`String`, `Object`> values
- Interpreter: private final Map<`Expr`, `Integer`> locals
- Resolver: private final Stack<Map<`String`, `Boolean`>> scopes

**分析**：在每一个environment中，同名对象的值是唯一的，故只需要用`String`来作为key，同样的在Resolver中每个scope对应实际执行时会出现的environment，故也只需要用`String`作为key。但在Interpreter中，locals用来管理所有局部变量所在的environment，即多个同名的对象多次出现，且每一个指向不同的environment。这种情况下`String`无法区分，只能使用这些对象在不同位置parse时生成`Expr`作为key。















