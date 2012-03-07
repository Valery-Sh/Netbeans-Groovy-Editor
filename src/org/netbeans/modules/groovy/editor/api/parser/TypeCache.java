/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.api.parser;

import java.util.*;
import javax.lang.model.element.TypeElement;
import org.codehaus.groovy.ast.ClassNode;
import org.netbeans.api.java.source.ClassIndex;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.api.java.source.JavaSource;
import org.openide.filesystems.FileObject;

public class TypeCache {

    protected Map<String, List<String>> cache = new HashMap<String, List<String>>(20);
    protected Map<String, List<ClassNode>> resolvedCache = new HashMap<String, List<ClassNode>>(20);
    protected JavaSource javaSource;
    
    
/*    public TypeCache(HasJavaSource hasJavaSource) {
        this.javaSource = hasJavaSource.getJavaSource();
    }
*/
    public TypeCache(JavaSource javaSource) {
        this.javaSource = javaSource;
    }
    
    public void print(String name) {
        List<String> l = getCache(name);
        System.out.println("PRINT FOR: " + name);
        for (String s : l) {

            System.out.println("--- FOR: " + name + " element: " + s);
        }
        System.out.println("________________ ENDPRINT FOR: " + name);

    }

    public void printAll() {
        HashMap<String, List> m;

        for (Map.Entry<String, List<String>> en : cache.entrySet()) {
            System.out.println("*** SimpleName : " + en.getKey() + " ***");
            System.out.println("--------------------------------------------");
            for (String s : en.getValue()) {
                System.out.println("------" + s);
            }
            System.out.println("==============================================");


        }
        System.out.println("*** END ***");

    }

    public void printJobTime() {
        System.out.println("PRINT JOB TIME: ");
        System.out.println("-----------------");
        if (tlist == null) {
            return;
        }
        for (String s : tlist) {
            System.out.println("------" + s);
        }
        System.out.println("==============================================");
    }

    protected String toSimpleName(String name) {
        String simpleName = name;
        int n1 = simpleName.lastIndexOf('.');
        int n2 = simpleName.lastIndexOf('$');
        int n = Math.max(n1, n2);

        if (n >= 0) {
            simpleName = simpleName.substring(n + 1);
        }
        return simpleName;
    }

    public boolean canBeResolved(String name) {
        //String simpleName = toSimpleName(name);
        String jname = name.replace('$', '.');
        List<String> l = getCache(name);
        if (l.contains(jname)) {
            return true;
        }
        return false;
    }

    public String getCacheProposal(String name) {
        List<String> l = getCache(name);
        String jname = name.replace('$', '.');

        int i = l.indexOf(jname);
        if (i < 0) {
            return null;
        }
        return l.get(i);
    }

    public ClassNode getResolvedBySimpleName(String name) {
        String simpleName = toSimpleName(name);

        List<ClassNode> l = resolvedCache.get(simpleName);
        if (l == null || l.isEmpty()) {
            return null;
        }
        ClassNode r = null;
        ClassNode r1 = null;
        String jname = name.replace('$', '.');

        for (ClassNode node : l) {
            if (node.getName().equals(jname)) {
                r = node;
                break;
            }
            r1 = node;
        }
        if (r == null) {
            r = r1;
        }

        return r;

    }

    public void setResolved(String name, ClassNode type) {
        String simpleName = toSimpleName(name);
        List<ClassNode> l = resolvedCache.get(simpleName);
        if (l == null) {
            l = new ArrayList<ClassNode>();
            resolvedCache.put(simpleName, l);
        }
        l.add(type);
    }
    private List<String> tlist = new ArrayList<String>(50);

    protected List<String> getCache(String name) {
        //System.out.println("GET CACHE ========== " + name);

        String simpleName = toSimpleName(name);

        List<String> l = cache.get(simpleName);
        if (l == null) {
            l = new ArrayList<String>();
            cache.put(simpleName, l);

            long start = System.currentTimeMillis();
            Set<ClassIndex.SearchScopeType> scope = new HashSet<ClassIndex.SearchScopeType>();

            scope.add(ClassIndex.SearchScope.DEPENDENCIES);
            scope.add(ClassIndex.SearchScope.SOURCE);

            Set<ElementHandle<TypeElement>> dtypes = javaSource.getClasspathInfo().getClassIndex().getDeclaredTypes(simpleName, ClassIndex.NameKind.SIMPLE_NAME, scope);
            if (dtypes != null || !dtypes.isEmpty()) {
                for (ElementHandle eh : dtypes) {
                    l.add(eh.getQualifiedName());
                    
//System.out.println("========= JAVASOURCE JAVASOURCE name="+eh.getQualifiedName());                    
                    
                }
            }
            long end = System.currentTimeMillis();
            long dif = end - start;
            tlist.add("SimpleName = " + simpleName + "; TIME(ms) = " + dif);
        }

        return l;
    }
    
    
    public List<String> getProjectSources() {
        return TypeCache.getProjectSources(javaSource);
    }
    
    public static List<String> getProjectSources(JavaSource javaSource) {
            List<String> l = new ArrayList<String>();
            long start = System.currentTimeMillis();
            Set<ClassIndex.SearchScopeType> scope = new HashSet<ClassIndex.SearchScopeType>();
            scope.add(ClassIndex.SearchScope.SOURCE);

            Set<ElementHandle<TypeElement>> dtypes = javaSource.getClasspathInfo().getClassIndex().getDeclaredTypes("", ClassIndex.NameKind.PREFIX, scope);
            if (dtypes != null || !dtypes.isEmpty()) {
                for (ElementHandle eh : dtypes) {
                    l.add(eh.getQualifiedName());
                    
                    
//System.out.println("TypeCache ========= JAVASOURCE JAVASOURCE name="+eh.getQualifiedName());                    
                    
                }
            }

        return l;
    }
    
    protected List<String> getAll(String name) {
        return getCache(name);
    }

    public boolean hasProposals(String name) {
        List<String> l = getCache(name);
        return !l.isEmpty();
    }
}
