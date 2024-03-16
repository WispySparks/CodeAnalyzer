package codeanalyzer;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.stream.Collectors;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

public class Main {
    
    public static void main(String[] args) throws FileNotFoundException {
        String srcDir = System.getProperty("user.dir") + "\\src\\main\\java\\codeanalyzer";
        File f = new File(srcDir + "\\Main.java");
        StaticJavaParser.setConfiguration(new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(new CombinedTypeSolver(new ReflectionTypeSolver(false), new JavaParserTypeSolver(srcDir)))));
        CompilationUnit cu = StaticJavaParser.parse(f);
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
