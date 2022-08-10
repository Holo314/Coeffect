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
                         .doTest();
    }
}
