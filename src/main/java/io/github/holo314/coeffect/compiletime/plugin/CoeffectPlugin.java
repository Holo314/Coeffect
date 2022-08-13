package io.github.holo314.coeffect.compiletime.plugin;

import com.google.auto.service.AutoService;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.tree.JCTree;

import java.util.List;
import java.util.Set;

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
        var jcMethod = (JCTree.JCMethodDecl)methodTree;
        var methodSymbol = jcMethod.sym;

        var specifiedRequirements = CoeffectPath.getContextOfSymbol(methodSymbol);

        var superMethods = InheritanceUtils.getSuperMethods(methodSymbol, Types.instance(visitorState.context));
        var requiredBySupers = superMethods.map(InheritanceUtils.Candidate::getContext);

        var covariant =
                requiredBySupers.filter(requirement -> !requirement.context().containsAll(specifiedRequirements))
                                .toList();

        return covariant.isEmpty() ? Description.NO_MATCH : describe(methodTree, covariant, specifiedRequirements);
    }


    public Description describe(Tree node, String message) {
        return Description.builder(node, this.canonicalName(), this.linkUrl(), this.defaultSeverity(), message).build();
    }

    public Description describe(
            Tree node, List<InheritanceUtils.Contextual> covariantViolation, Set<String> specifiedRequirements
    ) {
        var msgBuilder = new StringBuilder().append("Method requires ")
                                            .append(Iterables.toString(specifiedRequirements))
                                            .append(" but implements:");
        covariantViolation.forEach(violation ->
                                           msgBuilder.append(System.lineSeparator())
                                                     .append("\t")
                                                     .append(violation.candidate().clazz())
                                                     .append("#")
                                                     .append(violation.candidate().method())
                                                     .append(" which requires ")
                                                     .append(Iterables.toString(violation.context()))
                                                     .append(".")
                                                     .append(" Remove ")
                                                     .append(Iterables.toString(Sets.difference(specifiedRequirements, violation.context())))
                                                     .append(" from the current method context")
                                                     .append(" or add it to the context of")
                                                     .append(violation.candidate().clazz())
                                                     .append("#")
                                                     .append(violation.candidate().method()));

        return Description.builder(node, this.canonicalName(), this.linkUrl(), this.defaultSeverity(), msgBuilder.toString())
                          .build();
    }
}
