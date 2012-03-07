
package org.netbeans.modules.groovy.editor.stc;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.codehaus.groovy.transform.GroovyASTTransformationClass;

/**
 * This will let the Groovy compiler use compile time checks in the style of Java.
 * @author <a href="mailto:blackdrag@gmx.org">Jochen "blackdrag" Theodorou</a>
 */
@java.lang.annotation.Documented
@Retention(RetentionPolicy.SOURCE)
@Target({   ElementType.METHOD,         ElementType.TYPE,
            ElementType.CONSTRUCTOR
})
@GroovyASTTransformationClass("org.netbeans.modules.groovy.editor.stc.StaticTypesTransformation_IDE")
public @interface TypeChecked_IDE {
}