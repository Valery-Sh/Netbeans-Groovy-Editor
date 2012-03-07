
package org.netbeans.modules.groovy.editor.api.completion;

public enum CaretLocationMy {
    
    ABOVE_PACKAGE("ABOVE_PACKAGE"),         // above the "package" statement (if any).
    ABOVE_FIRST_CLASS("ABOVE_FIRST_CLASS"), // Outside any classs and above the first class or interface stmt.
    OUTSIDE_CLASSES("OUTSIDE_CLASSES"),     // Outside any class but behind some class or interface stmt.
    INSIDE_CLASS("INSIDE_CLASS"),           // inside a class definition but not in a method.
    INSIDE_METHOD("INSIDE_METHOD"),         // in a method definition.
    INSIDE_CLOSURE("INSIDE_CLOSURE"),       // inside a closure definition.
    INSIDE_PARAMETERS("INSIDE_PARAMETERS"), // inside a parameter-list definition (signature) of a method.
    INSIDE_COMMENT("INSIDE_COMMENT"),       // inside a line or block comment
    INSIDE_STRING("INSIDE_STRING"),         // inside string literal
    IMPORT("IMPORT"),                       // inside import statement
    
    UNDEFINED("UNDEFINED");
    
    private String id;

    CaretLocationMy(String id) {
        
        this.id = id;
    }
    
}
