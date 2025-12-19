package io.github.holo314.coeffect.compiletime.plugin;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import io.github.holo314.coeffect.compiletime.annotations.DelegateContext;

import java.util.Arrays;
import java.util.stream.Collectors;

public class TreeUtils {
    public static Symbol.TypeSymbol getSymbolOfLambdaType(JCTree.JCLambda lambdaDecl) {
        return lambdaDecl.type.asElement();
    }

    public static Symbol.MethodSymbol getAbstractMethodFromSAMInterface(JCTree.JCLambda lambdaDecl) {
        var methods = getSymbolOfLambdaType(lambdaDecl).getEnclosedElements();
        return methods.stream()
                .filter(sym -> sym instanceof Symbol.MethodSymbol)
                .map(sym -> (Symbol.MethodSymbol) sym)
                .filter(mSym -> !mSym.isDefault())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Lambda expression doesn't have SAM type")); // shouldn't be possible
    }

    public static boolean lambdaRunsInEnclosingMethod(JCTree.JCLambda lambdaDecl, JCTree.JCMethodInvocation inv, Symbol.MethodSymbol methodSymbol) {
        var pPositions = Arrays.stream(methodSymbol.getAnnotation(DelegateContext.class).variablePositions()).boxed().collect(Collectors.toSet());
        var pNames = Arrays.stream(methodSymbol.getAnnotation(DelegateContext.class).variableNames()).collect(Collectors.toSet());
        if (!pNames.isEmpty() || !pPositions.isEmpty()) {
            var paramsExpr = inv.args;
            var paramsDef = methodSymbol.getParameters();

            var myPos = paramsExpr.indexOf(lambdaDecl);
            var myName = paramsDef.get(myPos);

            return pPositions.contains(myPos) || pNames.contains(myName.toString());
        }
        return true;
    }

    public static JCTree.JCMethodDecl getConstructorFromClassDecl(JCTree.JCClassDecl classDecl) {
        return classDecl.defs.stream()
                .filter(el -> el instanceof JCTree.JCMethodDecl)
                .map(el -> (JCTree.JCMethodDecl) el)
                .filter(md -> md.getName().toString().equals("<init>"))
                .findFirst()
                .orElse(null);
    }
}
