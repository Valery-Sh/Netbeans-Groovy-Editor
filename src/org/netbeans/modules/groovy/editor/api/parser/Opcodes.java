/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.api.parser;

/**
 *
 * @author Valery
 */
public interface Opcodes {
            int ACC_PUBLIC = 0x0001; // class, field, method    
            int ACC_PRIVATE = 0x0002; // class, field, method
            int ACC_PROTECTED = 0x0004; // class, field, method
            int ACC_STATIC = 0x0008; // field, method
            int ACC_FINAL = 0x0010; // class, field, method
            int ACC_SUPER = 0x0020; // class
            int ACC_SYNCHRONIZED = 0x0020; // method
            int ACC_VOLATILE = 0x0040; // field
            int ACC_BRIDGE = 0x0040; // method
            int ACC_VARARGS = 0x0080; // method
            int ACC_TRANSIENT = 0x0080; // field
            int ACC_NATIVE = 0x0100; // method
            int ACC_INTERFACE = 0x0200; // class
            int ACC_ABSTRACT = 0x0400; // class, method
            int ACC_STRICT = 0x0800; // method
            int ACC_SYNTHETIC = 0x1000; // class, field, method
            int ACC_ANNOTATION = 0x2000; // class
            int ACC_ENUM = 0x4000; // class(?) field inner

            // ASM specific pseudo access flags

            int ACC_DEPRECATED = 131072; // class, field, method
    
}
