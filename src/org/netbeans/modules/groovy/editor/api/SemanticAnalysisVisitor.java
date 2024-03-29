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
package org.netbeans.modules.groovy.editor.api;

import java.util.*;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.control.SourceUnit;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.csl.api.ColoringAttributes;
import org.netbeans.modules.csl.api.OffsetRange;

/**
 *
 * @author Martin Adamek
 */
public class SemanticAnalysisVisitor extends ClassCodeVisitorSupport {

    private final ModuleNode root;
    private final BaseDocument doc;
    private final Map<OffsetRange, Set<ColoringAttributes>> highlights;

    public SemanticAnalysisVisitor(ModuleNode root, BaseDocument document) {
        this.root = root;
        this.doc = document;
        this.highlights = new HashMap<OffsetRange, Set<ColoringAttributes>>();
    }

    public Map<OffsetRange, Set<ColoringAttributes>> annotate() {
        highlights.clear();

        for (Object object : root.getClasses()) {
            visitClass((ClassNode) object);
        }

        for (Object object : root.getMethods()) {
            visitMethod((MethodNode) object);
        }

        visitBlockStatement(root.getStatementBlock());

        return highlights;
    }

    @Override
    protected SourceUnit getSourceUnit() {
        return root.getContext();
    }

    @Override
    public void visitField(FieldNode node) {
        if (node.getLineNumber() > 0) {
            OffsetRange range = AstUtilities.getRange(node, doc);
            EnumSet<ColoringAttributes> attributes = EnumSet.of(ColoringAttributes.FIELD);

            if (node.isStatic()) {
                attributes.add(ColoringAttributes.STATIC);
            }
            highlights.put(range, attributes);
        }
        super.visitField(node);
    }

    @Override
    public void visitConstructor(ConstructorNode node) {
        if (node.getLineNumber() > 0) {
            // Beware, a ConstructorNode is a MethodNode as well, (see below)
            // but we have to catch the Constructors first.
            OffsetRange range = AstUtilities.getRange(node, doc);
            highlights.put(range, ColoringAttributes.CONSTRUCTOR_SET);
        }
        super.visitConstructor(node);
    }

    @Override
    public void visitMethod(MethodNode node) {
        if (node.getLineNumber() > 0) {
            OffsetRange range = AstUtilities.getRange(node, doc);
            EnumSet<ColoringAttributes> attributes = EnumSet.of(ColoringAttributes.METHOD);

            if (node.isStatic()) {
                attributes.add(ColoringAttributes.STATIC);
            }
            highlights.put(range, attributes);
        }
        super.visitMethod(node);
    }

    @Override
    public void visitPropertyExpression(PropertyExpression expr) {
        // FIXME: Houston, we have a problem:
        // the PropertyExpression comes with no line/column information
        // this is supposed to be fixed in Groovy. See:
        // http://jira.codehaus.org/browse/GROOVY-2575
        if ( (expr.getObjectExpression() instanceof ClassExpression) &&
             (expr.getProperty() instanceof ConstantExpression ) ) {
                ConstantExpression constExpr = (ConstantExpression) expr.getProperty();
                //constExpr.
                if (constExpr.getLineNumber() > 0) {
                    OffsetRange range = AstUtilities.getRange(constExpr, doc);
                    EnumSet<ColoringAttributes> attributes = ColoringAttributes.STATIC_FIELD_SET;
                    highlights.put(range, attributes);
                }
        }
        super.visitPropertyExpression(expr);
    }

    @Override
    public void visitClass(ClassNode node) {
        if (node.getLineNumber() > 0) {
            OffsetRange range = AstUtilities.getRange(node, doc);
            highlights.put(range, ColoringAttributes.CLASS_SET);
        }

        super.visitClass(node);
    }

    @Override
    public void visitAnnotations(AnnotatedNode node) {
        List<AnnotationNode> annotations = node.getAnnotations();
        if (annotations.isEmpty()) {
            return;
        }
        for (AnnotationNode an : annotations) {
            if (an.getLineNumber() > 0) {
                OffsetRange range = AstUtilities.getRange(an, doc);
                EnumSet<ColoringAttributes> attributes = EnumSet.of(ColoringAttributes.ANNOTATION_TYPE);
                highlights.put(range, attributes);
//                highlights.put(range, ColoringAttributes.CUSTOM1_SET);
            }
        }

        //super.visitAnnotations(node);
    }

    @Override
    public void visitVariableExpression(VariableExpression node) {
        Variable var = node.getAccessedVariable();

        if (var instanceof FieldNode) {
            if (node.getLineNumber() > 0) {
                OffsetRange range = AstUtilities.getRange(node, doc);
                highlights.put(range, ColoringAttributes.FIELD_SET);
            }
        }
        super.visitVariableExpression(node);
    }
}
