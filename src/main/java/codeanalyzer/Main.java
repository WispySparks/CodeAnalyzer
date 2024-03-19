package codeanalyzer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.types.ResolvedPrimitiveType;
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
        sourceRoot.getParserConfiguration()
            .setSymbolResolver(new JavaSymbolSolver(
                new CombinedTypeSolver(new ReflectionTypeSolver(false), new JavaParserTypeSolver(sourcePath)))
            )
            .setLanguageLevel(LanguageLevel.JAVA_17);
        var parseResults = sourceRoot.tryToParse();
        System.out.println("Parse Results: " + parseResults);
        List<CompilationUnit> units = sourceRoot.getCompilationUnits();
        Set<ResolvedMethodDeclaration> unusedMethods = new HashSet<>();
        for (CompilationUnit unit : units) {
            unusedMethods.addAll(getUnusedMethods(unit));
        }
        System.out.println("Unused Methods:");
        for (ResolvedMethodDeclaration method : unusedMethods) {
            String sig = method.getSignature();
            // Ignore main, equals and hashcode method
            if ((sig.equals("main(java.lang.String[])") && method.getReturnType().isVoid())
            || (sig.endsWith("equals(java.lang.Object)") && method.getReturnType().isPrimitive() 
                && method.getReturnType().asPrimitive() == ResolvedPrimitiveType.BOOLEAN)
            || (sig.endsWith("hashCode()") && method.getReturnType().isPrimitive() 
                && method.getReturnType().asPrimitive() == ResolvedPrimitiveType.INT))
            continue;
            System.out.println(method.getQualifiedSignature());
        }
        System.out.println("PROGRAM END");
    }

    public static Set<ResolvedMethodDeclaration> getUnusedMethods(CompilationUnit cu) {
        Set<WrapperRMD> definedMethods;
        Set<WrapperRMD> usedMethods;
        Set<ResolvedMethodDeclaration> unusedMethods;
        List<MethodDeclaration> methodDeclarations = cu.findAll(MethodDeclaration.class)
            .stream()
            .filter(m -> !m.isPrivate())
            .collect(Collectors.toList());
        List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class); 
        List<MethodReferenceExpr> methodReferences = cu.findAll(MethodReferenceExpr.class);
        definedMethods = methodDeclarations
            .stream()
            .map(m -> new WrapperRMD(m.resolve()))
            .collect(Collectors.toSet());
        usedMethods = Stream.concat( 
            methodCalls
                .stream()
                .map(MethodCallExpr::resolve),
            methodReferences
                .stream()
                .map(MethodReferenceExpr::resolve)
        ).map(f -> new WrapperRMD(f))
        .distinct()
        .collect(Collectors.toSet());
        definedMethods.removeAll(usedMethods);
        unusedMethods = definedMethods.stream().map(WrapperRMD::unwrap).collect(Collectors.toSet());
        return unusedMethods;
    }   

    private static class WrapperRMD {
        private final ResolvedMethodDeclaration method;
        public WrapperRMD(ResolvedMethodDeclaration method) {
            this.method = method;
        }
        public ResolvedMethodDeclaration unwrap() {
            return method;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WrapperRMD other = (WrapperRMD) o;
            return Objects.equals(method.getQualifiedSignature(), other.unwrap().getQualifiedSignature());
        }
        @Override
        public int hashCode() {
            return Objects.hash(method.getQualifiedSignature());
        }
    }

}
