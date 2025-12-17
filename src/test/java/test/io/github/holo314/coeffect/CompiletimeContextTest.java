package test.io.github.holo314.coeffect;

import com.google.common.collect.Iterables;
import com.google.errorprone.CompilationTestHelper;
import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.compiletime.plugin.CoeffectPlugin;
import io.github.holo314.coeffect.runtime.Coeffect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.github.holo314.coeffect.compiletime.plugin.CoeffectPlugin.withCounter;

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

        var compilationHelper = CompilationTestHelper.newInstance(CoeffectPlugin.class, getClass());
        compilationHelper.addSourceLines(
                        source0,
                        Files.readAllLines(Path.of("src/test/java/" + source0))
                                .toArray(String[]::new)
                )
                .withClasspath(Coeffect.class, Coeffect.Carrier.class, WithContext.class)
                .expectErrorMessage("Context0", (error -> {
                    var missings =
                            List.of(String.class.getCanonicalName());

                    var wither = "@WithContext({" + Iterables.toString(missings.stream().sorted().toList()) // transform to sorted list for tests
                            .replaceAll("[\\[\\]]", "") + ", ...})"
                            + "public void qux() {...}";
                    var expected = new StringBuilder()
                            .append("[Coeffect] Missing requirements in `foo()`: ")
                            .append(Iterables.toString(missings.stream().sorted().toList())) // transform to sorted list for tests
                            .append(System.lineSeparator())
                            .append("\t")
                            .append("Add the requirements to the context or wrap it with run/call:")
                            .append(System.lineSeparator())
                            .append("\t\t")
                            .append(wither.replace("\n", "\n\t\t"))
                            .append("---")
                            .append(System.lineSeparator())
                            .append("\t\t");

                    var with = new StringBuilder().append("Coeffect");
                    missings.stream().sorted().forEach(withCounter((i, missing) -> {
                        var typeSplit = missing.split("[.]");
                        var type = typeSplit[typeSplit.length - 1];
                        with.append(".with(")
                                .append("v")
                                .append(type)
                                .append(i)
                                .append(")")
                                .append(System.lineSeparator())
                                .append("\t\t\t\t");
                    }));
                    var call = with + ".call(() -> ...);";
                    var run = with + ".run(() -> ...);";

                    expected.append(run)
                            .append(System.lineSeparator())
                            .append("---")
                            .append(System.lineSeparator())
                            .append("\t\t")
                            .append(call)
                            .append(System.lineSeparator())
                            .append("    (see https://github.com/Holo314/coeffect)");


                    return error.replaceAll("\\s", "")
                            .contentEquals(expected.toString().replaceAll("\\s", ""));
                }))
                .expectErrorMessage("Context1", (error -> {
                    var missings =
                            List.of(String.class.getCanonicalName());

                    var wither = "@WithContext({" + Iterables.toString(missings.stream().sorted().toList()) // transform to sorted list for tests
                            .replaceAll("[\\[\\]]", "") + ", ...})"
                            + "public void qux() {...}";
                    var expected = new StringBuilder()
                            .append("[Coeffect] Missing requirements in `foo('h')`: ")
                            .append(Iterables.toString(missings.stream().sorted().toList())) // transform to sorted list for tests
                            .append(System.lineSeparator())
                            .append("\t")
                            .append("Add the requirements to the context or wrap it with run/call:")
                            .append(System.lineSeparator())
                            .append("\t\t")
                            .append(wither.replace("\n", "\n\t\t"))
                            .append("---")
                            .append(System.lineSeparator())
                            .append("\t\t");

                    var with = new StringBuilder().append("Coeffect");
                    missings.stream().sorted().forEach(withCounter((i, missing) -> {
                        var typeSplit = missing.split("[.]");
                        var type = typeSplit[typeSplit.length - 1];
                        with.append(".with(")
                                .append("v")
                                .append(type)
                                .append(i)
                                .append(")")
                                .append(System.lineSeparator())
                                .append("\t\t\t\t");
                    }));
                    var call = with + ".call(() -> ...);";
                    var run = with + ".run(() -> ...);";

                    expected.append(run)
                            .append(System.lineSeparator())
                            .append("---")
                            .append(System.lineSeparator())
                            .append("\t\t")
                            .append(call)
                            .append(System.lineSeparator())
                            .append("    (see https://github.com/Holo314/coeffect)");


                    return error.replaceAll("\\s", "")
                            .contentEquals(expected.toString().replaceAll("\\s", ""));
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
                .expectErrorMessage("Lambda", (error -> {
                    var missings =
                            List.of(String.class.getCanonicalName());

                    var wither = "@WithContext({" + Iterables.toString(missings.stream().sorted().toList()) // transform to sorted list for tests
                            .replaceAll("[\\[\\]]", "") + ", ...})"
                            + "public void qux() {...}";
                    var expected = new StringBuilder()
                            .append("[Coeffect] Missing requirements in `foo('h')`: ")
                            .append(Iterables.toString(missings.stream().sorted().toList())) // transform to sorted list for tests
                            .append(System.lineSeparator())
                            .append("\t")
                            .append("Add the requirements to the context or wrap it with run/call:")
                            .append(System.lineSeparator())
                            .append("\t\t")
                            .append(wither.replace("\n", "\n\t\t"))
                            .append("---")
                            .append(System.lineSeparator())
                            .append("\t\t");

                    var with = new StringBuilder().append("Coeffect");
                    missings.stream().sorted().forEach(withCounter((i, missing) -> {
                        var typeSplit = missing.split("[.]");
                        var type = typeSplit[typeSplit.length - 1];
                        with.append(".with(")
                                .append("v")
                                .append(type)
                                .append(i)
                                .append(")")
                                .append(System.lineSeparator())
                                .append("\t\t\t\t");
                    }));
                    var call = with + ".call(() -> ...);";
                    var run = with + ".run(() -> ...);";

                    expected.append(run)
                            .append(System.lineSeparator())
                            .append("---")
                            .append(System.lineSeparator())
                            .append("\t\t")
                            .append(call)
                            .append(System.lineSeparator())
                            .append("    (see https://github.com/Holo314/coeffect)");


                    return error.replaceAll("\\s", "")
                            .contentEquals(expected.toString().replaceAll("\\s", ""));
                }))
                .doTest();
    }
}
