package codeanalyzer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;

public class Main {
    
    public static void main(String[] args) throws IOException {
        Path sourcePath = Paths.get(System.getProperty("user.dir") + "/src/main/java/");
        if (args.length > 0) {
            sourcePath = Paths.get(args[0]);
        }
        SourceRoot sourceRoot = new SourceRoot(sourcePath);
        sourceRoot.setParserConfiguration(new ParserConfiguration()
            .setSymbolResolver(new JavaSymbolSolver(
                new CombinedTypeSolver(new ReflectionTypeSolver(false), new JavaParserTypeSolver(sourcePath)))
            )
            .setLanguageLevel(LanguageLevel.JAVA_17)
        );
        var parseResults = sourceRoot.tryToParse();
        System.out.println("Parse Results: " + parseResults);
        List<CompilationUnit> cu = sourceRoot.getCompilationUnits();
        cu.forEach(Main::anaylze);
    }

    public static void anaylze(CompilationUnit cu) {
        //! https://github.com/javaparser/javaparser/issues/1881 solves all my problems, ResolvedMethodDeclaration 
        Map<ClassOrInterfaceDeclaration, MethodDeclaration> methodClassPairs = new HashMap<>();
        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class)
            .stream()
            .filter(m -> !m.isPrivate())
            .collect(Collectors.toList());
        List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class); 
        List<MethodReferenceExpr> methodReferences = cu.findAll(MethodReferenceExpr.class);
        System.out.println("Non Private Method Declarations:");
        methods.forEach(m -> {
            String methodParent = "[No Parent]";
            if (m.getParentNode().isPresent()) {
                if (m.getParentNode().get() instanceof ClassOrInterfaceDeclaration clazz) {
                    methodParent = clazz.getFullyQualifiedName().orElse("[Local Declaration]");
                    methodClassPairs.put(clazz, m);
                }
            };
            System.out.println(methodParent + "." + m.getSignature());
        });
        System.out.println("Method Calls:");
        methodCalls.forEach(call -> {
            if (call.getScope().isPresent()) {
                var scope = call.getScope().get();
                var resolvedType = scope.calculateResolvedType();
                if (resolvedType.isReferenceType()) {
                    System.out.print("Reference: " + resolvedType.asReferenceType().getQualifiedName());
                }
                else if (resolvedType.isConstraint()) {
                    System.out.print("Constraint: " + resolvedType.asConstraintType().getBound().describe());
                }
                else {
                    System.out.print("Different Type: " + resolvedType);
                }
                System.out.print("." + call.getNameAsString() + "()\n");
            }
            else {
                System.out.println("No Scope: " + call);
            };
        });
        System.out.println("Method References:");
        methodReferences.forEach(reference -> {
            var resolvedType = reference.getScope().calculateResolvedType();
            System.out.println(resolvedType.describe() + "." + reference.getIdentifier() + "()");
        });
    }

}
