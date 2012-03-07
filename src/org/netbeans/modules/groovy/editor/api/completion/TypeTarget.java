package org.netbeans.modules.groovy.editor.api.completion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.netbeans.modules.csl.api.CompletionProposal;

/**
 *
 * @author Valery
 */
public class TypeTarget implements CompletionTarget {

    protected CompletionHandlerContext context;
    
    protected boolean staticInner;
    
    protected boolean beforeType;
    /**
     * key = "extends" or "implements"
     * values = 
     *  
     *  1) for implements = a list of a list. Each list corresponds to a single list
     *     in a coma separated list of "implements" clause
     *  2) for extends - as for "implements" but with a single list
     */
    protected Map<String,List<List<String>>> packages;
    /**
     * May be "extends" or "implements"
     */
    protected String targetClause;
    protected String afterCaretIdentifier;    
    /**
     * Create an instance for a given context
     * @param context
     * @param afterCaretIdentifier
     */
    public TypeTarget(CompletionHandlerContext context,String afterCaretIdentifier) {
        this.context = context;
        packages = new HashMap<String,List<List<String>>>();
        this.afterCaretIdentifier = afterCaretIdentifier;
        
    }
    public TypeTarget(CompletionHandlerContext context) {
        this(context,null);
    }

    public void add(String forClause,List<String> list  ) {
        List<String> l = new ArrayList<String>(list);
        if ( packages.containsKey(forClause)) {
            packages.get(forClause).add(l);
        } else {
            List<List<String>> ll = new ArrayList<List<String>>();
            ll.add(l);
            packages.put(forClause, ll);
        }
    }
    public boolean contains(String key) {
        return packages.containsKey(key);
    }
    public boolean isBeforeType() {
        return beforeType;
    }

    public void setBeforeType(boolean beforeType) {
        this.beforeType = beforeType;
    }

  
    public boolean isStaticInner() {
        return staticInner;
    }

    public void setStaticInner(boolean staticInner) {
        this.staticInner = staticInner;
    }

    public String getTargetClause() {
        return targetClause;
    }

    public void setTargetClause(String targetClause) {
        this.targetClause = targetClause;
    }

  
    private void append(StringBuilder sb, String key) {
        List<List<String>> ll = packages.get(key);
        
        if (ll != null && ll.size() > 0) {
            for (int i = ll.size() - 1; i >= 0; i--) {
                append(sb,ll.get(i));
                if (i != ll.size()-1) {
                    sb.append(',');
                } 
            }
            sb.append(' ');

        }
    }
    private void append(StringBuilder sb, List<String> l) {
        if (l != null && l.size() > 0) {
            for (int i = l.size() - 1; i >= 0; i--) {
                sb.append(l.get(i));
                if (i != 0) {
                    sb.append('.');
                }
            }
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (contains("class")) {
            sb.append("class ");            
        } else if ( contains("enum" )) {
            sb.append("enum ");
        } else if ( contains("interface") ) {
            sb.append("interface ");
        } 
        if ( isStaticInner() ) {
            sb.append("static ");
        }
        
        append(sb,"class");
        append(sb,"enum");
        append(sb,"interface");
        
        sb.append(' ');
        
        if ( contains("extends") ) {
            sb.append("extends ");
            append(sb,"extends");
        }

        if ( contains("implements") ) {
            sb.append("implements ");
            append(sb,"implements");
        }

        return sb.toString();
    }
    @Override
    public List<CompletionProposal> getProposals() {
        List<CompletionProposal> proposals = new ArrayList<CompletionProposal>();
        
        return proposals;
    }
    
}
