package test.io.github.holo314.coeffect.testdata;

import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.runtime.Coeffect;

public class NonMethodTest {
    {
        Side.foo();
    }

    @WithContext(String.class)
    public NonMethodTest() {}
}

class Side {
    @WithContext(String.class)
    static String foo() {
        return Coeffect.get(String.class);
    }
}
