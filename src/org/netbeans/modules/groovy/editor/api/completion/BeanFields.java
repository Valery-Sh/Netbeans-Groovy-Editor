/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.api.completion;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Valery
 */
public class BeanFields {
    
    protected List<BeanField> beanFields = new ArrayList<BeanField>();
    
    public static class BeanField {
        protected String fieldName;
        protected String fieldType;
        protected int modifiers;
        public BeanField(String fieldName, String fieldType, int modifiers) {
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.modifiers = modifiers;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getFieldType() {
            return fieldType;
        }

        public void setFieldType(String fieldType) {
            this.fieldType = fieldType;
        }

        public int getModifiers() {
            return modifiers;
        }

        public void setModifiers(int modifiers) {
            this.modifiers = modifiers;
        }
        
    }
    public List<BeanField> get() {
        return beanFields;
    }
    public BeanField create(String getterName, String getterType, int modifiers ) {
        BeanField a = null;
        String name = getterName.substring(3);
        if ( name.length() == 0 || Character.isLowerCase(name.charAt(0))) {
            return null;
        }
        if ( name.length() > 1 && Character.isLowerCase(name.charAt(1))) {
            name = name.substring(0,1).toLowerCase() + name.substring(1);
        } 
        
        int i = beanFields.indexOf(name);
        a = new BeanField(name, getterType, modifiers);
        if ( i >=0 ) {
            beanFields.set(i, a);
        } else {
            beanFields.add(a);
        }
System.out.println("(( 1 )) " + name + "; size=" + beanFields.size());        
        return a;
    }
}
