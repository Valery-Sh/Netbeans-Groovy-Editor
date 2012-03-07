/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Oracle and/or its affiliates. All rights reserved.
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
 * If you wish your version of this file to be governed by only the CDDL or only
 * the GPL Version 2, indicate your decision by adding "[Contributor] elects to
 * include this software in this distribution under the [CDDL or GPL Version 2]
 * license." If you do not indicate a single choice of license, a recipient has
 * the option to distribute your version of this file under either the CDDL, the
 * GPL Version 2 or to extend the choice of license to its licensees as provided
 * above. However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is made
 * subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2008 Sun Microsystems, Inc.
 */
package org.netbeans.modules.groovy.editor.completion;

import java.util.*;
import org.netbeans.modules.groovy.editor.api.completion.CompletionItem;
import org.netbeans.modules.groovy.editor.api.completion.MethodSignature;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.element.Modifier;
import org.codehaus.groovy.ast.*;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.groovy.editor.api.GroovyIndex;
import org.netbeans.modules.groovy.editor.api.NbUtilities;
import org.netbeans.modules.groovy.editor.api.completion.AccessModifiers;
import org.netbeans.modules.groovy.editor.api.completion.FieldSignature;
import org.netbeans.modules.groovy.editor.api.elements.IndexedElement;
import org.netbeans.modules.groovy.editor.api.elements.IndexedField;
import org.netbeans.modules.groovy.editor.api.elements.IndexedMethod;
import org.netbeans.modules.groovy.editor.api.parser.GroovyModifier;
import org.netbeans.modules.groovy.editor.api.parser.JavaClassNode;
import org.netbeans.modules.parsing.spi.indexing.support.QuerySupport;

/**
 *
 * @author Petr Hejl
 */
public final class GroovyElementHandler {

    private static final Logger LOGGER = Logger.getLogger(GroovyElementHandler.class.getName());
    private final ParserResult info;

    private GroovyElementHandler(ParserResult info) {
        this.info = info;
    }

    public static GroovyElementHandler forCompilationInfo(ParserResult info) {
        return new GroovyElementHandler(info);
    }

    // FIXME ideally there should be something like nice CompletionRequest once public and stable
    // then this class could implement some common interface
    public Map<MethodSignature, ? extends CompletionItem> getMethods(GroovyIndex index, String className,
            String prefix, int anchor, boolean emphasise, Set<AccessLevel> levels, boolean nameOnly) {

        if (index == null) {
            return Collections.emptyMap();
        }

        String methodName = "";
        if (prefix != null) {
            methodName = prefix;
        }

        Set<IndexedMethod> methods;

        if (methodName.equals("")) {
            methods = index.getMethods(".*", className, QuerySupport.Kind.REGEXP);
        } else {
            methods = index.getMethods(methodName, className, QuerySupport.Kind.PREFIX);
        }

        if (methods.size() == 0) {
            LOGGER.log(Level.FINEST, "Nothing found in GroovyIndex");
            return Collections.emptyMap();
        }

        LOGGER.log(Level.FINEST, "Found this number of methods : {0} ", methods.size());

        Map<MethodSignature, CompletionItem> result = new HashMap<MethodSignature, CompletionItem>();
        for (IndexedMethod indexedMethod : methods) {
            LOGGER.log(Level.FINEST, "method from index : {0} ", indexedMethod.getName());

            if (!accept(levels, indexedMethod)) {
                continue;
            }

            // FIXME move sig to method item
            List<String> params = indexedMethod.getParameters();
            StringBuffer sb = new StringBuffer();

            for (String string : params) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(NbUtilities.stripPackage(string));
            }

            // FIXME return type
            result.put(getSignature(indexedMethod), CompletionItem.forJavaMethod(className, indexedMethod.getName(), sb.toString(), indexedMethod.getReturnType(),
                    org.netbeans.modules.groovy.editor.java.Utilities.gsfModifiersToModel(indexedMethod.getModifiers(), Modifier.PUBLIC), anchor, emphasise, nameOnly));
        }

        return result;
    }

    protected Map<String, ClassNode> getClassGenerics(ClassNode node) {
        Map<String, ClassNode> result = new HashMap<String, ClassNode>();
        if (!node.isUsingGenerics()) {
            return result;
        }
        GenericsType[] originTypes = node.getGenericsTypes();
        ClassNode nd = node.redirect();
        GenericsType[] gtypes = nd.getGenericsTypes();
        if (gtypes == null) {
            return result;
        }
        for (int i = 0; i < gtypes.length; i++) {
            System.out.println("1.3) wwwwwww GroovyElementHandler param.gtype.name=" + gtypes[i].getName() + "; gtype.getType.name=" + gtypes[i].getType().getName());
            result.put(gtypes[i].getName(), originTypes[i].getType());
        }

        return result;
    }

    private String genericTypeAsString(ClassNode forType,GenericsType genericsType, Map<String, ClassNode> generics) {
        String ret = NbUtilities.stripPackage(genericsType.getName());
        System.out.println("XXXX genericTypeAsString START ret = " + ret);

        if (genericsType.isPlaceholder()) {
            ClassNode gtype = generics.get(ret);
            System.out.println("XXXX genericTypeAsString genericsType.isPlaceholder() ret = " + ret);
            if (gtype != null) {
                //ret = typeToString(genericsType.getType(),generics);
                ret = getTypeName(gtype);
                System.out.println("XXXX genericTypeAsString genericsType.isPlaceholder() gtype != null ret = " + ret);
            }
        } else if (genericsType.getUpperBounds() != null) {
            ret += " extends ";
            for (int i = 0; i < genericsType.getUpperBounds().length; i++) {
                ClassNode classNode = genericsType.getUpperBounds()[i];
                if (classNode == forType) {
                    ret += getTypeName(classNode);
                } else {
                System.out.println("XXXX 1 genericsType.getUpperBound = " + classNode);
                System.out.println("XXXX 2 genericsType.getUpperBound = " + classNode.redirect());
                    
                    ret += typeToString(classNode, generics);
                }
                if (i + 1 < genericsType.getUpperBounds().length) {
                    ret += " & ";
                }
            }
        } else if (genericsType.getLowerBound() != null) {
            ClassNode classNode = genericsType.getLowerBound();
            if (classNode == forType) {
                ret += " super " + getTypeName(classNode);
            } else {
                ret += " super " + classNode;
            }
        } else if ( genericsType.getType().isUsingGenerics() ) {
                System.out.println("XXXX 3 genericsType.getUpperBound = " + genericsType.getType());
                System.out.println("XXXX 4 genericsType.getUpperBound = " + genericsType.getType().redirect());
                ClassNode classNode = genericsType.getType();
                if (classNode == forType) {
                    ret = classNode.getName();
                } else {
                    ret = typeToString(classNode, generics);
                }
            
        }
        return ret;
    }

    public String typeToString(ClassNode type, Map<String, ClassNode> generics) {

        String ret = getTypeName(type);
        System.out.println("XXXX typeToString  ret = " + ret);

        if (!type.isUsingGenerics()) {
            System.out.println("XXXX typeToString  ! type.isUsingGenerics() ret = " + ret);

            return ret;
        }
        GenericsType[] genericsTypes = type.getGenericsTypes();
        if (genericsTypes == null || genericsTypes.length == 0) {
            System.out.println("XXXX typeToString  genericsTypes == null");
            return ret;
        }
        System.out.println("XXXX typeToString  genericsTypes != null");

        if (type.isGenericsPlaceHolder()) {
            System.out.println("XXXX typeToString  type.isGenericsPlaceHolder() ret = " + ret);

            GenericsType gtype = genericsTypes[0];
            ClassNode gnode = generics.get(gtype.getName());
            System.out.println("XXXX typeToString  type.isGenericsPlaceHolder() gnode != null gnode = " + gnode);

            if (gnode != null) {

                return getTypeName(gnode);
            } else {
                return gtype.getName();
            }
        }

        if (genericsTypes != null) {
            ret += " <";
            for (int i = 0; i < genericsTypes.length; i++) {
                if (i != 0) {
                    ret += ", ";
                }
                GenericsType genericsType = genericsTypes[i];
        System.out.println("XXXX typeToString  genericsTypes != null genericsType.name=" + genericsType.getName());
                
                ret += genericTypeAsString(type,genericsType, generics);
            }
            ret += ">";
        }
//        if (type.isRedirectNode() ) {
//            ret += " -> " + type.redirect().toString();
//        }
        return ret;
    }

//    public Map<MethodSignature, ? extends CompletionItem> getMethods(ClassNode node, String className,
//            String prefix, int anchor, boolean emphasise, Set<AccessLevel> levels, boolean nameOnly) {
    public Map<MethodSignature, ? extends CompletionItem> getMethods(ClassNode source, ClassNode node,
            String prefix, int anchor, boolean emphasise, Set<Integer> levels, boolean nameOnly) {

//        System.out.println("1) wwwwwww GroovyElementHandler node=" + node);

        if (node == null) {
            return Collections.emptyMap();
        }
        String className = node.getName();
        
        List<MethodNode> methodNodes = null;
        if (node.isResolved()) {
            methodNodes = node.getMethods();
        } else if (node.redirect() instanceof JavaClassNode) {
            methodNodes = node.redirect().getMethods();
        }
        List<MethodNode> methods = new ArrayList<MethodNode>();

        for (MethodNode m : methodNodes) {
            if (AccessModifiers.accept(m, levels)) {
                methods.add(m);
            }
        }

        String methodName = "";
        if (prefix != null) {
            methodName = prefix;
        }

        //Set<IndexedMethod> methods;

        /*
         * if (methodName.equals("")) { methods = index.getMethods(".*",
         * className, QuerySupport.Kind.REGEXP); } else { methods =
         * index.getMethods(methodName, className, QuerySupport.Kind.PREFIX); }
         */
        if (methods.size() == 0) {
            LOGGER.log(Level.FINEST, "Nothing found in GroovyIndex");
            return Collections.emptyMap();
        }


        Map<String, ClassNode> classGenerics = getClassGenerics(node);
        Map<MethodSignature, CompletionItem> result = new HashMap<MethodSignature, CompletionItem>();

        for (MethodNode method : methods) {
            GenericsType[] gt = method.getGenericsTypes();
            int s = gt != null ? gt.length : 0;
//            System.out.println("2) wwwwwww GroovyElementHandler method=" + method.getName() + "; method.getGenericsTypes=" + gt + "; generics length=" + s);
            System.out.println("XXXX getMethods  node=" + node + "; method.name=" + method.getName());
            /*
             * if (!accept(levels, indexedMethod)) { continue; }
             */
            // FIXME move sig to method item
            Parameter[] methodParams = method.getParameters();
            //List<String> params = indexedMethod.getParameters();
            StringBuffer sb = new StringBuffer();
            for (Parameter p : methodParams) {
                System.out.println("XXXX getMethods  node=" + node + "; method.name=" + method.getName() + "; param.name=" + p.getName());

                String name = typeToString(p.getType(), classGenerics);
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(name);
                sb.append(" ").append(p.getName());

            }
            //ElemSet
//            method.getModifiers();
            String returnType = typeToString(method.getReturnType(),classGenerics);
            // FIXME return type
            result.put(getMethodSignature(method, methodParams),
                    CompletionItem.forJavaMethod(className, method.getName(), sb.toString(), returnType,
                    GroovyModifier.toModelModifiers(method), anchor, emphasise, nameOnly));

        }

        return result;
    }

    protected String getTypeName(ClassNode node) {
        String name = node.getNameWithoutPackage();
        if (node.isArray()) {
            name = node.getComponentType().getNameWithoutPackage() + "[]";
        }
        return name;
    }
    private FieldSignature getFieldSignature(FieldNode field) {
        return new FieldSignature(field.getName());
    }

    protected MethodSignature getMethodSignature(MethodNode method, Parameter[] params) {

        //String[] parameters = method.getParameters().toArray(new String[method.getParameters().size()]);
        String[] result = new String[params.length];
        for (int i = 0; i < params.length; i++) {
            result[i] = params[i].getType().getName();
        }
        return new MethodSignature(method.getName(), result);
    }

    public Map<FieldSignature, ? extends CompletionItem> getFields(GroovyIndex index, String className,
            String prefix, int anchor, boolean emphasise) {

        if (index == null) {
            return Collections.emptyMap();
        }
        System.out.println(" aaaaaaaaaaaaaaa prefix=" + prefix + "; className = " + className);
        String methodName = "";
        if (prefix != null) {
            methodName = prefix;
        }

        Set<IndexedField> fields;

        if (methodName.equals("")) {
            fields = index.getFields(".*", className, QuerySupport.Kind.REGEXP);
        } else {
            fields = index.getFields(methodName, className, QuerySupport.Kind.PREFIX);
        }

        if (fields.size() == 0) {
            LOGGER.log(Level.FINEST, "Nothing found in GroovyIndex");
            return Collections.emptyMap();
        }

        LOGGER.log(Level.FINEST, "Found this number of fields : {0} ", fields.size());

        Map<FieldSignature, CompletionItem.JavaFieldItem> result = new HashMap<FieldSignature, CompletionItem.JavaFieldItem>();
        for (IndexedField indexedField : fields) {
            LOGGER.log(Level.FINEST, "field from index : {0} ", indexedField.getName());
            System.out.println(" aaaaaaaaaaaaaaa indexedField.name=" + indexedField.getName());
            //System.out.println("INDEX: " + indexedField.getName() + " " + indexedField.getType() + " " + org.netbeans.modules.groovy.editor.java.Utilities.gsfModifiersToModel(indexedField.getModifiers(), Modifier.PRIVATE));
            result.put(getSignature(indexedField), new CompletionItem.JavaFieldItem(
                    className, indexedField.getName(), null, org.netbeans.modules.groovy.editor.java.Utilities.gsfModifiersToModel(indexedField.getModifiers(), Modifier.PRIVATE), anchor, emphasise));
        }

        return result;
    }

    public Map<FieldSignature, ? extends CompletionItem> getFields(ClassNode source, ClassNode node,
            String prefix, int anchor, boolean emphasise) {
        
        if (node == null) {
            return Collections.emptyMap();
        }
        String className = node.getName();
        List<FieldNode> fieldNodes = null;
        if (node.isResolved()) {
            fieldNodes = node.getFields();
        } else if (node.redirect() instanceof JavaClassNode) {
            fieldNodes = node.redirect().getFields();
        }
        List<FieldNode> fields = new ArrayList<FieldNode>();

        for (FieldNode m : fieldNodes) {
//            if (AccessModifiers.accept(m, levels)) {
                fields.add(m);
//            }
        }

        String methodName = "";
        if (prefix != null) {
            methodName = prefix;
        }

        if (fields.size() == 0) {
            LOGGER.log(Level.FINEST, "Nothing found in GroovyIndex");
            return Collections.emptyMap();
        }

        LOGGER.log(Level.FINEST, "Found this number of fields : {0} ", fields.size());

        Map<String, ClassNode> classGenerics = getClassGenerics(node);
        Map<FieldSignature, CompletionItem.JavaFieldItem> result = new HashMap<FieldSignature, CompletionItem.JavaFieldItem>();
        
        for (FieldNode field : fields) {
            //System.out.println("INDEX: " + indexedField.getName() + " " + indexedField.getType() + " " + org.netbeans.modules.groovy.editor.java.Utilities.gsfModifiersToModel(indexedField.getModifiers(), Modifier.PRIVATE));
            result.put(getFieldSignature(field), new CompletionItem.JavaFieldItem(
                    className, field.getName(), null, GroovyModifier.toModelModifiers(field,Modifier.PRIVATE), anchor, emphasise));
            
            //result.put(getMethodSignature(method, methodParams),
            //        CompletionItem.forJavaMethod(className, method.getName(), sb.toString(), returnType,
            //        GroovyModifier.toModelModifiers(method), anchor, emphasise, nameOnly));
            
        }

        return result;
    }
    
    private MethodSignature getSignature(IndexedMethod method) {
        String[] parameters = method.getParameters().toArray(new String[method.getParameters().size()]);
        return new MethodSignature(method.getName(), parameters);
    }

    private FieldSignature getSignature(IndexedField field) {
        return new FieldSignature(field.getName());
    }

    private boolean accept(Set<AccessLevel> levels, IndexedElement element) {
        for (AccessLevel level : levels) {
            if (level.accept(element.getModifiers())) {
                return true;
            }
        }

        return false;
    }
}
