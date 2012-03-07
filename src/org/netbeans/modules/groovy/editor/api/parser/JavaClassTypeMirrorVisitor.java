/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.api.parser;

import java.util.List;
import javax.lang.model.type.*;
import javax.lang.model.util.SimpleTypeVisitor6;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.syntax.SyntaxException;
import org.openide.util.Exceptions;

/**
 *
 * @author Valery
 */
public class JavaClassTypeMirrorVisitor<R extends GenericsType, P extends ClassNode> extends SimpleTypeVisitor6<R, P> {

    protected String getNameWithoutGenerics(TypeMirror t) {
        String name = t.toString();
        int i = name.indexOf('<');
        if (i >= 0) {
            name = name.substring(0, i);
        }
        return name;
    }

    /**
     * {@inheritDoc} This implementation calls {@code defaultAction}.
     *
     * @param t {@inheritDoc}
     * @param p {@inheritDoc}
     * @return  the result of {@code defaultAction}
     */
    @Override
    public R visitDeclared(DeclaredType t, P p) {

        ClassNode bound = ClassHelper.makeWithoutCaching(getNameWithoutGenerics(t));

        List<? extends TypeMirror> typeArgs = t.getTypeArguments();
        GenericsType[] gtTypes = null;
        if (typeArgs != null && !typeArgs.isEmpty()) {
            gtTypes = new GenericsType[typeArgs.size()];
            for (int i = 0; i < typeArgs.size(); i++) {
                TypeMirror tm = typeArgs.get(i);
                GenericsType typeArgGenerics = visit(tm);
                gtTypes[i] = typeArgGenerics;
            }
            bound.setGenericsTypes(gtTypes);
            
        }
        GenericsType r = new GenericsType(bound);
        return (R) r;
        //return defaultAction(t, p);
    }

    /**
     * {@inheritDoc} This implementation calls {@code defaultAction}.
     *
     * @param t {@inheritDoc}
     * @param p {@inheritDoc}
     * @return  the result of {@code defaultAction}
     */
    @Override
    public R visitTypeVariable(TypeVariable t, P p) {
//        System.out.println(":::::::::::: visitTypeVariable TypeVariable=" + t);
        GenericsType gt = new GenericsType(ClassHelper.makeWithoutCaching(t.toString()));
        TypeMirror upperBound = t.getUpperBound();
/*        if ( upperBound != null ) {
            GenericsType gtu = (R)visit(upperBound);
            gt.getType().setGenericsTypes(new GenericsType[] {gtu});
        }
*/
        return (R)gt;
    }

    /**
     *
     * @param t {@inheritDoc}
     * @param p {@inheritDoc}
     * @return  the result of {@code defaultAction}
     */
    @Override
    public R visitWildcard(WildcardType t, P p) {

        ClassNode node = ClassHelper.makeWithoutCaching("?");
        TypeMirror extendsBound = t.getExtendsBound();
        TypeMirror superBound = t.getSuperBound();
        
        GenericsType gt = null;
        if (extendsBound != null) {
            gt = visit(extendsBound);
            gt = new GenericsType(node, new ClassNode[]{gt.getType()}, null);
        }
        if (superBound != null) {
            gt = visit(superBound);
            gt = new GenericsType(node, null, gt.getType());

        }
        if ( extendsBound == null && superBound == null ) {
            ClassNode[] b = new ClassNode[] {ClassHelper.makeWithoutCaching("java.lang.Object")};
            gt = new GenericsType(node,b,null);
        }
        gt.setWildcard(true);

        return (R) gt;
        //return defaultAction(t, p);
    }
    @Override
    public R visitArray(ArrayType t, P p) {
        TypeMirror componentTypeMirror = t.getComponentType();
        GenericsType gtComponentType = visit(componentTypeMirror);
        ClassNode componentType = gtComponentType.getType();
        ClassNode array = componentType.makeArray();
        return (R) new GenericsType(array);
    }    
    @Override
    public R visitPrimitive(PrimitiveType t, P p) {
        return (R) new GenericsType(ClassHelper.make(t.toString()));
    }    
    @Override
    public R visitNoType(NoType t, P p) {
        return (R) new GenericsType(ClassHelper.make("void"));
    }    
    @Override
    public R visitError(ErrorType t, P p)  {
        throw new RuntimeException("visitorError ErrorType");
        //return (R) new GenericsType(ClassHelper.make("<_ERROR_TYPE_>"));
    }    
/*    public class TypeMirrorVisitorException extends RuntimeException {
    
    }
*/
}
