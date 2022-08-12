package test.io.github.holo314.coeffect.testdata;

import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.runtime.Coeffect;

import java.io.Serializable;

public class Test implements Serializable {
    @WithContext({java.lang.String.class})
    public void foo() {}

    public void foo(int x) {}

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
}

interface Test0 extends Runnable{
    @WithContext(Test0.class)
    void foo(char z);
}

class Test1
        extends Test
        implements Test0 {
    @WithContext(String.class)
    @Override
    public void foo() {}

    @WithContext({Test0.class})
    @Override
    public void foo(char z) {

    }

    @Override
    public void run() {

    }
}