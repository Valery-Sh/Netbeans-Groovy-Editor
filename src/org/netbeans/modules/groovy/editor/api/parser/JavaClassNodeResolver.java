/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.api.parser;

import groovy.lang.GroovyClassLoader;
import java.util.*;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.control.CompilationFailedException;
import org.netbeans.api.java.source.ElementUtilities;
import org.netbeans.modules.groovy.editor.java.ElementSearch;
import org.netbeans.modules.groovy.editor.java.Utilities;

/**
 *
 * @author V. Shyshkin
 */
public class JavaClassNodeResolver {

    protected Map<String, GenericsType> classTypeParameters = new HashMap<String, GenericsType>();
    //protected List<JavaClassNode> incompleteNodes = new ArrayList<JavaClassNode>();
    List<JavaClassNode> currentJavaClassNodes = new ArrayList<JavaClassNode>();
    protected EditorCompilationUnit compilationUnit;
    //protected SourceUnit sourceUnit;
//    protected JavaClassNode javaClassNode;
//    protected TypeMirror typeMirror;
//    protected Elements elements;

    public JavaClassNodeResolver(EditorCompilationUnit compilationUnit) {
        this.compilationUnit = compilationUnit;
    }

    public ClassNode getClass(String name, Elements elements) {
        ClassNode result = null;
        TypeElement typeElement = ElementSearch.getClass(elements, name);
        System.out.println(":::: :::::::: :::: ::: !!!!!!!!!!!!!!!! getClass() = " + name);
        result = createClassNode(name, typeElement);
        //typeElement
        if (result != null && (result instanceof JavaClassNode)) {
            complete((JavaClassNode) result, elements);
        }
        return result;
    }

    public void complete(JavaClassNode classNode, Elements elements) {
//        if ( classNode.getName().equals("org.learn.MyClassMy")) {
//            List<? extends TypeParameterElement> tpeList = 
//        }
        createClassGenerics(classNode, elements);
        /*
         * System.out.println("COMPLETE) ::::==========:::: classNode.name = " +
         * classNode.getName()); PrintUtils.printClassNode(classNode);
         * System.out.println("END COMPLETE) ::::==========:::: classNode.name =
         * " + classNode.getName());
         */
        // create methods
        List<MethodNode> methods = createMethods(classNode, elements);
        
        for (MethodNode method : methods) {
            classNode.addMethod(method);
        }
        List<FieldNode> fields = createFields(classNode, elements);
        for ( FieldNode field : fields) {
            classNode.addField(field);
        }
        
        ClassNode superType  = classNode.getSuperClass(); 
        if (superType != null && (superType instanceof JavaClassNode)) { 
            if (!((JavaClassNode)superType).isComplete()) { 
                new JavaClassNodeResolver(compilationUnit).complete((JavaClassNode)superType,elements);
            } 
        }
        
        
        classNode.setComplete(true);
        
        
        /*
         * List<MethodNode> methods = createMethods(classNode, elements); for
         * (MethodNode method : methods) { // resolveReturnType(method,
         * currentJavaClassNodes); //resolveParameters(method,
         * currentJavaClassNodes); classNode.addMethod(method); }
         *
         * classNode.setCompleteWithMethods(true); ClassNode superNode =
         * classNode.getSuperClass(); if (superNode != null && (superNode
         * instanceof JavaClassNode)) { if (!((JavaClassNode)
         * superNode).isCompleteWithMethods()) { // nodes.add((JavaClassNode)
         * node); complete((JavaClassNode) superNode, elements); } }
         */

    }

    protected void createClassGenerics(final JavaClassNode classNode, Elements elements) {
        if (elements != null) {
            TypeElement typeElement = ElementSearch.getClass(elements, classNode.getName());
            if (typeElement != null) {
                List<? extends TypeParameterElement> typeParameters = typeElement.getTypeParameters();
                //createGenerics(classNode, typeElement);
                GenericsType[] classGenericsTypes = createGenerics(typeParameters);
                /*
                 * System.out.println("1 ::: PRINT UTILS "); if
                 * (classGenericsTypes != null) { for (GenericsType gt :
                 * classGenericsTypes) { PrintUtils.printGenerics(gt); } }
                 * System.out.println("1 ::: END PRINT UTILS ");
                 */
                classNode.setUsingGenerics(true);
                classNode.setGenericsTypes(classGenericsTypes);
//                System.out.println("B2) ::::==========:::: createClassGenerics BEFORE resolveGenericsHeader");
//                if (classNode.getName().contains("MyClassMy")) {
//                    PrintUtils.printClassNode(classNode);
//                }
                this.resolveGenericsHeader(classGenericsTypes, elements);
            }
        }


    }

    protected GenericsType[] createGenerics(List<? extends TypeParameterElement> tpeList) {

        GenericsType[] classGenericsTypes = new GenericsType[tpeList.size()];

        for (int i = 0; i < tpeList.size(); i++) {
            TypeParameterElement tpe = tpeList.get(i);
            // it'a a placeholder
            ClassNode tpeClassNode = ClassHelper.make(tpe.getSimpleName().toString());
//            System.out.println("0 VVVVV  **** TypeParameterElement tpeMirror's name = " + tpe.getSimpleName().toString() + "tpe.getBounds().size()=" + tpe.getBounds().size());
            ClassNode[] tpeBounds = new ClassNode[tpe.getBounds().size()];
            List<? extends TypeMirror> tpeMirrorList = tpe.getBounds();

            for (int n = 0; n < tpeMirrorList.size(); n++) {
                TypeMirror tpeMirror = tpeMirrorList.get(n);
                
                //JavaClassTypeMirrorVisitor<GenericsType, ClassNode> tpbv = new JavaClassTypeMirrorVisitor<GenericsType, ClassNode>();

//                System.out.println("1 VVVVV  **** TypeParameterElement tpeMirror's =" + tpeMirror);
                tpeBounds[n] = visitMirror(tpeMirror); 
                //tpeBounds[n] = tpbv.visit(tpeMirror).getType();
//                System.out.println("2 VVVVV  **** TypeParameterElement tpeMirror's tpeBound=" + tpeBounds[n] + "; tpeBounds[n].name=" + tpeBounds[n].getName());

            }
            GenericsType gt = new GenericsType(tpeClassNode, tpeBounds, null);
//            System.out.println("A) ::::==========::::PRINT UTILS ");
//            PrintUtils.printGenerics(gt);
//            System.out.println("A) ::::==========:::: END PRINT UTILS ");

            classGenericsTypes[i] = gt;
        }


        System.out.println("B) ::::==========::::PRINT UTILS ");

        /*
         * if (classGenericsTypes != null) {
         * PrintUtils.printGenerics(classGenericsTypes[0]); if
         * (classGenericsTypes.length > 1) {
         * PrintUtils.printGenerics(classGenericsTypes[1]); } }
         * System.out.println("B) ::::==========:::: ENDPRINT UTILS ");
         */
        return classGenericsTypes;
    }

    private void resolveGenericsHeader(GenericsType[] genericsTypes, Elements elements) {
        if (genericsTypes == null) {
            return;
        }
        //currentClass.setUsingGenerics(true);
        for (GenericsType gt : genericsTypes) {
//System.out.println("1. resolveGenericsHeader(GenericsType[] types) type=" + type + "; type.name=" + type.getName());

            ClassNode gtType = gt.getType();
            String name = gt.getName();
//System.out.println("2. resolveGenericsHeader(GenericsType[] types) type.getType().getName=" + type.getType().getName());

            ClassNode[] bounds = gt.getUpperBounds();
            if (bounds != null) {
//System.out.println("3. resolveGenericsHeader(GenericsType[] types) BOUNDS !+ NULL ");

                // boolean nameAdded = false;
                for (ClassNode upperBound : bounds) {

//                    if (!nameAdded && upperBound != null || !resolve(classNode)) {
/*
                     * classTypeParameters.put(name, gt);
                     * gt.setPlaceholder(true); gtType.setRedirect(upperBound);
                     * nameAdded = true;
                     */
                    //                  }
                    System.out.println("~~~~~~~~~~~~~~~~~~~............. BEFORE RESOLVE OR FAIL ~~~~~ upperBound.type=" + upperBound + "; genericsType.name=" + gt.getName() + "; genericsType.type=" + gt.getType());

                    resolve(upperBound, elements);
                    System.out.println("~~~~~~~~~~~~~~~~~~~............. AFTER RESOLVE OR FAIL ~~~~~ upperBound.type=" + upperBound + "; genericsType.name=" + gt.getName() + "; genericsType.type=" + gt.getType());

                }
                classTypeParameters.put(name, gt);
                gt.setPlaceholder(true);
                gtType.setRedirect(bounds[0]);
            } else {
                classTypeParameters.put(name, gt);
                gtType.setRedirect(ClassHelper.OBJECT_TYPE);
                gt.setPlaceholder(true);
            }
        }
        System.out.println("~~~~~~~~~~~~~~~~~~~ END RESOLVE");

    }

    private void resolveGenericsTypes(GenericsType[] genericsTypes, Elements elements) {
//System.out.println("START resolveGenericsTypes(GenericsType[] types) ");                        
        if (genericsTypes == null) {
//System.out.println("types == NULL resolveGenericsTypes(GenericsType[] types) ");                        

            return;
        }

//System.out.println("1 resolveGenericsTypes(GenericsType[] types) length=" + types.length);                        

        //currentClass.setUsingGenerics(true);
        for (GenericsType gt : genericsTypes) {
//System.out.println("2 resolveGenericsTypes(GenericsType[] types) type=" + type);                        

            resolveGenericsType(gt, elements);
        }

    }

    private void resolveGenericsType(GenericsType genericsType, Elements elements) {
//System.out.println("START resolveGenericsType(GenericsType genericsType) " + genericsType);                        

        if (genericsType.isResolved()) {
            return;
        }
//System.out.println("1.1 resolveGenericsType(GenericsType genericsType) " + genericsType);                        

//        currentClass.setUsingGenerics(true);
        ClassNode gtType = genericsType.getType();
//System.out.println("1.2 resolveGenericsType(GenericsType genericsType) genericsType.getType()=" + genericsType.getType() +"; genericsType.getUpperBounds()=" + genericsType.getUpperBounds());                        

        // save name before redirect
        String name = gtType.getName();
        ClassNode[] bounds = genericsType.getUpperBounds();
        if (!classTypeParameters.containsKey(name)) {
            if (bounds != null) {
                for (ClassNode upperBound : bounds) {
                    resolve(upperBound, elements);
                    gtType.setRedirect(upperBound);
//System.out.println("1.3~~~~~~~~~~~~~~~~~~~..resolveGenericsType(GenericsType genericsType).... ~~~~~ upperBound.type="+upperBound+ "; genericsType.name=" + genericsType.getName() + "; genericsType.type=" + genericsType.getType());                    
                    resolveGenericsTypes(upperBound.getGenericsTypes(), elements);
                }
            } else if (genericsType.isWildcard()) {
//System.out.println("1.4~~~~~~~~~~~~~~~~~~~..resolveGenericsType(GenericsType genericsType)........... ~~~~~ is WILDCARD");                    

                gtType.setRedirect(ClassHelper.OBJECT_TYPE);
            } else {
                resolve(gtType, elements);
            }
        } else {
//System.out.println("1.5~~~~~~~~~~~~~~~~~~~..resolveGenericsType(GenericsType genericsType)........... ~~~~~ is WILDCARD");                    

            GenericsType gt = classTypeParameters.get(name);
            gtType.setRedirect(gt.getType());
            genericsType.setPlaceholder(true);
        }

        if (genericsType.getLowerBound() != null) {
            resolve(genericsType.getLowerBound(), elements);
        }
//System.out.println("3 resolveGenericsType(GenericsType genericsType) genericsType.getType()=" + genericsType.getType() +"; type.getGenericsTypes()=" + type.getGenericsTypes());                        

        resolveGenericsTypes(gtType.getGenericsTypes(), elements);
//System.out.println("$$$$ === ### BEFORE setResolved resolveGenericsType(GenericsType genericsType) genericsType.getType()=" + genericsType.getType() +"; getGenericsType.name=" + genericsType.getName()+"; getGenericsType.getType=" + genericsType.getType());                        

        genericsType.setResolved(genericsType.getType().isResolved());
    }

/*    protected void resolveClassTypeParameters(ClassNode forClass, Elements elements) {
        GenericsType[] genericsTypes = forClass.getGenericsTypes();
        if (genericsTypes != null && genericsTypes.length > 0) {
            resolveTypeParameters(genericsTypes, elements);
            forClass.setUsingGenerics(true);
        }
    }
*/
/*    protected void resolveTypeParameters(GenericsType[] genericsTypes, Elements elements) {
        if (genericsTypes == null) {
//            System.out.println("$ :: $ START resolveClassTypeParameters(GenericsType[] types) types = NULL !!!!");
        }
        if (genericsTypes == null) {
            return;
        }
//        System.out.println("$ :: $  START resolveClassTypeParameters(GenericsType[] types) length=" + genericsTypes.length);
//        printGenericsTypes(types, "AAAAAAAAA");
        //forClass.setUsingGenerics(true);

        for (GenericsType gt : genericsTypes) {
//            System.out.println("$ :: $  1. resolveClassTypeParameters(GenericsType[] types) type=" + gt + "; type.name=" + gt.getName());

            ClassNode gtType = gt.getType();
            String name = gt.getName(); // ? resolve
            System.out.println("UUUUU $ :: $  1. resolveClassTypeParameters(GenericsType[] types) gt.getName()=" + gt.getName() + "; type.getType().getName=" + gt.getType().getName() + "; gtType= " + gtType);

            ClassNode[] bounds = gt.getUpperBounds();
            if (bounds != null && bounds.length > 0) {
//                System.out.println("$ :: $ 3. resolveClassTypeParameters(GenericsType[] types) BOUNDS != NULL ");

                for (ClassNode upperBound : bounds) {
                    System.out.println("UUUUU $ :: $ 2. BEFORE RESOLVE resolveClassTypeParameters(GenericsType[] types) upperBound=" + upperBound + "; upperBound.getName()=" + upperBound.getName());

                    resolve(upperBound, elements);
                    System.out.println("UUUUU $ :: $ 3. AFTER RESOLVE resolveClassTypeParameters(GenericsType[] types) upperBound=" + upperBound + "; upperBound.getName()=" + upperBound.getName());

                }

                this.classTypeParameters.put(name, gt);
                gt.setPlaceholder(true);
                gtType.setRedirect(bounds[0]);

            } else {
                System.out.println("UUUUU $ :: $  4. resolveClassTypeParameters(GenericsType[] types) gt.getName()=" + gt.getName() + "; type.getType().getName=" + gt.getType().getName() + "; gtType= " + gtType + "; name=" + name);

                this.classTypeParameters.put(name, gt);
                gtType.setRedirect(ClassHelper.OBJECT_TYPE);
                gt.setPlaceholder(true);

            }
        }
//        System.out.println("$ :: $ ________________________ END ");
    }
*/
/*    protected void resolveTypeParameter(GenericsType genericsType, Elements elements) {

        if (genericsType.isResolved()) {
            return;
        }

        ClassNode gtType = genericsType.getType();
        String name = genericsType.getName(); // ? resolve

        System.out.println("UUUUU $ :: $  1. resolveClassTypeParameters(GenericsType[] types) gt.getName()=" + genericsType.getName() + "; type.getType().getName=" + genericsType.getType().getName() + "; gtType= " + gtType);

        ClassNode[] bounds = genericsType.getUpperBounds();
        if (bounds != null && bounds.length > 0) {
//                System.out.println("$ :: $ 3. resolveClassTypeParameters(GenericsType[] types) BOUNDS != NULL ");

            for (ClassNode upperBound : bounds) {
                System.out.println("UUUUU $ :: $ 2. BEFORE RESOLVE resolveClassTypeParameters(GenericsType[] types) upperBound=" + upperBound + "; upperBound.getName()=" + upperBound.getName());

                resolve(upperBound, elements);
                System.out.println("UUUUU $ :: $ 3. AFTER RESOLVE resolveClassTypeParameters(GenericsType[] types) upperBound=" + upperBound + "; upperBound.getName()=" + upperBound.getName());

            }

            this.classTypeParameters.put(name, genericsType);
            genericsType.setPlaceholder(true);
            gtType.setRedirect(bounds[0]);

        } else {
            System.out.println("UUUUU $ :: $  4. resolveClassTypeParameters(GenericsType[] types) gt.getName()=" + genericsType.getName() + "; type.getType().getName=" + genericsType.getType().getName() + "; gtType= " + gtType + "; name=" + name);

            this.classTypeParameters.put(name, genericsType);
            gtType.setRedirect(ClassHelper.OBJECT_TYPE);
            genericsType.setPlaceholder(true);

        }
        genericsType.setResolved(genericsType.getType().isResolved());

//        System.out.println("$ :: $ ________________________ END ");
    }
*/
    protected ClassNode resolveFromCache(ClassNode sourceNode) {
        String name = sourceNode.getName();
        TypeCache typeCache = compilationUnit.getTypeCache();
        if (!typeCache.canBeResolved(name)) {
            return null;
        }

        ClassNode node = typeCache.getResolvedBySimpleName(name);
        return node;

        /*
         * if (!compilationUnit.isProjectClass(name)) { return null; }
         */
        // if null or not present in cache
/*
         * classNode = super.getClass(name); if (classNode != null) { return
         * classNode; }
         */
        // if present in cache but null
/*
         * synchronized (cache) { if (cache.containsKey(name)) { return null; }
         * }
         */

    }

    /**
     * МАССИВЫ ? см ResolveVisitor.resolve
     * @param sourceNode
     * @param elements 
     */
    protected void resolve(ClassNode sourceNode, Elements elements) {

        //this.resolveClassTypeParameters(sourceNode, elements);
        this.resolveGenericsTypes(sourceNode.getGenericsTypes(), elements);
        //sourceNode.setUsingGenerics(true);

        if (sourceNode.isResolved()) {
            return;
        }
        if (sourceNode.isArray()) {
            ClassNode compType = sourceNode.getComponentType();
            resolve(compType, elements);
            sourceNode.setRedirect(compType.makeArray());
            
        }
        
        if (classTypeParameters.get(sourceNode.getName()) != null) {
            GenericsType gt = classTypeParameters.get(sourceNode.getName());

            sourceNode.setRedirect(gt.getType());
            sourceNode.setGenericsTypes(new GenericsType[]{gt});
            sourceNode.setGenericsPlaceHolder(true);
            return;
        }

        ClassNode classNode = resolveFromCache(sourceNode);

        if (classNode != null) {
            sourceNode.setRedirect(classNode);
            return;
        }

        if (compilationUnit.isProjectClass(sourceNode.getName())) {
            //long end = System.currentTimeMillis();
            classNode = new JavaClassNodeResolver(compilationUnit).getClass(sourceNode.getName(), elements);
        }

        if (classNode == null) {

            classNode = resolveToClass(sourceNode.getName());
            if (classNode != null) {
                sourceNode.setRedirect(classNode);
            }

            return;
        }
        sourceNode.setRedirect(classNode);
    }

    protected List<MethodNode> createMethods(final JavaClassNode classNode, Elements elements) {
        final List<MethodNode> methods = new ArrayList<MethodNode>();
        if (elements != null) {
            TypeElement typeElement = ElementSearch.getClass(elements, classNode.getName());
            if (typeElement != null) {
                //methods.addAll(getMethods(classNode, elements, typeElement));
                methods.addAll(createMethods(classNode, elements, typeElement));
            }
        }
        return methods;
    }
    public static int toAstModifiers(Element element) {
        return GroovyModifier.toAstModifiers(element);
/*            Set<Modifier> modifiers = element.getModifiers();
            int intMods = 0;

            for (Modifier m : modifiers) {
                if (m.equals(Modifier.ABSTRACT)) {
                    intMods = intMods | Opcodes.ACC_ABSTRACT;
                }
                if (m.equals(Modifier.PUBLIC)) {
                    intMods = intMods | Opcodes.ACC_PUBLIC;
                }
                if (m.equals(Modifier.PROTECTED)) {
                    intMods = intMods | Opcodes.ACC_PROTECTED;
                }
                if (m.equals(Modifier.PRIVATE)) {
                    intMods = intMods | Opcodes.ACC_PRIVATE;
                }
                if (m.equals(Modifier.STATIC)) {
                    intMods = intMods | Opcodes.ACC_STATIC;
                }
                if (m.equals(Modifier.SYNCHRONIZED)) {
                    intMods = intMods | Opcodes.ACC_SYNCHRONIZED;
                }
                if (m.equals(Modifier.FINAL)) {
                    intMods = intMods | Opcodes.ACC_FINAL;
                }

            }
            return intMods;
            */
    }

    public static Set<Modifier> toModelModifiers(MethodNode node) {
            return GroovyModifier.toModelModifiers(node);
/*            
            Set<Modifier> modifiers = Collections.EMPTY_SET;
            int intMods = node.getModifiers();
            
            if ( (intMods & Opcodes.ACC_ABSTRACT) != 0) {
                modifiers.add(Modifier.ABSTRACT);
            }
            if ( (intMods & Opcodes.ACC_PUBLIC) != 0) {
                modifiers.add(Modifier.PUBLIC);
            } else if ( (intMods & Opcodes.ACC_PROTECTED) != 0) {
                modifiers.add(Modifier.PROTECTED);
            } else if ( (intMods & Opcodes.ACC_PRIVATE) != 0) {
                modifiers.add(Modifier.PRIVATE);
            } else {
                modifiers.add(Modifier.PUBLIC);
            }
            if ( (intMods & Opcodes.ACC_STATIC) != 0) {
                modifiers.add(Modifier.STATIC);
            }
            if ( (intMods & Opcodes.ACC_SYNCHRONIZED) != 0) {
                modifiers.add(Modifier.SYNCHRONIZED);
            }
            if ( (intMods & Opcodes.ACC_FINAL) != 0) {
                modifiers.add(Modifier.FINAL);
            }
            return modifiers;
            */
    }
  
    
    protected List<MethodNode> createMethods(ClassNode classNode, Elements elements, TypeElement typeElement) {

        //Map<String, ClassNode[]> bounds = null;
        List<MethodNode> methods = new ArrayList<MethodNode>();
        ElementUtilities.ElementAcceptor acceptor = new ElementUtilities.ElementAcceptor() {

            @Override
            public boolean accept(Element e, TypeMirror type) {
                if (e.getKind() != ElementKind.METHOD && e.getKind() != ElementKind.CONSTRUCTOR) {
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

            Map<String, GenericsType> safeClassTypeParameters = new HashMap<String, GenericsType>();

            safeClassTypeParameters = new HashMap<String, GenericsType>(classTypeParameters);
            //classTypeParameters = new HashMap<String, GenericsType>(classTypeParameters);            
            GenericsType[] genericsTypes = createGenerics(element.getTypeParameters());

            this.resolveGenericsHeader(genericsTypes, elements);

            if (genericsTypes != null && genericsTypes.length > 0) {
                classNode.setUsingGenerics(true);
            }

            ClassNode returnClassNode = createMethodReturnType(element,elements);

  //          System.out.println("A1) createMethodParameters");
            Parameter[] parameters = createMethodParameters(element, elements);

            int modifiers = toAstModifiers(element);
            
            MethodNode mn = null;
            if (element.getKind().equals(ElementKind.CONSTRUCTOR)) {
                mn = new ConstructorNode(modifiers, parameters, null, null);

            } else {
                mn = new MethodNode(element.getSimpleName().toString(),
                        modifiers, returnClassNode, parameters, null, null);
            }
            mn.setGenericsTypes(genericsTypes);

            methods.add(mn);

            classTypeParameters = safeClassTypeParameters;
        }//for

        return methods;

    }//getMethods()
    protected List<FieldNode> createFields(final JavaClassNode classNode, Elements elements) {
        final List<FieldNode> fields = new ArrayList<FieldNode>();
        if (elements != null) {
            TypeElement typeElement = ElementSearch.getClass(elements, classNode.getName());
            if (typeElement != null) {
                //methods.addAll(getMethods(classNode, elements, typeElement));
                fields.addAll(createFields(classNode, elements, typeElement));
            }
        }
        return fields;
    }

    protected List<FieldNode> createFields(ClassNode ownerNode, Elements elements, TypeElement typeElement) {

        //Map<String, ClassNode[]> bounds = null;
        List<FieldNode> fields = new ArrayList<FieldNode>();
        ElementUtilities.ElementAcceptor acceptor = new ElementUtilities.ElementAcceptor() {

            @Override
            public boolean accept(Element e, TypeMirror type) {
                if (e.getKind() != ElementKind.FIELD) {
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

        for (VariableElement element : ElementFilter.fieldsIn(typeElement.getEnclosedElements())) {
            if (!acceptor.accept(element, typeElement.asType())) {
                continue;
            }

//            Map<String, GenericsType> safeClassTypeParameters = new HashMap<String, GenericsType>();

//            classTypeParameters = new HashMap<String, GenericsType>(classTypeParameters);
//            GenericsType[] genericsTypes = createGenerics(element.getTypeParameters());

//            this.resolveGenericsHeader(genericsTypes, elements);

//            if (genericsTypes != null && genericsTypes.length > 0) {
//                classNode.setUsingGenerics(true);
//            }

  //          System.out.println("A1) createMethodParameters");
//            Parameter[] parameters = createMethodParameters(element, elements);

            int modifiers = toAstModifiers(element);

            TypeMirror varElemMirror = element.asType();
            //JavaClassTypeMirrorVisitor<GenericsType, ClassNode> tpbv = new JavaClassTypeMirrorVisitor<GenericsType, ClassNode>();
            //ClassNode fieldType = tpbv.visit(varElemMirror).getType();
            ClassNode fieldType = visitMirror(varElemMirror);
            
//            System.out.println("||| ::::::: BEFORE RESOLVE PARAM METHOD " + methodElement.getSimpleName() + ": PARAMETER[" + i + "] name=" + varElem.getSimpleName() + "; type = " + paramClassNode + "; type.name=" + paramClassNode.getName());
            resolve(fieldType, elements);
//            System.out.println("||| ::::::: AFTER RESOLVE PARAM METHOD " + methodElement.getSimpleName() + ": PARAMETER[" + i + "] name=" + varElem.getSimpleName() + "; type = " + paramClassNode + "; type.name=" + paramClassNode.getName());

            
            
            FieldNode fieldNode =  new FieldNode(element.getSimpleName().toString(), 
                    modifiers, fieldType, ownerNode, null);
            fields.add(fieldNode);

//            classTypeParameters = safeClassTypeParameters;
        }//for

        return fields;

    }//createFields()
    
    
    protected Parameter[] createMethodParameters(ExecutableElement methodElement, Elements elements) {
//        System.out.println("A2) createMethodParameters");
        List<? extends VariableElement> params = methodElement.getParameters();
        if (params == null) {
            return null;
        }
        Parameter[] parameters = new Parameter[params.size()];

        for (int i = 0; i < params.size(); i++) {
            VariableElement varElem = params.get(i);

            TypeMirror varElemMirror = varElem.asType();
            //JavaClassTypeMirrorVisitor<GenericsType, ClassNode> tpbv = new JavaClassTypeMirrorVisitor<GenericsType, ClassNode>();
            //ClassNode paramClassNode = tpbv.visit(varElemMirror).getType();
            ClassNode paramClassNode = visitMirror(varElemMirror);
//            System.out.println("||| ::::::: BEFORE RESOLVE PARAM METHOD " + methodElement.getSimpleName() + ": PARAMETER[" + i + "] name=" + varElem.getSimpleName() + "; type = " + paramClassNode + "; type.name=" + paramClassNode.getName());
            resolve(paramClassNode, elements);
//            System.out.println("||| ::::::: AFTER RESOLVE PARAM METHOD " + methodElement.getSimpleName() + ": PARAMETER[" + i + "] name=" + varElem.getSimpleName() + "; type = " + paramClassNode + "; type.name=" + paramClassNode.getName());

            String paramName = varElem.getSimpleName().toString();
//            System.out.println("METHOD " + methodElement.getSimpleName() + ": PARAMETER[" + i + "] name=" + paramName + "; type = " + paramClassNode + "; type.name=" + paramClassNode.getName());
//???                ClassNode paramClassNode = createClassNode(varElem, bounds, genericsTypes);
//            System.out.println("=======================================================");

            parameters[i] = new Parameter(paramClassNode, paramName);
  /*          if (paramName.equals("uParam")) {
                PrintUtils.printClassNode(paramClassNode);
            }
*/

        }
        return parameters;
    }

    protected ClassNode createMethodReturnType(ExecutableElement methodElement, Elements elements) {
        TypeMirror mirror = methodElement.getReturnType();
/*        JavaClassTypeMirrorVisitor<GenericsType, ClassNode> tpbv = new JavaClassTypeMirrorVisitor<GenericsType, ClassNode>();
System.out.println("RETURN TYPE for methodElement = " + methodElement.getSimpleName());        
        ClassNode returnClassNode = null;
        try {
            returnClassNode = tpbv.visit(returnMirror).getType();
        } catch(RuntimeException ex) {
            returnClassNode = ClassHelper.makeWithoutCaching("Object");
            returnClassNode.setRedirect(ClassHelper.OBJECT_TYPE);
            return returnClassNode;
        }
*/
        ClassNode resultNode = visitMirror(mirror);
        
        resolve(resultNode, elements);

        return resultNode;
    }
    protected ClassNode visitMirror(TypeMirror mirror) {
        ClassNode node = null;
        JavaClassTypeMirrorVisitor<GenericsType, ClassNode> tpbv = new JavaClassTypeMirrorVisitor<GenericsType, ClassNode>();
        
        try {
            node = tpbv.visit(mirror).getType();
        } catch(Exception ex) {
            node = ClassHelper.makeWithoutCaching("Object");
            node.setRedirect(ClassHelper.OBJECT_TYPE);
        }
        return node;
    }
    protected ClassNode createClassNode(String name, TypeElement typeElement) {
        int modifiers = 0;
        ClassNode superClass = null;
        Set<ClassNode> interfaces = new HashSet<ClassNode>();

        if (typeElement.getKind().isInterface()) {
            if (typeElement.getKind() == ElementKind.ANNOTATION_TYPE) {
                return null;
            }
            modifiers |= Opcodes.ACC_INTERFACE;

            for (TypeMirror interf : typeElement.getInterfaces()) {
                interfaces.add(new ClassNode(Utilities.getClassName(interf).toString(),
                        Opcodes.ACC_INTERFACE, null));
            }
        } else {
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

    protected ClassNode createClassNode(String name, int modifiers, ClassNode superClass, Set<ClassNode> interfaces) {
        if ("java.lang.Object".equals(name) && superClass == null) { // NOI18N
            return ClassHelper.OBJECT_TYPE;
        }
        ClassNode cn = new JavaClassNode(name, modifiers, superClass,
                (ClassNode[]) interfaces.toArray(new ClassNode[interfaces.size()]), MixinNode.EMPTY_ARRAY);
System.out.println("$$$$ === ### BEFORE setResolved resolveGenericsType(GenericsType genericsType) cn=" + cn +"; cn.name=" + cn.getName());
        this.compilationUnit.getTypeCache().setResolved(name, cn);
        return cn;
    }

    public ClassNode resolveToClass(String name) {
        System.out.println(" 1))))) ===== $ :: $ resolveToClass  name" + name);

        if ("java.lang.Object".equals(name)) {
            return ClassHelper.OBJECT_TYPE;
        }

        TypeCache typeCache = compilationUnit.getTypeCache();

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
        System.out.println(" 2))))) ===== $ :: $ resolveToClass  name" + name);

        ClassNode cn = ClassHelper.make(cls);

        typeCache.setResolved(name, cn);
        return cn;
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
                    System.out.println(idxn + s + " = " + uppers[n]);
                    System.out.println(" ------- upperBound.getName()=" + uppers[n].getName());
                    System.out.println(" ------- upperBound.upperBound.getClass()=" + uppers[n].redirect().getClass() + "; isResolved=" + uppers[n].isResolved());
                    System.out.println(" ------- upperBound.upperBound.isResolved=" + uppers[n].isResolved());
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
                System.out.println(idx + s + " = " + lower + " --- lowerBound.getName()=" + lower.getName());
                System.out.println(idx + s + " = " + lower + " --- lowerBound.lowerBound.getClass()=" + lower.redirect().getClass());
                System.out.println(idx + s + " = " + lower + " --- lowerBound.lowerBound.isResolved=" + lower.isResolved());
                System.out.println(idx + s + " _________________START PRINT LOWER BOUND GENERICS________________ ");
                printGenerics(lower);
                System.out.println(idx + s + " _________________END PRINT LOWER BOUND GENERICS________________ ");
            }

/////////            
        }
    }
}
/*
    public void complete() {
        List<JavaClassNode> nodes = new ArrayList<JavaClassNode>();
        for (JavaClassNode jn : incompleteNodes) {
            jn.setCompleteWithMethods(true);
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
*/
