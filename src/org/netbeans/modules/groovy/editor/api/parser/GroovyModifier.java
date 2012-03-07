/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.api.parser;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;

/**
 *
 * @author Valery
 */
public class GroovyModifier {
    public static int toAstModifiers(Element element) {
            Set<Modifier> modifiers = element.getModifiers();
            int intMods = 0;

            for (Modifier m : modifiers) {
                if (m.equals(Modifier.ABSTRACT)) {
                    intMods = intMods | Opcodes.ACC_ABSTRACT;
                }
                if (m.equals(Modifier.PUBLIC)) {
                    intMods = intMods | Opcodes.ACC_PUBLIC;
                }
                if (m.equals(Modifier.PROTECTED)) {
                    intMods = intMods | Opcodes.ACC_PROTECTED;
                }
                if (m.equals(Modifier.PRIVATE)) {
                    intMods = intMods | Opcodes.ACC_PRIVATE;
                }
                if (m.equals(Modifier.STATIC)) {
                    intMods = intMods | Opcodes.ACC_STATIC;
                }
                if (m.equals(Modifier.SYNCHRONIZED)) {
                    intMods = intMods | Opcodes.ACC_SYNCHRONIZED;
                }
                if (m.equals(Modifier.FINAL)) {
                    intMods = intMods | Opcodes.ACC_FINAL;
                }

            }
            return intMods;
    }

    public static Set<Modifier> toModelModifiers(MethodNode node) {
            return toModelModifiers(node.getModifiers());
    }
    
    public static Set<Modifier> toModelModifiers(FieldNode node, Modifier defaultMod ) {
        Set<Modifier> result = toModelModifiers(node.getModifiers());
        if ( result.isEmpty() ) {
            result.add(defaultMod);
        }
        return result;
    }

    public static Set<Modifier> toModelModifiers(int intMods, Modifier defaultMod ) {
        Set<Modifier> result = toModelModifiers(intMods);
        if ( result.isEmpty() ) {
            result.add(defaultMod);
        }
        return result;
    }
    
    public static Set<Modifier> toModelModifiers(int intMods) {
            Set<Modifier> modifiers = new HashSet<Modifier>();

            if ( (intMods & Opcodes.ACC_ABSTRACT) != 0) {
                modifiers.add(Modifier.ABSTRACT);
            }
            if ( (intMods & Opcodes.ACC_PUBLIC) != 0) {
                modifiers.add(Modifier.PUBLIC);
            } else if ( (intMods & Opcodes.ACC_PROTECTED) != 0) {
                modifiers.add(Modifier.PROTECTED);
            } else if ( (intMods & Opcodes.ACC_PRIVATE) != 0) {
                modifiers.add(Modifier.PRIVATE);
            } else {
                modifiers.add(Modifier.PUBLIC);
            }
            if ( (intMods & Opcodes.ACC_STATIC) != 0) {
                modifiers.add(Modifier.STATIC);
            }
            if ( (intMods & Opcodes.ACC_SYNCHRONIZED) != 0) {
                modifiers.add(Modifier.SYNCHRONIZED);
            }
            if ( (intMods & Opcodes.ACC_FINAL) != 0) {
                modifiers.add(Modifier.FINAL);
            }
            return modifiers;
    }
    
}
