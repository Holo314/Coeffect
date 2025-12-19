package test.io.github.holo314.coeffect;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.errorprone.CompilationTestHelper;
import io.github.holo314.coeffect.compiletime.annotations.UseInplace;
import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.compiletime.plugin.CoeffectPlugin;
import io.github.holo314.coeffect.runtime.Coeffect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class CompiletimeContextTest {
    private static final String BASE = "test/io/github/holo314/coeffect/testdata/";

    @Test
    public void inheritance()
            throws IOException {
        var source0 = BASE + "InheritanceTest.java";

        var compilationHelper = CompilationTestHelper.newInstance(CoeffectPlugin.class, getClass());
        compilationHelper.addSourceLines(
                        source0,
                        Files.readAllLines(Path.of("src/test/java/" + source0))
                                .toArray(String[]::new)
                )
                .withClasspath(Coeffect.class, Coeffect.Carrier.class, WithContext.class)
                .expectErrorMessage("Inheritance", (error ->
                        error.replaceAll("\\s", "")
                                .contentEquals("""
                                        [Coeffect] Method requires [java.lang.CharSequence, java.lang.String, test.io.github.holo314.coeffect.testdata.InheritanceTest0] but implements:
                                              test.io.github.holo314.coeffect.testdata.InheritanceTest#foo(char) which requires []. Remove [java.lang.CharSequence, java.lang.String, test.io.github.holo314.coeffect.testdata.InheritanceTest0] from the current method context or add it to the context oftest.io.github.holo314.coeffect.testdata.InheritanceTest#foo(char)
                                              test.io.github.holo314.coeffect.testdata.InheritanceTest0#foo(char) which requires [java.lang.CharSequence]. Remove [java.lang.String, test.io.github.holo314.coeffect.testdata.InheritanceTest0] from the current method context or add it to the context oftest.io.github.holo314.coeffect.testdata.InheritanceTest0#foo(char)
                                            (see https://github.com/Holo314/coeffect)
                                        """.replaceAll("\\s", ""))))
                .doTest();
    }

    @Test
    public void missingContext()
            throws IOException {
        var source0 = BASE + "ContextTest.java";
        var errorPrefix = "[Coeffect] ";
        var errorSuffix = """
                            
                            \t(see https://github.com/Holo314/coeffect)""";

        var compilationHelper = CompilationTestHelper.newInstance(CoeffectPlugin.class, getClass());
        compilationHelper.addSourceLines(
                        source0,
                        Files.readAllLines(Path.of("src/test/java/" + source0))
                                .toArray(String[]::new)
                )
                .withClasspath(Coeffect.class, Coeffect.Carrier.class, WithContext.class, UseInplace.class)
                .expectErrorMessage("Context0", (error -> {
                    var callExpression = "foo";
                    var args = String.join(", ", List.of());

                    var explicitlyBounded = Set.<String>of();
                    var enclosingBounds = Set.<String>of();
                    var requirements = Set.of(String.class.getCanonicalName());

                    var bounds = Sets.union(explicitlyBounded, enclosingBounds);
                    var missings = Sets.difference(requirements, bounds);


                    var msg = new StringBuilder()
                            .append("Missing requirements in `")
                            .append(callExpression)
                            .append("(")
                            .append(args)
                            .append(")`. Required types for the call: ")
                            .append(Iterables.toString(requirements.stream().sorted().toList())) // all sets are sorted for consistent tests
                            .append(", bounded types: ")
                            .append(Iterables.toString(explicitlyBounded.stream().sorted().toList()))
                            .append(", context types: ")
                            .append(Iterables.toString(enclosingBounds.stream().sorted().toList()))
                            .append(", missing types: ")
                            .append(Iterables.toString(missings.stream().sorted().toList()))
                            .append(". Either bind the missing types with Coeffect#with method before call the method or add the missing types to the context of the current method via @WithContext annotation.")
                            .toString();


                    return error.replaceAll("\\s", "").contentEquals(
                            (errorPrefix + msg + errorSuffix).replaceAll("\\s", ""));
                }))
                .expectErrorMessage("Context1", (error -> {
                    var callExpression = "foo";
                    var args = String.join(", ", List.of("'h'"));

                    var explicitlyBounded = Set.of(Integer.class.getCanonicalName());
                    var enclosingBounds = Set.<String>of();
                    var requirements = Set.of(String.class.getCanonicalName());

                    var bounds = Sets.union(explicitlyBounded, enclosingBounds);
                    var missings = Sets.difference(requirements, bounds);


                    var msg = new StringBuilder()
                            .append("Missing requirements in `")
                            .append(callExpression)
                            .append("(")
                            .append(args)
                            .append(")`. Required types for the call: ")
                            .append(Iterables.toString(requirements.stream().sorted().toList())) // all sets are sorted for consistent tests
                            .append(", bounded types: ")
                            .append(Iterables.toString(explicitlyBounded.stream().sorted().toList()))
                            .append(", context types: ")
                            .append(Iterables.toString(enclosingBounds.stream().sorted().toList()))
                            .append(", missing types: ")
                            .append(Iterables.toString(missings.stream().sorted().toList()))
                            .append(". Either bind the missing types with Coeffect#with method before call the method or add the missing types to the context of the current method via @WithContext annotation.")
                            .toString();


                    return error.replaceAll("\\s", "").contentEquals(
                            (errorPrefix + msg + errorSuffix).replaceAll("\\s", ""));

                }))
                .doTest();
    }

    @Test
    public void lambda()
            throws IOException {
        var source0 = BASE + "LambdaTest.java";

        var compilationHelper = CompilationTestHelper.newInstance(CoeffectPlugin.class, getClass());
        compilationHelper.addSourceLines(
                        source0,
                        Files.readAllLines(Path.of("src/test/java/" + source0))
                                .toArray(String[]::new)
                )
                .withClasspath(Coeffect.class, Coeffect.Carrier.class, WithContext.class)
                .doTest();
    }

    @Test
    public void exoticBlocks()
            throws IOException {
        var source0 = BASE + "NonMethodTest.java";
        var compilationHelper = CompilationTestHelper.newInstance(CoeffectPlugin.class, getClass());
        compilationHelper.addSourceLines(
                        source0,
                        Files.readAllLines(Path.of("src/test/java/" + source0))
                                .toArray(String[]::new)
                )
                .withClasspath(Coeffect.class, Coeffect.Carrier.class, WithContext.class)
                .doTest();
    }
}
