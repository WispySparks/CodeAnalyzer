package codeanalyzer;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.resolution.MethodAmbiguityException;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.reflectionmodel.ReflectionMethodDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.SourceRoot;

public class Main {

    private static final Path defaultSourcePath = Paths.get("src/main/java");
    private static final String mainSignature = "main(java.lang.String[])";
    private static TypeSolver typeSolver;
    
    public static void main(String[] args) throws IOException {
        Path sourcePath = defaultSourcePath;
        if (args.length > 0) {
            sourcePath = Paths.get(args[0]);
        }
        List<TypeSolver> typeSolvers = new ArrayList<>();
        typeSolvers.add(new ReflectionTypeSolver(false));
        typeSolvers.add(new JavaParserTypeSolver(sourcePath));
        for (int i = 1; i < args.length; i++) {
            typeSolvers.add(new JarTypeSolver(args[i]));
        }
        typeSolver = new CombinedTypeSolver(typeSolvers);
        SourceRoot sourceRoot = new SourceRoot(sourcePath);
        sourceRoot.getParserConfiguration()
            .setSymbolResolver(new JavaSymbolSolver(typeSolver))
            .setLanguageLevel(LanguageLevel.JAVA_17);
        var parseResults = sourceRoot.tryToParse();
        parseResults.forEach(r -> {
            if (!r.isSuccessful()) System.out.println(r);
        });        
        List<CompilationUnit> units = sourceRoot.getCompilationUnits();
        Set<ResolvedMethodDeclaration> unusedMethods = new HashSet<>();
        for (CompilationUnit unit : units) {
            unusedMethods.addAll(getUnusedMethods(unit));
        }
        Set<String> objectMethods = typeSolvers.get(0).getSolvedJavaLangObject().getDeclaredMethods().stream()
            .map(m -> m.getSignature())
            .collect(Collectors.toSet());
        unusedMethods.removeIf(m -> objectMethods.contains(m.getSignature()));
        unusedMethods.removeIf(m -> m.getSignature().equals(mainSignature) && m.getReturnType().isVoid());
        String s = unusedMethods.size() == 0 ? "No Unused Methods." : "Unused Methods:";
        System.out.println(s);
        for (ResolvedMethodDeclaration method : unusedMethods) {
            Node methodAst = method.toAst().get();
            String filePath = findCompilationUnit(methodAst).getStorage().get().getPath().toString();
            if (filePath == null) filePath = "?";
            System.out.println(method.getQualifiedSignature() + " - " + filePath + ":" + methodAst.getBegin().get().line);
        }
    }

    private static Set<ResolvedMethodDeclaration> getUnusedMethods(CompilationUnit cu) {
        Set<WrapperRMD> definedMethods;
        Set<WrapperRMD> usedMethods;
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
                .map(m -> {
                    ResolvedMethodDeclaration r = new ReflectionMethodDeclaration(Object.class.getMethods()[0], typeSolver);
                    try {
                        r = m.resolve();
                    } catch (UnsolvedSymbolException | MethodAmbiguityException e) {
                        // System.out.println(findCompilationUnit(m).getStorage().get().getPath() + ":" + m.getBegin().get().line);
                        // e.printStackTrace();
                        // System.exit(1);
                    }
                    return r;
                }), // broken?
            methodReferences
                .stream()
                .map(m -> {
                    ResolvedMethodDeclaration r = new ReflectionMethodDeclaration(Object.class.getMethods()[0], typeSolver);
                    try {
                        r = m.resolve();
                    } catch (UnsolvedSymbolException | MethodAmbiguityException e) {
                    }
                    return r;
                }) // broken with new fs
        ).map(f -> new WrapperRMD(f))
        .distinct()
        .collect(Collectors.toSet());
        definedMethods.removeAll(usedMethods);
        return definedMethods.stream().map(WrapperRMD::unwrap).collect(Collectors.toSet());
    }   

    private static CompilationUnit findCompilationUnit(Node node) {
        while (node != null && !(node instanceof CompilationUnit)) {
            node = node.getParentNode().orElse(null);
        }
        return (CompilationUnit) node;
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
