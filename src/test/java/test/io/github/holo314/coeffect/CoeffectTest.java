package test.io.github.holo314.coeffect;

import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.runtime.Coeffect;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("preview")
public class CoeffectTest {
    @Test
    @WithContext(value = {String.class, CharSequence.class})
    public void singleThread() {
        Coeffect.with("Lawrence")
                .run(() -> {
                    assertDoesNotThrow(() -> Coeffect.get(String.class));
                    assertEquals("Lawrence", Coeffect.get(String.class));

                    assertEquals("Holo", scopeDelegation());

                    assertThrowsExactly(NoSuchElementException.class, () -> Coeffect.get(CharSequence.class));

                    assertDoesNotThrow(() -> Coeffect.getOrNull(CharSequence.class));
                    assertNull(Coeffect.getOrNull(CharSequence.class));
                });
    }

    @WithContext(value = {CharSequence.class, String.class})
    static private CharSequence scopeDelegation() {
        try {
            return Coeffect.with("Holo", CharSequence.class)
                    .call(() -> {
                        assertDoesNotThrow(() -> Coeffect.get(String.class));
                        assertEquals("Lawrence", Coeffect.get(String.class));

                        assertDoesNotThrow(() -> Coeffect.get(CharSequence.class));
                        assertEquals("Holo", Coeffect.get(CharSequence.class));
                        return Coeffect.get(CharSequence.class);
                    });
        } catch (Exception e) {
            fail(e);
            return null; // unreachable
        }
    }

    @Test
    public void multiThread() {
        // Flags to represent order, used to verify order between threads
        var orderTest = new AtomicIntegerArray(2);
        try (var scope = StructuredTaskScope.open(Joiner.awaitAllSuccessfulOrThrow(), cf -> cf.withTimeout(Duration.ofSeconds(2)))) {
            scope.fork(() ->
                    Coeffect.with("Holo")
                            .run(() -> {
                                orderTest.set(0, 1);
                                assertEquals(1, orderTest.get(0));

                                try (var iScope = StructuredTaskScope.open()) {
                                    iScope.fork(() -> {
                                        // forking from an inner scope inherent all bindings
                                        assertEquals("Holo", Coeffect.getOrNull(String.class));
                                        return null;
                                    });
                                    iScope.join();
                                } catch (InterruptedException e) {
                                    fail(e);
                                }

                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    fail(e);
                                }

                                assertEquals(1, orderTest.get(1));
                                assertEquals("Holo", Coeffect.getOrNull(String.class));
                            }));
            scope.fork(() -> {
                orderTest.set(1, 1);
                assertEquals(1, orderTest.get(1));
                Thread.sleep(1500);

                assertEquals(1, orderTest.get(0));
                assertNull(Coeffect.getOrNull(String.class));
                return null;
            });
            scope.join();
        } catch (InterruptedException e) {
            fail(e);
        }
    }
}
