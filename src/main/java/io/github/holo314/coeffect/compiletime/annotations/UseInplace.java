package io.github.holo314.coeffect.compiletime.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * When a function with this annotation receive a lambda definition as an input, the plugin will search for Coeffect binding and Coeffect context in the place the method was called, instead of the definition of the interface the lambda implements.
 * Consider the case where a retry method:
 * <pre>
 *     public void runWithRetry(Runnable f) {
 *         try {
 *             f.run();
 *         } catch(Exception e) {
 *             f.run();
 *         }
 *     }
 * </pre>
 * <p>
 * By default, if one try to use the Coeffect framework with this function likes so:
 * <pre>
 *     void main() {
 *          Coeffect.with(7)
 *              .runWithRetry(() -> Coeffect.get(Integer.class));
 *     }
 * </pre>
 * It would fail because by default when Coeffect plugin encounter a lambda expression it works similarly to Checked-Exceptions, it checks that every Coeffect used in the lambda is declared in the definition of the interface the lambda implements, in this case {@link Runnable} and look at the {@code @WithContext} of the abstract method in that interface.
 * <p>
 * This annotation let you change the default implementation, this annotation declares to the compiler that the lambda is used inside the method, and that the context comes from where the method is being called, and not from the declaration of the interface:
 * <pre>
 *     &#064;UseInplace
 *     public void runWithRetry(Runnable f) {
 *         try {
 *             f.run();
 *         } catch(Exception e) {
 *             f.run();
 *         }
 *     }
 * </pre>
 * <p>
 * <pre>
 *     void main() {
 *          Coeffect.with(7)
 *              .runWithRetry(() -> Coeffect.get(Integer.class));
 *     }
 * </pre>
 * <p>
 * In case the method receive several lambdas you can specify to which of the parameters to apply the annotation either positionally via {@code varPositions} or using the name of the parameters via {@code varNames}, by default the annotation will apply to all parameters.
 * <p>
 * WARNING: The annotation enables a backdoor, if your method is annotated with this annotation and inside your method you save the lambda (e.g. in a field) then nothing stops you to use the lambda later in an illegal situation.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface UseInplace {
    int[] varPositions() default {};

    String[] varNames() default {};
}
