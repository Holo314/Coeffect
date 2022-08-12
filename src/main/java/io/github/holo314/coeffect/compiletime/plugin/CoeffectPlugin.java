package io.github.holo314.coeffect.compiletime.plugin;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;

@AutoService(BugChecker.class)
@BugPattern(
        name = "Coeffect",
        summary = """
                  Using Coeffect require either binding or annotating your methods with @WithContext. The @WithContext annotation is covariant with inheritance. For more details see documentations.
                  Note that all usage of "Coeffect.get(T)" must be used with Class Literal, e.g. "Coeffect.get(String.class)".
                  """,
        severity = BugPattern.SeverityLevel.ERROR,
        linkType = BugPattern.LinkType.CUSTOM,
        link = "https://github.com/Holo314/coeffect"
)
public class CoeffectPlugin
        extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher, BugChecker.MemberReferenceTreeMatcher, BugChecker.MethodTreeMatcher {

    @Override
    public Description matchMethodInvocation(MethodInvocationTree methodInv, VisitorState visitorState) {
        var path = CoeffectPath.of(methodInv, visitorState);
        return checkTree(path);
    }

    @Override
    public Description matchMemberReference(MemberReferenceTree memberReferenceTree, VisitorState visitorState) {
        var path = CoeffectPath.of(memberReferenceTree, visitorState);
        return checkTree(path);
    }

    private Description checkTree(CoeffectPath path) {
        try {
            var requirements = path.getRequirements();
            if (!requirements.isEmpty()) {
                return describeMatch(path.expressionTree());
            }
        } catch (IllegalStateException e) {
            return describeMatch(path.expressionTree());
        }

        return Description.NO_MATCH;
    }

    @Override
    public Description matchMethod(MethodTree methodTree, VisitorState visitorState) {
        var types = Types.instance(visitorState.context);

        var jcMethod = (JCTree.JCMethodDecl)methodTree;
        var methodSymbol = jcMethod.sym;

        var specifiedRequirements = CoeffectPath.getContextOfSymbol(methodSymbol);

        var classSymbol = (Symbol.ClassSymbol)methodSymbol.owner;

        var superClasses = InheritanceUtils.getInheritanceFlatten(classSymbol);
        var superMethods = superClasses.stream()
                                       .map(clazz -> InheritanceUtils.Candidates.of(clazz, jcMethod.name))
                                       .flatMap(InheritanceUtils.Candidates::split)
                                       .filter(candidate -> methodSymbol.overrides(candidate.method(), candidate.clazz(), types, true, false))
                                       .map(InheritanceUtils.Candidate::method);

        var requiredBySupers = superMethods.map(CoeffectPath::getContextOfSymbol);
        var covariant = requiredBySupers.allMatch(requirement -> requirement.containsAll(specifiedRequirements));

        return covariant ? Description.NO_MATCH : describeMatch(methodTree);
    }
}
