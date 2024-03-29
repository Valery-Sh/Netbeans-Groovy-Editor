/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
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
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2007 Sun
 * Microsystems, Inc. All Rights Reserved.
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
 */

package org.netbeans.modules.groovy.editor.api;
//import java.util.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Scanner;
import javax.swing.text.BadLocationException;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.stmt.Statement;
import org.netbeans.editor.BaseDocument;
import org.openide.util.Exceptions;

/**
 * This represents a path in a Groovy AST.
 *
 * @author Tor Norbye
 * @author Martin Adamek
 */

public class  AstPath implements Iterable<ASTNode> {
    
    
    private ArrayList<ASTNode> path = new ArrayList<ASTNode>(30);

    private int lineNumber = -1;

    private int columnNumber = -1;

    public AstPath() {
        super();
    }

    public AstPath(ASTNode root, int caretOffset, BaseDocument document) {
        try {
            // make sure offset is not higher than document length, see #138353
            int length = document.getLength();
            int offset = length == 0 ? 0 : caretOffset + 1;
            if (length > 0 && offset >= length) {
                offset = length - 1;
            }
            Scanner scanner = new Scanner(document.getText(0, offset));
            int line = 0;
            String lineText = "";
            while (scanner.hasNextLine()) {
                lineText = scanner.nextLine();
                line++;
            }
            int column = lineText.length();

            this.lineNumber = line;
            this.columnNumber = column;

            findPathTo(root, line, column);
        } catch (BadLocationException ble) {
            Exceptions.printStackTrace(ble);
        }
    }

    /**
     * Initialize a node path to the given caretOffset
     */
    public AstPath(ASTNode root, int line, int column) {
        this.lineNumber = line;
        this.columnNumber = column;

        findPathTo(root, line, column);
    }

    /**
     * Find the path to the given node in the AST
     */
    @SuppressWarnings("unchecked")
    public AstPath(ASTNode node, ASTNode target) {
        if (!find(node, target)) {
            path.clear();
        } else {
            // Reverse the list such that node is on top
            // When I get time rewrite the find method to build the list that way in the first place
            Collections.reverse(path);
        }
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getColumnNumber() {
        return columnNumber;
    }

    public void descend(ASTNode node) {
        path.add(node);
    }

    public void ascend() {
        path.remove(path.size() - 1);
    }

    /**
     * Find the position closest to the given offset in the AST. Place the path from the leaf up to the path in the
     * passed in path list.
     */
    @SuppressWarnings("unchecked")
    private ASTNode findPathTo(ASTNode node, int line, int column) {
        
        assert line >=0 : "line number was negative: " + line;
        assert column >=0 : "column number was negative: " + column;
        assert node != null : "ASTNode should not be null";
        assert node instanceof ModuleNode : "ASTNode must be a ModuleNode";
        
        path.addAll(find(node, line, column));

        // in scripts ClassNode is not in path, let's add it
        // find class that has same name as the file
        if (path.isEmpty() || !(path.get(0) instanceof ClassNode)) {
            ModuleNode moduleNode = (ModuleNode) node;
            String name = moduleNode.getContext().getName();
            int index = name.lastIndexOf(".groovy"); // NOI18N
            if (index != -1) {
                name = name.substring(0, index);
            }
            index = name.lastIndexOf('.');
            if (index != -1) {
                name = name.substring(index + 1);
            }
            for (Object object : moduleNode.getClasses()) {
                ClassNode classNode = (ClassNode) object;
                if (name.equals(classNode.getNameWithoutPackage())) {
                    path.add(0, classNode);
                    break;
                }
            }
        }

        // let's fix script class - run method
        // FIXME this should be more accurate - package
        // and imports are not in the method ;)
        if (!path.isEmpty() && (path.get(0) instanceof ClassNode)) {
            ClassNode clazz = (ClassNode) path.get(0);
            if (clazz.isScript()
                    && (path.size() == 1 || path.get(1) instanceof Expression || path.get(1) instanceof Statement)) {

                MethodNode method = clazz.getMethod("run", new Parameter[]{}); // NOI18N
                if (method != null) {
                    if (method.getCode() != null && (path.size() <= 1 || method.getCode() != path.get(1))) {
                        path.add(1, method.getCode());
                    }
                    path.add(1, method);
                }
            }
        }

        path.add(0, node);

        ASTNode result = path.get(path.size() - 1);

        return result;
    }

    @SuppressWarnings("unchecked")
    private List<ASTNode> find(ASTNode node, int line, int column) {
        
        assert line >=0 : "line number was negative: " + line;
        assert column >=0 : "column number was negative: " + column;
        assert node != null : "ASTNode should not be null";
        assert node instanceof ModuleNode : "ASTNode must be a ModuleNode";
        
        ModuleNode moduleNode = (ModuleNode) node;
        PathFinderVisitor pathFinder = new PathFinderVisitor(moduleNode.getContext(), line, column);
//System.out.println("PATH: line=" + line + "; column=" + column);
//pathFinder.visitImports(moduleNode);
        
        for (Object object : moduleNode.getClasses()) {
            pathFinder.visitClass((ClassNode)object);
        }

        for (Object object : moduleNode.getMethods()) {
            pathFinder.visitMethod((MethodNode)object);
        }

        return pathFinder.getPath();
    }

    /**
     * Find the path to the given node in the AST
     */
    @SuppressWarnings("unchecked")
    public boolean find(ASTNode node, ASTNode target) {
        if (node == target) {
            return true;
        }

        List<ASTNode> children = AstUtilities.children(node);

        for (ASTNode child : children) {
            boolean found = find(child, target);

            if (found) {
                path.add(child);

                return found;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Path(");
        sb.append(path.size());
        sb.append(")=[");

        for (ASTNode n : path) {
            String name = n.getClass().getName();
            name = name.substring(name.lastIndexOf('.') + 1);
            sb.append(name);
            sb.append("\n");
        }

        sb.append("]");

        return sb.toString();
    }

    public ASTNode leaf() {
        if (path.size() == 0) {
            return null;
        } else {
System.out.println("ASTPath path.get(path.size() - 1) = " + path.get(path.size() - 1).getClass());                    
            return path.get(path.size() - 1);
        }
    }

    public ASTNode leafParent() {
        if (path.size() < 2) {
            return null;
        } else {
            return path.get(path.size() - 2);
        }
    }

    public ASTNode leafGrandParent() {
        if (path.size() < 3) {
            return null;
        } else {
            return path.get(path.size() - 3);
        }
    }

    public ASTNode root() {
        if (path.size() == 0) {
            return null;
        } else {
            return path.get(0);
        }
    }

    /** Return an iterator that returns the elements from the leaf back up to the root */
    public Iterator<ASTNode> iterator() {
        return new LeafToRootIterator(path);
    }

    /** REturn an iterator that starts at the root and walks down to the leaf */
    public ListIterator<ASTNode> rootToLeaf() {
        return path.listIterator();
    }

    /** Return an iterator that walks from the leaf back up to the root */
    public ListIterator<ASTNode> leafToRoot() {
        return new LeafToRootIterator(path);
    }

    private static class LeafToRootIterator implements ListIterator<ASTNode> {
        private final ListIterator<ASTNode> it;

        private LeafToRootIterator(ArrayList<ASTNode> path) {
            it = path.listIterator(path.size());
        }

        public boolean hasNext() {
            return it.hasPrevious();
        }

        public ASTNode next() {
            return it.previous();
        }

        public boolean hasPrevious() {
            return it.hasNext();
        }

        public ASTNode previous() {
            return it.next();
        }

        public int nextIndex() {
            return it.previousIndex();
        }

        public int previousIndex() {
            return it.nextIndex();
        }

        public void remove() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void set(ASTNode arg0) {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        public void add(ASTNode arg0) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

}
