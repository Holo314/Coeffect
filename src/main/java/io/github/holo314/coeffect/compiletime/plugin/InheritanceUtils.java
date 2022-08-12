package io.github.holo314.coeffect.compiletime.plugin;

import com.google.common.collect.Lists;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.Name;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class InheritanceUtils {
    public static Set<Symbol.ClassSymbol> getInheritanceFlatten(Symbol.ClassSymbol clazz) {
        var acc = new HashSet<Symbol.ClassSymbol>();
        accumulateInterfacesFlatten(clazz, acc);
        for (Symbol.ClassSymbol sClazz = (Symbol.ClassSymbol)clazz.getSuperclass().tsym;
             sClazz != null;
             sClazz = (Symbol.ClassSymbol)sClazz.getSuperclass().tsym) {
            acc.add(sClazz);
            accumulateInterfacesFlatten(sClazz, acc);
        }
        return acc;
    }

    public static Set<Symbol.ClassSymbol> getInterfacesFlatten(Symbol.ClassSymbol clazz) {
        var acc = new HashSet<Symbol.ClassSymbol>();
        accumulateInterfacesFlatten(clazz, acc);
        return acc;
    }

    private static void accumulateInterfacesFlatten(Symbol.ClassSymbol clazz, HashSet<Symbol.ClassSymbol> acc) {
        var directInterfaces = clazz.getInterfaces()
                                    .stream()
                                    .map(type -> type.tsym)
                                    .map(Symbol.ClassSymbol.class::cast)
                                    .toList();
        directInterfaces.forEach(directInterface -> accumulateInterfacesFlatten(directInterface, acc));
        acc.addAll(directInterfaces);
    }



    public record Candidates(Symbol.ClassSymbol clazz, List<Symbol> methods, Name name) {
        public static Candidates of(Symbol.ClassSymbol clazz, Name name) {
            var candidates = clazz.members().getSymbolsByName(name,
                                                              (s) -> s instanceof Symbol.MethodSymbol);
            return new Candidates(clazz, Lists.newArrayList(candidates), name);
        }

        public Stream<Candidate> split() {
            return methods.stream().map(method -> new Candidate(clazz, method, name));
        }
    }

    public record Candidate(Symbol.ClassSymbol clazz, Symbol method, Name name) {}
}
