package io.github.holo314.coeffect.compiletime.annotations;

import java.lang.annotation.*;

/**
 * This interface is used on methods/functions to represent what coeffects bindings need to present before calling this method.<br>
 * For example:<br><br>
 *
 * The following
 * <pre>
 *    public static void foo() {
 *        var x = Coeffect.get(String.class);
 *        ...
 *    }
 * </pre>
 * should look like:
 * <pre>
 *    &#064;WithContext({String.class})
 *    public static void foo() {
 *        var x = Coeffect.get(String.class);
 *        ...
 *    }
 * </pre>
 * Because if one calls {@code foo} without first binding {@code String.class}, the execution will fail.
 * But:
 * <pre>
 *    public static void foo() {
 *        Coeffect.with("Holo")
 *                  .run(() -> {
 *                      var x = Coeffect.get(String.class);
 *                      ...
 *                  });
 *    }
 * </pre>
 * Does not need the annotation, because the caller of the function does not need to bind {@code String.class} for {@code foo} to run successfully
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface WithContext {
    Class<?>[] value();
}
