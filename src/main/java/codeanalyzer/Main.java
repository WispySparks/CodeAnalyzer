package codeanalyzer;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.ExecutableReferenceFilter;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    public static final int languageLevel = 17;
    private static final String defaultInput = "src/main/java";
    private static final String mainSignature = "main(java.lang.String[])"; 

    public static void main(String[] args) throws IOException {
        //todo invocations in lambdas don't get seen, sometimes interface methods don't get seen? 
        Launcher launcher = new Launcher();
        if (args.length > 0) {
            launcher.addInputResource(args[0]);
        }
        else {
            launcher.addInputResource(defaultInput); 
        }
        if (args.length > 1) {
            launcher.getEnvironment().setSourceClasspath(Arrays.stream(args, 1, args.length).toArray(String[]::new));
        }
        launcher.buildModel();
        CtModel model = launcher.getModel();
        List<CtMethod<?>> methods = model.getElements(new TypeFilter<>(CtMethod.class));
        methods.removeIf(m -> {
           // Filter out overridden methods, private methods and main methods
            for (CtAnnotation<?> annotation : m.getAnnotations()) {
                if (annotation.getActualAnnotation().annotationType().equals(Override.class)) return true;
            }
            return m.isPrivate() || m.getSignature().equals(mainSignature);
        });
        Set<CtMethod<?>> unusedMethods = new HashSet<>();
        for (CtMethod<?> method : methods) {
            ExecutableReferenceFilter filter = new ExecutableReferenceFilter();
            filter.addExecutable(method);
            List<CtExecutableReference<?>> references = model.getElements(filter);
            if (references.isEmpty()) {
                unusedMethods.add(method);
            }
        }
        unusedMethods = unusedMethods.stream().sorted(Main::sortMethods).collect(Collectors.toCollection(LinkedHashSet::new));
        String s = unusedMethods.isEmpty() ?  "No Unused Methods." : "Unused Methods:";
        System.out.println(s);
        unusedMethods.forEach(m -> {
            CtType<?> parent = getMethodParent(m);
            System.out.println(parent.getQualifiedName() + "." + m.getSignature() + " - " + parent.getSimpleName() + ".java:" + m.getPosition().getLine());
        });
    }

    private static CtType<?> getMethodParent(CtMethod<?> method) {
        return (CtType<?>) method.getParent();
    }

    private static int sortMethods(CtMethod<?> m1, CtMethod<?> m2) {
        int lineCompare = 0;
        if (m1.getPosition().getLine() > m2.getPosition().getLine()) lineCompare = 1;
        else if (m1.getPosition().getLine() < m2.getPosition().getLine()) lineCompare = -1;
        return getMethodParent(m1).getQualifiedName().compareTo(getMethodParent(m2).getQualifiedName()) + lineCompare;
    }

}
