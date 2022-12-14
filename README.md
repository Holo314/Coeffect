# Coeffect

[![Maven Central](https://img.shields.io/maven-central/v/io.github.holo314/Coeffect.svg?label=Maven%20Central)](https://search.maven.org/artifact/io.github.holo314/Coeffect) [![license](https://img.shields.io/github/license/holo314/Coeffect)](https://www.apache.org/licenses/LICENSE-2.0)

Add a partial Coeffect system into Java using Loom's ExtentLocals.

---
In Java there are generally 2 strategies to manage the parameters a method needs:

1. Passing a value as a parameter
2. Having the value as fields of the class

Furthermore, to ensure thread safety we need to have more work.
For the first method the problem is less apparent, but for the latter it is much harder to deal with.

One way to ensure safety is to use
Java's [ThreadLocal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html), which
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

Project Loom has(/will) added [ExtentLocal](https://openjdk.org/jeps/8263012), which is basically a
structured `ThreadLocal`.

One of the most problematic parts of `ThreadLocal` and `ExtentLocal` is that we lose type safety. For `ThreadLocal` you
can get unexpected `null`s, and for `ExtentLocal` you would get an exception.

Any use of `ThreadLocal` or `ExtentLocal` should be attached to a null-check or binding-check.
Furthermore, have one of those 2 not being `private` creates coupling, security problems, ambiguous APIs.

On the other hand, sending dependencies as parameters have other problems, but the main two I want to talk about are:

1. Parameter bloating
2. Forced explicit binding

The first point is pretty clear, you can get methods with 5/6 parameters or more, which creates long signatures **as
well as long in the calling site**.

The second point is easier to miss, but here is an example:

```java
class clazz {
    public static void main(String[] args) {
        foo(666);
    }

    public static void foo(int x) {
        bar(x);
    }

    public static void bar(int x) {
        System.out.println(x);
    }
}
```

Notice that `foo` receive a parameter **only** to pass it to `bar`, it doesn't actually do anything with it.

---

# The "solution"

The solution this library offers is to create a (partial) [Coeffect System](http://tomasp.net/coeffects/).

The idea is to use `ExtentLocal` and a compiler plugin to add safety and explicitness.

`Implementation note:` It is impossible to create this system with `ThreadLocal` because there is no control over the
call of `ThreadLocal#remove`.

Before diving into the details, let's see how the above example will look like:

```java
class clazz {
    public static void main(String[] args) {
        Coeffect.with(666)
                .run(() -> foo());
    }

    @WithContext(Integer.class)
    public static void foo() {
        bar();
    }

    @WithContext(Integer.class)
    public static void bar() {
        System.out.println(Coeffect.get(Integer.class));
    }
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

Note that all of those points are **enforced at compile time**, remove any of the `@WithContext` and the compiler will
yell at you.

---

# The details

There are few basic rules one should keep in mind, let's go over them (every time I say `run`, it also applies to `call`
, which is the same but also returns a value):

### Enforcing of `Coeffect#get`

Any and all calls of `Coeffect.get(T)` must satisfy one of the 2 following conditions:

1. Inside of `Coeffect.with(T)#run` block
2. Inside a method annotated with `@WithContext(T)`

### Methods annotated with `@WithContext`

Any use of a method annotated with `@WithContext` act similarly to `Coeffect#get`, with the exception
that `@WithContext` can receive several types.

### `Coeffect` stacks

Coeffect internally saves an `ExtentLocal` instance for each `Class<T>`.
When calling `Coeffect.with(v)` it adds `v` to the top of the stack of `v.getClass()`.

### The value of `Coeffect#get`

When calling `Coeffect.get(T)` it will return the top value in the stack of `T`. Note that this is a peek, it does not
remove it from the stack.

`Implemention note:` `Coeffect#get` should be used only with Class literals, e.g. `String.class`, and
not `"hi".getClass()`, using non-class literals can either fail at complication, or create false negatives.

### Extents

The lifetime of every binding is exactly the `Coeffect.Carrier#run` clause:

```java
class clazz {
    void foo() {
        Coeffect.with(3)
                .with("Holo")
                .run(() -> {
                    Coeffect.with(6)
                            .run(() -> {
                                Coeffect.get(Integer.class); // 6
                                Coeffect.get(String.class); // Holo
                            });
                    Coeffect.get(Integer.class); // 3
                    Coeffect.get(String.class); // Holo
                });
    }
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
    @WithContext(CharSequence.class)
    // illegal, `CharSequence.class` does not appear in the `@WithContext` annotation of `clazz::foo`
    @Override
    void foo() {}
}
```

Similar thing is true about `interface`s and `implementation`

### Threads

One of the most complicated parts of programming is multiprocessing, be it with threads/continuations or any other
implementation.

`Coeffect` is built upon `ExtentLocal` that comes with project Loom to
complement [Structured Concurrency](https://openjdk.org/jeps/428), that means that all work with threads and `Coeffect`
together should use Structured Concurrency, any use of non-Structured Concurrency can cause false positives.

## The `Coeffect.Carrier` object

When first binding an object using `Coeffect#with` the return type is `Carrier<>`.

This object is an immutable object contains within it both the actual stacks, and the types that your bound, so:

```java
import io.github.holo314.coeffect.runtime.Coeffect;

class Example {
    void foo() {
        var carrier = Coeffect.with(":|");
        carrier.with("|:");
        carrier.run(() -> System.out.println(Coeffect.get(String.class))); // print ":|"
    }
}
```

Like I said above, this object holds the types that got bound, you can see that if you are use explicit typing, instead
of `var`:

```java
import io.github.holo314.coeffect.runtime.Coeffect;

class Example {
    void foo() {
        // Thanks god for type inference
        Coeffect.Carrier<String, Coeffect.Carrier<Void, Coeffect.Carrier<?, ?>>> carrier = Coeffect.with(":|");
        carrier.with("|:");
        carrier.run(() -> System.out.println(Coeffect.get(String.class))); // print ":|"
    }
}
```

The `Coeffect` plugin uses this type as a linked list:

```
null                            ??? Coeffect.Carrier<?, ?>
Node(Void, null)                ??? Coeffect.Carrier<Void, null>                  ??? Coeffect.Carrier<Void, Coeffect.Carrier<?, ?>>
Node(String, Node(Void, null))  ??? Coeffect.Carrier<String, Node(Void, null)>    ??? Coeffect.Carrier<String, Coeffect.Carrier<Void, null>> ??? Coeffect.Carrier<String, Coeffect.Carrier<Void, Coeffect.Carrier<?, ?>>>
```

Using this linked list it checks which types you used but didn't bind. This is why **you should never downcast the
carrier object**.

### Passing `Coeffect.Carrier` as a parameter

It is possible to think of `Coeffect.Carrier` as a set of types that represent some context, each instance
of `Coeffect.Carrier` represent a set of parameters that you can use explicitly.

This is why it may be sometimes tempting to pass `Coeffect.Carrier` as a parameter to a method, but **you should never
do this**.

This is several reasons, the first and most important of them is: the whole point of this library is to avoid passing
contextual objects as parameters to a method. Passing `Coeffect.Carrier` as a parameter is basically using
the `Coeffect` system to implement parameters!

Instead, any method that receive a `Coeffect.Carrier` parameter should transform it into `@WithContext` annotation:

```java
import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.runtime.Coeffect;

class Example {
    void foo() {
        bar(Coeffect.with(":'("));
    }

    void bar(Coeffect.Carrier<String, Coeffect.Carrier<Void, Coeffect.Carrier<?, ?>>> x) {
        x.run(Example::qux);
    }

    @WithContext(String.class)
    void qux() {
        System.out.println(Coeffect.get(String.class));
    }
}
```

**Into**

```java
import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.runtime.Coeffect;

class Example {
    void foo() {
        Coeffect.with(":')").run(Example::bar);
    }

    @WithContext(String.class)
    void bar() {
        qux();
    }

    @WithContext(String.class)
    void qux() {
        System.out.println(Coeffect.get(String.class));
    }
}
```

## Lambda's problem

Currently, annotation's parameters must be known at compiletime, that means that _there is not way to allow generics on
the annotation level_.

Why is this problematic? Let's take the following example:

```java
import java.util.ArrayList;
import java.util.function.Function;

public class IntTransformer {
    ArrayList<Function<Integer, Integer>> transformers = new ArrayList<>();

    public void transform(Function<Integer, Integer> transform) {
        transformers.add(map);
    }

    public List<Integer> run(int i) {
        for (var t: transformers) {
            i = t.apply(i);
        }
        return i;
    }
}
```

Now we want to use it with combination of `Coeffect`:

```java
import io.github.holo314.coeffect.runtime.Coeffect;

public class A {
    public static void main(String[] args) {
        var x = new intTransformer();
        x.transform(r -> {
            var z = Coeffect.get(Integer.class); // ?????
            return r + z;
        });
    }
}
```

We cannot dynamically bind objects to an effect, with generics we would "collect the effects" to the instance
of `IntTransformer` and "discharge" it on "run".

Because of that **the current implementation requires adding a context to the method that defines the lambda**.

I am open for suggestions for better solutions.

---

# Future Work and Extra notes

Currently, the compiletime component is a custom component of [error-prone](https://errorprone.info/) with is only an
analysing tool.

In the future I want to add a functionality for more fluent access to the stacks.

In particular, I want to be able to do something like the following:

```java
class clazz {
    void foo() {
        Coeffect.with(3)
                .with("Holo")
                .run(() -> {
                    Coeffect.with(6)
                            .run(() -> {
                                Integer.get(); // 6
                                String.get(); // Holo
                            });
                    Integer.get(); // 3
                    String.get(); // Holo
                });
    }
}
```

I was also toying with the idea of enabling _named coeffects_.

### Effects

The name `Coeffect` comes, unsurprisingly, from [`Effect` system](https://en.wikipedia.org/wiki/Effect_system).

Java does have a (partial) Effect System,
the [checked exceptions](https://docs.oracle.com/javase/tutorial/essential/exceptions/runtime.html), the difference
between an effect and a coeffect is relatively thin, I hope in the future to give the `Coeffect` type system the same
strength as Checked Exceptions

There are languages that are completely built upon an Effect System, for
example [Koka](https://koka-lang.github.io/koka/doc/index.html) and [Effekt](https://effekt-lang.org/).

---

## Usage

To use this project you first need to download [Early Access Java 19-loom](https://jdk.java.net/loom/). The project
currently use `build 19-loom+6-625`.

The plugin and library are available
in [Maven central](https://search.maven.org/artifact/io.github.holo314/Coeffect/1.0/jar) and requires Error-prone.

### Gradle

Because of [a missing feature](https://github.com/gradle/gradle/issues/20372) in gradle, it is not possible to use
arbitrary Java versions, in particular, early access releases don't work.

Hence, it is not possible to use it with Gradle

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

When running the program you need to add `--add-modules jdk.incubator.concurrent` to the JVM options.

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
