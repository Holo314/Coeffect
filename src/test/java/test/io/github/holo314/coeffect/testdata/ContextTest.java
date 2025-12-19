package test.io.github.holo314.coeffect.testdata;

import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.runtime.Coeffect;

@SuppressWarnings("unused")
public class ContextTest {
    @WithContext({java.lang.String.class})
    public void foo() {}

    @WithContext({String.class})
    public void foo(char x) {}

    @WithContext({Integer.class})
    public void bar() {
        var holo = Coeffect.with("Holo");
        holo.with("Myuri", CharSequence.class)
                .run(() -> {
                    Coeffect.get(CharSequence.class);
                    foo();
                });

        holo.run(this::foo);
    }

    public void qux() {
        // BUG: Diagnostic matches: Context0
        foo();
        // BUG: Diagnostic matches: Context1
        Coeffect.with(57).run(() -> new ContextTest1().foo('h'));
    }
}

class ContextTest1 extends ContextTest {
    @WithContext(String.class)
    @Override
    public void foo() {}

    @WithContext({String.class})
    @Override
    public void foo(char z) {}
}