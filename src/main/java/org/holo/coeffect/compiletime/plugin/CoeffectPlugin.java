package org.holo.coeffect.compiletime.plugin;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.model.JavacElements;
import org.holo.coeffect.compiletime.annotations.WithContext;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.Objects;

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
        implements BugChecker.MethodInvocationTreeMatcher {
    public static Elements eltUtils;
    public static TypeElement annotationElement;

    @Override
    public Description matchMethodInvocation(MethodInvocationTree methodInv, VisitorState visitorState) {
        eltUtils = JavacElements.instance(visitorState.context);
        var annotation = WithContext.class;
        var name = annotation.getCanonicalName();
        annotationElement = eltUtils.getTypeElement(name);
        if (annotationElement == null) {
            var moduleName = Objects.requireNonNullElse(annotation.getModule().getName(), "");
            annotationElement = eltUtils.getTypeElement(eltUtils.getModuleElement(moduleName), name);
        }

        var p = CoeffectPath.of(methodInv, visitorState);
        try {
            var r = p.getRequirements();
            if (!r.isEmpty()) {
                return describeMatch(methodInv);
            }
        } catch (IllegalStateException e) {
            return describeMatch(methodInv);
        }

        return Description.NO_MATCH;
    }
}
