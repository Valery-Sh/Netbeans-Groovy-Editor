/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 *
 * Contributor(s):
 *
 * Portions Copyrighted 2008 Sun Microsystems, Inc.
 */

package org.netbeans.modules.groovy.editor.api;

import java.util.Collections;
import java.util.Set;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.groovy.editor.completion.TypeInferenceVisitor;

/**
 *
 * @author Petr Hejl
 */
public class GroovyTypeAnalyzer {

    private final BaseDocument document;

    public GroovyTypeAnalyzer(BaseDocument document) {
        this.document = document;
    }
    public Set<ClassNode> getTypes(AstPath path, int astOffset) {
        ASTNode closest = path.leaf();
System.out.println("ASTPath .leaf class = " + closest.getClass());        
        if (closest instanceof VariableExpression) {
            ModuleNode moduleNode = (ModuleNode) path.root();
            TypeInferenceVisitor typeVisitor = new TypeInferenceVisitor(moduleNode.getContext(),
                    path, document, astOffset);
            typeVisitor.collect();
            ClassNode guessedType = typeVisitor.getGuessedType();
            if (guessedType != null) {
                return Collections.singleton(guessedType);
            }
        }

        return Collections.emptySet();
    }
/*    public Set<ClassNode> getTypes(AstPath path, int astOffset) {
        ASTNode closest = path.leaf();
        if (closest instanceof VariableExpression) {
            VariableExpression ve = (VariableExpression)closest;
System.out.println("GROOVY GroovyTypeAnalyzer.getTypes var expr=" + ve.getText() +"; type=" + ve.getType().getName());            
            //// My 
            //if ( "java.lang.Object".equals(ve.getType().getName()) 
            //    && ! ve.isDynamicTyped() ) {
            //}
            if ( true ) {
                return Collections.singleton(ve.getType());
            }
            //////////// end My
            ModuleNode moduleNode = (ModuleNode) path.root();
            TypeInferenceVisitor typeVisitor = new TypeInferenceVisitor(moduleNode.getContext(),
                    path, document, astOffset);
            typeVisitor.collect();
            ClassNode guessedType = typeVisitor.getGuessedType();
            if (guessedType != null) {
System.out.println("GROOVY GroovyTypeAnalyzer.getTypes var expr=" + ve.getText() +"; guessedType=" + guessedType.getName());                            
                return Collections.singleton(guessedType);
            }
        }

        return Collections.emptySet();
    }
    */
}
