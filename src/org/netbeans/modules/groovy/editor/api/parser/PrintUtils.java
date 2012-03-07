/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.api.parser;

import java.util.ArrayList;
import java.util.List;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GenericsType;

/**
 *
 * @author Valery
 */
public class PrintUtils {

    public static void printClassNode(ClassNode node) {
        printClassNode(node, null);
    }

    public static void printClassNode(ClassNode node, String title) {
        if (node == null) {
            return;
        }

        List<String> strList = print(node, 0, title);
        for (String s : strList) {
            System.out.println(s);
        }
    }

    public static List<String> print(ClassNode node, int level, String title) {
        if (node == null) {
            return null;
        }
        List<String> strList = new ArrayList<String>();

        String prefix = "";
        String prefix1 = "";
        String blanks = "|   ";
        if (level == 0) {
            prefix = "";
        } else {
            for (int i = 0; i < level; i++) {
                prefix += blanks;
            }
            prefix1 = prefix + blanks;
            prefix += "|---";

        }
        String t = title;
        if (title == null) {
            t = prefix + " CLASS NODE name = " + node.getName();
        } else {
            t = prefix + title;
        }

        strList.add(t);
        String str = prefix1 + " CLASS NODE PROPERTIES:";

        strList.add(str);
        str = prefix + "   ___________";
        strList.add(str);

        str = prefix + "          unresolvedName = " + node.getUnresolvedName();
        strList.add(str);
        str = prefix + "          isRedirectNode = " + node.isRedirectNode();
        strList.add(str);

        str = prefix + "          isResolved = " + node.isResolved();
        strList.add(str);
        str = prefix + "          isGenericsPlaceHolder() = " + node.isGenericsPlaceHolder();
        strList.add(str);
        str = prefix + "          isUsingGenerics = " + node.isUsingGenerics();
        strList.add(str);

        if (node.getGenericsTypes() == null) {
            str = prefix + "         getGenericsTypes() = null ( length = 0 )";
        } else {
            str = prefix + "         getGenericsTypes().length = " + node.getGenericsTypes().length;
        }
        
        strList.add(str);
        
        if (node.getGenericsTypes() != null && node.getGenericsTypes().length > 0) {
            GenericsType[] gtTypes = node.getGenericsTypes();
            str = prefix + "      ____________________________";
            strList.add(str);

            str = prefix1 + "           CLASS NODE " + node.getName() +" GENERICS PROPERTIES:";
            strList.add(str);
            str = prefix + "      ____________________________";
            strList.add(str);

            for (int i = 0; i < gtTypes.length; i++) {
                GenericsType gt = gtTypes[i];
                str = prefix + "         " + i + "). gt.name = " + gt.getName();
                strList.add(str);
                str = prefix + "         " + i + "). gt.text = " + gt.getText();
                strList.add(str);
                str = prefix + "         " + i + "). gt.isPlaceholder() = " + gt.isPlaceholder();
                strList.add(str);
                str = prefix + "         " + i + "). gt.isResolved() = " + gt.isResolved();
                strList.add(str);
                str = prefix + "         " + i + "). gt.isWildCard() = " + gt.isWildcard();
                strList.add(str);
                str = prefix + "         " + i + "). gt.LowerBound() = " + gt.getLowerBound();
                strList.add(str);
                if (gt.getUpperBounds() == null) {
                    str = prefix + "         " + i + ").gt.getUpperBounds() = null ( length = 0 )";
                } else {
                    str = prefix + "         " + i + ").gt.getUpperBounds().length = " + gt.getUpperBounds().length;
                }
                strList.add(str);
                List<String> l = print(gt.getType(), level + 1, "TYPE of GENERICS[" + i + "] getType().getName = " + gt.getType().getName());
                strList.addAll(l);

                if (gt.getUpperBounds() != null && gt.getUpperBounds().length > 0) {
                    str = prefix1 + "           CLASS NODE " + node.getName() +" GENERICS " + gt.getName() + " PROPERTIES:";
                    strList.add(str);
                    
                    ClassNode[] bounds = gt.getUpperBounds();
                    for (int u = 0; u < bounds.length; u++) {
                        l = print(bounds[u], level + 2, "UPPER BOUND[" + u + "] classNode.name = " + bounds[u].getName() + " FOR " + "GENERICS[" + i + "] " + gt.getName());
                        strList.addAll(l);
                    }
                }

            }
        }
        return strList;
    }
    public static void printGenerics(GenericsType node) {
        if (node == null) {
            return;
        }

        List<String> strList = printGenerics(node, 0, " PRINT GENERICS name=" + node.getName());
        for (String s : strList) {
            System.out.println(s);
        }
        
    }
    public static List<String> printGenerics(GenericsType node, int level, String title) {
        if (node == null) {
            return null;
        }
        List<String> strList = new ArrayList<String>();

        String prefix = "";
        String prefix1 = "";
        String blanks = "|   ";
        if (level == 0) {
            prefix = "";
        } else {
            for (int i = 0; i < level; i++) {
                prefix += blanks;
            }
            prefix1 = prefix + blanks;
            prefix += "|---";

        }
        String t = title;
        if (title == null) {
            t = prefix + " GENERICS name = " + node.getName();
        } else {
            t = prefix + title;
        }

        strList.add(t);
        String str = prefix1 + " PROPERTIES:";
        strList.add(str);

        str = prefix + "          getType() = " + node.getType();
        strList.add(str);

        str = prefix + "          getType() = " + node.getName();
        strList.add(str);

        strList.add(str);
        str = prefix + "   ___________";
        strList.add(str);

        str = prefix + "          node.isPlaceholder() = " + node.isPlaceholder();
        strList.add(str);

        str = prefix + "          isResolved = " + node.isResolved();
        strList.add(str);

        if (node.getUpperBounds() == null) {
            str = prefix + "         " + ").gt.getUpperBounds() = null ( length = 0 )";
        } else {
            str = prefix + "         " + ").gt.getUpperBounds().length = " + node.getUpperBounds().length;
        }
        strList.add(str);
        List<String>  l = null;
        if (node.getUpperBounds() != null && node.getUpperBounds().length > 0) {
            str = prefix + " --- UPPER BOUNDS ---";
            strList.add(str);
            
            ClassNode[] bounds = node.getUpperBounds();
            for (int u = 0; u < bounds.length; u++) {
                l = print(bounds[u], level + 1, "UPPER BOUND[" + u + "] classNode.name = " + bounds[u].getName() + " FOR " + "GENERICS[" +  "] " + node.getName());
                strList.addAll(l);
            }
        }

        return strList;

    }
}
