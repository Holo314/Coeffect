package test.org.holo.coeffect;

import com.google.errorprone.CompilationTestHelper;
import org.holo.coeffect.compiletime.annotations.WithContext;
import org.holo.coeffect.compiletime.plugin.CoeffectPlugin;
import org.holo.coeffect.runtime.Coeffect;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ContextTest {
    @Test
    public void coeffect()
            throws IOException {
        var source0 = "test/org/holo/coeffect/testdata/Test.java";

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
