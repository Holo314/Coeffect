package test.io.github.holo314.coeffect.testdata;

import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.runtime.Coeffect;

import java.util.ArrayList;

public class LambdaTest {
    void foo() {
        var x = new IntTransformer();
        // no problem here, IntTransformer#transform doesn't use any method that requires context
        x.transform(r -> {
            var z = Coeffect.get(Integer.class);
            return r + z;
        });
    }
}

@FunctionalInterface
interface FuncWithIntegerContext<T, R> {
    @WithContext(Integer.class)
    R apply(T t);
    default void holo() {}
}

class IntTransformer {
    ArrayList<FuncWithIntegerContext<Integer, Integer>> transformers = new ArrayList<>();

    public void transform(FuncWithIntegerContext<Integer, Integer> transform) {
        transformers.add(transform);
    }

    @WithContext(Integer.class)
    public int run(int i) {
        for (var t : transformers) {
            i = t.apply(i);
        }
        return i;

        // also works:
        // Coeffect.with(3)
        //         .call(() -> {
        //             var result = i;
        //             for (var t : transformers) {
        //                 result = t.apply(result);
        //             }
        //             return result;
        //         });

        // also works:
        // for (var t : transformers) {
        //     var temp = i;
        //     i = Coeffect.with(5).call(() -> t.apply(temp));
        // }
        // return i;
    }
}