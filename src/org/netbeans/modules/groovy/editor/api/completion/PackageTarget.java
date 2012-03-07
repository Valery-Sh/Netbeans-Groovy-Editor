package org.netbeans.modules.groovy.editor.api.completion;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.netbeans.api.java.source.ClassIndex;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.modules.csl.api.CompletionProposal;

/**
 *
 * @author V. Shyshkin
 */
public class PackageTarget implements CompletionTarget {

    protected CompletionHandlerContext context;
    protected List<String> packages;
    protected String afterCaretIdentifier;

    /**
     *
     * @param context
     * @param packages
     * @param afterCaretIdentifier
     */
    public PackageTarget(CompletionHandlerContext context, List<String> packages, String afterCaretIdentifier) {
        this.context = context;
        this.packages = packages;
        this.afterCaretIdentifier = afterCaretIdentifier;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    /**
     *
     * @param withoutPrefix
     * @return
     */
    public String toString(boolean withoutPrefix) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ");
        int bound = 0;
        String prefix = context.getCodeContext().getPrefix();
        if (prefix != null && withoutPrefix) {
            bound = 1;
        }
        if (packages.size() > 0) {
            for (int i = packages.size() - 1; i >= bound; i--) {

                sb.append(packages.get(i));
                if (i != 0) {
                    sb.append('.');
                }
            }
        }

        return sb.toString();
    }

    /**
     *
     * @return
     */
    @Override
    public List<CompletionProposal> getProposals() {
        List<CompletionProposal> proposals = new ArrayList<CompletionProposal>();
        getProposals(proposals);
        return proposals;
    }

    /**
     *
     * @param proposals
     */
    protected void getProposals(List<CompletionProposal> proposals) {
        int anchor = context.getLexOffset() - context.getPrefix().length();

        if (packages.isEmpty()) {
            String key = PackageInfo.getPackageNameByFile(context);
            CompletionItem.PackageItem item = new CompletionItem.PackageItem(key, anchor, context.getParserResult());
            proposals.add(item);
            item.setSortPrioOverride(1);
        }

        ClasspathInfo classPath = context.getParserResult().getClassPath();

        String path = PackageInfo.getPath(context, packages, afterCaretIdentifier);

        Set<String> pathPackages = classPath.getClassIndex().
                getPackageNames(path, true, EnumSet.allOf(ClassIndex.SearchScope.class));
        for (String p : pathPackages) {
            System.out.println("getProposals() FROM getPath() PACKAGE = " + p);
            String key = PackageInfo.getSimpleName(p);
            CompletionItem.PackageItem item = new CompletionItem.PackageItem(key, anchor, context.getParserResult());
            proposals.add(item);
            item.setSortPrioOverride(2);
            //item.setSmart(true);
        }

        System.out.println("PackageTarget getProposals() ===========================");
    }
}
