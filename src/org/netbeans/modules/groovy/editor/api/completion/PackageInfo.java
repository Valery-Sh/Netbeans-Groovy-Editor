/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.modules.groovy.editor.api.completion;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.ClassIndex;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.ElementHandle;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 *
 * @author Valery
 */
public class PackageInfo {

    protected CompletionHandlerContext context;
    protected boolean hasPackage;
    
    protected List<String> packages;
    protected int startOffset;
    protected int endOffset;
    
    private String name;

    /**
     * Create an instance for a given context and befire dot identifier
     *
     * @param context
     * @param id an identifier before dot
     */
    public PackageInfo(CompletionHandlerContext context) {
        this.context = context;
        packages = new ArrayList<String>();
        startOffset = -1;
        endOffset = -1;
    }

    public PackageInfo(CompletionHandlerContext context, boolean hasPackage, List<String> packages, int startOffset, int endOffset) {
        this.context = context;
        this.packages = packages;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.hasPackage = hasPackage;
    }

    public boolean hasPackage() {
        return hasPackage;
    }
    public boolean isAbovePackage(int offset) {
        if ( ! hasPackage() ) {
            return false;
        }
        return offset <= startOffset ;    
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("package ");
        for (int i = 0; i < packages.size(); i++) {
            sb.append(packages.get(i));
            if (i != packages.size() - 1) {
                sb.append('.');
            }
        }
        return sb.toString();
    }
    public String getName() {
        if ( name != null ) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < packages.size(); i++) {
            sb.append(packages.get(i));
            if (i != packages.size() - 1) {
                sb.append('.');
            }
        }
        name = sb.toString();
        return name;
    }
    public static String getPackageNameByFile(CompletionHandlerContext ctx) {
        FileObject fo = ctx.getParserResult().getSnapshot().getSource().getFileObject();
        String result = "";
        if (fo != null) {
//            ClasspathInfo cp = ClasspathInfo.create(fileObject);
  
            ClassPath cp = ClassPath.getClassPath(fo, ClassPath.SOURCE);
            
            File f = new File(cp.toString());
            FileObject cpFo = FileUtil.toFileObject(f);
            
            String rp = FileUtil.getRelativePath(cpFo, fo);
            rp = rp.replaceAll(File.pathSeparator, "/");
            int n = rp.lastIndexOf("/");
            if ( n > 0 ) {
                result = rp.substring(0,n);
                result = result.replaceAll("/", ".");
System.out.println("1 +++ relativePath = " + result);                            
            } 
System.out.println("2 +++ relativePath = " + result);            
//System.out.println("+++ relativePath norm = " + FileUtil.normalizePath(rp));            

//            return cp.toString();
        }
        return result;
    }
    private String getPackageNameByFile() {
        return getPackageNameByFile(context);
    }

    public static String getBasePackage(CompletionHandlerContext ctx,String path) {
        
        if (! ctx.isDotCompletion()) {
                return null;
        } 
        
        
        int n = path.lastIndexOf('.');
        String result = path;
        if (n > 0) {
            result = path.substring(0, n);
        }
        return result;
    }
    
    protected String getBasePackage(String path) {
        return getBasePackage(context,path);
    }
    
    public static String getSimpleName(String name) {
        if (name == null || name.length() == 0) {
            return null;
        }
        int n = name.lastIndexOf('.');
        String sname = name;
        if (n >= 0 && n + 1 < name.length()) {
            sname = name.substring(n + 1).trim();
        }
        return sname;

    }

    public static Set<ElementHandle<TypeElement>> getElementHandlesForPackage(CompletionHandlerContext ctx,String basePackage) {
        if (basePackage == null || basePackage.length() == 0) {
            return null;
        }
        Set<ElementHandle<TypeElement>> result = new HashSet<ElementHandle<TypeElement>>();
        Set<ElementHandle<TypeElement>> source = getElementHandlesForPackage(ctx,ClassIndex.SearchScope.SOURCE, basePackage);
        if (source != null && !source.isEmpty()) {
            result.addAll(source);
        }
        Set<ElementHandle<TypeElement>> dep = getElementHandlesForPackage(ctx,ClassIndex.SearchScope.DEPENDENCIES, basePackage);
        if (dep != null && !dep.isEmpty()) {
            result.addAll(dep);
        }
        return result;
    }

    public static Set<ElementHandle<TypeElement>> getElementHandlesForPackage(CompletionHandlerContext ctx,ClassIndex.SearchScope searchScope, String basePackage) {
        ClasspathInfo classPath = ctx.getParserResult().getClassPath();
        Set<ClassIndex.SearchScopeType> scope = new HashSet<ClassIndex.SearchScopeType>();

        ClassIndex.SearchScopeType scope1 = ClassIndex.createPackageSearchScope(searchScope, new String[]{basePackage});
        scope.add(scope1);
        Set<ElementHandle<TypeElement>> result = classPath.getClassIndex().getDeclaredTypes("", ClassIndex.NameKind.PREFIX, scope);
/*        int sz = -1;
        if (result != null) {
            sz = result.size();
        }
        System.out.println("getElementHandlesForPackage(" + searchScope + ") :  s1.size=" + sz);
        if (sz > 0) {
            for (ElementHandle<TypeElement> e : result) {
                System.out.println("getElementHandlesForPackage(" + searchScope + ") : e.getQualifiedName()=" + e.getQualifiedName());
            }
        }
*/
        return result;
    }

    private static String getPath(List<String> pkgs,boolean withLastDot) {
        StringBuilder sb = new StringBuilder();
        String last = "";

        if (withLastDot) {
            last = ".";
        }
        for (int i = 0; i < pkgs.size(); i++) {
            sb.append(pkgs.get(i));
            if (i == pkgs.size() - 1) {
                sb.append(last);
            } else {
                sb.append('.');
            }
        }
        return sb.toString();
    }

    public static String getPath(CompletionHandlerContext ctx,List<String> pkgs, String afterCaretIdentifier) {
        String path = "";
        String prefix = ctx.getPrefix();
        if (ctx.isDotCompletion()) {
            if (prefix.length() == 0 && afterCaretIdentifier == null) {
                // learn.ex.^*
                path = getPath(pkgs,true);
            } else if (prefix.length() == 0 && afterCaretIdentifier != null) {
                // learn.^ex.*
                path = getPath(pkgs,true);
            } else if (prefix.length() != 0 && afterCaretIdentifier != null) {
                // learn.e^x.*
                path = getPath(pkgs,true) + prefix;
            } else if (prefix.length() != 0 && afterCaretIdentifier == null) {
                // learn.ex^.*
                path = getPath(pkgs,false);
            }
        } else {
            path = prefix;
        }
        return path;
    }
    
}
