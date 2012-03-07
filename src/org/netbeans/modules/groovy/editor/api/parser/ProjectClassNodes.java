/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.api.parser;

import groovy.lang.GroovyClassLoader;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.ExceptionMessage;
import org.netbeans.api.java.source.CompilationController;
import org.netbeans.api.java.source.ElementUtilities;
import org.netbeans.api.java.source.Task;
import org.netbeans.modules.groovy.editor.java.ElementSearch;
import org.openide.util.Exceptions;

/**
 *
 * @author V. Shyshkin
 */
public class ProjectClassNodes {

    protected Map<String, GenericsType> classTypeParameters = new HashMap<String, GenericsType>();
    protected List<JavaClassNode> incompleteNodes = new ArrayList<JavaClassNode>();
    List<JavaClassNode> currentJavaClassNodes = new ArrayList<JavaClassNode>();
    protected EditorCompilationUnit compilationUnit;
    protected SourceUnit sourceUnit;

    public ProjectClassNodes(EditorCompilationUnit compilationUnit, SourceUnit sourceUnit) {
        this.compilationUnit = compilationUnit;
        this.sourceUnit = sourceUnit;
    }
    int ccount = 0;

    public void complete() {
        List<JavaClassNode> nodes = new ArrayList<JavaClassNode>();
        for (JavaClassNode jn : incompleteNodes) {
            jn.setComplete(true);
        }
        for (JavaClassNode classNode : incompleteNodes) {
            List<JavaClassNode> l = complete(classNode);
            currentJavaClassNodes.clear();
            int sz = -1;
            if (l != null) {
                sz = l.size();
                nodes.addAll(l);
            }
        }
        incompleteNodes.clear();
        incompleteNodes.addAll(nodes);
        if (!nodes.isEmpty()) {
            complete();
        }
    }

    protected void resolveClassTypeParameters(ClassNode forClass, GenericsType[] genericsTypes) {
        if (genericsTypes == null) {
            System.out.println("$ :: $ START resolveClassTypeParameters(GenericsType[] types) types = NULL !!!!");
        }
        if (genericsTypes == null) {
            return;
        }
        System.out.println("$ :: $  START resolveClassTypeParameters(GenericsType[] types) length=" + genericsTypes.length);
//        printGenericsTypes(types, "AAAAAAAAA");
        forClass.setUsingGenerics(true);

        for (GenericsType gt : genericsTypes) {
            System.out.println("$ :: $  1. resolveClassTypeParameters(GenericsType[] types) type=" + gt + "; type.name=" + gt.getName());

            ClassNode gtType = gt.getType();
            String name = gt.getName();
            System.out.println("$ :: $  2. resolveClassTypeParameters(GenericsType[] types) type.getType().getName=" + gt.getType().getName());

            ClassNode[] bounds = gt.getUpperBounds();
            if (bounds != null && bounds.length > 0) {
                System.out.println("$ :: $ 3. resolveClassTypeParameters(GenericsType[] types) BOUNDS != NULL ");

                for (ClassNode upperBound : bounds) {
                    System.out.println("$ :: $ 4. BEFORE RESOLVE resolveClassTypeParameters(GenericsType[] types) upperBound=" + upperBound + "; upperBound.getName()=" + upperBound.getName());

                    resolve(upperBound, true);
                    System.out.println("$ :: $ 5. AFTER RESOLVE resolveClassTypeParameters(GenericsType[] types) upperBound=" + upperBound + "; upperBound.getName()=" + upperBound.getName());

                }

                this.classTypeParameters.put(name, gt);
                gt.setPlaceholder(true);
                gtType.setRedirect(bounds[0]);

            } else {
                this.classTypeParameters.put(name, gt);
                gtType.setRedirect(ClassHelper.OBJECT_TYPE);
                gt.setPlaceholder(true);

            }
        }
        System.out.println("$ :: $ ________________________ END ");
    }

    public List<JavaClassNode> complete(JavaClassNode classNode) {
//        if ( classNode.getName().equals("org.learn.MyClassMy")) {
//            List<? extends TypeParameterElement> tpeList = 
//        }
        createGenerics(classNode);
        resolveSuper(classNode, currentJavaClassNodes);

        List<MethodNode> methods = createMethods(classNode);
        for (MethodNode method : methods) {
            resolveReturnType(method, currentJavaClassNodes);
            //resolveParameters(method, currentJavaClassNodes);
            classNode.addMethod(method);
        }

        return currentJavaClassNodes;
    }

    protected void print(TypeElement te, List<? extends TypeParameterElement> tpeList) {
        if (te.getQualifiedName().toString().equals("org.learn.MyClassMy")) {
            System.out.println(" TPINFO ------------ TypeElement name = org.learn.MyClassMy");
            System.out.println(" TPINFO ------------ TypeParameterElement ");

            for (int i = 0; i < tpeList.size(); i++) {
                TypeParameterElement tpe = tpeList.get(i);
                System.out.println(" **** TypeParameterElement getClass=" + tpe.getClass() + "; name=" + tpe.getSimpleName());
                System.out.println("    **** TypeParameterElement.getGenericElement()=" + tpe.getGenericElement().getSimpleName());
                System.out.println("    **** TypeParameterElement.asType().getClass()=" + tpe.asType().getClass() + "; is instanceof TypeVariable = " + (tpe.asType() instanceof TypeVariable));
                List<? extends TypeMirror> tpeMirrorList = tpe.getBounds();
                for (TypeMirror tpeMirror : tpeMirrorList) {

                    JavaClassTypeMirrorVisitor<GenericsType, ClassNode> tpbv = new JavaClassTypeMirrorVisitor<GenericsType, ClassNode>();
                    ClassNode tpeClassNode = ClassHelper.make(tpeMirror.toString());

                    System.out.println("    **** TypeParameterElement tpeMirror's classNode=" + tpeMirror);

                    ClassNode bound = tpbv.visit(tpeMirror).getType();
                    System.out.println("RESULT ::::::: bound=" + bound + "; bound.getName()=" + bound.getName());
                    GenericsType[] gts = bound.getGenericsTypes();
                    if (gts != null && gts.length > 0) {
                        for (GenericsType gt : gts) {

                            System.out.println("RESULT :: ----- generics[" + i + "] = " + gt + "; gt.getName()=" + gt.getName() + "; gt.getType()=" + gt.getType() + "; gt.getType().getName()");
                            ClassNode[] uppers = gt.getUpperBounds();
                            if (uppers != null && uppers.length > 0) {
                                for (ClassNode cn : uppers) {
                                    System.out.println("RESULT :: ----- UPPERRS cn.type = " + cn + "; cn.getName()=" + cn.getName());
                                }
                            }
                        }

                    }
                    /*
                     * System.out.println(" ---- TypeMirror getClass=" +
                     * tpeMirror.getClass() + "; name=" + tpeMirror.toString() +
                     * "; TypeKind=" + tpeMirror.getKind()); if
                     * (tpeMirror.getKind() == TypeKind.DECLARED) {
                     * //System.out.println(" ---- TypeMirror getClass="
                     * DeclaredType dt = (DeclaredType) tpeMirror; List<? extends
                     * TypeMirror> typeArgsList = dt.getTypeArguments(); for
                     * (TypeMirror taMirror : typeArgsList) {
                     * System.out.println(" INNER type argument list size()=" +
                     * typeArgsList.size() + "; typeMirror=" + taMirror);
                     *
                     * if (taMirror.getKind() == TypeKind.DECLARED) {
                     * System.out.println(" INNER ---- TypeMirror DECLARED ");
                     *
                     * } else if (taMirror.getKind() == TypeKind.WILDCARD) {
                     * System.out.println(" INNER ---- TypeMirror WILDCARD "); }
                     * else if (taMirror.getKind() == TypeKind.TYPEVAR) {
                     * System.out.println(" INNER ---- TypeMirror TYPEVAR "); } }
                     * System.out.println(" ---- TypeMirror DECLARED " +
                     * "asElement=" + dt.asElement()); } else if
                     * (tpeMirror.getKind() == TypeKind.TYPEVAR) { } else if
                     * (tpeMirror.getKind() == TypeKind.WILDCARD) { }
                     */
                }
            }
        }
    }

    protected void printTypeParameters(TypeMirror typeMirror, List<? extends TypeParameterElement> tpeList) {
        for (int i = 0; i < tpeList.size(); i++) {
            TypeParameterElement tpe = tpeList.get(i);
            System.out.println(" **** TypeParameterElement getClass=" + tpe.getClass() + "; name=" + tpe.getSimpleName());
            System.out.println("    **** TypeParameterElement.getGenericElement()=" + tpe.getGenericElement().getSimpleName());
            System.out.println("    **** TypeParameterElement.asType().getClass()=" + tpe.asType().getClass() + "; is instanceof TypeVariable = " + (tpe.asType() instanceof TypeVariable));
            List<? extends TypeMirror> tpeMirrorList = tpe.getBounds();
            for (TypeMirror tpeMirror : tpeMirrorList) {

                System.out.println("       ---- TypeMirror getClass=" + tpeMirror.getClass() + "; name=" + tpeMirror.toString() + "; TypeKind=" + tpeMirror.getKind());
                if (tpeMirror.getKind() == TypeKind.DECLARED) {
                    //System.out.println("       ---- TypeMirror getClass="                        
                } else if (tpeMirror.getKind() == TypeKind.TYPEVAR) {
                } else if (tpeMirror.getKind() == TypeKind.WILDCARD) {
                }

            }
        }
    }

    protected void printGenerics(ClassNode classNode) {
        GenericsType[] genericsTypes = classNode.getGenericsTypes();
        String s = " $$$ ::: $$$ printGenerics for classNode.name=" + classNode.getName() + ". ";
        if (genericsTypes == null || genericsTypes.length == 0) {
            System.out.println(s + " NO GENERICS ");
            return;
        }
        System.out.println(s + " genericsTypes.length=" + genericsTypes.length);
        for (int i = 0; i < genericsTypes.length; i++) {
            String idx = "[" + i + "] - ";
            System.out.println(idx + s + " genericsType=" + genericsTypes[i] + "; genericType.getType()=" + genericsTypes[i].getType() + "; genericType.getType().getName()=" + genericsTypes[i].getType().getName() + "; genericsTypes[i].getType().getClass()=" + genericsTypes[i].getType().redirect().getClass());
            GenericsType gt = genericsTypes[i];
            ClassNode[] uppers = gt.getUpperBounds();
            if (uppers == null || uppers.length == 0) {
                System.out.println(idx + s + " NO UPPER BOUNDS for genericsType=" + gt);

            } else {
                System.out.println(idx + s + " ------------ UPPER BOUNDS for genericsType=" + gt);
                for (int n = 0; n < uppers.length; n++) {
                    String idxn = idx + "[" + n + "] upperBound ";
                    System.out.println(idxn + s + " = " + uppers[n] + "; upperBound.getName()=" + uppers[n].getName() + "; upperBound.getClass()=" + uppers[n].redirect().getClass() + "; isResolved=" + uppers[n].isResolved());
                    System.out.println(idxn + s + " _________________START PRINT UPPER BOUND GENERICS________________ ");
                    printGenerics(uppers[n]);
                    System.out.println(idxn + s + " _________________END PRINT UPPER BOUND GENERICS________________ ");

                }
            }
/////////
            ClassNode lower = gt.getLowerBound();
            if (lower == null) {
                System.out.println(idx + s + " NO LOWER BOUND for genericsType=" + gt);

            } else {
                System.out.println(idx + s + " ------------ LOWER BOUND for genericsType=" + gt);
                System.out.println(idx + s + " = " + lower + "; lowerBound.getName()=" + lower.getName() + "; lowerBound.getClass()=" + lower.redirect().getClass() + "; isResolved=" + lower.isResolved());
                System.out.println(idx + s + " _________________START PRINT LOWER BOUND GENERICS________________ ");
                printGenerics(lower);
                System.out.println(idx + s + " _________________END PRINT LOWER BOUND GENERICS________________ ");
            }

/////////            
        }
    }

    protected void createGenerics(final JavaClassNode classNode) {
        try {
            Task<CompilationController> task = new Task<CompilationController>() {

                @Override
                public void run(CompilationController controller) throws Exception {
                    Elements elements = controller.getElements();
                    if (elements != null) {
                        TypeElement typeElement = ElementSearch.getClass(elements, classNode.getName());
                        if (typeElement != null) {
                            List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
                            //print(typeElement, typeParameters);
                            //methods.addAll(getMethods(classNode, elements, typeElement));
                            createGenerics(classNode, typeElement);
                            printGenerics(classNode);
                            resolveClassTypeParameters(classNode, classNode.getGenericsTypes());
                            printGenerics(classNode);
                        }
                    }
                }
            };

            compilationUnit.getJavaSource().runUserActionTask(task, true);
        } catch (IOException ex) {
            System.out.println("*********** EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE " + ex.getMessage());
            Exceptions.printStackTrace(ex);
        }

    }

    protected void createGenerics(JavaClassNode forClass, TypeElement typeElement) {
        if (typeElement.getQualifiedName().toString().equals("org.learn.MyClassMy")) {
            System.out.println(" TPINFO ------------ TypeElement name = org.learn.MyClassMy");
            System.out.println(" TPINFO ------------ TypeParameterElement ");
            List<? extends TypeParameterElement> tpeList = typeElement.getTypeParameters();
            GenericsType[] classGenericsTypes = new GenericsType[tpeList.size()];
            for (int i = 0; i < tpeList.size(); i++) {
                TypeParameterElement tpe = tpeList.get(i);
                ClassNode tpeClassNode = ClassHelper.makeWithoutCaching(tpe.getSimpleName().toString());
                ClassNode[] tpeBounds = new ClassNode[tpe.getBounds().size()];
//                System.out.println(" **** TypeParameterElement getClass=" + tpe.getClass() + "; name=" + tpe.getSimpleName());
//                System.out.println("    **** TypeParameterElement.getGenericElement()=" + tpe.getGenericElement().getSimpleName());
//                System.out.println("    **** TypeParameterElement.asType().getClass()=" + tpe.asType().getClass() + "; is instanceof TypeVariable = " + (tpe.asType() instanceof TypeVariable));
                List<? extends TypeMirror> tpeMirrorList = tpe.getBounds();
                for (int n = 0; n < tpeMirrorList.size(); n++) {
                    TypeMirror tpeMirror = tpeMirrorList.get(n);
                    JavaClassTypeMirrorVisitor<GenericsType, ClassNode> tpbv = new JavaClassTypeMirrorVisitor<GenericsType, ClassNode>();
//                    ClassNode tpeClassNode = ClassHelper.make(tpeMirror.toString());

//                System.out.println("    **** TypeParameterElement tpeMirror's classNode=" + tpeMirror);

                    tpeBounds[n] = tpbv.visit(tpeMirror).getType();
                }
                classGenericsTypes[i] = new GenericsType(tpeClassNode, tpeBounds, null);
            }
            forClass.setUsingGenerics(true);
            forClass.setGenericsTypes(classGenericsTypes);
        }
    }

    protected List<MethodNode> createMethods(final JavaClassNode classNode) {
        final List<MethodNode> methods = new ArrayList<MethodNode>();
        try {
            Task<CompilationController> task = new Task<CompilationController>() {

                @Override
                public void run(CompilationController controller) throws Exception {
                    Elements elements = controller.getElements();
                    if (elements != null) {
                        TypeElement typeElement = ElementSearch.getClass(elements, classNode.getName());
                        if (typeElement != null) {
                            //methods.addAll(getMethods(classNode, elements, typeElement));
                            methods.addAll(createMethods(classNode, elements, typeElement));
                        }
                    }
                }
            };

            compilationUnit.getJavaSource().runUserActionTask(task, true);
        } catch (IOException ex) {
            System.out.println("*********** EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE " + ex.getMessage());
            Exceptions.printStackTrace(ex);
        }
        return methods;
    }

    protected List<MethodNode> createMethods(ClassNode classNode, Elements elements, TypeElement typeElement) {

        /**
         * bounds contains ClassNode[] for each type parameter name that is
         * found in the typeElement and al enclosing TypeElements.
         */
        Map<String, ClassNode[]> bounds = createClassTypeParameterBounds(typeElement);
        List<MethodNode> methods = new ArrayList<MethodNode>();
        ElementUtilities.ElementAcceptor acceptor = new ElementUtilities.ElementAcceptor() {

            @Override
            public boolean accept(Element e, TypeMirror type) {
                if (e.getKind() != ElementKind.METHOD) {

                    return false;
                }
                /*
                 * for (AccessLevel level : levels) { if
                 * (level.getJavaAcceptor().accept(e, type)) { return true; } }
                 */
                //return false;
                return true;
            }
        };

        for (ExecutableElement element : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
            if (!acceptor.accept(element, typeElement.asType())) {
                continue;
            }

            System.out.println("BEFORE createMethodGenericsTypes method name" + element.getSimpleName());

            //Map<String, ClassNode> methodBounds = new HashMap<String, ClassNode>(bounds.size());
            //methodBounds.putAll(bounds);
            /*
             * methodBounds contains ClassNode[] for each typeParameter. Then
             * ExecutableElement's type parameters are added
             */
            GenericsType[] genericsTypes = this.createMethodGenericsTypes(element.getTypeParameters(), bounds);
            System.out.println("AFTER createMethodGenericsTypes method name" + element.getSimpleName());
            for (GenericsType g : genericsTypes) {
                System.out.println("AFTER createMethodGenericsTypes method name" + element.getSimpleName() + "; generics=" + g);
            }

            if (genericsTypes != null && genericsTypes.length > 0) {
                classNode.setUsingGenerics(true);
            }

            ClassNode returnClassNode = null;
            TypeMirror returnTypeMirror = element.getReturnType();
            TypeKind typeKind = returnTypeMirror.getKind();

            if (typeKind == TypeKind.TYPEVAR) {
                ClassNode[] b = bounds.get(returnTypeMirror.toString());
                if (b != null && b.length > 0) {
                    returnClassNode = ClassHelper.make(b[0].getName());
                }
            } else {
                returnClassNode = ClassHelper.make(returnTypeMirror.toString());
            }
            returnClassNode = ClassHelper.make(returnTypeMirror.toString());

            List<? extends VariableElement> params = element.getParameters();

            Parameter[] parameters = new Parameter[params.size()];

            for (int i = 0; i < params.size(); i++) {
                VariableElement varElem = params.get(i);
                String paramName = varElem.getSimpleName().toString();
                ClassNode paramClassNode = createClassNode(varElem, bounds, genericsTypes);
                parameters[i] = new Parameter(paramClassNode, paramName);
            }

            MethodNode mn = new MethodNode(element.getSimpleName().toString(),
                    0, returnClassNode, parameters, null, null);
            mn.setGenericsTypes(genericsTypes);
//System.out.println("===== GETMETHODS FROM ClassNode.name=" + classNode.getName() + "MethodNode.Name=" + mn.getName() + "; methodNode=" + mn.toString());        
            methods.add(mn);
            if ("mySay2".equals(mn.getName())) {
                GenericsType[] gtypes = mn.getGenericsTypes();
                if (gtypes != null) {
                    System.out.println("CUSTOM MYSAY2  GenericsType[].length=" + gtypes.length);
                    for (int i = 0; i < gtypes.length; i++) {
                        System.out.println("CUSTOM MYSAY2  GenericsType[" + i + "] = " + gtypes[i]);
                    }
                }
                Parameter[] pars = mn.getParameters();
                System.out.println("CUSTOM MYSAY2  parameters.length=" + pars.length);
                for (int i = 0; i < pars.length; i++) {
                    System.out.println("CUSTOM MYSAY2 --------- parameter index=" + i + "; param name=" + pars[i].getName() + "; param type=" + pars[i].getType());
                    GenericsType[] ptypes = pars[i].getType().getGenericsTypes();
                    if (ptypes != null) {
                        System.out.println("CUSTOM MYSAY2 param[" + i + "].GenericsType[].length=" + ptypes.length);
                        for (int n = 0; n < ptypes.length; n++) {
                            System.out.println("CUSTOM MYSAY2 param[" + i + "].GenericsType[" + n + "] = " + ptypes[n]);
                        }
                    }
                }

                System.out.println("CUSTOM MYSAY2  END _______________________________________");

            }

        }//for

        return methods;

    }//getMethods()

    protected ClassNode createClassNode(VariableElement element, Map<String, ClassNode[]> classBounds, GenericsType[] genericsTypes) {
        ClassNode classNode = null;
        TypeMirror typeMirror = element.asType();
        TypeKind typeKind = typeMirror.getKind();
        String mirrorName = typeMirror.toString();

        if (typeKind == TypeKind.TYPEVAR && classBounds.containsKey(mirrorName)) {
            classNode = classBounds.get(mirrorName)[0];
        } else if (typeKind == TypeKind.TYPEVAR) {

            GenericsType generics = null;
            for (GenericsType gt : genericsTypes) {
                System.out.println(" ^^^^^^^^^^ createClassNode mirrorName=" + mirrorName + "; gt.name=" + gt.getName());
                if (mirrorName.equals(gt.getName())) {
                    generics = gt;
                    break;
                }
            }

            classNode = ClassHelper.makeWithoutCaching(mirrorName);
            classNode.setRedirect(generics.getType());
            System.out.println(" ^^^^^^^^^^ createClassNode mirrorName=" + mirrorName + "; classNode.name=" + classNode.getName() + "; classNode=" + classNode);

            //classNode.setGenericsTypes(genericsTypes);
            classNode.setGenericsTypes(new GenericsType[]{generics});
            classNode.setGenericsPlaceHolder(true);
            //classNode.setUsingGenerics(true);

        } else {
            classNode = ClassHelper.make(typeMirror.toString());
            resolve(classNode);
        }

        return classNode;
    }

    protected void resolveReturnType(MethodNode methodNode, List<JavaClassNode> nodes) {
        ClassNode node = methodNode.getReturnType();

        if (!node.isResolved()) {
            resolve(node, nodes);
        }
    }

    protected void resolveParameters(MethodNode methodNode, List<JavaClassNode> nodes) {
        Parameter[] parameters = methodNode.getParameters();

        for (Parameter p : parameters) {
            if (!p.getType().isResolved()) {
                resolve(p.getType(), nodes);
            }
        }
    }

    public EditorCompilationUnit.CompileUnit getAST() {
        return (EditorCompilationUnit.CompileUnit) compilationUnit.getAST();
    }

    public TypeCache getTypeCache() {
        return compilationUnit.getResolveVisitor().getTypeCache();
    }

    protected void resolve(ClassNode sourceNode, List<JavaClassNode> nodes) {
        ClassNode classNode = getAST().getClass(sourceNode.getName());
        if (classNode == null) {
            resolveToClass(sourceNode);
            return;
        }
        sourceNode.setRedirect(classNode);
        if ((!classNode.isResolved()) && (classNode instanceof JavaClassNode)) {
            if (!((JavaClassNode) classNode).isComplete()) {
                nodes.add((JavaClassNode) classNode);
            }
        }
    }

    protected void resolveSuper(ClassNode sourceNode, List<JavaClassNode> nodes) {
        ClassNode node = sourceNode.getSuperClass();
        while (node != null) {
            if (node.isResolved()) {
                break;
            }
            if (node instanceof JavaClassNode) {
                if (!((JavaClassNode) node).isComplete()) {
                    nodes.add((JavaClassNode) node);
                }
            }
            node = node.getSuperClass();
        }

    }

    public void resolveToClass(ClassNode type) {
        String name = type.getName();


        ClassNode cached = getTypeCache().getResolvedBySimpleName(name);
        if (cached != null) {
            type.setRedirect((ClassNode) cached);
            return;
        }

        if (!getTypeCache().canBeResolved(name)) {
            return;
        }
        String pname = getTypeCache().getCacheProposal(name);
        if (pname == null) {
            return;
        }
        name = pname;
        GroovyClassLoader loader = compilationUnit.getClassLoader();
        Class cls;
        try {
            cls = loader.loadClass(name, false, true);
        } catch (ClassNotFoundException cnfe) {
            return;
        } catch (CompilationFailedException cfe) {
            compilationUnit.getErrorCollector().addErrorAndContinue(new ExceptionMessage(cfe, true, sourceUnit));
            return;

        }

        if (cls == null) {
            return;
        }

        ClassNode cn = ClassHelper.make(cls);
        type.setRedirect(cn);
        getTypeCache().setResolved(type.getName(), cn);
    }

    protected void resolve(ClassNode sourceNode) {
        ClassNode classNode = getAST().getClass(sourceNode.getName());
        String nm1 = "java.util.List";
        String nm2 = "org.learn.MyInterface";

        if (classNode == null && (nm1.equals(sourceNode.getName()) || nm2.equals(sourceNode.getName()))) {
            System.out.println("===== $ :: $ RESOLVE  FROM AST classNode==NULL sourceNode.name" + sourceNode.getName());
        } else if (nm1.equals(sourceNode.getName()) || nm2.equals(sourceNode.getName())) {
            System.out.println("===== $ :: $ RESOLVED FROM AST classNode==" + classNode.getName() + ";classNode.getClass=" + classNode.redirect().getClass());
        }

        if (classNode == null) {
            String sn = sourceNode.getName();
            if (nm1.equals(sn) || nm2.equals(sn)) {
                System.out.println("===== $ :: $ BEFORE RESOLVE TO CLASS sourceNode==" + sourceNode.getName() + ";classNode.getClass=" + sourceNode.redirect().getClass() + "; isResolved=" + sourceNode.isResolved());
            }

            resolveToClass(sourceNode);
            if (nm1.equals(sn) || nm2.equals(sn)) {
                System.out.println("===== $ :: $ AFTER RESOLVE TO CLASS sourceNode==" + sourceNode.getName() + ";classNode.getClass=" + sourceNode.redirect().getClass() + "; isResolved=" + sourceNode.isResolved());
            }

            return;
        }
        sourceNode.setRedirect(classNode);
        if (nm1.equals(sourceNode.getName()) || nm2.equals(sourceNode.getName())) {
            System.out.println("===== $ :: $ AFTER REDIRECT classNode==" + sourceNode.getName());
        }

    }

    protected void resolve(ClassNode sourceNode, boolean resolveGenerics) {
        if (resolveGenerics) {
            System.out.println("%%%%%% $ :: $ RESOLVE  resolveGenerics" + sourceNode.getName());
            this.resolveClassTypeParameters(sourceNode, sourceNode.getGenericsTypes());
        }
        ClassNode classNode = getAST().getClass(sourceNode.getName());
        String nm1 = "java.util.List";
        String nm2 = "org.learn.MyInterface";

        if (classNode == null && (nm1.equals(sourceNode.getName()) || nm2.equals(sourceNode.getName()))) {
            System.out.println("===== $ :: $ RESOLVE  FROM AST classNode==NULL sourceNode.name" + sourceNode.getName());
        } else if (nm1.equals(sourceNode.getName()) || nm2.equals(sourceNode.getName())) {
            System.out.println("===== $ :: $ RESOLVED FROM AST classNode==" + classNode.getName() + ";classNode.getClass=" + classNode.redirect().getClass());
        }

        if (classNode == null) {
            String sn = sourceNode.getName();
            if (nm1.equals(sn) || nm2.equals(sn)) {
                System.out.println("===== $ :: $ BEFORE RESOLVE TO CLASS sourceNode==" + sourceNode.getName() + ";classNode.getClass=" + sourceNode.redirect().getClass() + "; isResolved=" + sourceNode.isResolved());
            }

            resolveToClass(sourceNode);
            if (nm1.equals(sn) || nm2.equals(sn)) {
                System.out.println("===== $ :: $ AFTER RESOLVE TO CLASS sourceNode==" + sourceNode.getName() + ";classNode.getClass=" + sourceNode.redirect().getClass() + "; isResolved=" + sourceNode.isResolved());
            }

            return;
        }
        sourceNode.setRedirect(classNode);
        if (nm1.equals(sourceNode.getName()) || nm2.equals(sourceNode.getName())) {
            System.out.println("===== $ :: $ AFTER REDIRECT classNode==" + sourceNode.getName());
        }

    }

    protected MethodNode createMyClassMethod(ClassNode classNode, Elements elements, TypeElement typeElement) {
        MethodNode r = null;

        ClassNode[] bounds = new ClassNode[]{ClassHelper.make("org.learn.MyTestUpperBound")};

        resolve(bounds[0]);

        ClassNode basicType = ClassHelper.makeWithoutCaching("T1_1_2");

        GenericsType gt = new GenericsType(basicType, bounds, null);
        gt.setPlaceholder(true); // then basicType be marked as placeholdee

        GenericsType[] genericsTypes = new GenericsType[]{gt};
        basicType.setRedirect(bounds[0]);
        classNode.setUsingGenerics(true);

//        basicType.setRedirect(bounds[0]);

//        basicType.setGenericsTypes(genericsTypes);

        System.out.println("1 CREATE MY CLASS createMyClassMethod GenericsType gt=" + gt);

        //ClassNode paramType = ClassHelper.makeWithoutCaching("org.learn.MyTestUpperBound");
        ClassNode paramType = ClassHelper.makeWithoutCaching("T1_1_2");
        //ClassNode paramType = bounds[0];
        paramType.setRedirect(gt.getType());
        paramType.setGenericsTypes(new GenericsType[]{gt});
        paramType.setGenericsPlaceHolder(true);

        /*
         * paramType.setRedirect(bounds[0]);
         * paramType.setGenericsTypes(genericsTypes);
         * paramType.setGenericsPlaceHolder(true);
         */
//        ClassNode paramType = ClassHelper.makeWithoutCaching("org.learn.MyTestUpperBound");
        System.out.println("1 CREATE MY CLASS createMyClassMethod paramType=" + paramType + "; or = " + paramType.toString(true));

        //paramType.setGenericsPlaceHolder(true);
//        paramType.setRedirect(bounds[0]);
        Parameter p = new Parameter(paramType, "myParam_p1");
        Parameter[] parameters = new Parameter[]{p};
        ClassNode returnClassNode = ClassHelper.make("java.lang.Object");
        returnClassNode.setRedirect(ClassHelper.OBJECT_TYPE);
        r = new MethodNode("myGen",
                0, returnClassNode, parameters, null, null);
        r.setGenericsTypes(genericsTypes);

        gt = genericsTypes[0];
        System.out.println(" ======================= T1_1_2 GenericsType INFO FOR PARAM = " + p.getName() + " -------------");
        System.out.println(" ====BOUNDS[0] =" + bounds[0]);
        System.out.println("(1) ------------  gt.getName()=" + gt.getName());
        System.out.println("(2) ------------  gt.getType().getName()=" + gt.getType().getName());
        System.out.println("(3) ------------  gt.getUpperBounds().length=" + gt.getUpperBounds().length);
        System.out.println("(4) ------------  gt.getUpperBounds()[0].getName()=" + gt.getUpperBounds()[0].getName());
        System.out.println("(5) ------------  gt.getUpperBounds()[0]=" + gt.getUpperBounds()[0].isGenericsPlaceHolder());
        System.out.println("(6) ------------  gt.getUpperBounds()[0].isRedirectNode()=" + gt.getUpperBounds()[0].isRedirectNode());
        System.out.println("(7) ------------  gt.getUpperBounds()[0].isUsingGenerics()=" + gt.getUpperBounds()[0].isUsingGenerics());

        ClassNode cn = r.getParameters()[0].getType();

        System.out.println("3 ******************** T1_1_2 PARAMETER INFO TYPE  paramName= p1" + "; param obj p=" + p);

        System.out.println("3.0 -------------------------- TYPE =  T1_1_2 p.getType().getGenericsTypes()=" + cn.getGenericsTypes());
        System.out.println("3.1 -------------------------- TYPE =  T1_1_2 p.getType().isGenericsPlaceHolder()=" + cn.isGenericsPlaceHolder());
        System.out.println("3.2 -------------------------- TYPE =  T1_1_2 p.getType().isRedirectNode()=" + cn.isRedirectNode());
        System.out.println("3.3 -------------------------- TYPE =  T1_1_2 p.getType().isUsingGenerics()=" + cn.isUsingGenerics());
        System.out.println("3.4 -------------------------- TYPE =  T1_1_2 p.getType().getName()=" + cn.getName());
        System.out.println("3.5 -------------------------- TYPE =  T1_1_2 p.getType() object = " + cn);

        return r;
    }

    protected GenericsType[] createMethodGenericsTypes(List<? extends TypeParameterElement> elements, Map<String, ClassNode[]> classBounds) {
        System.out.println("START createMethodGenericsTypes ");


        GenericsType[] genericsTypes = new GenericsType[elements.size()];
        for (int i = 0; i < elements.size(); i++) {
            TypeParameterElement element = elements.get(i);
            List<? extends TypeMirror> mirrorBounds = element.getBounds();
            String tpName = element.getSimpleName().toString();
            ClassNode basicType = ClassHelper.makeWithoutCaching(tpName);
            basicType.setGenericsPlaceHolder(true);

            ClassNode[] upperBounds = null;
            ClassNode boundNode;
            if (mirrorBounds == null || mirrorBounds.isEmpty()) {
                boundNode = ClassHelper.makeWithoutCaching("java.lang.Object");
                boundNode.setRedirect(ClassHelper.OBJECT_TYPE);
                upperBounds = new ClassNode[]{boundNode};
            } else if (mirrorBounds.get(0).getKind() == TypeKind.TYPEVAR) {
                if (classBounds.containsKey(mirrorBounds.get(0).toString())) {
                    upperBounds = classBounds.get(mirrorBounds.get(0).toString());
                } else {
                    boundNode = ClassHelper.makeWithoutCaching(mirrorBounds.get(0).toString());
                    /*
                     * mark as a placeholder and later must be recreated
                     */
                    boundNode.setGenericsPlaceHolder(true);
                    upperBounds = new ClassNode[]{boundNode};

                }
            } else {
                upperBounds = new ClassNode[mirrorBounds.size()];
                //int n = 0;
                //for (TypeMirror mir : mirrorBounds) {
                for (int n = 0; n < upperBounds.length; n++) {
                    TypeMirror mir = mirrorBounds.get(n);
                    boundNode = ClassHelper.makeWithoutCaching(mir.toString());
                    resolve(boundNode);
                    upperBounds[n] = boundNode;
                    System.out.println("1) START !!!!!!!! createMethodGenericsTypes !upperBounds[0].isGenericsPlaceHolder()");

                }
            }
            if (!upperBounds[0].isGenericsPlaceHolder()) {
                System.out.println("1) START !!!!!!!! createMethodGenericsTypes !upperBounds[0].isGenericsPlaceHolder()");

                basicType.setRedirect(upperBounds[0]);
            }

            GenericsType gt = new GenericsType(basicType, upperBounds, null);
            gt.setPlaceholder(true);
            genericsTypes[i] = gt;
        }
        for (int i = 0; i < genericsTypes.length; i++) {
            GenericsType gt = genericsTypes[i];
            //gt.setPlaceholder(true);
            if (gt.getUpperBounds()[0].isGenericsPlaceHolder()) {
                System.out.println("2) START !!!!!!!! createMethodGenericsTypes !upperBounds[0].isGenericsPlaceHolder()");

//                ClassNode basicType = ClassHelper.makeWithoutCaching(tpName);
                String phName = gt.getUpperBounds()[0].getName();
                GenericsType gtDepend = null;
                for (GenericsType gt1 : genericsTypes) {
                    if (gt1.getName().equals(phName)) {
                        gtDepend = gt1;
                        break;
                    }
                }
                ClassNode[] uppers = gtDepend.getUpperBounds();
                ClassNode basicType = ClassHelper.makeWithoutCaching(gt.getName());
                basicType.setGenericsPlaceHolder(true);
                basicType.setRedirect(uppers[0]);
                GenericsType gtNew = new GenericsType(basicType, uppers, null);
                gtNew.setPlaceholder(true);
                genericsTypes[i] = gtNew;
            }
        }

        return genericsTypes;
    }//createMethodGenericsTypes

    /**
     * Creates bounds for a given TypeElement and all enclosing TypeElemens 
     * if exist.
     * 
     * @param element 
     * @return 
     */
    protected Map<String, ClassNode[]> createClassTypeParameterBounds(TypeElement element) {
        Map<String, ClassNode[]> r = new HashMap<String, ClassNode[]>();
        Element e = element;

        Stack<Element> stack = new Stack<Element>();
        while (e != null && (e.getKind() == ElementKind.CLASS || e.getKind() == ElementKind.INTERFACE)) {
            stack.push(e);
            e = e.getEnclosingElement();
        }
        if (stack.isEmpty()) {
            return r;
        }
        while (!stack.isEmpty()) {
            TypeElement te = (TypeElement) stack.pop();
            createClassTypeParameterBounds(te, r);
        }

        return r;
    }

    protected void createClassTypeParameterBounds(TypeElement typeElement, Map<String, ClassNode[]> result) {

        List<? extends TypeParameterElement> typeParams = typeElement.getTypeParameters();
        if (typeParams == null || typeParams.isEmpty()) {
            return;
        }
        for (TypeParameterElement tp : typeParams) {
            //tp.
            createClassTypeParameterBounds(tp, result);
        }

    }

    protected void createClassTypeParameterBounds(TypeParameterElement element, Map<String, ClassNode[]> result) {

        String typeParamName = element.getSimpleName().toString();
        List<? extends TypeMirror> bounds = element.getBounds();

        if (bounds == null || bounds.isEmpty()) {
            ClassNode boundClassNode = ClassHelper.make("java.lang.Object");
            boundClassNode.setRedirect(ClassHelper.OBJECT_TYPE);
            result.put(typeParamName, new ClassNode[]{boundClassNode});
            return;
        }
        ClassNode[] uppers = new ClassNode[bounds.size()];
        for (int i = 0; i < bounds.size(); i++) {
            TypeMirror m = bounds.get(i);

            TypeKind boundTypeKind = m.getKind();
            String boundTypeMirror = m.toString();

            if (boundTypeKind == TypeKind.DECLARED) {
                ClassNode node = ClassHelper.make(boundTypeMirror);
                if ("java.lang.Object".equals(boundTypeMirror)) {
                    node.setRedirect(ClassHelper.OBJECT_TYPE);
                }
                uppers[i] = node;
            } else if (boundTypeKind == TypeKind.TYPEVAR) {
                result.put(typeParamName, result.get(boundTypeMirror));
                return;


            }
        }
        result.put(typeParamName, uppers);
    }//createClassTypeParameterBounds(
}//ProjectClassNodes
