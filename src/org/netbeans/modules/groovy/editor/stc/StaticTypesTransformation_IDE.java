/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.stc;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.codehaus.groovy.transform.StaticTypesTransformation;
import org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor;

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class StaticTypesTransformation_IDE extends StaticTypesTransformation{
    @Override
    protected StaticTypeCheckingVisitor newVisitor(SourceUnit unit, ClassNode node) {
        return new StaticTypeCheckingVisitor(unit, node);
    }
    
}
