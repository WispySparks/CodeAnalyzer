package codeanalyzer;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.InvocationFilter;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String mainSignature = "main(java.lang.String[])"; 

    public static void main(String[] args) throws IOException {
        Launcher launcher = new Launcher();
        launcher.addInputResource("src/main/java"); 
        launcher.getEnvironment().setSourceClasspath(args);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.buildModel();
        CtModel model = launcher.getModel();
        List<CtMethod<?>> methods = model.getElements(new TypeFilter<>(CtMethod.class));
        methods.removeIf(m -> m.isPrivate());
        Set<CtMethod<?>> unusedMethods = new HashSet<>();
        for (CtMethod<?> method : methods) {
            List<CtInvocation<?>> invocations = model.getElements(new InvocationFilter(method));
            if (invocations.isEmpty()) {
                unusedMethods.add(method);
            }
        }
        unusedMethods.removeIf(m -> {
            // Filter out overridden methods and main methods
            for (var annotation : m.getAnnotations()) {
                if (annotation.getActualAnnotation().annotationType().equals(Override.class)) return true;
            }
            return m.getSignature().equals(mainSignature);
        });
        String s = unusedMethods.isEmpty() ?  "No Unused Methods." : "Unused Methods:";
        System.out.println(s);
        unusedMethods.forEach(m -> 
            System.out.println(((CtType<?>) m.getParent()).getSimpleName() + "." + m.getSignature())
        );
    }

}
