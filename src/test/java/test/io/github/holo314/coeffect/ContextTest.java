package test.io.github.holo314.coeffect;

import com.google.errorprone.CompilationTestHelper;
import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.compiletime.plugin.CoeffectPlugin;
import io.github.holo314.coeffect.runtime.Coeffect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ContextTest {
    @Test
    public void coeffect()
            throws IOException {
        var source0 = "test/io/github/holo314/coeffect/testdata/Test.java";

        var compilationHelper = CompilationTestHelper.newInstance(CoeffectPlugin.class, getClass());
        // TODO: add actual tests instead of this show-case example
        compilationHelper.addSourceLines(
                                 source0,
                                 Files.readAllLines(Path.of("src/test/java/" + source0))
                                      .toArray(String[]::new)
                         )
                         .withClasspath(Coeffect.class, Coeffect.Carrier.class, WithContext.class)
                         .expectErrorMessage("Inheritance", (error -> error.contentEquals("""
                                                                                          [Coeffect] Method requires [java.lang.CharSequence, test.io.github.holo314.coeffect.testdata.Test0, java.lang.String] but implements:
                                                                                                test.io.github.holo314.coeffect.testdata.Test0#foo(char) which requires [java.lang.CharSequence]. Remove [test.io.github.holo314.coeffect.testdata.Test0, java.lang.String] from the current method context or add it to the context oftest.io.github.holo314.coeffect.testdata.Test0#foo(char)
                                                                                                test.io.github.holo314.coeffect.testdata.Test#foo(char) which requires []. Remove [java.lang.CharSequence, test.io.github.holo314.coeffect.testdata.Test0, java.lang.String] from the current method context or add it to the context oftest.io.github.holo314.coeffect.testdata.Test#foo(char)
                                                                                              (see https://github.com/Holo314/coeffect)
                                                                                          """)))
                         .doTest();
    }
}
