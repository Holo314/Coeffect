package io.github.holo314.coeffect.compiletime.plugin;

import com.google.errorprone.VisitorState;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;

public class LambdaUtils {
    public static JCTree.JCMethodDecl getMethodDefinitionFromLambda(JCTree.JCLambda lambdaDecl, JavacTrees javacTrees) {
        // For lambda: ((JCTree.JCLambda) (path.getParentPath().getParentPath().getParentPath().getLeaf())).type.asElement().getEnclosedElements().getFirst().getAnnotation(WithContext.class)
        var methodSymbol = getAbstractMethodFromSAMInterface(lambdaDecl);
        return javacTrees.getTree(methodSymbol);
    }

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
                .orElseThrow(() -> new RuntimeException("Lambda expression doesn't have SAM type"));
    }
}
