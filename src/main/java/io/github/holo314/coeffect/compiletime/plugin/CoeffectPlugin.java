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

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
                return describeContextViolation(path, requirements);
            }
        } catch (IllegalStateException e) {
            return describeLiteralViolation(path, e.getMessage());
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

        return covariant.isEmpty() ? Description.NO_MATCH
                                   : describeInheritanceViolation(methodTree, covariant, specifiedRequirements);
    }

    public Description describeLiteralViolation(CoeffectPath node, String msg) {
        return Description.builder(node.expressionTree(), this.canonicalName(), this.linkUrl(), this.defaultSeverity(), msg)
                          .build();
    }

    public Description describeContextViolation(CoeffectPath node, Collection<String> missings) {
        var wither = "@WithContext({" + Iterables.toString(missings)
                                                 .replaceAll("[\\[\\]]", "") + ", ...}"
                + node.enclosingMethod()
                      .toString()
                      .replaceAll("(?s)\\{.*}", "{...}")
                      .replaceAll("@[a-zA-Z0-9_]*(\\([^)]*\\))?", "");

        var msg = new StringBuilder()
                .append("Missing requirements in @WithContext: ")
                .append(Iterables.toString(missings))
                .append(System.lineSeparator())
                .append("\t")
                .append("Add the requirements to the context or wrap it with run/call:")
                .append(System.lineSeparator())
                .append("\t\t")
                .append(wither.replace("\n", "\n\t\t"))
                .append(System.lineSeparator())
                .append("---")
                .append(System.lineSeparator())
                .append("\t\t");

        var with = new StringBuilder().append("Coeffect");
        missings.forEach(withCounter((i, missing) -> {
            var typeSplit = missing.split("[.]");
            var type = typeSplit[typeSplit.length - 1];
            with.append(".with(")
                .append("v")
                .append(type)
                .append(i)
                .append(")")
                .append(System.lineSeparator())
                .append("\t\t\t\t");
        }));
        var call = with + ".call(() -> ...);";
        var run = with + ".run(() -> ...);";

        msg.append(run)
           .append(System.lineSeparator())
           .append("---")
           .append(System.lineSeparator())
           .append("\t\t")
           .append(call);
        return Description.builder(node.expressionTree(), this.canonicalName(), this.linkUrl(), this.defaultSeverity(), msg.toString())
                          .build();
    }

    public Description describeInheritanceViolation(
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

    public static <T> Consumer<T> withCounter(BiConsumer<Integer, T> consumer) {
        AtomicInteger counter = new AtomicInteger(0);
        return item -> consumer.accept(counter.getAndIncrement(), item);
    }
}
