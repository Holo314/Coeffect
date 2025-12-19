# Coeffect

[![Maven Central](https://img.shields.io/maven-central/v/io.github.holo314/Coeffect.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.github.holo314/Coeffect) [![license](https://img.shields.io/github/license/holo314/Coeffect)](https://www.apache.org/licenses/LICENSE-2.0)

Add a partial Coeffect system into Java using Loom's ScopedValues.

---
In Java there are generally 2 strategies to manage the parameters a method needs:

1. Passing a value as a parameter
2. Having the value as fields of the class

Furthermore, to ensure thread safety we need to have more work.
For the first method the problem is less apparent, but for the latter it is much harder to deal with.

One way to ensure safety is to use
Java's [ThreadLocal](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ThreadLocal.html), which
ensures that a parameter cannot pass through different threads:

```java
public class Example {
    private static ThreadLocal<String> TL = new ThreadLocal<>();

    public void foo() {
        System.out.println(TL.get());
    }

    public static void main(String[] args) {
        var x = new Example();
        CompletableFuture.runAsync(() -> {
            TL.set("^o^");
            Thread.sleep(3000); // omitting exception handling
            x.foo();
        });
        CompletableFuture.runAsync(() -> {
            Thread.sleep(1000); // omitting exception handling
            TL.set("o7");
            x.foo();
        });
    }
}
```

This will print
> o7
>
> ^o^

Project Loom finalised [ScopedValue](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ScopedValue.html), which is basically a
structured version of `ThreadLocal`.

One of the most problematic parts of `ThreadLocal` and `ScopedValue` is that we lose type safety. For `ThreadLocal` you
can get unexpected `null`s, and for `ScopedValue` you would get an exception.

Any use of `ThreadLocal` or `ScopedValue` should be attached to a null-check or binding-check.
Furthermore, have either of those 2 not being `private` is dangerous as it will most likely lead into coupling in your code, security problems and ambiguous APIs.

On the other hand, sending dependencies as parameters have other problems, but the main two I want to talk about are:

1. Parameter bloating
2. Explicit binding of all parameters

The first point is pretty clear, you can get methods with 5/6 parameters or more, which creates long signatures that are inconvenient to call and read.

The second point is easier to miss, consider the following snippet:

```java
void main() {
    foo(666);
}

void foo(int x) {
    bar(x);
}

void bar(int x) {
    System.out.println(x);
}
```

Notice that `foo` receive a parameter **only** to pass it to `bar`, it doesn't actually do anything with it.

---

# How we solve it

The solution this library offers is to create a (partial) [Coeffect System](http://tomasp.net/coeffects/) (for those who do not care about the maths behind the concept, simply skip the link, this readme will not assume any such knowledge).

The idea is to use `ScopedValue` to create an implicit context behind every function call and use a compiler plugin to add type safety.

> `Implementation note:` It is impossible to create this system with `ThreadLocal` because there is no control over the
> call of `ThreadLocal#remove`.

Before diving into the details, let's see how the above example will look like:

```java
import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.runtime.Coeffect;

void main() {
    Coeffect.with(666)
            .run(this::foo);
}

@WithContext(Integer.class)
void foo() {
    bar();
}

@WithContext(Integer.class)
void bar() {
    IO.println(Coeffect.get(Integer.class));
}
```

With can notice few parts:

- We used `Coeffect.get(Integer.class)` in `bar` to get the top integer stored in the global `Coeffect`.
- We annotated `bar` with `@WithContext(Integer.class)` to denote that we are using `Integer` in the method.
- We called `bar` in `foo`.
- We annotated `foo` with `@WithContext(Integer.class)` to denote that we are using a method that requires `Integer` in
  it.
- We called `Coeffect.with(666)` to put `666` in the top of the stack of `Integer.class`.
- We called `run` on the `Coeffect.with(666)` to run a `Runnable` with the current stack.
- In the `Coeffect.with(666).run` clause we are running `foo`
- We **do not** need to specify `@WithContext(Integer.class)` on the `main` method because we don't use any unbound
  dependency

When using the annotation processor the library is shipped with **all of those points are enforced at compile time**. If, for example, we were to change `main` to simply be `foo();` we would get the error:

```text
error: [Coeffect] Missing requirements in `foo()`: [java.lang.Integer]
    foo();
       ^
  	Add the requirements to the context or wrap it with run/call:
  		@WithContext({java.lang.Integer, ...})
  		void main() {...}
  ---
  		Coeffect.with(vInteger0)
  				.run(() -> ...);
  ---
  		Coeffect.with(vInteger0)
  				.call(() -> ...);
    (see https://github.com/Holo314/coeffect)
```

---

# The details

There are few basic rules one should keep in mind, let's go over them (every time `run` is being used, it also applies to `call`
, which is the same but also returns a value):

### Enforcing of `Coeffect#get`

Any and all calls of `Coeffect.get(T)` must satisfy one of the 2 following conditions:

1. Inside of `Coeffect.with(T)#run` block
2. Inside a method annotated with `@WithContext(T)`

### Methods annotated with `@WithContext`

Any use of a method annotated with `@WithContext` act similarly to `Coeffect#get`, with the exception
that `@WithContext` can receive several types.

### `Coeffect` stacks

Coeffect internally saves an `ScopedValue` instance for each `Class<T>`.
When calling `Coeffect.with(v)` it adds `v` to the top of the stack of `v.getClass()`.

### The value of `Coeffect#get`

When calling `Coeffect.get(T)` it will return the top value in the stack of `T`. Note that this is a peek, it does not
remove it from the stack.

`Implemention note:` `Coeffect#get` should be used only with Class literals, e.g. `String.class`, and
not `"hi".getClass()`, using non-class literals can either fail at complication, or create false negatives.

### Extents

The lifetime of every binding is exactly the `Coeffect.Carrier#run` clause:

```java
import io.github.holo314.coeffect.runtime.Coeffect;

void main() {
    Coeffect.with(3)
            .with("Holo")
            .run(() -> {
                Coeffect.with(6)
                        .run(() -> {
                            IO.println(Coeffect.get(Integer.class)); // 6
                            IO.println(Coeffect.get(String.class)); // Holo
                        });
                IO.println(Coeffect.get(Integer.class)); // 3
                IO.println(Coeffect.get(String.class)); // Holo
            });
}
```

### Inheritance

For similar reasoning as return types
and [checked exceptions](https://docs.oracle.com/javase/tutorial/essential/exceptions/runtime.html), the classes in
the `@WithContext` annotations
are [covariant](https://en.wikipedia.org/wiki/Covariance_and_contravariance_(computer_science)).

That means that if method `clazz::foo` is annotated with `@WithContext(...T)` (where `...T` means list of types),
and `clazz1` extends `clazz` as well as `clazz1::foo` is annotated with `@WithContext(...Z)` then we require that `...Z`
will be a subset of `...T`:

```java
import io.github.holo314.coeffect.compiletime.annotations.WithContext;

class clazz {
    @WithContext({String.class, Integer.class})
    void foo() {}
}

class class1
        extends clazz {
    // @WithContext({String.class, Integer.class}) // legal
    // @WithContext({String.class}) // legal
    // @WithContext({Integer.class}) // legal
    // @WithContext() // legal
    // No @WithContext at all, equivalent to @WithContext() // legal
    // @WithContext(CharSequence.class) // illegal, `CharSequence.class` does not appear in the `@WithContext` annotation of `clazz::foo`
    @Override
    void foo() {}
}
```

Similar thing is true about `interface`s and `implementation`

### Threads

`Coeffect` is built upon `ScopedValue` that comes with project Loom to
complement [Structured Concurrency](https://openjdk.org/jeps/505), that means that all work with threads and `Coeffect`
together should use Structured Concurrency, any use of non-Structured Concurrency can cause false positives.

## The `Coeffect.Carrier` object

When first binding an object using `Coeffect#with` the return type is `Carrier<>`.

This object is an immutable object contains within it both the actual stacks, and the types that your bound, so:

```java
import io.github.holo314.coeffect.runtime.Coeffect;

void main() {
    var carrier = Coeffect.with(":|");
    carrier.with("|:");
    carrier.run(() -> IO.println(Coeffect.get(String.class))); // print ":|"
}
```

Like mentioned above, this object holds the types that got bound, you can see that if you are use explicit typing, instead
of `var`:

```java
import io.github.holo314.coeffect.runtime.Coeffect;

class Example {
    void foo() {
        Coeffect.Carrier<String, Coeffect.Carrier<Void, Coeffect.Carrier<?, ?>>> carrier = Coeffect.with(":|");
        carrier.with("|:");
        carrier.run(() -> IO.println(Coeffect.get(String.class))); // print ":|"
    }
}
```

The `Coeffect` annotation processor uses this type as a linked list:

```
null                            ⇔ Coeffect.Carrier<?, ?>
Node(Void, null)                ⇔ Coeffect.Carrier<Void, null>                  ⇔ Coeffect.Carrier<Void, Coeffect.Carrier<?, ?>>
Node(String, Node(Void, null))  ⇔ Coeffect.Carrier<String, Node(Void, null)>    ⇔ Coeffect.Carrier<String, Coeffect.Carrier<Void, null>> ⇔ Coeffect.Carrier<String, Coeffect.Carrier<Void, Coeffect.Carrier<?, ?>>>
```

Using this linked list it checks which types you used but didn't bind. This is why **you should never downcast the
carrier object**.

### Passing `Coeffect.Carrier` as a parameter

It is possible to think of `Coeffect.Carrier` as a set of types that represent some context, each instance
of `Coeffect.Carrier` is a context contains the parameters that you can use.

It is technically possible to pass `Coeffect.Carrier` as a parameter to a method, but **you should never
do this**.

First of all, if you pass the `Coeffect.Carrier` object it means you can already pass normal parameters, so there is really never a need for that.

Secondly it is very ugly, as discussed above the library uses the generic type of `Coeffect.Carrier` as a kind of "compile time linked list", so the full name of the type of the `Coeffect.Carrier` contains all the types that are bound to it.

Thirdly, as a result of the second point, passing the `Coeffect.Carrier` object as a parameter couples the function that receives it to *every* type it contains, which both restrict the usage of the function, and more importantly expose other dependencies to it which can cause a security risk.

Instead, any method that receive a `Coeffect.Carrier` parameter should transform it into `@WithContext` annotation:

```java
import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.runtime.Coeffect;

void main() {
    bar(Coeffect.with(":'("));
}

void bar(Coeffect.Carrier<String, Coeffect.Carrier<Void, Coeffect.Carrier<?, ?>>> x) {
    x.run(this::qux);
}

@WithContext(String.class)
void qux() {
    System.out.println(Coeffect.get(String.class));
}
```

**Into**

```java
import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.runtime.Coeffect;

void main() {
    Coeffect.with(":')").run(this::bar);
}

@WithContext(String.class)
void bar() {
    qux();
}

@WithContext(String.class)
void qux() {
    System.out.println(Coeffect.get(String.class));
}
```

## Lambdas

Lambdas by default works like checked exceptions with lambdas. Consider the following situation
```java
static class IntTransformer {
    ArrayList<Function<Integer, Integer>> transformers = new ArrayList<>();

    public void addTransformer(Function<Integer, Integer> transform) {
        transformers.add(transform);
    }

    public int run(int i) {
        for (var t : transformers) {
            i = t.apply(i);
        }
        return i;
    }
}
```

If we want to use it with combination of `Coeffect`:

```java
import io.github.holo314.coeffect.runtime.Coeffect;

void main() {
    var x = new IntTransformer();
    x.addTransformer(r -> {
        var z = Coeffect.get(Integer.class); // !!
        return r + z;
    });
    x.run();
}
```

We get a compile time exception because the lambda expression has type `Function<>`, but the abstract method of `Function<>`, `Runnable<>#apply`, does not declare `@WithContext(Integer.class)`.

But if we create

```java
@FunctionalInterface
interface FuncWithIntegerContext<T, R> {
    @WithContext(Integer.class)
    R apply(T t);
    
    // Default methods omitted for brevity
}
```

And use `FuncWithIntegerContext<>` instead of `Function<>` in `IntTransformer`:

```java
import io.github.holo314.coeffect.runtime.Coeffect;

static class IntTransformer {
    ArrayList<FuncWithIntegerContext<Integer, Integer>> transformers = new ArrayList<>();

    public void addTransformer(FuncWithIntegerContext<Integer, Integer> transform) {
        transformers.add(transform);
    }

    public int run(int i) {
        for (var t : transformers) {
            i = t.apply(i); // !!
        }
        return i;
    }
}

void main() {
    var x = new IntTransformer();
    x.addTransformer(r -> {
        var z = Coeffect.get(Integer.class); // OK
        return r + z;
    });
    x.run();
}
```
The compiler now will complain on `IntTranformer#run` because it uses `FuncWithIntegerContext<>#apply` without the necessary context, so we need to now add `@WithContext(Integer.class)` to that method:

```java
import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.runtime.Coeffect;

static class IntTransformer {
    ArrayList<FuncWithIntegerContext<Integer, Integer>> transformers = new ArrayList<>();

    public void addTransformer(FuncWithIntegerContext<Integer, Integer> transform) {
        transformers.add(transform);
    }

    @WithContext(Integer.class)
    public int run(int i) {
        for (var t : transformers) {
            i = t.apply(i); // OK
        }
        return i;
    }
}

void main() {
    var x = new IntTransformer();
    x.addTransformer(r -> {
        var z = Coeffect.get(Integer.class); // OK
        return r + z;
    });
    x.run(); // !!
}
```

And finally the compiler will complain about our `x.run()` line because `IntTransformer#run` has `Integer.class` in the context.

### Bypassing the plugin

In some cases you have a method `myFunc(Function<> f)` which I know does not store `f` anywhere, but `myFunc` uses `f` inside of it. In that case you can tell the plugin to treat context that `f` uses as context that `myFunc` uses instead.

**WARNING:** the following feature can be used to bypass the compiler plugin type checking and cause illegal accesses to `ScopedValue`s, leakage, and even security breach. Only use it if you are completely sure that the lambda parameter is not accessible from anywhere outside of that specific method.

Consider the following scenario:
```java
void retry(Runnable func, RetryConfig config) {
    // implement retry logic
}
```

You want to be able to use the `retry` method for all sort of functions, and you want the `retry` method to be reusable and not coupled to any context.

As explained in the first part of the Lambda section, the following will fail:

```java
import io.github.holo314.coeffect.runtime.Coeffect;

void main() {
    Coeffect.with(7).run(() -> 
            retry(() -> Coeffect.get(Integer.class), new RetryConfig()));
}
```

Because when the compiler sees the `Coeffect.get(Integer.class)` it sees that it is inside a lambda, so it checks what interface the lambda implements, in this case `Runnable`, find the Abstract Method of the interface, in this case `Runnable#run`, and checks the `@WithContext` of that method.

To fix this, we can annotate our `retry` annotation with `@UseInplace`

```java
import io.github.holo314.coeffect.compiletime.annotations.DelegateContext;
import io.github.holo314.coeffect.compiletime.annotations.UseInplace;
import io.github.holo314.coeffect.runtime.Coeffect;

@DelegateContext
void retry(Runnable func, RetryConfig config) {
    // implement retry logic
}

void main() {
    Coeffect.with(7).run(() ->
            retry(() -> Coeffect.get(Integer.class), new RetryConfig()));
}
```

This will now compile. Because the method is annotated with `@DelegateContext`, when you pass a lambda expression into it as a parameter, the compiler assumes that the lambda expression will run inside the method (the compiler also assumes that the lambda won't outside of that method **but it cannot check this**, it trust the programmer to not leak the lambda from inside the method).

The annotation also allows you to specify which of the arguments of the method should be delegated, either using the variable name, or the variable position:

```java
import io.github.holo314.coeffect.compiletime.annotations.DelegateContext;
import io.github.holo314.coeffect.compiletime.annotations.UseInplace;
import io.github.holo314.coeffect.runtime.Coeffect;

@DelegateContext(variableNames = {"func"})
// @DelegateContext(variablePositions = {0})
void retry(Runnable func, Function<RetryConfig, RetryConfig> configProvider) {
    // implement retry logic
}

void main() {
    Coeffect.with(7).run(() ->
            retry(() -> Coeffect.get(Integer.class), conf -> conf));
}
```

---


## Effects

The name `Coeffect` comes, unsurprisingly, from [`Effect` system](https://en.wikipedia.org/wiki/Effect_system).

Java does have a (partial) Effect System,
the [checked exceptions](https://docs.oracle.com/javase/tutorial/essential/exceptions/runtime.html), the difference
between an effect and a coeffect is relatively thin, I hope in the future to give the `Coeffect` type system the same
strength as Checked Exceptions

There are languages that are completely built upon an Effect System, for
example [Koka](https://koka-lang.github.io/koka/doc/index.html) and [Effekt](https://effekt-lang.org/).

---

## Usage

The project run and tested on Java 25, the first version where `ScopedValue` was finalised.

The plugin and library are available
in [Maven central](https://central.sonatype.com/artifact/io.github.holo314/Coeffect) and requires Error-prone.

### Gradle
#### Library
For the runtime only simply add

```kotlin
implementation("io.github.holo314:Coeffect:1.2.0")
```

to the dependencies section of the `gradle.build(.kts)` file

#### Plugin

To enable the compile type type checks you need to add the `errorprone` plugin and dependency:
```kotlin
plugins {
    id("java")
    id("application")
    id("net.ltgt.errorprone") version "<current version>"
}
    ...

dependencies {
    errorprone("com.google.errorprone:error_prone_core:2.45.0")
    
    errorprone("io.github.holo314:Coeffect:1.2.0")
    implementation("io.github.holo314:Coeffect:1.2.0")
}
```

`errorprone` has several built in plugins out of the box, to disable them all and enable only this plugin you need to change the `JavaCompile` task:

First add `import net.ltgt.gradle.errorprone.CheckSeverity` and `import net.ltgt.gradle.errorprone.errorprone` to the `build.gradle(.kts)` file and then add the followings:

```groovy
> build.gradle
tasks.withType(JavaCompile).configureEach {
    options.errorprone.disableAllChecks.set(true)
    options.errorprone.check('Coeffect', CheckSeverity.DEFAULT)
}
```

```kotlin
> build.gradle.kts
tasks.withType<JavaCompile>().configureEach {
    options.errorprone.disableAllChecks.set(true)
    options.errorprone.check("Coeffect" to CheckSeverity.DEFAULT)
}
```

Here is a minimal working `build.gradle.kts` file:

```kotlin
import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("java")
    id("application")
    id("net.ltgt.errorprone") version "4.3.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

application {
    mainClass = "PoC"
}


group = "org.holo"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    errorprone("com.google.errorprone:error_prone_core:2.45.0")
    errorprone("io.github.holo314:Coeffect:1.2.0")
    implementation("io.github.holo314:Coeffect:1.2.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone.disableAllChecks.set(true)
    options.errorprone.check("Coeffect" to CheckSeverity.DEFAULT)
}
```

### Maven

#### Library

To use the library itself first add to your `pom.xml` the following dependency:

```xml

<dependency>
    <groupId>io.github.holo314</groupId>
    <artifactId>Coeffect</artifactId>
    <version>{coeffect.version}</version>
</dependency>
```

#### Plugin

To run the plugin you need to add the following section to your `maven-compiler-plugin`:

```xml

<configuration>
    ...
    <compilerArgs>
        <arg>-XDcompilePolicy=simple</arg>
        <arg>-Xplugin:ErrorProne -XepDisableAllChecks -Xep:Coeffect</arg>
    </compilerArgs>
    <annotationProcessorPaths>
        <path>
            <groupId>io.github.holo314</groupId>
            <artifactId>Coeffect</artifactId>
            <version>{coeffect.version}</version>
        </path>
        <path>
            <groupId>com.google.errorprone</groupId>
            <artifactId>error_prone_core</artifactId>
            <version>${errorprone.version}</version>
        </path>
    </annotationProcessorPaths>
</configuration>
```

The `-XepDisableAllChecks` flag is optional, it is there to disable all the default Error-Prone checks
