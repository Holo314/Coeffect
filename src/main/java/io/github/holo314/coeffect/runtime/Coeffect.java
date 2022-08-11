package io.github.holo314.coeffect.runtime;

import com.sun.tools.javac.code.Type;
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

    private static final ExtentLocal.Carrier baseExtentCarrier = ExtentLocal.where(COEFFECT.get(void.class), null);
    private static final Carrier<Void, Carrier<?, ?>> baseCarrier = new Carrier<>(baseExtentCarrier);

    /**
     * creates a new binding for type {@code value#getClass()}
     *
     * @param value the new value of the binding
     */
    public static <StartType> Carrier<StartType, Carrier<Void, Carrier<?, ?>>>
    with(StartType value) {
        return baseCarrier.with(value);
    }

    /**
     * creates a new binding for type StartType
     *
     * @param value    the new binding of StartType
     * @param classKey use to bypass type-erasure, equals to {@code Class&lt;StartType&gt;}
     */
    public static <StartType> Carrier<StartType, Carrier<Void, Carrier<?, ?>>>
    with(StartType value, Class<? extends StartType> classKey) {
        return baseCarrier.with(value, classKey);
    }

    /**
     * Create new binding for {@code classKey} with value {@code null}
     * Because of type erasure, we cannot use generic-with method to set values to null
     *
     * @param classKey use to bypass type-erasure, equals to {@code Class&lt;StartType&gt;}
     */
    public <StartType> Carrier<StartType, Carrier<Void, Carrier<?, ?>>>
    bindNull(Class<StartType> classKey) {
        return baseCarrier.bindNull(classKey);
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

    /**
     * God bless generics type inference. We are using Java's Generics Type System to create compile time recursive data type.
     * <p>
     * The object {@link Carrier} contains the current instance of {@link ExtentLocal.Carrier}. The type {@link Carrier}{@code <ValueType, Previous>} is a recursive data type that represent a linked list at compiletime with terminating value {@link Type.WildcardType}({@code ?}).
     * @param <ValueType> The type of the last value that got bind, or {@link Type.WildcardType}
     * @param <Previous> A type {@link Carrier} that represent the previews bind, or {@link Type.WildcardType}
     */
    public static final class Carrier<ValueType, Previous extends Carrier<?, ?>> {

        private final ExtentLocal.Carrier innerCarrier;

        private Carrier(ExtentLocal.Carrier innerCarrier) {
            this.innerCarrier = innerCarrier;
        }

        /**
         * creates a new binding for type {@code value#getClass()}
         *
         * @param value the new value of the binding
         */
        public <NextType> Carrier<NextType, Carrier<ValueType, Previous>>
        with(NextType value) {
            if (value == null) {
                throw new NullPointerException("Value cannot be null, use 'bindNull' for binding null");
            }
            var classKey = value.getClass();
            createInstance(classKey);
            return new Carrier<>(innerCarrier.where(COEFFECT.get(classKey), value));
        }

        /**
         * creates a new binding for type T
         *
         * @param value    the new binding of T
         * @param classKey use to bypass type-erasure, equals to {@code Class&lt;T&gt;}
         */
        public <NextType> Carrier<NextType, Carrier<ValueType, Previous>>
        with(NextType value, Class<? extends NextType> classKey) {
            if (value == null) {
                throw new NullPointerException("Value cannot be null, use 'bindNull' for binding null");
            }

            createInstance(classKey);
            return new Carrier<>(innerCarrier.where(COEFFECT.get(classKey), value));
        }

        /**
         * Create new binding for {@code classKey} with value {@code null}
         * Because of type erasure, we cannot use generic-with method to set values to null
         *
         * @param classKey use to bypass type-erasure, equals to {@code Class&lt;T&gt;}
         */
        public <NextType> Carrier<NextType, Carrier<ValueType, Previous>>
        bindNull(Class<NextType> classKey) {
            createInstance(classKey);
            return new Carrier<>(innerCarrier.where(COEFFECT.get(classKey), null));
        }

        public void run(Runnable op) {
            innerCarrier.run(op);
        }

        public <R> R call(Callable<R> op)
                throws Exception {
            return innerCarrier.call(op);
        }
    }

    private static void createInstance(Class<?> classKey) {
        COEFFECT.putIfAbsent(classKey, ExtentLocal.newInstance());
    }
}

