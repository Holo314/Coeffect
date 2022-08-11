package io.github.holo314.coeffect.compiletime.plugin;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.model.JavacElements;
import io.github.holo314.coeffect.compiletime.annotations.WithContext;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.Objects;

@AutoService(BugChecker.class)
@BugPattern(
        name = "Coeffect",
        summary = """
                  Using Coeffect require either binding or annotating your methods with @WithContext. For more details see documentations.
                  Note that all usage of "Coeffect.get(T)" must be used with Class Literal, e.g. "Coeffect.get(String.class)".
                  """,
        severity = BugPattern.SeverityLevel.ERROR,
        linkType = BugPattern.LinkType.CUSTOM,
        link = "https://github.com/Holo314/coeffect"
)
public class CoeffectPlugin
        extends BugChecker
        implements BugChecker.MethodInvocationTreeMatcher, BugChecker.MemberReferenceTreeMatcher {

    @Override
    public Description matchMethodInvocation(MethodInvocationTree methodInv, VisitorState visitorState) {
        var path = CoeffectPath.of(methodInv, visitorState);
        return checkTree(path, methodInv, visitorState);
    }

    @Override
    public Description matchMemberReference(MemberReferenceTree memberReferenceTree, VisitorState visitorState) {
        var path = CoeffectPath.of(memberReferenceTree, visitorState);
        return checkTree(path, memberReferenceTree, visitorState);
    }

    private Description checkTree(CoeffectPath path, ExpressionTree ExpressionTree, VisitorState visitorState) {
        try {
            var requirements = path.getRequirements();
            if (!requirements.isEmpty()) {
                return describeMatch(ExpressionTree);
            }
        } catch (IllegalStateException e) {
            return describeMatch(ExpressionTree);
        }

        return Description.NO_MATCH;
    }
}
