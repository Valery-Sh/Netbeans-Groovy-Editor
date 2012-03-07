package org.netbeans.modules.groovy.editor.api.completion;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.java.source.ClassIndex;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.ElementHandle;
import org.netbeans.modules.csl.api.CompletionProposal;

/**
 *
 * @author Valery
 */
public class ImportTarget implements CompletionTarget {

    protected CompletionHandlerContext context;
    protected boolean staticImport;
    protected List<String> packages;
    protected String afterCaretIdentifier;

    /**
     * Create an instance for a given context, static flag, list of packages and before dot identifier
     * @param context
     * @param staticImport
     * @param packages
     * @param afterCaretIdentifier 
     */
    public ImportTarget(CompletionHandlerContext context, boolean staticImport, List<String> packages, String afterCaretIdentifier) {
        this.context = context;
        this.staticImport = staticImport;
        this.packages = packages;
        this.afterCaretIdentifier = afterCaretIdentifier;
    }


    public boolean isStaticImport() {
        return staticImport;
    }

    public void setStaticImport(boolean staticImport) {
        this.staticImport = staticImport;
    }

//    protected String getPath() {
//        return PackageInfo.getPath(context, packages, afterCaretIdentifier);
//    }

    protected String getBasePackage(String path) {
        
        if (! context.isDotCompletion()) {
            if ( context.getPackageInfo().hasPackage  ) {
                if ( packages.isEmpty() && isStaticImport() && context.getPrefix().length()==0 ) { 
                    return context.getPackageInfo().getName();
                }               
            } else {
                return null;
            }
        } 
        return PackageInfo.getBasePackage(context, path);
    }
    
    @Override
    public String toString() {
        return toString(false);
        /*
         * StringBuilder sb = new StringBuilder(); sb.append("import "); if
         * (isStaticImport()) { sb.append("static "); } if (packages.size() > 0)
         * { for (int i = packages.size() - 1; i >= 0; i--) {
         * sb.append(packages.get(i)); if (i != 0) { sb.append('.'); } } }
         *
         * return sb.toString();
         */
    }

    public String toString(boolean withoutPrefix) {
        StringBuilder sb = new StringBuilder();
        sb.append("import ");
        if (isStaticImport()) {
            sb.append("static ");
        }
        int size = packages.size();
        String prefix = context.getCodeContext().getPrefix();
        if (prefix != null && withoutPrefix) {
            size = packages.size() - 1;
        }

        if (packages.size() > 0) {
            for (int i = 0; i < size; i++) {
                sb.append(packages.get(i));
                if (i != 0) {
                    sb.append('.');
                }
            }
        }

        return sb.toString();
    }



    @Override
    public List<CompletionProposal> getProposals() {
        List<CompletionProposal> proposals = new ArrayList<CompletionProposal>();
        if (packages.isEmpty()) {
            if (!isStaticImport()) {
                getStaticKeywordProposal(proposals);
            } 
            
        } else if ("*".equals(packages.get(0))) {
            return null;
        }
        getProposals(proposals);
        return proposals;
    }

    protected void getProposals(List<CompletionProposal> proposals) {

        ClasspathInfo classPath = context.getParserResult().getClassPath();
        String path = PackageInfo.getPath(context, packages, afterCaretIdentifier);
        
        Set<String> pathPackages = classPath.getClassIndex().
                getPackageNames(path, true, EnumSet.allOf(ClassIndex.SearchScope.class));
        int anchor = context.getLexOffset() - context.getPrefix().length();
        for (String p : pathPackages) {
            System.out.println("getProposals() FROM getPath() PACKAGE = " + p);
            String key = PackageInfo.getSimpleName(p);
            CompletionItem.PackageItem item = new CompletionItem.PackageItem(key, anchor, context.getParserResult());
            proposals.add(item);
            item.setSortPrioOverride(2);
            //item.setSmart(true);
        }

        String basePackage = getBasePackage(path);
        System.out.println("getProposals() BASE PACKAGE = " + basePackage);

        Set<ElementHandle<TypeElement>> typeHandles = PackageInfo.getElementHandlesForPackage(context,basePackage);
        if (typeHandles != null) {
            for (ElementHandle<TypeElement> eh : typeHandles) {
                System.out.println("getProposals() Classes or Other types = " + eh.getQualifiedName());
                String key = PackageInfo.getSimpleName(eh.getQualifiedName());
                CompletionItem.TypeItem item = new CompletionItem.TypeItem(key, anchor, eh.getKind());
                proposals.add(item);
                item.setSortPrioOverride(3);
                //item.setSmart(true);
            }
        }
        System.out.println("getProposals() ===========================");
    }
    protected void getStaticKeywordProposal(List<CompletionProposal> proposals) {
        int anchor = context.getLexOffset() - context.getPrefix().length();
        CompletionItem.KeywordItem item = new CompletionItem.KeywordItem("static", "Static Import", anchor, context.getParserResult(), true);
        proposals.add(item);
        item.setSortPrioOverride(1);
    }
    
}
