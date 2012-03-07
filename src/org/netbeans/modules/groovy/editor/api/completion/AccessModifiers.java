/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.api.completion;

import java.util.HashSet;
import java.util.Set;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.netbeans.modules.groovy.editor.api.parser.Opcodes;

/**
 *
 * @author Valery
 */
public class AccessModifiers {
    public static Set<Integer> get(ClassNode enclosingNode, ClassNode node) {
        Set<Integer> mods = new HashSet<Integer>();
        if ( enclosingNode == null ) {
            mods.add(Opcodes.ACC_PUBLIC);
        } else if ( node.equals(enclosingNode)) {
            mods.add(Opcodes.ACC_PUBLIC);
            mods.add(Opcodes.ACC_PROTECTED);
            mods.add(Opcodes.ACC_PRIVATE);
        } else  if (getPackageName(enclosingNode).equals(getPackageName(node))) {
            mods.add(Opcodes.ACC_PUBLIC);
            mods.add(Opcodes.ACC_PROTECTED);
        } else {
            mods.add(Opcodes.ACC_PUBLIC);
        }
        
        return mods;
    }
    public static Set<Integer> update(Set<Integer> oldMods, ClassNode enclosingNode, ClassNode node) {
        Set<Integer> mods = new HashSet<Integer>(oldMods);
        // leav flag
        if (enclosingNode == null || ! node.equals(enclosingNode)) {
            mods.remove(Opcodes.ACC_PRIVATE);
        }

        if (enclosingNode == null || !getPackageName(enclosingNode).equals(getPackageName(node))) {
            mods.remove(Opcodes.ACC_PROTECTED);
        } else {
            mods.add(Opcodes.ACC_PROTECTED);
        }

        return mods;
    }
    
    public static boolean accept(MethodNode method, Set<Integer> mods) {
        boolean result = false;
        if ( method.isPublic() && mods.contains(Opcodes.ACC_PUBLIC) ) {
            result = true;
        } else if ( method.isProtected() && mods.contains(Opcodes.ACC_PROTECTED) ) {
            result = true;
        } else if ( method.isPrivate() && mods.contains(Opcodes.ACC_PRIVATE) ) {
            result = true;
        }
        return result;
    }
    public static String getPackageName(ClassNode node) {
        if (node.getPackageName() != null) {
            return node.getPackageName();
        }
        return ""; // NOI18N
    }
    
}
