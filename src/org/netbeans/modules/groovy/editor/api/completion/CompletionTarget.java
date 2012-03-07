package org.netbeans.modules.groovy.editor.api.completion;

import java.util.List;
import org.netbeans.modules.csl.api.CompletionProposal;

public interface CompletionTarget {
    List<CompletionProposal> getProposals();
}
