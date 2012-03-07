package org.netbeans.modules.groovy.editor.api.parser;

import groovy.lang.GroovyClassLoader;
import java.io.IOException;
import java.security.CodeSource;
import java.util.*;
import java.util.concurrent.CancellationException;
import javax.lang.model.element.*;
import javax.lang.model.util.Elements;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.JavaSource;
import org.netbeans.api.java.source.Task;
import org.netbeans.modules.groovy.editor.java.ElementSearch;
import org.openide.util.Exceptions;

/**
 *
 * @author Martin Adamek
 */
final class EditorCompilationUnit extends org.codehaus.groovy.control.CompilationUnit {

    protected JavaSource javaSource;
    protected List<String> projectClasses;
    protected TypeCache typeCache;

    public EditorCompilationUnit(EditorParser parser, CompilerConfiguration configuration,
            CodeSource security, GroovyClassLoader loader, GroovyClassLoader transformationLoader,
            JavaSource javaSource) {

        super(configuration, security, loader, transformationLoader);
        this.ast = new CompileUnit(this, parser, this.classLoader, security, this.configuration, javaSource);
        this.resolveVisitor = new EditorResolveVisitor(this, false);
//        System.out.println("EditorCompilationUnit");
        typeCache = new TypeCache(javaSource);
        this.javaSource = javaSource;
    }

    public boolean isProjectClass(String className) {
        boolean b = false;
        if (projectClasses == null) {
            projectClasses = TypeCache.getProjectSources(javaSource);
        }
        if (projectClasses.contains(className)) {
            b = true;
        }

        return b;
    }

    public TypeCache getTypeCache() {
        return typeCache;
    }

    public JavaSource getJavaSource() {
        return this.javaSource;
    }

    public EditorResolveVisitor getResolveVisitor() {
        return (EditorResolveVisitor) this.resolveVisitor;
    }

    /*    protected static class SourceRoots {

        FileObject[] roots;

        public SourceRoots(JavaSource js) {
            ClassPath s = js.getClasspathInfo().getClassPath(ClasspathInfo.PathKind.SOURCE);
            roots = s.getRoots();
        }

    }
*/
    public static class CompileUnit extends org.codehaus.groovy.ast.CompileUnit {

        private final EditorParser parser;
        private final JavaSource javaSource;
        private final Map<String, ClassNode> cache = new HashMap<String, ClassNode>();
//        private SourceRoots roots;
        protected EditorCompilationUnit compilationUnit;
        protected List<JavaClassNode> incompleteNodes = new ArrayList<JavaClassNode>();

        public CompileUnit(EditorCompilationUnit compilationUnit, EditorParser parser, GroovyClassLoader classLoader,
                CodeSource codeSource, CompilerConfiguration config, JavaSource javaSource) {
            super(classLoader, codeSource, config);
            this.parser = parser;
            this.javaSource = javaSource;
            //roots = new SourceRoots(javaSource);
            this.compilationUnit = compilationUnit;


        }

        public Map<String, ClassNode> getCache() {
            return cache;
        }

        /*        public SourceRoots getRoots() {
            return roots;
        }
*/
        public List<JavaClassNode> getIncompleteNodes() {
            return incompleteNodes;
        }

        @Override
        public ClassNode getClass(final String name) {
//System.out.println("#### 6666 ~~~~~ getClass name=" + name);                        
            if (parser.isCancelled()) {
                throw new CancellationException();
            }

            ClassNode classNode;
            // check the cache for non-null value
            synchronized (cache) {
                classNode = cache.get(name);
                if (classNode != null) {
                    //if ( classNode instanceof JavaClassNode)
                    return cache.get(name);
                }
            }

//            TypeCache typeCache = ((EditorResolveVisitor) compilationUnit.resolveVisitor).getTypeCache();
            TypeCache typeCache = this.compilationUnit.typeCache;
            if (!typeCache.canBeResolved(name)) {
                return null;
            }
            String pname = typeCache.getCacheProposal(name);
            if (pname == null) {
                return null;
            }
            ClassNode pnode = null;

            long start = System.currentTimeMillis();
            //if (!roots.containsPackageByClassName(name)) {

            if (!compilationUnit.isProjectClass(name)) {
                //long end = System.currentTimeMillis();
                return null;
            }

            // if null or not present in cache
            classNode = super.getClass(name);
            if (classNode != null) {
                return classNode;
            }

            // if present in cache but null
            synchronized (cache) {
                if (cache.containsKey(name)) {
                    return null;
                }
            }

            try {
                Task<CompilationController> task = new Task<CompilationController>() {

                    public void run(CompilationController controller) throws Exception {
                        Elements elements = controller.getElements();

                        TypeElement typeElement = ElementSearch.getClass(elements, name);
                        JavaClassNodeResolver javaClassResolver = new JavaClassNodeResolver(compilationUnit);
                        synchronized (cache) {
                            //javaClassResolver.getClass(name,elements);
                            if (typeElement != null) {
                                ClassNode node = javaClassResolver.getClass(name, elements);
                                //ClassNode node = createClassNode(name, typeElement);
                                if ((node instanceof JavaClassNode) && !node.isResolved()) {
                                    incompleteNodes.add((JavaClassNode) node);
                                }
                                if (node != null) {
                                    cache.put(name, node);
                                }
                            } else {
                                if (!cache.containsKey(name)) {
                                    cache.put(name, null);
                                }
                            }
                        }
                    }
                };

                javaSource.runUserActionTask(task, true);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }

            synchronized (cache) {
                return cache.get(name);
            }
        }

/*        private ClassNode createClassNode(String name, TypeElement typeElement) {
            int modifiers = 0;
            ClassNode superClass = null;
            Set<ClassNode> interfaces = new HashSet<ClassNode>();

            if (typeElement.getKind().isInterface()) {
                if (typeElement.getKind() == ElementKind.ANNOTATION_TYPE) {
// FIXME give it up and use classloader - annotations created in sources won't work
// OTOH it will not resolve annotation coming from java source file :( 170517
                    return null;
//                    modifiers |= Opcodes.ACC_ANNOTATION;
//                    interfaces.add(ClassHelper.Annotation_TYPE);
                }
                modifiers |= Opcodes.ACC_INTERFACE;

                for (TypeMirror interf : typeElement.getInterfaces()) {
                    interfaces.add(new ClassNode(Utilities.getClassName(interf).toString(),
                            Opcodes.ACC_INTERFACE, null));
                }
            } else {
                // initialize supertypes
                // super class is required for try {} catch block exception type
                Stack<DeclaredType> supers = new Stack<DeclaredType>();
                while (typeElement != null && typeElement.asType().getKind() != TypeKind.NONE) {
                    TypeMirror type = typeElement.getSuperclass();

                    if (type.getKind() != TypeKind.DECLARED) {
                        break;
                    }

                    DeclaredType superType = (DeclaredType) typeElement.getSuperclass();
                    String className = superType.toString();
                    ClassNode cn = resolveToClass(className);
                    if (cn != null) {
                        //superClass = ClassHelper.make(className);
                        //superClass.setRedirect(cn);
                        superClass = cn;
                        //supers.push(superType);
                        break;
                    }

                    supers.push(superType);

                    Element element = superType.asElement();
                    if ((element.getKind() == ElementKind.CLASS
                            || element.getKind() == ElementKind.ENUM) && (element instanceof TypeElement)) {

                        typeElement = (TypeElement) element;
                        continue;
                    }

                    typeElement = null;
                }

                while (!supers.empty()) {
                    superClass = createClassNode(Utilities.getClassName(supers.pop()).toString(),
                            0, superClass, Collections.<ClassNode>emptySet());
                }
            }
            return createClassNode(name, modifiers, superClass, interfaces);
        }

        private ClassNode createClassNode(String name, int modifiers, ClassNode superClass, Set<ClassNode> interfaces) {
            if ("java.lang.Object".equals(name) && superClass == null) { // NOI18N
                return ClassHelper.OBJECT_TYPE;
            }
            ClassNode cn = new JavaClassNode(name, modifiers, superClass,
                    (ClassNode[]) interfaces.toArray(new ClassNode[interfaces.size()]), MixinNode.EMPTY_ARRAY);

            return cn;
        }

        public ClassNode resolveToClass(String name) {

            if ("java.lang.Object".equals(name)) {
                return ClassHelper.OBJECT_TYPE;
            }

            TypeCache typeCache = ((EditorResolveVisitor) compilationUnit.resolveVisitor).getTypeCache();

            ClassNode cached = typeCache.getResolvedBySimpleName(name);
            if (cached != null) {
                return cached;
            }

            if (!typeCache.canBeResolved(name)) {
                return null;
            }
            String pname = typeCache.getCacheProposal(name);
            if (pname == null) {
                return null;
            }
            name = pname;
            GroovyClassLoader loader = compilationUnit.getClassLoader();
            Class cls;
            try {
                // NOTE: it's important to do no lookup against script files
                // here since the GroovyClassLoader would create a new CompilationUnit
                cls = loader.loadClass(name, false, true);
            } catch (ClassNotFoundException cnfe) {
                //System.out.println("4) resolveToClass COMPILEUNIT CLASS NOT FOUND name=" + name + "; msg=" + cnfe.getMessage());
                return null;
            } catch (CompilationFailedException cfe) {
                System.out.println("5) resolveToClass COMPILEUNIT CLASS NOT FOUND name=" + name + "; msg=" + cfe.getMessage());
                return null;
            }

            if (cls == null) {
                return null;
            }

            ClassNode cn = ClassHelper.make(cls);

            typeCache.setResolved(name, cn);
            return cn;
        }
        */
    }

}
