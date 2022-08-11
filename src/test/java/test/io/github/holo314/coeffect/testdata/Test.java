package test.io.github.holo314.coeffect.testdata;

import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.runtime.Coeffect;

public class Test {
    @WithContext({java.lang.String.class})
    public static void foo() {}

    @WithContext({Integer.class})
    public static void bar() {
        var holo = Coeffect.with("Holo");
        holo.with("Myuri", CharSequence.class)
            .run(() -> {
                Coeffect.get(CharSequence.class);
                foo();
            });

        holo.run(Test::foo);
    }
}
