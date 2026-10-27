package javaprac.grade;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.SwitchExpressionTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import javaprac.model.Challenge;

/**
 * Checks which language constructs the user's code uses by walking the parsed syntax tree (never by regex), so
 * {@code "for"} in a comment or string literal is not mistaken for a loop.
 *
 * <p>Features: {@code call:NAME}, {@code new:TYPE}, {@code for}, {@code foreach}, {@code while}, {@code do-while},
 * {@code loop} (any of them), {@code lambda}, {@code method-ref}, {@code switch}, {@code switch-expr}, {@code try},
 * {@code try-with-resources}, {@code catch}, {@code multi-catch}, {@code finally}, {@code throws}, {@code if},
 * {@code ternary}, {@code instanceof}, {@code cast}, {@code var}, {@code class:class|record|enum|interface} and
 * {@code modifier:NAME}.
 */
final class StructureChecker {
    private StructureChecker() {}

    /** Returns a failing verdict, or {@code null} when the structure is acceptable. */
    static Verdict check(Challenge ch, String userCode) {
        Challenge.Structure s = ch.structure();
        if (s.isEmpty()) {
            return null;
        }
        Set<String> features = features(ch.kind(), userCode);
        for (String required : s.require()) {
            if (!anyPresent(required, features)) {
                return new Verdict(Verdict.Outcome.STRUCTURE, "Your solution should use " + describe(required) + ".");
            }
        }
        for (String forbidden : s.forbid()) {
            if (anyPresent(forbidden, features)) {
                return new Verdict(Verdict.Outcome.STRUCTURE, "This challenge doesn't allow " + describe(forbidden) + ".");
            }
        }
        return null;
    }

    static Set<String> features(Challenge.Kind kind, String userCode) {
        String text = switch (kind) {
            case EXPRESSION -> "class W { void m() { Object __r = (\n" + SourceBuilder.stripTrailingSemicolons(userCode) + "\n); } }";
            case STATEMENTS -> "class W { void m() {\n" + userCode + "\n} }";
            case DECLARATIONS -> userCode;
        };
        Set<String> found = new TreeSet<>();
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            JavacTask task = (JavacTask) compiler.getTask(null, null, d -> { }, List.of("-proc:none"), null, List.of(source(text)));
            for (CompilationUnitTree unit : task.parse()) {
                Collector collector = new Collector();
                if (kind == Challenge.Kind.DECLARATIONS) {
                    collector.scan(unit, found);
                } else {
                    scanMethodBody(unit, collector, found);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse submission", e);
        }
        return found;
    }

    private static void scanMethodBody(CompilationUnitTree unit, Collector collector, Set<String> found) {
        for (Tree type : unit.getTypeDecls()) {
            if (type instanceof ClassTree cls) {
                for (Tree member : cls.getMembers()) {
                    if (member instanceof MethodTree m && m.getBody() != null) {
                        collector.scan(m.getBody(), found);
                    }
                }
            }
        }
    }

    private static boolean anyPresent(String token, Set<String> features) {
        return Arrays.stream(token.split("\\|")).map(String::strip).anyMatch(t -> matches(t, features));
    }

    private static boolean matches(String token, Set<String> features) {
        return features.contains(token);
    }

    static String describe(String token) {
        return Arrays.stream(token.split("\\|"))
                .map(String::strip)
                .map(t -> {
                    if (t.startsWith("call:")) {
                        return "." + t.substring(5) + "(...)";
                    }
                    if (t.startsWith("new:")) {
                        return "new " + t.substring(4) + "(...)";
                    }
                    return t.replace(':', ' ').replace('-', ' ');
                })
                .collect(Collectors.joining(" or "));
    }

    private static JavaFileObject source(String text) {
        return new SimpleJavaFileObject(URI.create("string:///W.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return text;
            }
        };
    }

    private static final class Collector extends TreeScanner<Void, Set<String>> {
        @Override
        public Void visitMethodInvocation(MethodInvocationTree t, Set<String> f) {
            ExpressionTree select = t.getMethodSelect();
            if (select instanceof MemberSelectTree m) {
                f.add("call:" + m.getIdentifier());
            } else if (select instanceof IdentifierTree i) {
                f.add("call:" + i.getName());
            }
            return super.visitMethodInvocation(t, f);
        }

        @Override
        public Void visitForLoop(ForLoopTree t, Set<String> f) {
            f.add("for");
            f.add("loop");
            return super.visitForLoop(t, f);
        }

        @Override
        public Void visitEnhancedForLoop(EnhancedForLoopTree t, Set<String> f) {
            f.add("foreach");
            f.add("loop");
            return super.visitEnhancedForLoop(t, f);
        }

        @Override
        public Void visitWhileLoop(WhileLoopTree t, Set<String> f) {
            f.add("while");
            f.add("loop");
            return super.visitWhileLoop(t, f);
        }

        @Override
        public Void visitDoWhileLoop(DoWhileLoopTree t, Set<String> f) {
            f.add("do-while");
            f.add("loop");
            return super.visitDoWhileLoop(t, f);
        }

        @Override
        public Void visitLambdaExpression(LambdaExpressionTree t, Set<String> f) {
            f.add("lambda");
            return super.visitLambdaExpression(t, f);
        }

        @Override
        public Void visitMemberReference(MemberReferenceTree t, Set<String> f) {
            f.add("method-ref");
            return super.visitMemberReference(t, f);
        }

        @Override
        public Void visitNewClass(NewClassTree t, Set<String> f) {
            Tree id = t.getIdentifier();
            if (id instanceof ParameterizedTypeTree p) {
                id = p.getType();
            }
            if (id instanceof MemberSelectTree m) {
                f.add("new:" + m.getIdentifier());
            } else if (id instanceof IdentifierTree i) {
                f.add("new:" + i.getName());
            }
            return super.visitNewClass(t, f);
        }

        @Override
        public Void visitSwitch(SwitchTree t, Set<String> f) {
            f.add("switch");
            return super.visitSwitch(t, f);
        }

        @Override
        public Void visitSwitchExpression(SwitchExpressionTree t, Set<String> f) {
            f.add("switch-expr");
            return super.visitSwitchExpression(t, f);
        }

        @Override
        public Void visitTry(TryTree t, Set<String> f) {
            f.add("try");
            if (!t.getResources().isEmpty()) {
                f.add("try-with-resources");
            }
            BlockTree fin = t.getFinallyBlock();
            if (fin != null) {
                f.add("finally");
            }
            return super.visitTry(t, f);
        }

        @Override
        public Void visitCatch(CatchTree t, Set<String> f) {
            f.add("catch");
            if (t.getParameter().getType() instanceof UnionTypeTree) {
                f.add("multi-catch");
            }
            return super.visitCatch(t, f);
        }

        @Override
        public Void visitThrow(ThrowTree t, Set<String> f) {
            f.add("throw");
            return super.visitThrow(t, f);
        }

        @Override
        public Void visitIf(IfTree t, Set<String> f) {
            f.add("if");
            return super.visitIf(t, f);
        }

        @Override
        public Void visitConditionalExpression(ConditionalExpressionTree t, Set<String> f) {
            f.add("ternary");
            return super.visitConditionalExpression(t, f);
        }

        @Override
        public Void visitInstanceOf(InstanceOfTree t, Set<String> f) {
            f.add("instanceof");
            return super.visitInstanceOf(t, f);
        }

        @Override
        public Void visitTypeCast(TypeCastTree t, Set<String> f) {
            f.add("cast");
            return super.visitTypeCast(t, f);
        }

        @Override
        public Void visitVariable(VariableTree t, Set<String> f) {
            Tree type = t.getType();
            if (type == null || type.toString().equals("var")) {
                f.add("var");
            }
            return super.visitVariable(t, f);
        }

        @Override
        public Void visitClass(ClassTree t, Set<String> f) {
            f.add("class:" + t.getKind().name().toLowerCase(Locale.ROOT));
            for (Modifier m : t.getModifiers().getFlags()) {
                f.add("modifier:" + m);
            }
            return super.visitClass(t, f);
        }

        @Override
        public Void visitMethod(MethodTree t, Set<String> f) {
            if (!t.getThrows().isEmpty()) {
                f.add("throws");
            }
            for (Modifier m : t.getModifiers().getFlags()) {
                f.add("modifier:" + m);
            }
            return super.visitMethod(t, f);
        }
    }
}
