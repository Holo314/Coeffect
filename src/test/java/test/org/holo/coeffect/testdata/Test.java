package test.org.holo.coeffect.testdata;

import org.holo.coeffect.compiletime.annotations.WithContext;
import org.holo.coeffect.runtime.Coeffect;

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
