# Lox Language

## Lox.java

- `main`
- `runPrompt`, `runFile`
- `run`
- `error`, `report`
  - 可能的错误来自于三个过程，不同过程的错误处理方式也不同
    - 静态过程：1.scan的时候发现不规范的字符导致的此法错误；2.pasre的时候发现无法闭合规则导致的语法错误
    - 动态过程（Runtime Error）：运行时产生的计算错误，如整数和字符串相加

---

## 1. Token 定义& Scanner 的实现

### 1.1 TokenType.java

- 定义了一系列TokenType

### 1.2 Token.java

- 定义了Token类，包含了type，原文lexeme，值literal和所在的行数line

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

- Scanner类的属性

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

>功能函数实现对全局token序列的读取和分析，维护当前的lexeme和其位置，生成Token对象保存到list中

- 功能函数
  - `advance`
  - `peek`, `peekNext`
  - `match`
  - `addToken`
  - `isAtEnd`, `isDigt`, `isAlpha`, `isAlphaNumeric`

>`scan`不采用正则表达式而用swith case的方式处理各种lexeme

- 核心函数
  - **`scan`**
  - `string`, `number`, `comment`, `identifier`

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
        Lox.error(line, "Unexcepted character."); break;//每次scan都会遍历全部的长度，每次遇到不规范的字符报一次错
    }
}
```

---

## 2. AST节点定义、生成&执行

### 2.1  Expr.java

> abstrct class，定义Expr的各个子类，均为AST的节点

- 属性

  - interface **`visitor`**

- 子类

- > 每个子类中包含自己的构造函数和一个重载的accept函数，accept的参数为visitor，在其中调用visitor对应不同expr子类的方法。该方式为java的一种设计模式——Visitor Pattern。

  - static class **`Binary`**
  - static class **`Unary`**

  - static class **`Literal`**

  - static class **`Grouping`** 增加此类是为了区分 a=3; 和 (a)=3; 前者是合法的，后者不行。

  - static class **`Variable`** 


```java
interface Visitor<R> {
    R visitBinaryExpr(Binary expr);
    R visitGroupingExpr(Grouping expr);
    R visitLiteralExpr(Literal expr);
    R visitUnaryExpr(Unary expr);
}

abstract <R> R accept(Visitor<R> visitor);

//子类中的accept重载示例
@Override
<R> R accept(Visitor<R> visitor) {
    return visitor.visitBinaryExpr(this);
}
```

### 2.2 Stmt.java

> abstract class，定义Stmt的各个子类，均为AST的节点

- 属性
  - interface **`visitor`**
- 子类
  - static class **`Print`**
  - static class **`Expression`**
  - static class **`Var`**
  - static class **`Block`**

> 将之前的expression作为statement的其中一种子类，并完善如打印、赋值等操作。

```java
interface Visitor<R> {
    R visitExpressionStmt(Expression stmt);
    R visitPrintStmt(Print stmt);
}
abstract <R> R accept(Visitor<R> visitor);

//子类中的accept重载示例
@Override
<R> R accept(Visitor<R> visitor) {
    return visitor.visitExpressionStmt(this);
}
```

### 2.3 Paeser.java

>**展开规则**：
>
>```java
>program     → declaration* EOF ;
>
>declaration → varDecl
>       | statement ;
>
>------------------------------------
>
>varDecl → "var" IDENTIFIER ( "=" expression )? ";" ;
>
>------------------------------------
>
>statement → exprStmt | printStmt | block;
>exprStmt  → expression ";" ;
>printStmt → "print" expression ";" ;
>block     → "{" declaration* "}" ;
>
>------------------------------------
>expression → assignment ;
>
>//若该语句是assignment，则等式的左边一定是Token而不是Expr，即左边为变量右侧为可计算值的Expr
>//但有时候左边是复杂的表达式如 makeList().head.next = node; 中，parser在遇到‘=’前都不知道
>//在parse一个l-value，如何解决这个问题？先用Expr来计算l-value，若得到Expr.Valiable则赋值
>assignment → IDENTIFIER "=" assignment
>      | equality ;
>
>equality       → comparison ( ( "!=" | "==" ) comparison )* ;
>comparison     → addition ( ( ">" | ">=" | "<" | "<=" ) addition )* ;
>addition       → multiplication ( ( "-" | "+" ) multiplication )* ;
>multiplication → unary ( ( "/" | "*" ) unary )* ;
>unary          → ( "!" | "-" ) unary
>          | primary ;
>primary        → NUMBER | STRING | "false" | "true" | "nil"
>          | "(" expression ")" | IDENTIFIER | block;
>
>```

- 属性
  - `tokens`
  - `current`
  - `List<Stmt> statements`

- 调用方法

1. ~~返回 `Expr`类~~

```java
Expr parse() {
    try {
        return expression();
    } catch (ParseError error) {
        return null;
    }
}
```

2. 返回 List\<`Stmt`>

```java
List<Stmt> parse() {
    List<Stmt> statements = new ArrayList<>();
    while (!isAtEnd()) {
        statements.add(statement());
    }

    return statements;
}
```

>功能函数和scanner的类似，实现对全局token序列的读取和分析，多了一个特殊的ParserError类(extends RuntimeException)用来处理parse过程中遇到的错误

- 功能函数
  - `advance`
  - `peek`, `previous`
  - `check`, `match`
  - `isAtEnd`
  - **`error`**
  - **`synchronize`**, `consume`

    - 实现对错误的处理，discard当前的tokens直到下一个条语句的开头(暂时无法处理for语句内部的token错误)

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

>每个核心函数暂时只能处理一个现规则下的expr，调用Expr类中某个子类的构造函数生成该子类并返回，parser.parse()返回一个Expr子类对象，随后调用Interpreter类求值。**最后得到的expr对象实际上是一颗AST树**。

- 核心函数
  - 处理`Expr`类
    - `expression`
    - `equality`
    - `comparision`
    - `addtion`
    - `mutiplication`
    - `unary`
    - `primary`
  - 处理`Stmt`类
    - `block`
    - `declaration`
    - `statement`
    - `printStatement`
    - `expressionStatement`
    - `varDeclarationStatement`

### 2.4 Interpreter.java

>implements Expr.Visitor\<Object>, Stmt.Visitor\<Void> 完善之前定义类的visit接口
>
>void不是函数，是方法的修饰符，void的意思是该方法没有返回值，意思就是方法只会运行方法中的语句，但是不返回任何东西。 java.lang.Void是一种类型。例如给Void引用赋值null。通过Void类的源代码可以看到，Void类型不可以继承与实例化。
>[其他用法参考此博文](https://blog.csdn.net/f641385712/article/details/80409211)

- 属性

  - Environment  `environment`
- 辅助函数

  - `isTruthy`, `isEqual`
- 调用方法
  - **`interpret`**
- 依次调用List\<Stmt>中的statement

- **`execute`**

  - 调用Stmt对象的accept方法中的visit接口来执行该节点

  - **`evaluate`**
    - 调用expr对象的accept方法计算单个expressiong的值

  - 总体的调用顺序为: **`interpret`**→`execute`→`evaluate`→`visitXXXX`
- 重载visitXXX接口
  - `Expr`类
    - `visitLiteralExpr`
    - `visitGroupingExpr`
    - `visitUnaryExpr`
    - `visitBinaryExpr` 根据left、right和operator来做运算，本质上是一个Post Order遍历树的过程
    - `visitVariableExpr`
  - `Stmt`类
    - `visitBlockStmt`
      - `executeBlock` 新建一个local环境来执行当前的block
    - `visitExpressionStmt`
    - `visitPrintStmt`
    - `visitVarStmt`

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

- `executeBlock`函数，新建一个scope来执行块
  - 当前的解决方案是把interpreter的environment进行替换，保存之前的环境作为previous，最后再进行恢复。如此一来没有local和global之分，interpreter始终在global中执行。这种解决方案的缺点在于相当浪费内存，而且效率不高。
  - 另一种更优雅的方式为：在每个visitXXX接口中将当前的environment作为一个参数输入，不采用是因为有些麻烦👀

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



---

## 3. 全局变量和局部变量

<img src="/Users/inlab/Documents/scope.png" alt="全局变量和局部变量的关系" style="zoom:35%;" />

### 3.1 Environment.java

> 全局变量和局部变量的覆盖关系如上图所示，local scope中的变量继承上一层级，在该scope中发生的修改会在该scope中生效（即覆盖shadow），但在该scope结束后恢复到进入前的状态。
>
> 要实现上述的目标，理想的形式是使用链状的数据结构来管理所有的环境变量

- 属性

  - Environment `enclosing`
  - Hashmap `values`

- 构造方法

  - Environment ( ) { enclosing = null; }
  - Environment (Environment enclosing) { this.enclosing = enclosing; }

- 调用方法

  - > #Question 为什么这里一个用String，一个用Token？?

  - `define(String name,Object value)`

  - `get(Token token)`

