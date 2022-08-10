package test.org.holo.coeffect;

import jdk.incubator.concurrent.StructuredTaskScope;
import org.holo.coeffect.compiletime.annotations.WithContext;
import org.holo.coeffect.runtime.Coeffect;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.jupiter.api.Assertions.*;

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
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            scope.fork(() -> {
                Coeffect.with("Holo")
                        .run(() -> {
                            orderTest.set(0, 1);
                            assertEquals(1, orderTest.get(0));

                            try (var iScope = new StructuredTaskScope.ShutdownOnFailure()) {
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
                        });

                return null;
            });

            scope.fork(() -> {
                orderTest.set(1, 1);
                assertEquals(1, orderTest.get(1));
                Thread.sleep(500);

                assertEquals(1, orderTest.get(0));
                assertNull(Coeffect.getOrNull(String.class));
                return null;
            });

            scope.joinUntil(Instant.now().plus(2, ChronoUnit.SECONDS));
            scope.throwIfFailed();
        } catch (InterruptedException | ExecutionException e) {
            fail(e);
        } catch (TimeoutException e) {
            fail("Scope didn't end after 2s, should have ended after 1s", e);
        }
    }
}
