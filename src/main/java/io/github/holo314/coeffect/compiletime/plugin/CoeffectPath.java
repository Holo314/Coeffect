package io.github.holo314.coeffect.compiletime.plugin;

import com.google.common.collect.Sets;
import com.google.errorprone.VisitorState;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import io.github.holo314.coeffect.compiletime.annotations.WithContext;
import io.github.holo314.coeffect.runtime.Coeffect;

import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.stream.Collectors;

public record CoeffectPath(
        ExpressionTree expressionTree,
        Set<Type> binds,
        JCTree.JCMethodDecl enclosingMethod,
        VisitorState visitorState
) {
    public Collection<String> getRequirements() {
        Set<String> required = switch (expressionTree) {
            case MethodInvocationTree methodInv -> extractMethodRequirements(methodInv);
            case JCTree.JCMemberReference referenceTree -> extractReferenceRequirements(referenceTree);
            case null, default -> Set.of();
        };


        var enclosingBinding = enclosingMethod == null ?
                               Set.of() : // Inside a static block
                               getContextOfSymbol(TreeInfo.symbolFor(enclosingMethod));
        var bounds = Sets.union(
                binds.stream()
                     .map(Type::toString)
                     .collect(Collectors.toSet()),
                enclosingBinding
        );

        return Sets.difference(required, bounds);
    }

    public static CoeffectPath of(ExpressionTree expressionTree, VisitorState visitorState) {
        var invokedPath = visitorState.getPath();
        var binds = new ArrayList<Type>();
        var enclosingOfTree = getEnclosingOfTree(invokedPath, binds);
        var coeffectClause = getCoeffectClause(invokedPath);
        var carried = coeffectClause.stream().map(CoeffectPath::extractCarrierContext)
                                    .flatMap(Collection::stream)
                                    .collect(Collectors.toSet());
        return new CoeffectPath(expressionTree, carried, enclosingOfTree, visitorState);
    }

    public static Set<Type> extractCarrierContext(Type carrier) {
        if (carrier == null || !carrier.tsym.toString().equals(Coeffect.Carrier.class.getCanonicalName())) {
            return Set.of();
        }
        var result = new HashSet<Type>();
        List<Type> args;
        while (!((args = carrier.getTypeArguments()).get(1) instanceof Type.WildcardType)
                && !(args.get(1) instanceof Type.CapturedType)) {
            result.add(args.get(0));
            carrier = args.get(1);
        }
        return result;
    }

    public static Set<Type> getCoeffectClause(TreePath path) {
        var result = new HashSet<Type>();
        for (; !(path.getLeaf() instanceof JCTree.JCMethodDecl); path = path.getParentPath()) {
            if (path.getLeaf() instanceof JCTree.JCMethodInvocation inv
                    && inv.getMethodSelect() instanceof JCTree.JCFieldAccess access
                    && access.selected.type.tsym.toString().equals(Coeffect.Carrier.class.getCanonicalName())
                    && (access.name.contentEquals("call") || access.name.contentEquals("run"))) {
                result.add(access.selected.type);
            }
        }

        return result;
    }

    public static void addWithes(JCTree.JCFieldAccess access, ArrayList<Type> acc) {
        if (access.selected instanceof JCTree.JCMethodInvocation innerInv // the "with" invocation
                && innerInv.type.tsym.toString().equals(Coeffect.Carrier.class.getCanonicalName())
                && innerInv.getMethodSelect() instanceof JCTree.JCFieldAccess innerAccess // the "with" field access
                && innerAccess.name.contentEquals("with")) {

            if (innerInv.getArguments().size() == 1) {
                acc.add(innerInv.getArguments().getFirst().type);
            } else {
                acc.add(innerInv.getArguments().get(1).type.getTypeArguments().getFirst());
            }

            addWithes(innerAccess, acc);
        }
    }

    public static JCTree.JCMethodDecl getEnclosingOfTree(TreePath path, final ArrayList<Type> acc) {
        Tree leaf;
        while (!((leaf = path.getLeaf()) instanceof JCTree.JCMethodDecl)) {
            if (leaf instanceof JCTree.JCMethodInvocation inv
                    && inv.getMethodSelect() instanceof JCTree.JCFieldAccess access) {
                addWithes(access, acc);
            }
            path = path.getParentPath();
            if (path == null) {
                return null;
            }
        }

        return (JCTree.JCMethodDecl)leaf;
    }

    public static Set<String> extractReferenceRequirements(JCTree.JCMemberReference referenceTree) {
        return getContextOfSymbol(referenceTree.sym);
    }

    public static Set<String> extractMethodRequirements(MethodInvocationTree methodInv) {
        var methodTree = (JCTree)methodInv.getMethodSelect();
        var methodSymbol = TreeInfo.symbol(methodTree);
        var requiredContext = getContextOfSymbol(methodSymbol);
        var additionalContext = extractUsedContext(methodInv, methodTree);
        if (additionalContext == null) {
            throw new IllegalStateException("Coeffect.get(...) used with non-class literal");
        }

        return Sets.union(requiredContext, additionalContext);
    }

    /**
     * @return The fully qualified name of the parameter inside of "Coeffect.get(...)". For methods that are not
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
