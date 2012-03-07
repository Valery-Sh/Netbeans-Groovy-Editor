/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.api.completion;

/**
 *
 * @author Valery
 */
public enum CompletionTargetEnum {
    PACKAGE("PACKAGE"),
    
    IMPORT("IMPORT"),
    
    CLASS("CLASS"),
    
    INTERFACE("INTERFACE"),

    EXTENDS("EXTENDS"),
    
    IMPLEMENTS("IMPLEMENTS"),
    
    DEF("DEF"),
    
    
    ;
    private String id;
    private boolean before;
    private boolean above;
    private boolean after;
    private boolean inContent;
    
    CompletionTargetEnum(String id) {
        this.id = id;
    }

    public boolean isAbove() {
        return above;
    }

    public void setAbove(boolean above) {
        this.above = above;
    }

    public boolean isAfter() {
        return after;
    }

    public void setAfter(boolean after) {
        this.after = after;
    }

    public boolean isBefore() {
        return before;
    }

    public void setBefore(boolean before) {
        this.before = before;
    }

    public boolean isInContent() {
        return inContent;
    }

    public void setInContent(boolean inContent) {
        this.inContent = inContent;
    }

    
    
}
