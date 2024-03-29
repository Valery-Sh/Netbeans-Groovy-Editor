package org.netbeans.modules.groovy.editor.api.elements;

import groovy.lang.MetaMethod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.netbeans.modules.csl.api.ElementKind;
import org.netbeans.modules.csl.api.Modifier;
import org.netbeans.modules.groovy.editor.api.parser.GroovyParserResult;

public class AstMethodElement extends AstElement implements MethodElement {
    private List<String> parameters;
    private Modifier access = Modifier.PUBLIC;
    private Class clz;
    private MetaMethod method;
    boolean GDK;
    private String methodSignature;

    public AstMethodElement(GroovyParserResult info, ASTNode node) {
        super(info, node);
    }
    
    // We need this variant to drag the Class to which this Method belongs with us.
    // This is used in the CodeCompleter complete/document pair.
    
    public AstMethodElement(GroovyParserResult info, ASTNode node,
            Class clz, MetaMethod method, boolean GDK) {

        super(info, node);
        this.clz = clz;
        this.method = method;
        this.GDK = GDK;
    }

    public boolean isGDK() {
        return GDK;
    }

    public MetaMethod getMethod() {
        return method;
    }
    
    public Class getClz() {
        return clz;
    }

    @SuppressWarnings("unchecked")
    public List<String> getParameters() {
        if (parameters == null) {
            parameters = new ArrayList<String>();
            for (Parameter parameter : ((MethodNode)node).getParameters()) {
                parameters.add(parameter.getName());
            }
        }

        return parameters;
    }

    @Override
    public String getSignature() {
        if (methodSignature == null) {
            StringBuilder builder = new StringBuilder(super.getSignature());
            Parameter[] params = ((MethodNode) node).getParameters();
            if (params.length > 0) {
                builder.append("("); // NOI18N
                for (Parameter parameter : params) {
                    builder.append(parameter.getType().getName());
                    builder.append(","); // NOI18N
                }
                builder.setLength(builder.length() - 1);
                builder.append(")"); // NOI18N
            }
            methodSignature = builder.toString();
        }
        return methodSignature;
    }

    public boolean isDeprecated() {
        // XXX TODO: When wrapping java objects I guess these functions -could- be deprecated, right?
        return false;
    }

    @Override
    public String getName() {
        if (name == null) {
            if (node instanceof ConstructorNode) {
                name = ((ConstructorNode)node).getDeclaringClass().getNameWithoutPackage();
            } else if (node instanceof MethodNode) {
                name = ((MethodNode)node).getName();
            }

            if (name == null) {
                name = node.toString();
            }
        }

        return name;
    }

    public void setModifiers(Set<Modifier> modifiers) {
        this.modifiers = modifiers;
    }

    public void setAccess(Modifier access) {
        this.access = access;
        if (modifiers != null && modifiers.contains(Modifier.STATIC)) {
            modifiers = EnumSet.of(Modifier.STATIC, access);
        } else {
            modifiers = null;
        }
    }

    @Override
    public ElementKind getKind() {
        if (node instanceof ConstructorNode) {
            return ElementKind.CONSTRUCTOR;
        } else {
            return ElementKind.METHOD;
        }
    }

    /**
     * @todo Compute answer
     */
    public boolean isTopLevel() {
        return false;
    }

    /**
     * @todo Compute answer
     */
    public boolean isInherited() {
        return false;
    }
}
