package test.io.github.holo314.coeffect.testdata;

import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.runtime.Coeffect;

@SuppressWarnings("unused")
public class Test {
    @WithContext({java.lang.String.class,})
    public void foo() {}

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

    @WithContext(System.class)
    public void qux() {
        // BUG: Diagnostic matches: Context
        new Test1().foo('h');
    }
}

interface Test0 {
    @WithContext({CharSequence.class})
    void foo(char z);
}

class Test1
        extends Test
        implements Test0 {
    @WithContext(String.class)
    @Override
    public void foo() {}

    @WithContext({Test0.class, String.class, CharSequence.class})
    @Override
    // BUG: Diagnostic matches: Inheritance
    public void foo(char z) {

    }
}