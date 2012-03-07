/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.api.parser;

import groovy.lang.GroovyClassLoader;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.Verifier;
import org.codehaus.groovy.control.messages.ExceptionMessage;
import org.codehaus.groovy.syntax.Types;
import org.codehaus.groovy.GroovyBugError;


import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import javax.lang.model.element.TypeElement;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;
import org.netbeans.api.java.source.ClassIndex;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;

/**
 * Visitor to resolve Types and convert VariableExpression to ClassExpressions
 * if needed. The ResolveVisitor will try to find the Class for a ClassExpression
 * and prints an error if it fails to do so. Constructions like C[], foo as C,
 * (C) foo will force creation of a ClassExpression for C
 * <p/> Note: the method to start the resolving is startResolving(ClassNode,
 * SourceUnit).
 *
 * @author Jochen Theodorou
 * @author Roshan Dawrani
 * @author Alex Tkachman
 */
public class PlaceholderResolveVisitorDelegate extends ClassCodeExpressionTransformer {

    protected boolean completionTask;
    //// My
    protected TypeCache typeCache;
    //// end My
    //public List resolvInfo = new ArrayList(50);
    private ClassNode currentClass;
    // note: BigInteger and BigDecimal are also imported by default
    public static final String[] DEFAULT_IMPORTS = {"java.lang.", "java.io.", "java.net.", "java.util.", "groovy.lang.", "groovy.util."};
    private org.codehaus.groovy.control.CompilationUnit compilationUnit;
    private Map cachedClasses = new HashMap();
    private static final Object NO_CLASS = new Object();
    private SourceUnit source;
    private VariableScope currentScope;
    private boolean isTopLevelProperty = true;
    private boolean inPropertyExpression = false;
    private boolean inClosure = false;
    private Map<String, GenericsType> genericParameterNames = new HashMap<String, GenericsType>();
    private Set<FieldNode> fieldTypesChecked = new HashSet<FieldNode>();
    private boolean checkingVariableTypeInDeclaration = false;
    private ImportNode currImportNode = null;
    private MethodNode currentMethod;

    /**
     * we use ConstructedClassWithPackage to limit the resolving the compiler
     * does when combining package names and class names. The idea
     * that if we use a package, then we do not want to replace the
     * '.' with a '$' for the package part, only for the class name
     * part. There is also the case of a imported class, so this logic
     * can't be done in these cases...
     */
    private static class ConstructedClassWithPackage extends ClassNode {

        String prefix;
        String className;

        public ConstructedClassWithPackage(String pkg, String name) {
            super(pkg + name, Opcodes.ACC_PUBLIC, ClassHelper.OBJECT_TYPE);
            isPrimaryNode = false;
            this.prefix = pkg;
            this.className = name;
        }

        @Override
        public String getName() {
            if (redirect() != this) {
                return super.getName();
            }
            return prefix + className;
        }

        @Override
        public boolean hasPackageName() {
            if (redirect() != this) {
                return super.hasPackageName();
            }
            return className.indexOf('.') != -1;
        }

        @Override
        public String setName(String name) {
            if (redirect() != this) {
                return super.setName(name);
            } else {
                throw new GroovyBugError("ConstructedClassWithPackage#setName should not be called");
            }
        }
    }
    

    /**
     * we use LowerCaseClass to limit the resolving the compiler
     * does for vanilla names starting with a lower case letter. The idea
     * that if we use a vanilla name with a lower case letter, that this
     * is in most cases no class. If it is a class the class needs to be
     * imported explicitly. The efffect is that in an expression like
     * "def foo = bar" we do not have to use a loadClass call to check the
     * name foo and bar for being classes. Instead we will ask the module
     * for an alias for this name which is much faster.
     */
    private static class LowerCaseClass extends ClassNode {

        String className;

        public LowerCaseClass(String name) {
            super(name, Opcodes.ACC_PUBLIC, ClassHelper.OBJECT_TYPE);
            isPrimaryNode = false;
            this.className = name;
        }

        @Override
        public String getName() {
            if (redirect() != this) {
                return super.getName();
            }
            return className;
        }

        @Override
        public boolean hasPackageName() {
            if (redirect() != this) {
                return super.hasPackageName();
            }
            return false;
        }

        @Override
        public String setName(String name) {
            if (redirect() != this) {
                return super.setName(name);
            } else {
                throw new GroovyBugError("ConstructedClassWithPackage#setName should not be called");
            }
        }
    }

    protected EditorResolveVisitor owner;
    
    public PlaceholderResolveVisitorDelegate(EditorResolveVisitor owner,CompilationUnit cu, boolean isCompletionTask) {
        compilationUnit = cu;
        this.completionTask = isCompletionTask;
        this.owner = owner;
    }

/*    public void startResolving(ClassNode node, SourceUnit source) {
        this.source = source;
        this.typeCache = new TypeCache((HasJavaSource)compilationUnit);
        visitClass(node);
    }
*/
    public void startResolving(ClassNode node, SourceUnit source, TypeCache typeCache) {
        this.source = source;
        this.typeCache = typeCache;
        visitClass(node);
    }

    protected void visitSuperConstructorOrMethod(MethodNode node, boolean isConstructor) {
        visitAnnotations(node);
        visitClassCodeContainer(node.getCode());
        for (Parameter param : node.getParameters()) {
            visitAnnotations(param);
        }
    }

    @Override
    protected void visitConstructorOrMethod(MethodNode node, boolean isConstructor) {
        VariableScope oldScope = currentScope;
        currentScope = node.getVariableScope();
        Map<String, GenericsType> oldPNames = genericParameterNames;
        genericParameterNames = new HashMap<String, GenericsType>(genericParameterNames);

        resolveGenericsHeader(node.getGenericsTypes());

        Parameter[] paras = node.getParameters();
        for (Parameter p : paras) {
            p.setInitialExpression(transform(p.getInitialExpression()));
            resolveOrFail(p.getType(), p.getType());
            visitAnnotations(p);
        }
        ClassNode[] exceptions = node.getExceptions();
        for (ClassNode t : exceptions) {
            resolveOrFail(t, node);
        }
        resolveOrFail(node.getReturnType(), node);

        MethodNode oldCurrentMethod = currentMethod;
        currentMethod = node;
        //// Mysuper.visitConstructorOrMethod(node, isConstructor);
        visitSuperConstructorOrMethod(node, isConstructor);
        currentMethod = oldCurrentMethod;

        genericParameterNames = oldPNames;
        currentScope = oldScope;
    }

    @Override
    public void visitField(FieldNode node) {
        ClassNode t = node.getType();
        if (!fieldTypesChecked.contains(node)) {
            resolveOrFail(t, node);
        }
        super.visitField(node);
        tryInferFieldType(node);
    }

    @Override
    public void visitProperty(PropertyNode node) {
        ClassNode t = node.getType();
        resolveOrFail(t, node);
        super.visitProperty(node);
        fieldTypesChecked.add(node.getField());
    }

    private boolean resolveToInner(ClassNode type) {
        // we do not do our name mangling to find an inner class
        // if the type is a ConstructedClassWithPackage, because in this case we
        // are resolving the name at a different place already
        if (type instanceof ConstructedClassWithPackage) {
            return false;
        }
        String name = type.getName();
        String saved = name;
        while (true) {
            int len = name.lastIndexOf('.');
            if (len == -1) {
                break;
            }
            name = name.substring(0, len) + "$" + name.substring(len + 1);
            type.setName(name);
            if (resolve(type)) {
                return true;
            }
        }
        if (resolveToInnerEnum(type)) {
            return true;
        }

        type.setName(saved);
        return false;
    }

    private boolean resolveToInnerEnum(ClassNode type) {
        // GROOVY-3110: It may be an inner enum defined by this class itself, in which case it does not need to be
        // explicitly qualified by the currentClass name
        String name = type.getName();
        if (currentClass != type && !name.contains(".") && type.getClass().equals(ClassNode.class)) {
            type.setName(currentClass.getName() + "$" + name);
            if (resolve(type)) {
                return true;
            }
        }
        return false;
    }

    private void resolveOrFail(ClassNode type, String msg, ASTNode node) {
        if (resolve(type)) {
            return;
        }
        if (resolveToInner(type)) {
            return;
        }
        addError("unable to resolve class " + type.getName() + " " + msg, node);
    }

    private void resolveOrFail(ClassNode type, ASTNode node, boolean prefereImports) {
        resolveGenericsTypes(type.getGenericsTypes());
        if (prefereImports && resolveAliasFromModule(type)) {
            return;
        }
        resolveOrFail(type, node);
    }

    private void resolveOrFail(ClassNode type, ASTNode node) {
        resolveOrFail(type, "", node);
    }

    private boolean resolve(ClassNode type) {
        boolean r = resolve(type, true, true, true);
        return r;
    }

    protected String toSimpleName(String name) {
        String simpleName = name;
        int n1 = simpleName.lastIndexOf('.');
        int n2 = simpleName.lastIndexOf('$');
        int n = Math.max(n1, n2);

        if (n >= 0) {
            simpleName = simpleName.substring(n + 1);
        }
        return simpleName;
    }

    protected boolean isSimpleName(String name) {
        String sname = toSimpleName(name);
        return name.length() == sname.length();
    }

    private boolean isResolvedVirtually(ClassNode type) {
        if (type.isResolved()) {
            return true;
        }
//        if (!this.completionTask) {
//            return false;
//        }
        ClassNode n = type.redirect();
        if (n instanceof ResolvedTypePlaceholder) {
            return true;
        }
        return false;
    }
    private boolean resolveOld(ClassNode type, boolean testModuleImports, boolean testDefaultImports, boolean testStaticInnerClasses) {
        resolveGenericsTypes(type.getGenericsTypes());
        if (type.isResolved() || type.isPrimaryClassNode()) return true;
        if (type.isArray()) {
            ClassNode element = type.getComponentType();
            boolean resolved = resolve(element, testModuleImports, testDefaultImports, testStaticInnerClasses);
            if (resolved) {
                ClassNode cn = element.makeArray();
                type.setRedirect(cn);
            }
            return resolved;
        }

        // test if vanilla name is current class name
        if (currentClass == type) return true;

        if (genericParameterNames.get(type.getName()) != null) {
            GenericsType gt = genericParameterNames.get(type.getName());
            type.setRedirect(gt.getType());
            type.setGenericsTypes(new GenericsType[]{gt});
            type.setGenericsPlaceHolder(true);
            return true;
        }

        if (currentClass.getNameWithoutPackage().equals(type.getName())) {
            type.setRedirect(currentClass);
            return true;
        }

        return resolveNestedClass(type) ||
                resolveFromModule(type, testModuleImports) ||
                resolveFromCompileUnit(type) ||
                resolveFromDefaultImports(type, testDefaultImports) ||
                resolveFromStaticInnerClasses(type, testStaticInnerClasses) ||
                resolveToClass(type);
    }

    private boolean resolve(ClassNode type, boolean testModuleImports, boolean testDefaultImports, boolean testStaticInnerClasses) {
//System.out.println("@@@@ 1) ==========================" + type.getName() + "isThis isResolved()=" + type.isResolved());                
        resolveGenericsTypes(type.getGenericsTypes());

        if (isResolvedVirtually(type) || type.isPrimaryClassNode()) {
//System.out.println("@@@@ 2) ========================== isResolved="+isResolvedVirtually(type) + "; isPrimary=" + type.isPrimaryClassNode());                            
            return true;
        }
        if (type.isArray()) {
            ClassNode element = type.getComponentType();
//System.out.println("@@@@ 3) is Array() TRY RESOLVE ================ ComponentTypename = "+element.getName() + "; isResolved = " + isResolvedVirtually(element));                            

            boolean resolved = resolve(element, testModuleImports, testDefaultImports, testStaticInnerClasses);
//System.out.println("@@@@ 4) is Array() resolveResult ================ ComponentTypename = "+element.getName()+ "; isResolved = " + isResolvedVirtually(element) + "; resolveClass=" + element.redirect().getClass());                                        
            if (resolved) {
                ClassNode cn = element.makeArray();
                type.setRedirect(cn);
            }

            return resolved;
        }

        // test if vanilla name is current class name
        if (currentClass == type) {
            return true;
        }

        if (genericParameterNames.get(type.getName()) != null) {
            GenericsType gt = genericParameterNames.get(type.getName());
            type.setRedirect(gt.getType());
            type.setGenericsTypes(new GenericsType[]{gt});
            type.setGenericsPlaceHolder(true);
            return true;
        }

        if (currentClass.getNameWithoutPackage().equals(type.getName())) {
            type.setRedirect(currentClass);
            return true;
        }

        boolean r = false;
        long start = System.currentTimeMillis();
        //typeCache.print(type.getName());

        if (!typeCache.hasProposals(type.getName())) {
            return false;
        }

        if (isSimpleName(type.getName())) {
            ClassNode rnode = typeCache.getResolvedBySimpleName(type.getName());
            if (rnode != null) {
                type.setRedirect(rnode);
                return true;
            }
        }
        long end = System.currentTimeMillis();
//        System.out.println(" ---- RESOLVE .... tryMainCache TIME=" + (end - start) + "; name=" + type.getName());

        /*
         * r = resolveNestedClass(type) || resolveFromModule(type,
         * testModuleImports) || resolveFromCompileUnit(type) ||
         * resolveFromDefaultImports(type, testDefaultImports) ||
         * resolveFromStaticInnerClasses(type, testStaticInnerClasses) ||
         * resolveToClass(type);
         */
        start = System.currentTimeMillis();
        r = resolveNestedClass(type);
        end = System.currentTimeMillis();
//        System.out.println(" ---- resolveNestedClass TIME=" + (end - start) + "; name=" + type.getName());
        start = System.currentTimeMillis();
        if (!r) {
            r = resolveFromModule(type, testModuleImports);
        }
        end = System.currentTimeMillis();
//        System.out.println(" ---- resolveFromModule TIME=" + (end - start) + "; name=" + type.getName());

        start = System.currentTimeMillis();
        if (!r) {
            r = resolveFromCompileUnit(type);
        }
        end = System.currentTimeMillis();
//        System.out.println(" ---- resolveFromCompileUnit TIME=" + (end - start) + "; name=" + type.getName());


        start = System.currentTimeMillis();
        if (!r) {
//System.out.println("@@@@ 10) ==========================");                                    
            r = resolveFromDefaultImports(type, testDefaultImports);
        }
        end = System.currentTimeMillis();
        //System.out.println(" ---- resolveFromDefaultImports TIME=" + (end - start) + "; name=" + type.getName());
        start = System.currentTimeMillis();

        if (!r) {
//System.out.println("@@@@ 11) ==========================");                                    
            r = resolveFromStaticInnerClasses(type, testStaticInnerClasses);
        }
        end = System.currentTimeMillis();
        //System.out.println(" ---- resolveFromStaticInnerClasses TIME=" + (end - start) + "; name=" + type.getName());
        start = System.currentTimeMillis();

        if (!r) {
//System.out.println("@@@@ 12) ==========================");                                    
            r = resolveToClass(type);
        }
        end = System.currentTimeMillis();
        //System.out.println(" ---- resolveFromStaticInnerClasses TIME=" + (end - start) + "; name=" + type.getName());

        //System.out.println("========================================================");
//System.out.println("@@@@ 13) ========================== " + r);                        
        return r;
    }

    /*
    private String[] resolveNestedClass(ClassNode type, LinkedList<String> hierClasses, LinkedList<String> outerClasses) {
        String[] r = new String[]{null, null};
        CompilationUnit.CompileUnit cu = (CompilationUnit.CompileUnit) this.compilationUnit.getAST();
        JavaSource javaSource = cu.javaSource;

        Set<ClassIndex.SearchScopeType> scope = new HashSet<ClassIndex.SearchScopeType>();

        scope.add(ClassIndex.SearchScope.DEPENDENCIES);
        scope.add(ClassIndex.SearchScope.SOURCE);
        System.out.println("!!!!! RESOLVE resolveNestedClass 1" + type.getName());


        for (int i = 0; i < hierClasses.size(); i++) {
            String simpleName = hierClasses.get(i);
            int n1 = simpleName.lastIndexOf('.');
            int n2 = simpleName.lastIndexOf('$');
            int n = Math.max(n1, n2);

            if (n >= 0) {
                simpleName = simpleName.substring(n + 1);
            }

            Set<ElementHandle<TypeElement>> dtypes = javaSource.getClasspathInfo().getClassIndex().getDeclaredTypes(simpleName, ClassIndex.NameKind.SIMPLE_NAME, scope);
            if (dtypes == null || dtypes.isEmpty()) {
                if (!cu.cache.containsKey(hierClasses.get(i))) {
                    cu.cache.put(hierClasses.get(i), null);
                }
                //return null;
            } else {
                System.out.println("!!!!! RESOLVE resolveNestedClass 2" + type.getName() + "; hierClasses.get(" + i + ")="
                        + hierClasses.get(i));

                for (ElementHandle h : dtypes) {
                    String s = h.getQualifiedName();
                    System.out.println("!!!!! RESOLVE resolveNestedClass 3" + type.getName() + "; hierClasses.get(" + i + ")="
                            + hierClasses.get(i) + "; h.qual=" + s);

                    if (s.equals(hierClasses.get(i))) {
                        r[0] = "" + i;
                        break;
                    }
                }
                if (r[0] == null) {
                    cu.cache.put(hierClasses.get(i), null);
                }
            }
        }//for
        if (r[0] != null || outerClasses == null) {
            return r;
        }

        for (int i = 0; i < outerClasses.size(); i++) {
            String simpleName = outerClasses.get(i);
            int n1 = simpleName.lastIndexOf('.');
            int n2 = simpleName.lastIndexOf('$');
            int n = Math.max(n1, n2);

            if (n >= 0) {
                simpleName = simpleName.substring(n + 1);
            }

            Set<ElementHandle<TypeElement>> dtypes = javaSource.getClasspathInfo().getClassIndex().getDeclaredTypes(simpleName, ClassIndex.NameKind.SIMPLE_NAME, scope);
            if (dtypes == null || dtypes.isEmpty()) {
                if (!cu.cache.containsKey(simpleName)) {
                    cu.cache.put(simpleName, null);
                }
                //return null;
            } else {
                for (ElementHandle h : dtypes) {
                    String s = h.getQualifiedName();

                    if (s.equals(outerClasses.get(i))) {
                        r[1] = "" + i;
                        break;
                    }
                }
            }
        }//for

        return r;
    }

    private int resolveNestedFromJavaIndex(ClassNode type, LinkedList<String> hierClasses) {
        int r = -1;

        CompilationUnit.CompileUnit cu = (CompilationUnit.CompileUnit) this.compilationUnit.getAST();
        JavaSource javaSource = cu.javaSource;

        Set<ClassIndex.SearchScopeType> scope = new HashSet<ClassIndex.SearchScopeType>();

        scope.add(ClassIndex.SearchScope.DEPENDENCIES);
        scope.add(ClassIndex.SearchScope.SOURCE);
        System.out.println("!!!!! RESOLVE resolveNestedClass 1" + type.getName());


        for (int i = 0; i < hierClasses.size(); i++) {
            String simpleName = hierClasses.get(i);
            int n1 = simpleName.lastIndexOf('.');
            int n2 = simpleName.lastIndexOf('$');
            int n = Math.max(n1, n2);

            if (n >= 0) {
                simpleName = simpleName.substring(n + 1);
            }

            Set<ElementHandle<TypeElement>> dtypes = javaSource.getClasspathInfo().getClassIndex().getDeclaredTypes(simpleName, ClassIndex.NameKind.SIMPLE_NAME, scope);
            if (dtypes == null || dtypes.isEmpty()) {
                if (!cu.cache.containsKey(hierClasses.get(i))) {
                    cu.cache.put(hierClasses.get(i), null);
                }
                //return null;
            } else {
                System.out.println("!!!!! RESOLVE resolveNestedClass 2" + type.getName() + "; hierClasses.get(" + i + ")="
                        + hierClasses.get(i));

                for (ElementHandle h : dtypes) {
                    String s = h.getQualifiedName();
                    System.out.println("!!!!! RESOLVE resolveNestedClass 3" + type.getName() + "; hierClasses.get(" + i + ")="
                            + hierClasses.get(i) + "; h.qual=" + s);

                    if (s.equals(hierClasses.get(i))) {
                        r = i;
                        break;
                    }
                }
                if (r < 0) {
                    cu.cache.put(hierClasses.get(i), null);
                }
            }
        }//for

        return r;
    }
*/
    private int resolveNestedIfGroovy(ClassNode type, LinkedList<String> hierClasses) {
        int r = -1;
        boolean b = false;

        CompletionCompilationUnit.CompileUnit cu = (CompletionCompilationUnit.CompileUnit) this.compilationUnit.getAST();

        for (int i = 0; i < hierClasses.size(); i++) {
            String name = hierClasses.get(i);
            try {
                b = cu.getClassLoader().getResourceLoader().loadGroovySource(name) != null;
            } catch (Exception ex) {
            }
            if (b) {
                r = i;
                if (cu.cache.containsKey(name) && cu.cache.get(name) == null) {
                    cu.cache.remove(name);
                }
                break;
            }
            if (r < 0) {
                cu.cache.put(hierClasses.get(i), null);
            }
        }//for

        return r;
    }
    /*
    private boolean resolveNestedClassNew(ClassNode type) {
        System.out.println("START  resolveNestedClass " + type.getName());
        // we have for example a class name A, are in class X
        // and there is a nested class A$X. we want to be able 
        // to access that class directly, so A becomes a valid
        // name in X.
        // GROOVY-4043: Do this check up the hierarchy, if needed
        LinkedList<String> hierClasses = new LinkedList<String>();
        Map<String, ClassNode> hierClassesMap = new LinkedHashMap<String, ClassNode>();
        //boolean r = false;

        ClassNode val;
        String name;
        for (ClassNode classToCheck = currentClass; classToCheck != ClassHelper.OBJECT_TYPE;
                classToCheck = classToCheck.getSuperClass()) {
            if (classToCheck == null || hierClassesMap.containsKey(classToCheck.getName())) {
                break;
            }
            hierClassesMap.put(classToCheck.getName(), classToCheck);
        }
        for (ClassNode classToCheck : hierClassesMap.values()) {
            name = classToCheck.getName() + "$" + type.getName();
            hierClasses.add(name);
        }

        CompilationUnit.CompileUnit cu = (CompilationUnit.CompileUnit) this.compilationUnit.getAST();
        System.out.println("^^^ resolveNestedClass " + type.getName() + "; "
                + "hierClasses.size()=" + hierClasses.size() + "; cu!=null =" + (cu != null));

        for (int i = 0; i < hierClasses.size(); i++) {
            String simpleName = hierClasses.get(i);
            ClassNode cuNode = cu.cache.get(simpleName);
            if (cuNode != null) {
                type.setRedirect(cuNode);
                return true;
            }
            if (cu.cache.containsKey(simpleName)) {
                return false;
            }
        }
        System.out.println("^^^ resolveNestedClass " + type.getName() + "; "
                + "hierClasses END 1");

        // another case we want to check here is if we are in a
        // nested class A$B$C and want to access B without
        // qualifying it by A.B. A alone will work, since that
        // is the qualified (minus package) name of that class
        // anyway. 

        // That means if the current class is not an InnerClassNode
        // there is nothing to be done.
        if (!(currentClass instanceof InnerClassNode)) {
            String[] r = this.resolveNestedClass(type, hierClasses, null);
            System.out.println("^^^ resolveNestedClass 2 " + type.getName() + "; "
                    + "r = " + r);


            if (r[0] == null) {
                return false;
            } else {
                name = hierClasses.get(Integer.parseInt(r[0]));
                val = ClassHelper.make(name);
                if (resolveFromCompileUnit(val)) {
                    type.setRedirect(val);
                    return true;
                }
                return false;
            }
        }

        // since we have B and want to get A we start with the most 
        // outer class, put them together and then see if that does
        // already exist. In case of B from within A$B we are done 
        // after the first step already. In case of for example
        // A.B.C.D.E.F and accessing E from F we test A$E=failed, 
        // A$B$E=failed, A$B$C$E=fail, A$B$C$D$E=success

        LinkedList<ClassNode> outerClasses = new LinkedList<ClassNode>();
        LinkedList<String> outerNames = new LinkedList<String>();
        ClassNode outer = currentClass.getOuterClass();
        while (outer != null) {
            outerClasses.addFirst(outer);
            outer = outer.getOuterClass();
        }
        for (ClassNode testNode : outerClasses) {
            name = testNode.getName() + "$" + type.getName();
            outerNames.add(name);
        }

        for (int i = 0; i < outerNames.size(); i++) {
            String simpleName = outerNames.get(i);
            ClassNode cuNode = cu.cache.get(simpleName);
            if (cuNode != null) {
                type.setRedirect(cuNode);
                return true;
            }
            if (cu.cache.containsKey(simpleName)) {
                return false;
            }
        }

        String[] r = this.resolveNestedClass(type, hierClasses, outerNames);

        if (r[0] != null) {
            name = hierClasses.get(Integer.parseInt(r[0]));
            val = ClassHelper.make(name);
            if (resolveFromCompileUnit(val)) {
                type.setRedirect(val);
                return true;
            }
        }
        if (r[1] != null) {
            name = outerNames.get(Integer.parseInt(r[1]));
            val = ClassHelper.make(name);
            if (resolveFromCompileUnit(val)) {
                type.setRedirect(val);
                return true;
            }
        }
        return false;

        // most outer class is now element 0

    }
*/

    private boolean resolveNestedClass(ClassNode type) {
//        System.out.println("START  resolveNestedClass " + type.getName());
        // we have for example a class name A, are in class X
        // and there is a nested class A$X. we want to be able 
        // to access that class directly, so A becomes a valid
        // name in X.
        // GROOVY-4043: Do this check up the hierarchy, if needed
        Map<String, ClassNode> hierClasses = new LinkedHashMap<String, ClassNode>();
        ClassNode val;
        String name;
        for (ClassNode classToCheck = currentClass; classToCheck != ClassHelper.OBJECT_TYPE;
                classToCheck = classToCheck.getSuperClass()) {
            if (classToCheck == null || hierClasses.containsKey(classToCheck.getName())) {
                break;
            }
            hierClasses.put(classToCheck.getName(), classToCheck);
        }

        for (ClassNode classToCheck : hierClasses.values()) {
            name = classToCheck.getName() + "$" + type.getName();
            val = ClassHelper.make(name);

//            System.out.println(" try  resolveNestedClass 0 TRUE" + type.getName());

            if (resolveFromCompileUnit(val)) {
                type.setRedirect(val);
//                System.out.println("+++  resolveNestedClass 1 TRUE" + type.getName());
                return true;
            }
        }

        // another case we want to check here is if we are in a
        // nested class A$B$C and want to access B without
        // qualifying it by A.B. A alone will work, since that
        // is the qualified (minus package) name of that class
        // anyway. 

        // That means if the current class is not an InnerClassNode
        // there is nothing to be done.
        if (!(currentClass instanceof InnerClassNode)) {
            return false;
        }

        // since we have B and want to get A we start with the most 
        // outer class, put them together and then see if that does
        // already exist. In case of B from within A$B we are done 
        // after the first step already. In case of for example
        // A.B.C.D.E.F and accessing E from F we test A$E=failed, 
        // A$B$E=failed, A$B$C$E=fail, A$B$C$D$E=success

        LinkedList<ClassNode> outerClasses = new LinkedList<ClassNode>();
        ClassNode outer = currentClass.getOuterClass();
        while (outer != null) {
            outerClasses.addFirst(outer);
            outer = outer.getOuterClass();
        }
        // most outer class is now element 0
        for (ClassNode testNode : outerClasses) {
            name = testNode.getName() + "$" + type.getName();
            val = ClassHelper.make(name);
            if (resolveFromCompileUnit(val)) {
                type.setRedirect(val);
//                System.out.println("+++  resolveNestedClass 2 TRUE" + type.getName());
                return true;
            }
        }

        return false;
    }

    // NOTE: copied from GroovyClassLoader
    private long getTimeStamp(Class cls) {
        return Verifier.getTimestamp(cls);
    }

    // NOTE: copied from GroovyClassLoader
    private boolean isSourceNewer(URL source, Class cls) {
        try {
            long lastMod;

            // Special handling for file:// protocol, as getLastModified() often reports
            // incorrect results (-1)
            if (source.getProtocol().equals("file")) {
                // Coerce the file URL to a File
                String path = source.getPath().replace('/', File.separatorChar).replace('|', ':');
                File file = new File(path);
                lastMod = file.lastModified();
            } else {
                URLConnection conn = source.openConnection();
                lastMod = conn.getLastModified();
                conn.getInputStream().close();
            }
            return lastMod > getTimeStamp(cls);
        } catch (IOException e) {
            // if the stream can't be opened, let's keep the old reference
            return false;
        }
    }

    private boolean resolveToScript(ClassNode type) {
        String name = type.getName();

        if (name.startsWith("java.")) {
            //return type.isResolved();
            return isResolvedVirtually(type);
        }
        //TODO: don't ignore inner static classes completely
        if (name.indexOf('$') != -1) {
            //return type.isResolved();
            return isResolvedVirtually(type);

        }
        ModuleNode module = currentClass.getModule();
        if (module.hasPackageName() && name.indexOf('.') == -1) {
            //return type.isResolved();
            return isResolvedVirtually(type);

        }
        // try to find a script from classpath
        GroovyClassLoader gcl = compilationUnit.getClassLoader();
        URL url = null;
        try {
            url = gcl.getResourceLoader().loadGroovySource(name);
        } catch (MalformedURLException e) {
            // fall through and let the URL be null
        }
        if (url != null) {
            if (isResolvedVirtually(type)) {
                Class cls = type.getTypeClass();
                // if the file is not newer we don't want to recompile
                if (!isSourceNewer(url, cls)) {
                    return true;
                }
                // since we came to this, we want to recompile
                cachedClasses.remove(type.getName());
                type.setRedirect(null);
            }
            SourceUnit su = compilationUnit.addSource(url);
            currentClass.getCompileUnit().addClassNodeToCompile(type, su);
            return true;
        }
        // type may be resolved through the classloader before
        return this.isResolvedVirtually(type);
        //return type.isResolved();
    }

    private String replaceLastPoint(String name) {
        int lastPoint = name.lastIndexOf('.');
        name = new StringBuffer().append(name.substring(0, lastPoint)).append("$").append(name.substring(lastPoint + 1)).toString();
        return name;
    }

    private boolean resolveFromStaticInnerClasses(ClassNode type, boolean testStaticInnerClasses) {
//        System.out.println("   --------------- START resolveFromStaticInnerClasses " + type.getName() + "nead: " + (testStaticInnerClasses &= type.hasPackageName()));

//        System.out.println("START resolveFromStaticInnerClasses " + type.getName());
        // a class consisting of a vanilla name can never be
        // a static inner class, because at least one dot is
        // required for this. Example: foo.bar -> foo$bar
        if (type instanceof LowerCaseClass) {
            return false;
        }

        // try to resolve a public static inner class' name
        testStaticInnerClasses &= type.hasPackageName();
        if (testStaticInnerClasses) {
            if (type instanceof ConstructedClassWithPackage) {
                // we replace '.' only in the className part
                // with '$' to find an inner class. The case that
                // the package is really a class is handled elsewhere
                ConstructedClassWithPackage tmp = (ConstructedClassWithPackage) type;
                String savedName = tmp.className;
                long start = System.currentTimeMillis();

                tmp.className = replaceLastPoint(savedName);
                if (resolve(tmp, false, true, true)) {
                    type.setRedirect(tmp.redirect());
//                    System.out.println(" ----- resolveFromStaticInnerClasses 1 TRUE !!! " + type.getName());
                    return true;
                }
                tmp.className = savedName;
                long end = System.currentTimeMillis();
                //System.out.println(" CHECK:  resolveFromStaticInnerClasses **0000** " + (end - start));

            } else {
                String savedName = type.getName();
                long start = System.currentTimeMillis();

                String replacedPointType = replaceLastPoint(savedName);
                type.setName(replacedPointType);
                if (resolve(type, false, true, true)) {
//                    System.out.println(" ----- resolveFromStaticInnerClasses 2 TRUE!!! " + type.getName());
                    return true;
                }
                type.setName(savedName);
                long end = System.currentTimeMillis();
                //System.out.println(" CHECK:  resolveFromStaticInnerClasses **0001** " + (end - start));

            }
        }
//        System.out.println(" ----- END  resolveFromStaticInnerClasses 3 FALSE" + type.getName());
        return false;
    }

    private boolean resolveFromDefaultImports(ClassNode type, boolean testDefaultImports) {
//        System.out.println("   --------------- START resolveFromDefaultImports " + type.getName());

//        System.out.println("START DEFAULT resolveFromDefaultModule " + type.getName());
        // test default imports
        testDefaultImports &= !type.hasPackageName();
        // we do not resolve a vanilla name starting with a lower case letter
        // try to resolve against a default import, because we know that the
        // default packages do not contain classes like these
        testDefaultImports &= !(type instanceof LowerCaseClass);
        if (testDefaultImports) {
//System.out.println("CYCLE ________________________________________");                        

            for (int i = 0, size = DEFAULT_IMPORTS.length; i < size; i++) {
                String packagePrefix = DEFAULT_IMPORTS[i];
                String name = type.getName();
                // We limit the inner class lookups here by using ConstructedClassWithPackage.
                // This way only the name will change, the packagePrefix will
                // not be included in the lookup. The case where the
                // packagePrefix is really a class is handled elsewhere.
                // WARNING: This code does not expect a class that has a static
                //          inner class in DEFAULT_IMPORTS
                ConstructedClassWithPackage tmp = new ConstructedClassWithPackage(packagePrefix, name);

//System.out.println("BEFORE DEFAULT resolveFromDefaultImports i=" + i + "; name=" + type.getName() + "; tmp.name=" + tmp.getName());                
                if (resolve(tmp, false, false, false)) {
                    type.setRedirect(tmp.redirect());
//                    System.out.println("DEFAULT resolveFromDefaultImports i=" + i + "; name=" + type.getName() + "; tmp.name=" + tmp.getName());
                    return true;
                }
            }
            String name = type.getName();
            if (name.equals("BigInteger")) {
                type.setRedirect(ClassHelper.BigInteger_TYPE);
                return true;
            } else if (name.equals("BigDecimal")) {
                type.setRedirect(ClassHelper.BigDecimal_TYPE);
                return true;
            }
        }
        return false;
    }

    private boolean resolveFromCompileUnit(ClassNode type) {
//System.out.println("   --------------- START resolveFromCompileUnit " + type.getName());

//        System.out.println("START resolveFromCompileUnit 0 " + type.getName());
        // look into the compile unit if there is a class with that name
        //CompileUnit compileUnit = currentClass.getCompileUnit();
        CompletionCompilationUnit.CompileUnit compileUnit = (CompletionCompilationUnit.CompileUnit) this.compilationUnit.getAST();
        if (compileUnit == null) {
            return false;
        }
        String jname = type.getName().replace('$', '.');

        ClassNode cuClass = compileUnit.getClass(jname, this);

///////////////////////        
        if (cuClass != null) {
            if (type != cuClass) {
                type.setRedirect(cuClass);
            }
            typeCache.setResolved(type.getName(), cuClass); //// my
//            System.out.println("+++ resolveFromCompileUnit 1 TRUE !!!" + type.getName()+ "; cuClass.getName()");
            return true;
        }
        return false;
    }

    private void ambiguousClass(ClassNode type, ClassNode iType, String name) {
        if (type.getName().equals(iType.getName())) {
            addError("reference to " + name + " is ambiguous, both class " + type.getName() + " and " + iType.getName() + " match", type);
        } else {
            type.setRedirect(iType);
        }
    }

    private boolean resolveAliasFromModule(ClassNode type) {
//        System.out.println("   --------------- START resolveAliasFromModule " + type.getName());

//        System.out.println("ALIAS START resolveAliasFromModule 0 " + type.getName());
        // In case of getting a ConstructedClassWithPackage here we do not do checks for partial
        // matches with imported classes. The ConstructedClassWithPackage is already a constructed
        // node and any subclass resolving will then take place elsewhere
        if (type instanceof ConstructedClassWithPackage) {
            return false;
        }

        ModuleNode module = currentClass.getModule();
        if (module == null) {
            return false;
        }
        String name = type.getName();

        // check module node imports aliases
        // the while loop enables a check for inner classes which are not fully imported,
        // but visible as the surrounding class is imported and the inner class is public/protected static
        String pname = name;
        int index = name.length();
        /*
         * we have a name foo.bar and an import foo.foo. This means foo.bar is
         * possibly foo.foo.bar rather than foo.bar. This means to cut at the dot
         * in foo.bar and foo for import
         */
        while (true) {
            pname = name.substring(0, index);
            ClassNode aliasedNode = null;
            ImportNode importNode = module.getImport(pname);
            if (importNode != null && importNode != currImportNode) {
                aliasedNode = importNode.getType();
            }
            if (aliasedNode == null) {
                importNode = module.getStaticImports().get(pname);
                if (importNode != null && importNode != currImportNode) {
                    // static alias only for inner classes and must be at end of chain
                    ClassNode tmp = ClassHelper.make(importNode.getType().getName() + "$" + importNode.getFieldName());
//System.out.println("   --------------- try resolveAliasFromModule 1" + type.getName());

                    if (resolve(tmp, false, false, true)) {
                        if ((tmp.getModifiers() & Opcodes.ACC_STATIC) != 0) {
                            type.setRedirect(tmp.redirect());
//                            System.out.println("ALIAS resolveAliasFromModule 1" + type.getName());
                            return true;
                        }
                    }
//System.out.println("   --------------- end try resolveAliasFromModule 2" + type.getName());

                }
            }

            if (aliasedNode != null) {
//System.out.println("   --------------- resolveAliasFromModule 3" + type.getName());

                if (pname.length() == name.length()) {
                    // full match
//System.out.println("   --------------- resolveAliasFromModule 4" + type.getName());

                    // We can compare here by length, because pname is always
                    // a substring of name, so same length means they are equal.
                    type.setRedirect(aliasedNode);
//                    System.out.println("ALIAS resolveAliasFromModule 2" + type.getName());
                    return true;
                } else {
//System.out.println("   --------------- resolveAliasFromModule 5" + type.getName());

                    //partial match

                    // At this point we know that we have a match for pname. This may
                    // mean, that name[pname.length()..<-1] is a static inner class.
                    // For this the rest of the name does not need any dots in its name.
                    // It is either completely a inner static class or it is not.
                    // Since we do not want to have useless lookups we create the name
                    // completely and use a ConstructedClassWithPackage to prevent lookups against the package.
                    String className = aliasedNode.getNameWithoutPackage() + '$'
                            + name.substring(pname.length() + 1).replace('.', '$');
                    ConstructedClassWithPackage tmp = new ConstructedClassWithPackage(aliasedNode.getPackageName() + ".", className);
//System.out.println("   --------------- try else resolveAliasFromModule 6" + type.getName());

                    if (resolve(tmp, true, true, false)) {
                        type.setRedirect(tmp.redirect());
//                        System.out.println("ALIAS resolveAliasFromModule 3" + type.getName());
                        return true;
                    }
                }
            }
//System.out.println("   --------------- resolveAliasFromModule 7" + type.getName());

            index = pname.lastIndexOf('.');
            if (index == -1) {
                break;
            }
        }
//System.out.println("   --------------- resolveAliasFromModule 8" + type.getName());

//        System.out.println("ALIAS END resolveAliasFromModule 4 FALSE" + type.getName());
        return false;
    }

    private boolean resolveFromModule(ClassNode type, boolean testModuleImports) {
//System.out.println("   --------------- START resolveFromModule " + type.getName());
        // we decided if we have a vanilla name starting with a lower case
        // letter that we will not try to resolve this name against .*
        // imports. Instead a full import is needed for these.
        // resolveAliasFromModule will do this check for us. This method
        // does also check the module contains a class in the same package
        // of this name. This check is not done for vanilla names starting
        // with a lower case letter anymore
        if (type instanceof LowerCaseClass) {
            return resolveAliasFromModule(type);
        }

        String name = type.getName();
        ModuleNode module = currentClass.getModule();
        if (module == null) {
            return false;
        }

        boolean newNameUsed = false;
        // we add a package if there is none yet and the module has one. But we
        // do not add that if the type is a ConstructedClassWithPackage. The code in ConstructedClassWithPackage
        // hasPackageName() will return true if ConstructedClassWithPackage#className has no dots.
        // but since the prefix may have them and the code there does ignore that
        // fact. We check here for ConstructedClassWithPackage.
        if (!type.hasPackageName() && module.hasPackageName() && !(type instanceof ConstructedClassWithPackage)) {
            type.setName(module.getPackageName() + name);
//System.out.println("START resolveFromModule newname = " + type.getName());                       
            newNameUsed = true;
        }
        // look into the module node if there is a class with that name
        List<ClassNode> moduleClasses = module.getClasses();
        for (ClassNode mClass : moduleClasses) {
//System.out.println("****************** " + type.getName() + "; mclass = " + mClass.getName());

            if (mClass.getName().equals(type.getName())) {
                if (mClass != type) {
                    type.setRedirect(mClass);
                }
//System.out.println("+++ resolveFromModule 1 TRUE !!!" + type.getName());
                return true;
            }
        }
        if (newNameUsed) {
            type.setName(name);
        }

        if (testModuleImports) {
            if (resolveAliasFromModule(type)) {
//                System.out.println("+++ resolveFromModule 2 TRUE !!! " + type.getName());
                return true;
            }

            if (module.hasPackageName()) {
                // check package this class is defined in. The usage of ConstructedClassWithPackage here
                // means, that the module package will not be involved when the
                // compiler tries to find an inner class.
                ConstructedClassWithPackage tmp = new ConstructedClassWithPackage(module.getPackageName(), name);
                boolean r = false;
                /*
                 * boolean r = findByJavaIndex(tmp); if (!r) { try { r =
                 * tmp.getCompileUnit().getClassLoader().getResourceLoader().loadGroovySource(tmp.getName())
                 * != null; } catch (Exception ex) { } }
                 */
                long start = System.currentTimeMillis();
                long end = 0;
                if (!r) {
                    r = resolve(tmp, false, false, false);
//System.out.println("***** ---- *********** tmp.name=" + tmp.getName() + "; isResolved=" + r);

                    end = System.currentTimeMillis();
                }

                if (r) {
                    ambiguousClass(type, tmp, name);
                    type.setRedirect(tmp.redirect());
                    return true;
                }
            }

            // check module static imports (for static inner classes)
            for (ImportNode importNode : module.getStaticImports().values()) {
                if (importNode.getFieldName().equals(name)) {
                    ClassNode tmp = ClassHelper.make(importNode.getType().getName() + "$" + name);
                    if (resolve(tmp, false, false, true)) {
                        if ((tmp.getModifiers() & Opcodes.ACC_STATIC) != 0) {
                            type.setRedirect(tmp.redirect());
                            //System.out.println("+++ resolveFromModule 4 TRUE !!!" + type.getName());
                            return true;
                        }
                    }
                }
            }

            // check module node import packages
            for (ImportNode importNode : module.getStarImports()) {
                String packagePrefix = importNode.getPackageName();
                // We limit the inner class lookups here by using ConstructedClassWithPackage.
                // This way only the name will change, the packagePrefix will
                // not be included in the lookup. The case where the
                // packagePrefix is really a class is handled elsewhere.
                ConstructedClassWithPackage tmp = new ConstructedClassWithPackage(packagePrefix, name);
                if (resolve(tmp, false, false, true)) {
                    ambiguousClass(type, tmp, name);
                    type.setRedirect(tmp.redirect());
                    //System.out.println("+++ resolveFromModule 5 TRUE " + type.getName());
                    return true;
                }
            }

            // check for star imports (import static pkg.Outer.*) matching static inner classes
            for (ImportNode importNode : module.getStaticStarImports().values()) {
                ClassNode tmp = ClassHelper.make(importNode.getClassName() + "$" + name);
                if (resolve(tmp, false, false, true)) {
                    if ((tmp.getModifiers() & Opcodes.ACC_STATIC) != 0) {
                        ambiguousClass(type, tmp, name);
                        type.setRedirect(tmp.redirect());
                        //System.out.println("+++ resolveFromModule 6 TRUE " + type.getName());
                        return true;
                    }
                }

            }
        }
        //System.out.println("END resolveFromModule 7 FALSE " + type.getName());
        return false;
    }

    private boolean resolveToClass(ClassNode type) {
        //System.out.println("START resolveToClass 0 " + type.getName());
        String name = type.getName();

        // We use here the class cache cachedClasses to prevent
        // calls to ClassLoader#loadClass. disabling this cache will
        // cause a major performance hit. Unlike at the end of this
        // method we do not return true or false depending on if we
        // want to recompile or not. If the class was cached, then
        // we do not want to recompile, recompilation is already
        // scheduled then

        Object cached = cachedClasses.get(name);
        if (cached == NO_CLASS) {
            return false;
        }

        if (cached != null) {
            // cached == SCRIPT should not happen here!
            type.setRedirect((ClassNode) cached);
            //System.out.println("+++ resolveToClass 1 CACHED  " + type.getName());
            return true;
        }


        // We do not need to check instances of LowerCaseClass
        // to be a Class, because unless there was an import for
        // for this we  do not lookup these cases. This was a decision
        // made on the mailing list. To ensure we will not visit this
        // method again we set a NO_CLASS for this name
        if (type instanceof LowerCaseClass) {
            cachedClasses.put(name, NO_CLASS);
            return false;
        }

        if (currentClass.getModule().hasPackageName() && name.indexOf('.') == -1) {
            return false;
        }
        /*
         * String sname = typeCache.getCacheProposal(name); if (sname == null) {
         * return false; }
         */
/*////111        if (!typeCache.canBeResolved(name)) {
            return false;
        }

        if (this.completionTask) {
            return false;
        }
*/
//System.out.println("START resolveToClass 0 " + type.getName());

        GroovyClassLoader loader = compilationUnit.getClassLoader();
        Class cls;
        try {
            //System.out.println("+++ resolveToClass TRY LOAD FROM CLASS  " + type.getName());
            // NOTE: it's important to do no lookup against script files
            // here since the GroovyClassLoader would create a new CompilationUnit
            cls = loader.loadClass(name, false, true);

        } catch (ClassNotFoundException cnfe) {
            cachedClasses.put(name, NO_CLASS);
            return false;
//             return resolveToScript(type);
        } catch (CompilationFailedException cfe) {
            compilationUnit.getErrorCollector().addErrorAndContinue(new ExceptionMessage(cfe, true, source));
//            return resolveToScript(type);
            return false;
        }
        //TODO: the case of a NoClassDefFoundError needs a bit more research
        // a simple recompilation is not possible it seems. The current class
        // we are searching for is there, so we should mark that somehow.
        // Basically the missing class needs to be completely compiled before
        // we can again search for the current name.
        /*
         * catch (NoClassDefFoundError ncdfe) { cachedClasses.put(name,SCRIPT);
         * return false; }
         */
        if (cls == null) {
            return false;
        }
        ClassNode cn = ClassHelper.make(cls);
        cachedClasses.put(name, cn);
        type.setRedirect(cn);
        typeCache.setResolved(type.getName(), cn);
        //NOTE: we might return false here even if we found a class,
        //      because  we want to give a possible script a chance to
        //      recompile. This can only be done if the loader was not
        //      the instance defining the class.
        return true;
//boolean r = cls.getClassLoader() == loader || resolveToScript(type);
//System.out.println("END resolveToClass 2 " +r + "; name="+ type.getName());                                                            
//        return r;
    }

    public Expression transform(Expression exp) {
        if (exp == null) {
            return null;
        }
        Expression ret = null;
        if (exp instanceof VariableExpression) {
            ret = transformVariableExpression((VariableExpression) exp);


        } else if (exp.getClass() == PropertyExpression.class) {
            ret = transformPropertyExpression((PropertyExpression) exp);
        } else if (exp instanceof DeclarationExpression) {
            ret = transformDeclarationExpression((DeclarationExpression) exp);
        } else if (exp instanceof BinaryExpression) {
            ret = transformBinaryExpression((BinaryExpression) exp);
        } else if (exp instanceof MethodCallExpression) {
            ret = transformMethodCallExpression((MethodCallExpression) exp);
        } else if (exp instanceof ClosureExpression) {
            ret = transformClosureExpression((ClosureExpression) exp);
        } else if (exp instanceof ConstructorCallExpression) {
            ret = transformConstructorCallExpression((ConstructorCallExpression) exp);
        } else if (exp instanceof AnnotationConstantExpression) {
            ret = transformAnnotationConstantExpression((AnnotationConstantExpression) exp);
        } else {
            resolveOrFail(exp.getType(), exp);
            ret = exp.transformExpression(this);
        }
        if (ret != null && ret != exp) {
            ret.setSourcePosition(exp);
        }
        return ret;
    }

    private String lookupClassName(PropertyExpression pe) {
//System.out.println(" START lookupClassName ");                                        
        boolean doInitialClassTest = true;
        String name = "";
        // this loop builds a name from right to left each name part
        // separated by "."
        for (Expression it = pe; it != null; it = ((PropertyExpression) it).getObjectExpression()) {
            if (it instanceof VariableExpression) {
                VariableExpression ve = (VariableExpression) it;
                // stop at super and this
                if (ve.isSuperExpression() || ve.isThisExpression()) {
//System.out.println(" lookupClassName 1");                                
                    return null;
                }
                String varName = ve.getName();
                if (doInitialClassTest) {
                    // we are at the first name part. This is the right most part.
                    // If this part is in lower case, then we do not need a class
                    // check. other parts of the property expression will be tested
                    // by a different method call to this method, so foo.Bar.bar
                    // can still be resolved to the class foo.Bar and the static
                    // field bar.
                    if (!testVanillaNameForClass(varName)) {
                        return null;
                    }
                    doInitialClassTest = false;
                    name = varName;
                } else {
                    name = varName + "." + name;
                }
                break;


            } // anything other than PropertyExpressions or
            // VariableExpressions will stop resolving
            else if (it.getClass() != PropertyExpression.class) {

//System.out.println(" lookupClassName 2");                                
                return null;
            } else {
//System.out.println(" lookupClassName 3");                                                
                PropertyExpression current = (PropertyExpression) it;
                String propertyPart = current.getPropertyAsString();
                // the class property stops resolving, dynamic property names too
                if (propertyPart == null || propertyPart.equals("class")) {
                    return null;
                }
                if (doInitialClassTest) {
                    // we are at the first name part. This is the right most part.
                    // If this part is in lower case, then we do not need a class
                    // check. other parts of the property expression will be tested
                    // by a different method call to this method, so foo.Bar.bar
                    // can still be resolved to the class foo.Bar and the static
                    // field bar.
                    if (!testVanillaNameForClass(propertyPart)) {
                        return null;
                    }
                    doInitialClassTest = false;
                    name = propertyPart;
                } else {
                    name = propertyPart + "." + name;
                }
            } //if
        } //for
//System.out.println(" lookupClassName 4");
        if (name.length() == 0) {
            return null;
        }
//System.out.println(" lookupClassName 5");        
        return name;
    }

    // iterate from the inner most to the outer and check for classes
    // this check will ignore a .class property, for Example Integer.class will be
    // a PropertyExpression with the ClassExpression of Integer as objectExpression
    // and class as property
    private Expression correctClassClassChain(PropertyExpression pe) {
        LinkedList<Expression> stack = new LinkedList<Expression>();
        ClassExpression found = null;
        for (Expression it = pe; it != null; it = ((PropertyExpression) it).getObjectExpression()) {
            if (it instanceof ClassExpression) {
                found = (ClassExpression) it;
                break;


            } else if (!(it.getClass() == PropertyExpression.class)) {

                return pe;
            }
            stack.addFirst(it);
        }
        if (found == null) {
            return pe;
        }

        if (stack.isEmpty()) {
            return pe;
        }
        Object stackElement = stack.removeFirst();


        if (!(stackElement.getClass() == PropertyExpression.class)) {

            return pe;
        }
        PropertyExpression classPropertyExpression = (PropertyExpression) stackElement;
        String propertyNamePart = classPropertyExpression.getPropertyAsString();
        if (propertyNamePart == null || !propertyNamePart.equals("class")) {
            return pe;
        }

        found.setSourcePosition(classPropertyExpression);
        if (stack.isEmpty()) {
            return found;
        }
        stackElement = stack.removeFirst();


        if (!(stackElement.getClass() == PropertyExpression.class)) {

            return pe;
        }
        PropertyExpression classPropertyExpressionContainer = (PropertyExpression) stackElement;

        classPropertyExpressionContainer.setObjectExpression(found);
        return pe;
    }
    /*    
    @Override
    public void visitDeclarationExpression(DeclarationExpression expression) {
System.out.println("***** visitDeclarationExpression " + expression.getText());                
        visitAnnotations(expression);
        super.visitDeclarationExpression(expression);
    }
    public void visitBinaryExpression(BinaryExpression expression) {
System.out.println("***** visitBinaryExpression " + expression.getText());                        
        expression.getLeftExpression().visit(this);
        expression.getRightExpression().visit(this);
    }
    
    public void visitPropertyExpression(PropertyExpression expression) {
System.out.println("***** visitPropertyExpression " + expression.getText());        
        expression.getObjectExpression().visit(this);
        expression.getProperty().visit(this);
    }
*/

    protected Expression transformPropertyExpression(PropertyExpression pe) {
        boolean itlp = isTopLevelProperty;
        boolean ipe = inPropertyExpression;

        Expression objectExpression = pe.getObjectExpression();
        inPropertyExpression = true;
        isTopLevelProperty = (objectExpression.getClass() != PropertyExpression.class);
        objectExpression = transform(objectExpression);
        // we handle the property part as if it were not part of the property
        inPropertyExpression = false;
        Expression property = transform(pe.getProperty());
        isTopLevelProperty = itlp;
        inPropertyExpression = ipe;
        boolean spreadSafe = pe.isSpreadSafe();
        PropertyExpression old = pe;
        pe = new PropertyExpression(objectExpression, property, pe.isSafe());

        pe.setSpreadSafe(spreadSafe);

        pe.setSourcePosition(old);
        String className = lookupClassName(pe);
        System.out.println("*** transformPropertyExpression AFTER lookupClassName expr=" + pe.getText() + "; className=" + className);
        if (className != null) {
            ClassNode type = ClassHelper.make(className);
            System.out.println("*** transformPropertyExpression  before Resolve expr=" + pe.getText() + "; className=" + className);
            if (resolve(type)) {
                Expression ret = new ClassExpression(type);
                ret.setSourcePosition(pe);
                return ret;
            }
            System.out.println("*** transformPropertyExpression NOT Resolved expr=" + pe.getText() + "; className=" + className);
        }

        if (objectExpression instanceof ClassExpression
                && pe.getPropertyAsString()
                != null) {
            // possibly an inner class
            ClassExpression ce = (ClassExpression) objectExpression;
            ClassNode type = ClassHelper.make(ce.getType().getName() + "$" + pe.getPropertyAsString());
            if (resolve(type, false, false, false)) {
                Expression ret = new ClassExpression(type);
                ret.setSourcePosition(ce);
//                System.out.println("transformPropertyExpression 1 " + type.getName());
                return ret;
            }
        }
        Expression ret = pe;

        checkThisAndSuperAsPropertyAccess(pe);

        if (isTopLevelProperty) {
            ret = correctClassClassChain(pe);
        }

        return ret;
    }

    private void checkThisAndSuperAsPropertyAccess(PropertyExpression expression) {
//System.out.println(" START checkThisAndSuperAsPropertyAccess ");        
        if (expression.isImplicitThis()) {
//System.out.println(" checkThisAndSuperAsPropertyAccess 1");
            return;
        }
//System.out.println(" checkThisAndSuperAsPropertyAccess 2");        
        String prop = expression.getPropertyAsString();
        if (prop == null) {
            return;
        }
//System.out.println(" checkThisAndSuperAsPropertyAccess 3");        
        if (!prop.equals("this") && !prop.equals("super")) {
            return;
        }
//System.out.println(" checkThisAndSuperAsPropertyAccess 4");
        if (expression.getObjectExpression() instanceof ClassExpression) {
//System.out.println(" THIS FOUND 1");            
            if (!(currentClass instanceof InnerClassNode)) {
                addError("The usage of 'Class.this' and 'Class.super' is only allowed in nested/inner classes.", expression);
                return;
            }
            ClassNode type = expression.getObjectExpression().getType();
            ClassNode iterType = currentClass;
            while (iterType != null) {
                if (iterType.equals(type)) {
                    break;
                }
                iterType = iterType.getOuterClass();
            }
            if (iterType == null) {
                addError("The class '" + type.getName() + "' needs to be an outer class of '"
                        + currentClass.getName() + "' when using '.this' or '.super'.", expression);
            }
//System.out.println(" THIS FOUND 2");                        
            if ((currentClass.getModifiers() & Opcodes.ACC_STATIC) == 0) {
                return;
            }
            if (!currentScope.isInStaticContext()) {
                return;
            }
            addError("The usage of 'Class.this' and 'Class.super' within static nested class '"
                    + currentClass.getName() + "' is not allowed in a static context.", expression);
        }
    }

    public void tryInferVariableType(DeclarationExpression de) {
        if ( true ) {
            return;
        }
        
        if ( ! (de.getLeftExpression() instanceof VariableExpression) ) {
            return;
        }
        Expression re = de.getRightExpression();
        if ( re == null || "java.lang.Object".equals(re.getType().getName()) ) {
            return;
        }

        VariableExpression ve = (VariableExpression) de.getLeftExpression();
System.out.println("VARIABLE EXPR  = " + ve.getText() + "; isDynamic = " + ve.isDynamicTyped() + "; expr.type = " + ve.getType().getName());                
        if ( ! "java.lang.Object".equals(ve.getType().getName()) ) {
            return;
        }
        
        ve.setType(re.getType());
        System.out.println("VARIABLE EXPR  = " + ve.getText() + "; isDynamic = " + ve.isDynamicTyped() + "; expr.type = " + ve.getType().getName());        
//        System.out.println("VARIABLE EXPR  = " + ve.getText() + "; type.name = " + "; expr.type = " + ve.getType().getName());
        //return ve;

    }

    protected Expression transformVariableExpression(VariableExpression ve) {
        visitAnnotations(ve);
        Variable v = ve.getAccessedVariable();
        if (!(v instanceof DynamicVariable) && !checkingVariableTypeInDeclaration) {
            /*
             * GROOVY-4009: when a normal variable is simply being used, there
             * is no need to try to resolve its type. Variable type resolve
             * should proceed only if the variable is being declared.
             */
            return ve;
        }
        if (v instanceof DynamicVariable) {
            String name = ve.getName();
            ClassNode t = ClassHelper.make(name);

            // asking isResolved here allows to check if a primitive
            // type name like "int" was used to make t. In such a case
            // we have nothing left to do.
            //boolean isClass = t.isResolved();
            boolean isClass = isResolvedVirtually(t);

            if (!isClass) {

                // It was no primitive type, so next we see if the name,
                // which is a vanilla name, starts with a lower case letter.
                // In that case we change it to a LowerCaseClass to let the
                // compiler skip the resolving at several places in this class.
                if (Character.isLowerCase(name.charAt(0))) {
                    t = new LowerCaseClass(name);
                }

                isClass = resolve(t);
                if (!isClass) {
                    isClass = resolveToInnerEnum(t);
                }
            }
            if (isClass) {
                // the name is a type so remove it from the scoping
                // as it is only a classvariable, it is only in
                // referencedClassVariables, but must be removed
                // for each parentscope too
                for (VariableScope scope = currentScope; scope != null && !scope.isRoot(); scope = scope.getParent()) {
                    if (scope.isRoot()) {
                        break;
                    }
                    if (scope.removeReferencedClassVariable(ve.getName()) == null) {
                        break;
                    }
                }

                ClassExpression ce = new ClassExpression(t);
                ce.setSourcePosition(ve);
                return ce;
            }
        }
        resolveOrFail(ve.getType(), ve);
        return ve;

    }

    private boolean testVanillaNameForClass(String name) {
        if (name == null || name.length() == 0) {
            return false;
        }
        return !Character.isLowerCase(name.charAt(0));
    }

    protected Expression transformBinaryExpression(BinaryExpression be) {
        Expression left = transform(be.getLeftExpression());
        int type = be.getOperation().getType();
        if ((type == Types.ASSIGNMENT_OPERATOR || type == Types.EQUAL)
                && left instanceof ClassExpression) {
            ClassExpression ce = (ClassExpression) left;
            String error = "you tried to assign a value to the class '" + ce.getType().getName() + "'";
            if (ce.getType().isScript()) {
                error += ". Do you have a script with this name?";
            }
            addError(error, be.getLeftExpression());
            return be;
        }
        if (left instanceof ClassExpression) {
            if (be.getRightExpression() instanceof ListExpression) {
                ListExpression list = (ListExpression) be.getRightExpression();
                if (list.getExpressions().isEmpty()) {
                    // we have C[] if the list is empty -> should be an array then!
                    final ClassExpression ce = new ClassExpression(left.getType().makeArray());
                    ce.setSourcePosition(be);
                    return ce;
                } else {
                    // may be we have C[k1:v1, k2:v2] -> should become (C)([k1:v1, k2:v2])
                    boolean map = true;
                    for (Expression expression : list.getExpressions()) {
                        if (!(expression instanceof MapEntryExpression)) {
                            map = false;
                            break;
                        }
                    }

                    if (map) {
                        final MapExpression me = new MapExpression();
                        for (Expression expression : list.getExpressions()) {
                            me.addMapEntryExpression((MapEntryExpression) transform(expression));
                        }
                        me.setSourcePosition(list);
                        final CastExpression ce = new CastExpression(left.getType(), me);
                        ce.setSourcePosition(be);
                        return ce;
                    }
                }
            }

            if (be.getRightExpression() instanceof MapEntryExpression) {
                // may be we have C[k1:v1] -> should become (C)([k1:v1])
                final MapExpression me = new MapExpression();
                me.addMapEntryExpression((MapEntryExpression) transform(be.getRightExpression()));
                me.setSourcePosition(be.getRightExpression());
                final CastExpression ce = new CastExpression(left.getType(), me);
                ce.setSourcePosition(be);
                return ce;
            }
        }
        Expression right = transform(be.getRightExpression());
        be.setLeftExpression(left);
        be.setRightExpression(right);
        return be;
    }

    public void tryInferFieldType(ASTNode astNode) {
        if ( true ) {
            return;
        }

 //System.out.println("!!!!!!!!!!!!!!!! VERY START tryInferType(ASTNode astNode) ");
        if (astNode instanceof FieldNode) {

            FieldNode node = (FieldNode) astNode;
            
//System.out.println("111111111 tryInferFieldType field.name=" + node.getName() + "; type=" + node.getType().getName());
            if (node.getType().getName().equals("java.lang.Object")) {
                if (node.getInitialExpression() != null) {
                    ClassNode e = node.getInitialExpression().getType();
                    if (e != null && !e.getName().equals("java.lang.Object")) {
                        System.out.println("VISIT DECL START 4");
                        //ClassNode pnode = new ResolvedTypePlaceholder(e.getName());
                        ClassNode pnode = ResolvedTypePlaceholder.createNode(e.getName(), compilationUnit);
                        node.setType(pnode);
//System.out.println("VISIT DECL END 5 left.type=" + node.getType().getName() + "; leftVar.name=" + node.getName());                        
                    }
                }
            }
        } 
//        else {
//            System.out.println("!!!!!!!!!!!!!!!! NOT FIELD NODE tryInferType(ASTNode astNode) ");            
//        }
    }

    protected Expression transformClosureExpression(ClosureExpression ce) {
        boolean oldInClosure = inClosure;
        inClosure = true;
        Parameter[] paras = ce.getParameters();
        if (paras != null) {
            for (Parameter para : paras) {
                ClassNode t = para.getType();
                resolveOrFail(t, ce);
                visitAnnotations(para);
                if (para.hasInitialExpression()) {
                    Object initialVal = para.getInitialExpression();
                    if (initialVal instanceof Expression) {
                        para.setInitialExpression(transform((Expression) initialVal));
                    }
                }
                visitAnnotations(para);
            }
        }
        Statement code = ce.getCode();
        if (code != null) {
            code.visit(this);
        }
        inClosure = oldInClosure;
        return ce;
    }

    protected Expression transformConstructorCallExpression(ConstructorCallExpression cce) {
//System.out.println(" ---- START transformConstructorCallExpression 0");        
        ClassNode type = cce.getType();
        resolveOrFail(type, cce);
        if (Modifier.isAbstract(type.getModifiers())) {
            addError("You cannot create an instance from the abstract " + getDescription(type) + ".", cce);
        }

        Expression ret = cce.transformExpression(this);
        return ret;

    }

    private String getDescription(ClassNode node) {
        return (node.isInterface() ? "interface" : "class") + " '" + node.getName() + "'";
    }

    protected Expression transformMethodCallExpression(MethodCallExpression mce) {
//System.out.println(" ---- START transformMethodCallExpression 0");                
        Expression args = transform(mce.getArguments());
        Expression method = transform(mce.getMethod());
        System.out.println(" ---- 1) transformMethodCallExpression method.name=" + method.getText() + "; method.type=" + (method.getType() != null ? method.getType().getName() : null));
        Expression object = transform(mce.getObjectExpression());

        resolveGenericsTypes(mce.getGenericsTypes());

        MethodCallExpression result = new MethodCallExpression(object, method, args);

        result.setSafe(mce.isSafe());
        result.setImplicitThis(mce.isImplicitThis());
        result.setSpreadSafe(mce.isSpreadSafe());
        result.setSourcePosition(mce);
        result.setGenericsTypes(mce.getGenericsTypes());
        result.setMethodTarget(mce.getMethodTarget());

        MethodNode mt = result.getMethodTarget();
        ClassNode onode = null;
        String s = null;
        String text = object.getText();

        if (mt == null) {
            if ((object instanceof VariableExpression) || (object instanceof PropertyExpression)) {
                onode = object.getType();
                if (onode != null) {
                    s = onode.getName();
                }
            }
        }
        System.out.println(" ---- 2) transformMethodCallExpression object.name=" + text + "; object.type=" + s);
        return result;
    }

    protected Expression transformDeclarationExpression(DeclarationExpression de) {
        System.out.println(" ---- START transformDeclarationExpression 0" + de.getText());
        visitAnnotations(de);
        Expression oldLeft = de.getLeftExpression();
        checkingVariableTypeInDeclaration = true;
        Expression left = transform(oldLeft);
        checkingVariableTypeInDeclaration = false;
        if (left instanceof ClassExpression) {
            ClassExpression ce = (ClassExpression) left;
            addError("you tried to assign a value to the class " + ce.getType().getName(), oldLeft);
            return de;
        }
        Expression right = transform(de.getRightExpression());
System.out.println(" ---- 0000 transformDeclarationExpression " + de.getText());        
        if (right == de.getRightExpression()) {
            fixDeclaringClass(de);
System.out.println(" ---- 0001 transformDeclarationExpression" + de.getText());        

        if (de.getLeftExpression() instanceof VariableExpression) {
System.out.println(" ---- 0002 transformDeclarationExpression BEFORE TryInfer " + de.getText());
            this.tryInferVariableType(de); 
       }

            return de;
        }
        DeclarationExpression newDeclExpr = new DeclarationExpression(left, de.getOperation(), right);
        newDeclExpr.setDeclaringClass(de.getDeclaringClass());
        fixDeclaringClass(newDeclExpr);
        newDeclExpr.setSourcePosition(de);
        newDeclExpr.addAnnotations(de.getAnnotations());

        /////////////// My
        if (newDeclExpr.getLeftExpression() instanceof VariableExpression) {
System.out.println(" ---- 0003 transformDeclarationExpression BEFORE TryInfer " + newDeclExpr.getText());
            this.tryInferVariableType(newDeclExpr); 
       }

        return newDeclExpr;
    }

    // TODO get normal resolving to set declaring class
    private void fixDeclaringClass(DeclarationExpression newDeclExpr) {
//System.out.println(" ---- START fixDeclaringClass 0");                        
        if (newDeclExpr.getDeclaringClass() == null && currentMethod != null) {
            newDeclExpr.setDeclaringClass(currentMethod.getDeclaringClass());
        }
    }

    protected Expression transformAnnotationConstantExpression(AnnotationConstantExpression ace) {
//System.out.println(" ---- START transformAnnotationConstantExpression 0");                                
        AnnotationNode an = (AnnotationNode) ace.getValue();
        ClassNode type = an.getClassNode();
        resolveOrFail(type, ", unable to find class for annotation", an);
        for (Map.Entry<String, Expression> member : an.getMembers().entrySet()) {
            member.setValue(transform(member.getValue()));
        }
        return ace;
    }

    @Override
    public void visitAnnotations(AnnotatedNode node) {
//System.out.println(" ---- START visitAnnotations 0");                                        
        List<AnnotationNode> annotations = node.getAnnotations();
        if (annotations.isEmpty()) {
            return;
        }

        Map<String, AnnotationNode> tmpAnnotations = new HashMap<String, AnnotationNode>();
        ClassNode annType;
        for (AnnotationNode an : annotations) {
            // skip built-in properties
            if (an.isBuiltIn()) {
                continue;
            }
            annType = an.getClassNode();
            resolveOrFail(annType, ",  unable to find class for annotation", an);
            for (Map.Entry<String, Expression> member : an.getMembers().entrySet()) {
                Expression newValue = transform(member.getValue());
                newValue = transformInlineConstants(newValue);
                member.setValue(newValue);
                checkAnnotationMemberValue(newValue);
            }
//            if (annType.isResolved()) {
            //// if (this.isResolvedVirtually(annType)) {
System.out.println("VISIT ANNOTATION !!!!!!!!!!!!!!!!!! NAME=" + annType.getName());            
            if (this.isResolvedVirtually(annType) && !completionTask) {
                Class annTypeClass = annType.getTypeClass();
                Retention retAnn = (Retention) annTypeClass.getAnnotation(Retention.class);

                if (retAnn
                        != null && retAnn.value().equals(RetentionPolicy.RUNTIME)) {
                    AnnotationNode anyPrevAnnNode = tmpAnnotations.put(annTypeClass.getName(), an);
                    if (anyPrevAnnNode != null) {
                        addError("Cannot specify duplicate annotation on the same member : " + annType.getName(), an);
                    }
                }
            }
        }
    }

    // resolve constant-looking expressions statically (do here as gets transformed away later)
    private Expression transformInlineConstants(Expression exp) {
//System.out.println(" ---- START transformInlineConstants 0");                                                
        if (exp instanceof PropertyExpression) {
            PropertyExpression pe = (PropertyExpression) exp;
            if (pe.getObjectExpression() instanceof ClassExpression) {
                ClassExpression ce = (ClassExpression) pe.getObjectExpression();
                ClassNode type = ce.getType();
                if (type.isEnum()) {
                    return exp;
                }

                FieldNode fn = type.getField(pe.getPropertyAsString());
                if (fn != null && !fn.isEnum() && fn.isStatic() && fn.isFinal()) {
                    if (fn.getInitialValueExpression() instanceof ConstantExpression) {
                        return fn.getInitialValueExpression();
                    }
                }
            }
        } else if (exp instanceof ListExpression) {
            ListExpression le = (ListExpression) exp;
            ListExpression result = new ListExpression();
            for (Expression e : le.getExpressions()) {
                result.addExpression(transformInlineConstants(e));
            }
            return result;
        } else if (exp instanceof AnnotationConstantExpression) {
            ConstantExpression ce = (ConstantExpression) exp;
            if (ce.getValue() instanceof AnnotationNode) {
                // replicate a little bit of AnnotationVisitor here
                // because we can't wait until later to do this
                AnnotationNode an = (AnnotationNode) ce.getValue();
                for (Map.Entry<String, Expression> member : an.getMembers().entrySet()) {
                    member.setValue(transformInlineConstants(member.getValue()));
                }

            }
        }
        return exp;
    }

    private void checkAnnotationMemberValue(Expression newValue) {
//System.out.println(" ---- START checkAnnotationMemberValue 0");                                                        
        if (newValue instanceof PropertyExpression) {
            PropertyExpression pe = (PropertyExpression) newValue;
            if (!(pe.getObjectExpression() instanceof ClassExpression)) {
                addError("unable to find class '" + pe.getText() + "' for annotation attribute constant", pe.getObjectExpression());
            }
        } else if (newValue instanceof ListExpression) {
            ListExpression le = (ListExpression) newValue;
            for (Expression e : le.getExpressions()) {
                checkAnnotationMemberValue(e);
            }
        }
    }

    @Override
    public void visitClass(ClassNode node) {
        ClassNode oldNode = currentClass;
        Map<String, GenericsType> oldPNames = genericParameterNames;
        genericParameterNames = new HashMap<String, GenericsType>(genericParameterNames);
        currentClass = node;

        resolveGenericsHeader(node.getGenericsTypes());

        ModuleNode module = node.getModule();
        if (!module.hasImportsResolved()) {
            for (ImportNode importNode : module.getImports()) {
                currImportNode = importNode;
                ClassNode type = importNode.getType();

                if (resolve(type, false, false, true)) {
                    currImportNode = null;
                    continue;
                }
                currImportNode = null;
                addError("unable to resolve class " + type.getName(), type);
            }
            for (ImportNode importNode : module.getStaticStarImports().values()) {
                ClassNode type = importNode.getType();
                if (resolve(type, false, false, true)) {
                    continue;
                }
                // Maybe this type belongs in the same package as the node that is doing the
                // static import. In that case, the package may not have been explicitly specified.
                // Try with the node's package too. If still not found, revert to original type name.
                if (type.getPackageName() == null && node.getPackageName() != null) {
                    String oldTypeName = type.getName();
                    type.setName(node.getPackageName() + "." + oldTypeName);
                    if (resolve(type, false, false, true)) {
                        continue;
                    }
                    type.setName(oldTypeName);
                }
                addError("unable to resolve class " + type.getName(), type);
            }
            for (ImportNode importNode : module.getStaticImports().values()) {
                ClassNode type = importNode.getType();
                if (resolve(type, true, true, true)) {
                    continue;
                }
                addError("unable to resolve class " + type.getName(), type);
            }
            for (ImportNode importNode : module.getStaticStarImports().values()) {
                ClassNode type = importNode.getType();
                if (resolve(type, true, true, true)) {
                    continue;
                }
                addError("unable to resolve class " + type.getName(), type);
            }
            module.setImportsResolved(true);
        }
        ClassNode sn = node.getUnresolvedSuperClass();
        if (sn != null) {
            resolveOrFail(sn, node, true);
        }
        for (ClassNode anInterface : node.getInterfaces()) {
            resolveOrFail(anInterface, node, true);
        }

        checkCyclicInheritence(node, node.getUnresolvedSuperClass(), node.getInterfaces());
        super.visitClass(node);
        genericParameterNames = oldPNames;

        currentClass = oldNode;
    }

    private void checkCyclicInheritence(ClassNode originalNode, ClassNode parentToCompare, ClassNode[] interfacesToCompare) {
//System.out.println(" ---- START checkCyclicInheritence ");                                                                
        if (!originalNode.isInterface()) {
            if (parentToCompare == null) {
                return;
            }
            if (originalNode == parentToCompare.redirect()) {
                addError("Cyclic inheritance involving " + parentToCompare.getName() + " in class " + originalNode.getName(), originalNode);
                return;
            }
            if (interfacesToCompare != null && interfacesToCompare.length > 0) {
                for (ClassNode intfToCompare : interfacesToCompare) {
                    if (originalNode == intfToCompare.redirect()) {
                        addError("Cycle detected: the type " + originalNode.getName() + " cannot implement itself", originalNode);
                        return;
                    }
                }
            }
            if (parentToCompare == ClassHelper.OBJECT_TYPE) {
                return;
            }
            checkCyclicInheritence(originalNode, parentToCompare.getUnresolvedSuperClass(), null);
        } else {
            if (interfacesToCompare != null && interfacesToCompare.length > 0) {
                // check interfaces at this level first
                for (ClassNode intfToCompare : interfacesToCompare) {
                    if (originalNode == intfToCompare.redirect()) {
                        addError("Cyclic inheritance involving " + intfToCompare.getName() + " in interface " + originalNode.getName(), originalNode);
                        return;
                    }
                }
                // check next level of interfaces
                for (ClassNode intf : interfacesToCompare) {
                    checkCyclicInheritence(originalNode, null, intf.getInterfaces());
                }
            }
        }
    }

    @Override
    public void visitCatchStatement(CatchStatement cs) {
        resolveOrFail(cs.getExceptionType(), cs);


        if (cs.getExceptionType() == ClassHelper.DYNAMIC_TYPE) {
            cs.getVariable().setType(ClassHelper.make(Exception.class));
        }
        super.visitCatchStatement(cs);
    }

    @Override
    public void visitForLoop(ForStatement forLoop) {
        resolveOrFail(forLoop.getVariableType(), forLoop);
        super.visitForLoop(forLoop);
    }

    @Override
    public void visitBlockStatement(BlockStatement block) {

//System.out.println(" ---- START visitBlockStatement ");                                                                        
        VariableScope oldScope = currentScope;
        currentScope = block.getVariableScope();
        super.visitBlockStatement(block);
        currentScope = oldScope;
    }

    @Override
    protected SourceUnit getSourceUnit() {
        return owner.getSourceUnit();
    }

    private void resolveGenericsTypes(GenericsType[] types) {
        if (types == null) {
            return;
        }
        currentClass.setUsingGenerics(true);
        for (GenericsType type : types) {
            resolveGenericsType(type);
        }
    }

    private void resolveGenericsHeader(GenericsType[] types) {
        if (types == null) {
            return;
        }
        currentClass.setUsingGenerics(true);
        for (GenericsType type : types) {
            ClassNode classNode = type.getType();
            String name = type.getName();
            ClassNode[] bounds = type.getUpperBounds();
            if (bounds != null) {
                boolean nameAdded = false;
                for (ClassNode upperBound : bounds) {
                    if (!nameAdded && upperBound != null || !resolve(classNode)) {
                        genericParameterNames.put(name, type);
                        type.setPlaceholder(true);
                        classNode.setRedirect(upperBound);
                        nameAdded = true;
                    }
                    resolveOrFail(upperBound, classNode);
                }
            } else {
                genericParameterNames.put(name, type);
                classNode.setRedirect(ClassHelper.OBJECT_TYPE);
                type.setPlaceholder(true);
            }
        }
    }

    private void resolveGenericsType(GenericsType genericsType) {
        if (genericsType.isResolved()) {
//        if (this.isResolvedVirtually(genericsType)) {            
            return;
        }

        currentClass.setUsingGenerics(true);
        ClassNode type = genericsType.getType();
        // save name before redirect
        String name = type.getName();
        ClassNode[] bounds = genericsType.getUpperBounds();
        if (!genericParameterNames.containsKey(name)) {
            if (bounds != null) {
                for (ClassNode upperBound : bounds) {
                    resolveOrFail(upperBound, genericsType);
                    type.setRedirect(upperBound);
                    resolveGenericsTypes(upperBound.getGenericsTypes());
                }
            } else if (genericsType.isWildcard()) {
                type.setRedirect(ClassHelper.OBJECT_TYPE);
            } else {
                resolveOrFail(type, genericsType);
            }
        } else {
            GenericsType gt = genericParameterNames.get(name);
            type.setRedirect(gt.getType());
            genericsType.setPlaceholder(true);
        }

        if (genericsType.getLowerBound() != null) {
            resolveOrFail(genericsType.getLowerBound(), genericsType);
        }
        resolveGenericsTypes(type.getGenericsTypes());
        genericsType.setResolved(genericsType.getType().isResolved());
    }

}
