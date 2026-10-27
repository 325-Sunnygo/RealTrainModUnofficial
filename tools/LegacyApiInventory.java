import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import javax.lang.model.element.Modifier;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;

/** Parse Java declarations without resolving Minecraft dependencies. TSV output. */
public final class LegacyApiInventory {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(null, null, java.nio.charset.StandardCharsets.UTF_8)) {
            List<java.io.File> files;
            try (var paths = Files.walk(Path.of(args[0]))) {
                files = paths.filter(p -> p.toString().endsWith(".java")).map(Path::toFile).toList();
            }
            JavacTask task = (JavacTask) compiler.getTask(null, manager, d -> {}, List.of("-proc:none"), null, manager.getJavaFileObjectsFromFiles(files));
            for (CompilationUnitTree unit : task.parse()) {
                String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
                new TreePathScanner<Void, String>() {
                    @Override public Void visitClass(ClassTree node, String parent) {
                        String name = node.getSimpleName().toString();
                        if (name.isEmpty()) return null;
                        String qualified = parent == null ? packageName + "." + name : parent + "$" + name;
                        String superclass = node.getExtendsClause() == null ? "" : node.getExtendsClause().toString();
                        System.out.println("CLASS\t" + qualified + "\t" + superclass);
                        boolean publicMembers = node.getKind() == Tree.Kind.INTERFACE || node.getKind() == Tree.Kind.ANNOTATION_TYPE;
                        for (Tree member : node.getMembers()) {
                            if (member instanceof MethodTree method && (publicMembers || visible(method.getModifiers()))) {
                                String params = String.join(",", method.getParameters().stream().map(p -> p.getType().toString()).toList());
                                System.out.println("METHOD\t" + qualified + "\t" + method.getName() + "\t" + method.getParameters().size() + "\t" + params);
                            } else if (member instanceof VariableTree field && (publicMembers || visible(field.getModifiers()))) {
                                System.out.println("FIELD\t" + qualified + "\t" + field.getName());
                            }
                        }
                        return super.visitClass(node, qualified);
                    }
                }.scan(unit, null);
            }
        }
    }
    private static boolean visible(ModifiersTree modifiers) {
        return modifiers.getFlags().contains(Modifier.PUBLIC) || modifiers.getFlags().contains(Modifier.PROTECTED);
    }
}
