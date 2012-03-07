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
 * @author V.Shyskin
 */
public class ModifierTarget implements CompletionTarget {

    protected CompletionHandlerContext context;
    protected List<String> modifiers;
    /**
     * "class" or "interfacee" or "enum"
     */
    protected String typeKind;
    /**
     * true if before caret type at least one modifier was found
     */
    protected boolean insideTypeDefinition;
    

    public ModifierTarget(CompletionHandlerContext context,String typeKind, List<String> modifiers, boolean insideTypeDefinition) {
        this.context = context;
        this.modifiers = modifiers;
        this.typeKind = typeKind;
        this.insideTypeDefinition = insideTypeDefinition;
    }


    public boolean isInsideTypeDefinition() {
        return insideTypeDefinition;
    }

    public void setInsideTypeDefinition(boolean insideTypeDefinition) {
        this.insideTypeDefinition = insideTypeDefinition;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public void setModifiers(List<String> modifiers) {
        this.modifiers = modifiers;
    }

    public String getTypeKind() {
        return typeKind;
    }

    public void setTypeKind(String typeKind) {
        this.typeKind = typeKind;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (modifiers.size() > 0) {
            for (int i = 0; i < modifiers.size(); i++) {
                sb.append(modifiers.get(i));
                sb.append(' ');
            }
        }
        sb.append(typeKind);
        return sb.toString();
    }
    @Override
    public List<CompletionProposal> getProposals() {
        List<CompletionProposal> proposals = new ArrayList<CompletionProposal>();
        int anchor = context.getLexOffset() - context.getPrefix().length();
        List<String> supported = new ArrayList<String>();
        supported.add("public");
        supported.add("protected");
        supported.add("private");
        supported.add("final");
        supported.add("staic");
        supported.add("abstract");        
        
        for ( String mod : supported) {
            if ( modifiers.contains(mod) ) {
                continue;
            }
            CompletionItem.KeywordItem item = new CompletionItem.KeywordItem(mod, "Type Modifier", anchor, context.getParserResult(), true);
            proposals.add(item);
            item.setSortPrioOverride(1);
        }

        return proposals;
    }

}

