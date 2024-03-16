package codeanalyzer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;

public class Main {
    
    public static void main(String[] args) throws IOException {
        Path sourcePath = Paths.get(System.getProperty("user.dir") + "/src/main/java/");
        SourceRoot sourceRoot = new SourceRoot(sourcePath);
        sourceRoot.setParserConfiguration(new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(new CombinedTypeSolver(new ReflectionTypeSolver(false), new JavaParserTypeSolver(sourcePath)))));
        sourceRoot.tryToParse();
        List<CompilationUnit> cu = sourceRoot.getCompilationUnits();
        cu.forEach(Main::anaylze);
    }

    public static void anaylze(CompilationUnit cu) {
        List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class)
            .stream()
            .filter(m -> !m.isPrivate())
            .collect(Collectors.toList());
        List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class);
        System.out.println("Non Private Method Declarations:");
        methods.forEach(m -> System.out.println(m.getDeclarationAsString()));
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
    }

}
