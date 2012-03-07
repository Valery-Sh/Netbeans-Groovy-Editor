/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.modules.groovy.editor.api.completion;

import java.util.ArrayList;
import java.util.List;
import org.netbeans.modules.csl.api.CompletionProposal;

/**
 * 
 *
 * @author Valery
 */
public class OutsideClassTarget implements CompletionTarget {
    
    protected CompletionHandlerContext context;
    
    public OutsideClassTarget(CompletionHandlerContext context) {
        this.context = context;
    }

    @Override
    public List<CompletionProposal> getProposals() {
        List<CompletionProposal> proposals = new ArrayList<CompletionProposal>();
        
        return proposals;
    }
    
}
