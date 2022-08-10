package org.holo.coeffect.runtime;

import jdk.incubator.concurrent.ExtentLocal;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Coeffect has the same assumptions as ExtentLocal variables. <br>
 * A thread that is open in coeffect scope must terminate before the coeffect scope ends, and vice versa. <br>
 * when this is assumption is violated the best case scenario is an exception, worst case scenario is that the stack of the Coeffect will be shifted, which can cause both logical errors and security problems.
 */
public final class Coeffect {
    private static final Map<Class<?>, ExtentLocal<Object>> COEFFECT = new HashMap<>();

    static {
        COEFFECT.put(void.class, ExtentLocal.newInstance());
    }

    private static final ExtentLocal.Carrier extentCarrier = ExtentLocal.where(COEFFECT.get(void.class), null);

    /**
     * creates a new binding for type {@code value#getClass()}
     *
     * @param value the new value of the binding
     */
    public static Carrier with(Object value) {
        return new Carrier(extentCarrier).with(value);
    }

    /**
     * creates a new binding for type T
     *
     * @param value    the new binding of T
     * @param classKey use to bypass type-erasure, equals to {@code Class&lt;T&gt;}
     */
    public static <T> Carrier with(T value, Class<? extends T> classKey) {
        return new Carrier(extentCarrier).with(value, classKey);
    }

    /**
     * Create new binding for {@code classKey} with value {@code null}
     * Because of type erasure, we cannot use generic-with method to set values to null
     *
     * @param classKey use to bypass type-erasure, equals to {@code Class&lt;T&gt;}
     */
    public Carrier bindNull(Class<?> classKey) {
        return new Carrier(extentCarrier).bindNull(classKey);
    }


    private static void createInstance(Class<?> classKey) {
        COEFFECT.putIfAbsent(classKey, ExtentLocal.newInstance());
    }

    @SuppressWarnings({"unchecked"})
    public static <T> T get(Class<T> c)
            throws NoSuchElementException {
        createInstance(c);
        return (T)COEFFECT.get(c).get();
    }

    public static <T> T getOrNull(Class<T> c) {
        return getOrDefault(c, null);
    }

    public static <T> T getOrDefault(Class<T> c, T defaultValue) {
        return getOrSupply(c, () -> defaultValue);
    }

    @SuppressWarnings({"unchecked"})
    public static <T> T getOrSupply(Class<T> c, Supplier<T> defaultValue) {
        createInstance(c);
        var extent = COEFFECT.get(c);
        if (!extent.isBound()) {
            return defaultValue.get();
        }
        return (T)COEFFECT.get(c).get();
    }

    @SuppressWarnings({"ClassCanBeRecord"})
    public static class Carrier {
        private final ExtentLocal.Carrier innerCarrier;

        private Carrier(ExtentLocal.Carrier innerCarrier) {
            this.innerCarrier = innerCarrier;
        }

        /**
         * creates a new binding for type {@code value#getClass()}
         *
         * @param value the new value of the binding
         */
        public Carrier with(Object value) {
            if (value == null) {
                return this;
            }
            var classKey = value.getClass();
            createInstance(classKey);
            return new Carrier(innerCarrier.where(COEFFECT.get(classKey), value));
        }

        /**
         * creates a new binding for type T
         *
         * @param value    the new binding of T
         * @param classKey use to bypass type-erasure, equals to {@code Class&lt;T&gt;}
         */
        public <T> Carrier with(T value, Class<? extends T> classKey) {
            if (value == null) {
                return this;
            }

            createInstance(classKey);
            return new Carrier(innerCarrier.where(COEFFECT.get(classKey), value));
        }

        /**
         * Create new binding for {@code classKey} with value {@code null}
         * Because of type erasure, we cannot use generic-with method to set values to null
         *
         * @param classKey use to bypass type-erasure, equals to {@code Class&lt;T&gt;}
         */
        public Carrier bindNull(Class<?> classKey) {
            createInstance(classKey);
            return new Carrier(innerCarrier.where(COEFFECT.get(classKey), null));
        }

        public void run(Runnable op) {
            innerCarrier.run(op);
        }

        public <R> R call(Callable<R> op)
                throws Exception {
            return innerCarrier.call(op);
        }
    }
}

