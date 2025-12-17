package test.io.github.holo314.coeffect.testdata;

import io.github.holo314.coeffect.compiletime.annotations.WithContext;

@SuppressWarnings("unused")
public class InheritanceTest {
    @WithContext({java.lang.String.class,})
    public void foo() {}

    public void foo(char x) {}

    @WithContext({Integer.class})
    public void bar() {}
}

@SuppressWarnings("unused")
interface InheritanceTest0 {
    @WithContext({CharSequence.class})
    void foo(char z);
}

class InheritanceTest1
        extends InheritanceTest
        implements InheritanceTest0 {
    @WithContext(String.class)
    @Override
    public void foo() {}

    @WithContext({InheritanceTest0.class, String.class, CharSequence.class})
    @Override
    // BUG: Diagnostic matches: Inheritance
    public void foo(char z) {}
}