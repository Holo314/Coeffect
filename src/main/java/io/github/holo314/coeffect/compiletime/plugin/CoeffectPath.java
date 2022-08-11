package io.github.holo314.coeffect.compiletime.plugin;

import com.google.common.collect.Sets;
import com.google.errorprone.VisitorState;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record CoeffectPath(
        MethodInvocationTree methodInv,
        List<Type> binds,
        JCTree.JCMethodDecl enclosingMethod,
        VisitorState visitorState
) {
    public Collection<String> getRequirements() {
        var requiredByMethod = extractMethodRequirements();
        var requiredByLambdaReference = extractReferenceRequirements();
        var required = Sets.union(
                requiredByMethod,
                requiredByLambdaReference
        );

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

    private Set<String> extractReferenceRequirements() {
        var methodTree = (JCTree)methodInv.getMethodSelect();
        if (!(methodTree instanceof JCTree.JCFieldAccess access)) {
            return Set.of();
        }
        var selected = access.selected;
        var parentType = selected.type;
        if (!parentType.toString()
                       .equals(Coeffect.Carrier.class.getCanonicalName())
                || !(access.name.contentEquals("run") || access.name.contentEquals("call"))) {
            return Set.of();
        }
        var argument = methodInv.getArguments().get(0);
        if (!(argument instanceof JCTree.JCMemberReference ref)) {
            return Set.of();
        }

        return getContextOfSymbol(ref.sym);
    }
    private Set<String> extractMethodRequirements() {
        var methodTree = (JCTree)methodInv.getMethodSelect();
        var methodSymbol = TreeInfo.symbol(methodTree);
        var requiredContext = getContextOfSymbol(methodSymbol);
        var additionalContext = extractUsedContext(methodInv, methodTree);
        if (additionalContext == null) {
            throw new IllegalStateException("Coeffect.get(...) used with non-class literal");
        }

        return Sets.union(requiredContext, additionalContext);
    }

    // region CoeffectPath construction functions
    public static CoeffectPath of(MethodInvocationTree methodInv, VisitorState visitorState) {
        var invokedPath = visitorState.getPath();
        var binds = new ArrayList<Type>();
        var enclosingOfTree = getEnclosingOfTree(invokedPath, binds);

        return new CoeffectPath(methodInv, binds, enclosingOfTree, visitorState);
    }

    /**
     * Implementation note:
     * <p>
     * It is currently impossible to detect "with" invocation through  variable, e.g.:
     * <pre>
     *     var x = Coeffect.with("^o^");
     *     x.run(...); // the "String.class" binding is not detected
     * </pre>
     * <p>
     * This is because with the current implementation of Coeffect.Carrier, the type of the binding is not encoded in the class.
     * If in feature versions it will be encoded, then there won't be the need to traverse over "with", but over the generic type of "Coeffect.Carrier".
     */
    private static void addWithes(JCTree.JCFieldAccess access, ArrayList<Type> acc) {
        if (access.selected instanceof JCTree.JCMethodInvocation innerInv // the "with" invocation
                && innerInv.type.toString().equals(Coeffect.Carrier.class.getCanonicalName())
                && innerInv.getMethodSelect() instanceof JCTree.JCFieldAccess innerAccess // the "with" field access
                && innerAccess.name.contentEquals("with")) {

            if (innerInv.getArguments().size() == 1) {
                acc.add(innerInv.getArguments().get(0).type);
            } else {
                acc.add(innerInv.getArguments().get(1).type.getTypeArguments().get(0));
            }

            addWithes(innerAccess, acc);
        }
    }

    private static JCTree.JCMethodDecl getEnclosingOfTree(final TreePath oPath, final ArrayList<Type> acc) {
        var path = oPath;
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
    // endregion

    /**
     * @return The fully qualified name of the parameter inside of "Coeffect.get(...)". For methods that are not
     * "Coeffect.get(...)" return an empty list, and for non-Class-literal invocation of "Coeffect.get(...)" return null.
     */
    private static Set<String> extractUsedContext(MethodInvocationTree methodInv, JCTree methodTree) {
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
        var argument = methodInv.getArguments().get(0);
        if (!(argument instanceof JCTree.JCFieldAccess classAccess)) {
            return null;
        }
        var argumentType = classAccess.type;
        var argumentSymbol = argumentType.tsym;
        if (!argumentSymbol.toString().equals(Class.class.getCanonicalName())) {
            return null;
        }
        var argumentDiamondType = argumentType.getTypeArguments().get(0);

        if (!(argumentDiamondType instanceof Type.ClassType)
                && !(argumentDiamondType instanceof Type.ArrayType)) {
            return null;
        }
        return Set.of(argumentDiamondType.toString());
    }

    private static Set<String> getContextOfSymbol(Symbol methodSymbol) {
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

    private static List<? extends TypeMirror> getContextTypes(WithContext contextDeclaration) {
        try {
            var ignore = contextDeclaration.value();// always throws exceptions
        } catch (MirroredTypesException mirrors) {
            return mirrors.getTypeMirrors();
        }
        return List.of(); // can never happen
    }
}
