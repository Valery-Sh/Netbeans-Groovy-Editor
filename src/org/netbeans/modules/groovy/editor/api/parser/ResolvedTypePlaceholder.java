/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.api.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.codehaus.groovy.GroovyBugError;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilationUnit;

public class ResolvedTypePlaceholder extends ClassNode {

        //String prefix;
        String className;

        public ResolvedTypePlaceholder(String name) {
            super(name, Opcodes.ACC_PUBLIC, ClassHelper.OBJECT_TYPE);
            isPrimaryNode = false;
            //this.prefix = pkg;
            this.className = name;
        }
        
        public static ClassNode createNode(String name,CompilationUnit unit) {
            CompletionCompilationUnit cu = (CompletionCompilationUnit)unit;

            ResolvedTypePlaceholder p = new ResolvedTypePlaceholder(name);
            List<ClassNode> l = new ArrayList<ClassNode>();
            if ( ! cu.typePlaceHolders.containsKey(name)) {
                cu.typePlaceHolders.put(name,l);
            } 
            if ( ! l.contains(p) ) {
                l.add(p);
            }
            
            return p;
        }
        
        @Override
        public boolean isResolved() {
            return true;
        }

        @Override
        public String getName() {
            if (redirect() != this) {
                return super.getName();
            }
            return className;
        }

        @Override
        public boolean hasPackageName() {
            if (redirect() != this) {
                return super.hasPackageName();
            }
            return className.indexOf('.') != -1;
        }

        @Override
        public String setName(String name) {
            if (redirect() != this) {
                return super.setName(name);
            } else {
                throw new GroovyBugError("ResolvedTypePlaceholder#setName should not be called");
            }
        }
    }
