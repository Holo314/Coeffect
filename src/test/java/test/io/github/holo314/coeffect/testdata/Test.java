package test.io.github.holo314.coeffect.testdata;

import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.runtime.Coeffect;

public class Test {
    @WithContext({java.lang.String.class})
    public static void foo() {}

    @WithContext({Integer.class})
    public static void bar() {
        Coeffect
                .with("Holo")
                .with("Myuri", CharSequence.class)
                .run(() -> {
                    Coeffect.get(CharSequence.class);
                    foo();
                });
    }
}
