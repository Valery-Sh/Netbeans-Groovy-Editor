package org.netbeans.modules.groovy.editor.api.parser;

import org.codehaus.groovy.GroovyBugError;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.FieldExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.codehaus.groovy.vmplugin.VMPluginFactory;

import java.lang.reflect.Array;
import java.util.*;

import groovy.lang.GroovyObject;
import org.codehaus.groovy.ast.*;

/**
 * Represents a class in the AST.<br/>
 * A ClassNode should be created using the methods in ClassHelper.
 * This ClassNode may be used to represent a class declaration or
 * any other type. This class uses a proxy mechanism allowing to
 * create a class for a plain name at AST creation time. In another
 * phase of the compiler the real ClassNode for the plain name may be
 * found. To avoid the need of exchanging this ClassNode with an
 * instance of the correct ClassNode the correct ClassNode is set as
 * redirect. Most method calls are then redirected to that ClassNode.
 * <br>
 * There are three types of ClassNodes:
 * <br>
 * <ol>
 * <li> Primary ClassNodes:<br>
 * A primary ClassNode is one where we have a source representation
 * which is to be compiled by Groovy and which we have an AST for. 
 * The groovy compiler will output one class for each such ClassNode
 * that passes through AsmBytecodeGenerator... not more, not less.
 * That means for example Closures become such ClassNodes too at
 * some point. 
 * 
 * <li> ClassNodes create through different sources (typically created
 * from a java.lang.reflect.Class object):<br>
 * The compiler will not output classes from these, the methods
 * usually do not contain bodies. These kind of ClassNodes will be
 * used in different checks, but not checks that work on the method 
 * bodies. For example if such a ClassNode is a super class to a primary
 * ClassNode, then the abstract method test and others will be done 
 * with data based on these. Theoretically it is also possible to mix both 
 * (1 and 2) kind of classes in a hierarchy, but this probably works only
 *  in the newest Groovy versions. Such ClassNodes normally have to
 *  isResolved() returning true without having a redirect.In the Groovy 
 *  compiler the only version of this, that exists, is a ClassNode created 
 *  through a Class instance
 *
 * <li> Labels:<br>
 * ClassNodes created through ClassHelper.makeWithoutCaching. They 
 * are place holders, its redirect points to the real structure, which can
 * be a label too, but following all redirects it should end with a ClassNode
 * from one of the other two categories. If ResolveVisitor finds such a 
 * node, it tries to set the redirects. Any such label created after 
 * ResolveVisitor has done its work needs to have a redirect pointing to 
 * case 1 or 2. If not the compiler may react strange... this can be considered 
 * as a kind of dangling pointer. 
 * <br>
 * <b>Note:</b> the redirect mechanism is only allowed for classes 
 * that are not primary ClassNodes. Typically this is done for classes
 * created by name only.  The redirect itself can be any type of ClassNode.
 * <br>
 * To describe generic type signature see {@link #getGenericsTypes()} and
 * {@link #setGenericsTypes(GenericsType[])}. These methods are not proxied,
 * they describe the type signature used at the point of declaration or the
 * type signatures provided by the class. If the type signatures provided
 * by the class are needed, then a call to {@link #redirect()} will help.
 *
 * @see org.codehaus.groovy.ast.ClassHelper
 *
 * @author <a href="mailto:james@coredevelopers.net">James Strachan</a>
 * @author Jochen Theodorou
 * @version $Revision$
 */
public class JavaClassNode extends ClassNode {
    protected boolean resolveFromCache;
    protected boolean complete;
    
    /**
     * Creates a ClassNode from a real class. The resulting
     * ClassNode will not be a primary ClassNode.
     */
    public JavaClassNode(Class c) {
        super(c);
    }




    /**
     * @param name       is the full name of the class
     * @param modifiers  the modifiers,
     * @param superClass the base class name - use "java.lang.Object" if no direct
     *                   base class
     * @see org.objectweb.asm.Opcodes
     */
    public JavaClassNode(String name, int modifiers, ClassNode superClass) {
        super(name, modifiers, superClass, EMPTY_ARRAY, MixinNode.EMPTY_ARRAY);
    }

    /**
     * @param name       is the full name of the class
     * @param modifiers  the modifiers,
     * @param superClass the base class name - use "java.lang.Object" if no direct
     *                   base class
     * @param interfaces the interfaces for this class
     * @param mixins     the mixins for this class
     * @see org.objectweb.asm.Opcodes
     */
    public JavaClassNode(String name, int modifiers, ClassNode superClass, ClassNode[] interfaces, MixinNode[] mixins) {
        super(name, modifiers, superClass, interfaces, mixins);
    }

    
    public List<JavaClassNode> complete() {
        return null;
    }

/*    public boolean isResolveFromCache() {
        return resolveFromCache;
    }

    public void setResolveFromCache(boolean resolveFromCache) {
        this.resolveFromCache = resolveFromCache;
    }
*/
    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }
    

}
