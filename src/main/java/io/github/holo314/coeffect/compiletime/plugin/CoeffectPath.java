package io.github.holo314.coeffect.compiletime.plugin;

import com.google.common.collect.Sets;
import com.google.errorprone.VisitorState;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import io.github.holo314.coeffect.compiletime.annotations.UseInplace;
import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.runtime.Coeffect;

import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.stream.Collectors;

import static io.github.holo314.coeffect.compiletime.plugin.TreeUtils.getAbstractMethodFromSAMInterface;

public record CoeffectPath(
        ExpressionTree expressionTree,
        Set<String> explicitlyBounded,
        Set<String> enclosingBounds,
        Set<String> requirements
) {
    public Collection<String> getMissingRequirements() {
        var bounds = Sets.union(explicitlyBounded, enclosingBounds);
        return Sets.difference(requirements, bounds);
    }

    public static CoeffectPath of(ExpressionTree expressionTree, VisitorState visitorState) {
        var invokedPath = visitorState.getPath();
        var enclosingBounds = getEnclosingBounds(invokedPath);

        var coeffectClause = getCoeffectClause(invokedPath);
        var explicitlyBounded = coeffectClause.stream()
                .map(CoeffectPath::extractCarrierContext)
                .flatMap(Collection::stream)
                .map(Type::toString)
                .collect(Collectors.toSet());

        var requirements = extractRequirements(expressionTree);
        return new CoeffectPath(expressionTree, explicitlyBounded, enclosingBounds, requirements);
    }

    public static Set<String> extractRequirements(ExpressionTree expressionTree) {
        return switch (expressionTree) {
            case MethodInvocationTree methodInv -> extractMethodRequirements(methodInv);
            case JCTree.JCMemberReference referenceTree -> extractReferenceRequirements(referenceTree);
            case null, default -> Set.of();
        };
    }

    public static Set<Type> extractCarrierContext(Type carrier) {
        if (carrier == null || !carrier.tsym.toString().equals(Coeffect.Carrier.class.getCanonicalName())) {
            return Set.of();
        }
        var result = new HashSet<Type>();
        for (var args = carrier.getTypeArguments();
             !args.getFirst().toString().equals(Void.class.getCanonicalName());
             args = args.getLast().getTypeArguments()) {
            result.add(args.getFirst());
        }

        return result;
    }

    public static Set<Type> getCoeffectClause(TreePath path) {
        var result = new HashSet<Type>();
        while (true) {
            var leaf = path.getLeaf();
            if (leaf instanceof JCTree.JCMethodDecl || leaf instanceof JCTree.JCClassDecl)
                return result;

            if (leaf instanceof JCTree.JCMethodInvocation inv
                    && inv.getMethodSelect() instanceof JCTree.JCFieldAccess access
                    && access.selected.type.tsym.toString().equals(Coeffect.Carrier.class.getCanonicalName())
                    && (access.name.contentEquals("call") || access.name.contentEquals("run"))) {
                result.add(access.selected.type);
            }

            path = path.getParentPath();
        }
    }


    public static Set<String> getEnclosingBounds(TreePath path) {
        return switch (path.getLeaf()) {
            case JCTree.JCBlock blockDecl when blockDecl.isStatic() -> Set.of(); // static init block
            case JCTree.JCClassDecl classDecl -> // handle init block as a part of the constructor
                    getContextOfSymbol(TreeUtils.getConstructorFromClassDecl(classDecl).sym);
            case JCTree.JCMethodDecl methodDecl -> getContextOfSymbol(methodDecl.sym);
            case JCTree.JCLambda lambdaDecl -> {
                if (path.getParentPath().getLeaf() instanceof JCTree.JCMethodInvocation methodInvocation &&
                        methodInvocation.getMethodSelect() instanceof JCTree.JCFieldAccess fieldAccess &&
                        fieldAccess.sym instanceof Symbol.MethodSymbol methodSymbol &&
                        methodSymbol.getAnnotation(UseInplace.class) != null &&
                        TreeUtils.lambdaRunsInEnclosingMethod(lambdaDecl, methodInvocation, methodSymbol)) {
                    yield getEnclosingBounds(path.getParentPath());
                }
                yield getContextOfSymbol(getAbstractMethodFromSAMInterface(lambdaDecl));
            }
            case null ->
                    throw new IllegalStateException("Coeffect detected an unexpected type graph, please report a bug to the Coeffect git repository");
            default -> getEnclosingBounds(path.getParentPath());
        };
    }

    public static Set<String> extractReferenceRequirements(JCTree.JCMemberReference referenceTree) {
        return getContextOfSymbol(referenceTree.sym);
    }

    public static Set<String> extractMethodRequirements(MethodInvocationTree methodInv) {
        var methodTree = (JCTree) methodInv.getMethodSelect();
        var methodSymbol = TreeInfo.symbol(methodTree);
        var requiredContext = getContextOfSymbol(methodSymbol);
        var additionalContext = extractUsedContext(methodInv, methodTree);
        if (additionalContext == null) {
            throw new IllegalStateException("Coeffect.get(...) used with non-class literal");
        }

        return Sets.union(requiredContext, additionalContext);
    }

    /**
     * @return The fully qualified name of the parameter inside "Coeffect.get(...)". For methods that are not
     * "Coeffect.get(...)" return an empty list, and for non-Class-literal invocation of "Coeffect.get(...)" return null.
     */
    public static Set<String> extractUsedContext(MethodInvocationTree methodInv, JCTree methodTree) {
        if (!(methodTree instanceof JCTree.JCFieldAccess fieldAccess)) {
            return Set.of();
        }

        var selected = fieldAccess.selected;
        var parentType = selected.type;
        if (!parentType.toString()
                .equals(Coeffect.class.getCanonicalName())
                || !fieldAccess.name.contentEquals("get")) {
            return Set.of();
        }
        var argument = methodInv.getArguments().getFirst();
        if (!(argument instanceof JCTree.JCFieldAccess classAccess)) {
            return null;
        }
        var argumentType = classAccess.type;
        var argumentSymbol = argumentType.tsym;
        if (!argumentSymbol.toString().equals(Class.class.getCanonicalName())) {
            return null;
        }
        var argumentDiamondType = argumentType.getTypeArguments().getFirst();

        if (!(argumentDiamondType instanceof Type.ClassType)
                && !(argumentDiamondType instanceof Type.ArrayType)) {
            return null;
        }
        return Set.of(argumentDiamondType.toString());
    }

    public static Set<String> getContextOfSymbol(Symbol methodSymbol) {
        Set<String> requiredContext;
        var contextDeclaration = methodSymbol.getAnnotation(WithContext.class);
        if (contextDeclaration == null) {
            requiredContext = Set.of();
        } else {
            requiredContext = getContextTypes(contextDeclaration)
                    .stream().map(TypeMirror::toString).collect(Collectors.toSet());
        }
        return requiredContext;
    }

    public static List<? extends TypeMirror> getContextTypes(WithContext contextDeclaration) {
        try {
            var ignore = contextDeclaration.value();// always throws exceptions
        } catch (MirroredTypesException mirrors) {
            return mirrors.getTypeMirrors();
        }
        return List.of(); // can never happen
    }
}
