/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU General
 * Public License Version 2 only ("GPL") or the Common Development and
 * Distribution License("CDDL") (collectively, the "License"). You may not use
 * this file except in compliance with the License. You can obtain a copy of the
 * License at http://www.netbeans.org/cddl-gplv2.html or
 * nbbuild/licenses/CDDL-GPL-2-CP. See the License for the specific language
 * governing permissions and limitations under the License. When distributing the
 * software, include this License Header Notice in each file and include the
 * License file at nbbuild/licenses/CDDL-GPL-2-CP. Oracle designates this
 * particular file as subject to the "Classpath" exception as provided by Oracle
 * in the GPL Version 2 section of the License file that accompanied this code.
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2007 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL or only
 * the GPL Version 2, indicate your decision by adding "[Contributor] elects to
 * include this software in this distribution under the [CDDL or GPL Version 2]
 * license." If you do not indicate a single choice of license, a recipient has
 * the option to distribute your version of this file under either the CDDL, the
 * GPL Version 2 or to extend the choice of license to its licensees as provided
 * above. However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is made
 * subject to such option by the copyright holder.
 */
package org.netbeans.modules.groovy.editor.api.parser;

import groovy.lang.GroovyClassLoader;
import groovyjarjarasm.asm.Opcodes;
import java.io.IOException;
import java.io.InputStream;
import java.security.CodeSource;
import java.util.*;
import java.util.concurrent.CancellationException;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MixinNode;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.io.InputStreamReaderSource;
import org.codehaus.groovy.control.io.ReaderSource;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.*;
import org.netbeans.modules.groovy.editor.api.GroovyIndex;
import org.netbeans.modules.groovy.editor.java.ElementSearch;
import org.netbeans.modules.groovy.editor.java.Utilities;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;

/**
 *
 * @author Martin Adamek
 */
final class CompletionCompilationUnit extends org.codehaus.groovy.control.CompilationUnit {
//final class CompilationUnit extends org.codehaus.groovy.tools.javac.JavaAwareCompilationUnit {

    protected Map<String, List<ClassNode>> typePlaceHolders = new HashMap<String, List<ClassNode>>(30);
    protected JavaSource javaSource;
    protected List<String> projectClasses;

    public CompletionCompilationUnit(EditorParser parser, CompilerConfiguration configuration,
            CodeSource security, GroovyClassLoader loader, GroovyClassLoader transformationLoader,
            JavaSource javaSource) {
        this(parser, configuration, security, loader, transformationLoader, javaSource, false);
    }

    public CompletionCompilationUnit(EditorParser parser, CompilerConfiguration configuration,
            CodeSource security, GroovyClassLoader loader, GroovyClassLoader transformationLoader,
            JavaSource javaSource, boolean isCompletionTask) {

        super(configuration, security, loader, transformationLoader);
        this.ast = new CompileUnit(this, parser, this.classLoader, security, this.configuration, javaSource);
        this.resolveVisitor = new EditorResolveVisitor(this, isCompletionTask);
        this.javaSource = javaSource;
    }

    /**
     * Adds a InputStream source to the unit.
     */
    @Override
    public SourceUnit addSource(String name, InputStream stream) {
        ReaderSource source = new InputStreamReaderSource(stream, configuration);

        return addSource(new SourceUnit(name, source, configuration, classLoader, getErrorCollector()));
    }

    public JavaSource getJavaSource() {
        return this.javaSource;
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

    public static class CompileUnit extends org.codehaus.groovy.ast.CompileUnit {

//        private EditorCompilationUnit.SourceRoots roots;
        public GroovyIndex index;
        public final EditorParser parser;
        public final JavaSource javaSource;
        public final Map<String, ClassNode> cache = new HashMap<String, ClassNode>();
        public final Map<String, ClassNode> resolvedNodeCache = new HashMap<String, ClassNode>();
        public final CompilationUnit compilationUnit;

        public CompileUnit(CompletionCompilationUnit compilationUnit, EditorParser parser, GroovyClassLoader classLoader,
                CodeSource codeSource, CompilerConfiguration config, JavaSource javaSource) {
            super(classLoader, codeSource, config);
            this.parser = parser;
            this.javaSource = javaSource;
            this.compilationUnit = compilationUnit;
            //roots = new EditorCompilationUnit.SourceRoots(javaSource);
        }


        public ClassNode getClass(final String name, PlaceholderResolveVisitorDelegate rv) {
//
/*
             * if ( true) { return getClass(name); }
             */
            if (!rv.completionTask) {
                return getClass(name);
            }
            if (parser.isCancelled()) {
                throw new CancellationException();
            }

            ClassNode classNode;
            // check the cache for non-null value
            synchronized (cache) {
                classNode = cache.get(name);
                if (classNode != null) {
                    return cache.get(name);
                }
            }
            synchronized (cache) {
                if (cache.containsKey(name)) {
                    return null;
                }
            }

            if (!rv.typeCache.canBeResolved(name)) {
                return null;
            }
            String pname = rv.typeCache.getCacheProposal(name);
            if (pname == null) {
                return null;
            }
            ClassNode pnode = null;

            if ("groovy.transform.TypeChecked".equals(pname)) {
                System.out.println("1) GET TYPE CHECKED ANNOTATION " + name);
                pnode = getClass(name);
                return pnode;
                /*
                 * if (pnode != null) { System.out.println("2) GET TYPE CHECKED
                 * ANNOTATION NOT NULL " + name); cache.put(name, pnode); return
                 * pnode;
                 *
                 * } else { System.out.println("3) GET TYPE CHECKED ANNOTATION
                 * NULL " + name); }
                 */
            }
//            if ( ! roots.containsPackageByClassName(name) ) {
            if (!((CompletionCompilationUnit)compilationUnit).isProjectClass(name)) {

                System.out.println("COMPLETION NOT CONTAINS in ROOTS (((((((((((((((((((( " + name);
                return null;
            }


            pnode = ResolvedTypePlaceholder.createNode(name, compilationUnit);
            cache.put(name, pnode);
            return pnode;

        }

        @Override
        public ClassNode getClass(final String name) {

            if (parser.isCancelled()) {
                throw new CancellationException();
            }

            ClassNode classNode;
            // check the cache for non-null value
            synchronized (cache) {
                classNode = cache.get(name);
                if (classNode != null) {
                    return cache.get(name);
                }
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
                // if it is a groovy file it is useless to load it with java
                // at least until VirtualSourceProvider will do the job ;)
/*
                 * if
                 * (getClassLoader().getResourceLoader().loadGroovySource(name)
                 * != null) { return null; }
                 */
                Task<CompilationController> task = new Task<CompilationController>() {

                    public void run(CompilationController controller) throws Exception {
                        Elements elements = controller.getElements();
                        TypeElement typeElement = ElementSearch.getClass(elements, name);

                        synchronized (cache) {
                            if (typeElement != null) {
                                ClassNode node = createClassNode(name, typeElement);
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

        public ClassNode getClass(final ClassNode node) {

            if (parser.isCancelled()) {
                throw new CancellationException();
            }
            ClassNode classNode;
            final String name = node.getName();
            // check the cache for non-null value
            synchronized (resolvedNodeCache) {
                classNode = resolvedNodeCache.get(name);
                if (classNode != null) {
//                    System.out.println("GGGGGGGG   CompilationUnit FROM CACHE TRUE !!! " + name);
                    return resolvedNodeCache.get(name);
                }
            }

            // if null or not present in cache
            classNode = super.getClass(name);
            if (classNode != null) {
                return classNode;
            }

            // if present in cache but null
            synchronized (resolvedNodeCache) {
                if (resolvedNodeCache.containsKey(name)) {
                    return null;
                }
            }
            try {
                // if it is a groovy file it is useless to load it with java
                // at least until VirtualSourceProvider will do the job ;)
                Task<CompilationController> task = new Task<CompilationController>() {

                    @Override
                    public void run(CompilationController controller) throws Exception {

                        Elements elements = controller.getElements();
                        TypeElement typeElement = ElementSearch.getClass(elements, name);

                        synchronized (resolvedNodeCache) {
                            if (typeElement != null) {
                                ClassNode node = createClassNode(name, typeElement);
                                if (node != null) {
                                    resolvedNodeCache.put(name, node);
                                }
                            } else {
                                if (!resolvedNodeCache.containsKey(name)) {
                                    resolvedNodeCache.put(name, null);
                                }
                            }
                        }
                    }
                };
//                System.out.println("GGGGGGGG   CompilationUnit javaSource.runUserActionTask ClassNode.name=" + name);
                javaSource.runUserActionTask(task, true);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }

            synchronized (resolvedNodeCache) {
                return resolvedNodeCache.get(name);
            }
        }

        private ClassNode createClassNode(String name, TypeElement typeElement) {
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

        private ClassNode createClassNodeOld(String name, TypeElement typeElement) {
            int modifiers = 0;
            ClassNode superClass = null;
            Set<ClassNode> interfaces = new HashSet<ClassNode>();

            if (typeElement.getKind().isInterface()) {
                if (typeElement.getKind() == ElementKind.ANNOTATION_TYPE) {
// FIXME give it up and use classloader - annotations created in sources won't work
// OTOH it will not resolve annotation coming from java source file :( 170517
//                    return null;
                    modifiers |= Opcodes.ACC_ANNOTATION;
                    interfaces.add(ClassHelper.Annotation_TYPE);
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
//            System.out.println("OK !!! GGGGGGGG   CompilationUnit javaSource.runUserActionTask ClassNode.name=" + name);
            return createClassNode(name, modifiers, superClass, interfaces);
        }

        private ClassNode createClassNode(String name, int modifiers, ClassNode superClass, Set<ClassNode> interfaces) {
            if ("java.lang.Object".equals(name) && superClass == null) { // NOI18N

                return ClassHelper.OBJECT_TYPE;
            }
            return new ClassNode(name, modifiers, superClass,
                    (ClassNode[]) interfaces.toArray(new ClassNode[interfaces.size()]), MixinNode.EMPTY_ARRAY);
        }
    }
}
