/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.api.completion;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.codehaus.groovy.ast.ClassNode;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.groovy.editor.completion.DynamicElementHandler;
import org.netbeans.modules.groovy.editor.completion.GroovyMetaElementHandler;
import org.netbeans.modules.groovy.editor.completion.MainElementHandler;
import org.netbeans.modules.groovy.editor.completion.MetaElementHandler;



/**
 *
 * @author Petr Hejl
 */
public final  class GroovyCompleteElementHandler {


    private final ParserResult info;
    private final GroovyCompletionHandler.CompletionRequest request;
    

    private GroovyCompleteElementHandler(GroovyCompletionHandler.CompletionRequest request) {
        this.request = request;
        this.info = request.info;

System.out.println("0) cccccccccccc CompletionElementHandler GET index START time=" + System.currentTimeMillis());
System.out.println("------------------------------------------------------------------------------------------");

    }

    public static GroovyCompleteElementHandler create(GroovyCompletionHandler.CompletionRequest request) {
        return new GroovyCompleteElementHandler(request);
    }

    // FIXME ideally there should be something like nice CompletionRequest once public and stable
    // then this class could implement some common interface
    public Map<MethodSignature, ? extends CompletionItem> getMethods(
            ClassNode source, ClassNode node, String prefix, int anchor, boolean nameOnly) {

        //Map<MethodSignature, CompletionItem> meta = new HashMap<MethodSignature, CompletionItem>();
        Set<Integer> accessMods = AccessModifiers.get(source, node);
        Map<MethodSignature, CompletionItem> result = getMethodsInner(
                source, node, prefix, anchor, 0, accessMods, nameOnly);

        //fillSuggestions(meta, result);
        return result;
    }

    public Map<FieldSignature, ? extends CompletionItem> getFields(
            ClassNode source, ClassNode node, String prefix, int anchor) {

        //Map<MethodSignature, CompletionItem> meta = new HashMap<MethodSignature, CompletionItem>();
        Map<FieldSignature, CompletionItem> result = getFieldsInner(source, node, prefix, anchor, 0);

        //fillSuggestions(meta, result);
        return result;
    }

    // FIXME configure acess levels
    private Map<MethodSignature, CompletionItem> getMethodsInner(
            ClassNode source, ClassNode typeNode, String prefix, int anchor, int level, Set<Integer> accessMods, boolean nameOnly) {
        boolean leaf = (level == 0);

        Map<MethodSignature, CompletionItem> result = new HashMap<MethodSignature, CompletionItem>();

        Set<Integer> modifiedAccessMods = AccessModifiers.update(accessMods,source, typeNode);        
System.out.println(" (((( 2 ))))) ======= CompletionElementHandler GET getMethodsInnere  request.accessors.size()=" + request.accessors.get().size());
        Map<MethodSignature, ? extends CompletionItem> groovyItems = MainElementHandler.create(info,request.accessors)
                .getMethods(source,typeNode, prefix, anchor, leaf, accessMods, nameOnly);

        fillSuggestions(groovyItems, result);
System.out.println(" 1) cccccccccccc CompletionElementHandler GET getMethodsInnere  time=" + System.currentTimeMillis());
        // FIXME not sure about order of the meta methods, perhaps interface
        // methods take precedence
        fillSuggestions(GroovyMetaElementHandler.forCompilationInfo(info)
                .getMethods(typeNode, prefix, anchor, nameOnly), result);

        if (source != null) {
            fillSuggestions(DynamicElementHandler.forCompilationInfo(info)
                    .getMethods(source.getName(), typeNode.getName(), prefix, anchor, nameOnly, leaf, null), result);
System.out.println(" 3) cccccccccccc CompletionElementHandler GET getMethodsInnere  time=" + System.currentTimeMillis());
            
        }

        if (typeNode.getSuperClass() != null) {

            fillSuggestions(getMethodsInner(source, typeNode.getSuperClass(),
                    prefix, anchor, level + 1, modifiedAccessMods, nameOnly), result);
System.out.println(" 4) cccccccccccc CompletionElementHandler GET getMethodsInnere  time=" + System.currentTimeMillis());
            
        } /*else if (leaf) {
            fillSuggestions(JavaElementHandler.forCompilationInfo(info)
                    .getMethods("java.lang.Object", prefix, anchor, new String[]{}, false, modifiedAccess, nameOnly), result); // NOI18N
System.out.println(" 5) cccccccccccc CompletionElementHandler GET getMethodsInnere  time=" + System.currentTimeMillis());
            
        }
*/
/*        
        for (ClassNode inter : typeNode.getInterfaces()) {
            fillSuggestions(getMethodsInner(source, inter,
                    prefix, anchor, level + 1, modifiedAccess, nameOnly), result);
System.out.println(" 6) cccccccccccc CompletionElementHandler GET getMethodsInnere  time=" + System.currentTimeMillis());
            
        }
*/
System.out.println(" END cccccccccccc CompletionElementHandler GET getMethodsInnere END time=" + System.currentTimeMillis());

        return result;
    }

    private Map<FieldSignature, CompletionItem> getFieldsInner(
            ClassNode source, ClassNode typeNode, String prefix, int anchor, int level) {
System.out.println(" cccccccccccc CompletionElementHandler GET getFieldsInnere START time=" + System.currentTimeMillis());

        boolean leaf = (level == 0);

        Map<FieldSignature, CompletionItem> result = new HashMap<FieldSignature, CompletionItem>();

        fillSuggestions(MainElementHandler.create(info,request.accessors)
                .getFields(source, typeNode, prefix, anchor, leaf), result);
        // FIXME not sure about order of the meta methods, perhaps interface
        // methods take precedence
        fillSuggestions(MetaElementHandler.forCompilationInfo(info)
                .getFields(typeNode.getName(), prefix, anchor), result);

        if (source != null) {
            fillSuggestions(DynamicElementHandler.forCompilationInfo(info)
                    .getFields(source.getName(), typeNode.getName(), prefix, anchor, leaf, null), result);
        }

        if (typeNode.getSuperClass() != null) {
            fillSuggestions(getFieldsInner(source, typeNode.getSuperClass(), prefix, anchor, level + 1), result);
        } 
        /*else if (leaf) {
            fillSuggestions(JavaElementHandler.forCompilationInfo(info)
                    .getFields("java.lang.Object", prefix, anchor, false), result); // NOI18N
        }
*/
/*        for (ClassNode inter : typeNode.getInterfaces()) {
            fillSuggestions(getFieldsInner(source, inter, prefix, anchor, level + 1), result);
        }
*/
System.out.println(" cccccccccccc CompletionElementHandler GET getFieldsInnere END time=" + System.currentTimeMillis());
System.out.println("----------------------------------------------------------------------------------");

        return result;
    }


    private static <T> void fillSuggestions(Map<T, ? extends CompletionItem> input, Map<T, ? super CompletionItem> result) {
        for (Map.Entry<T, ? extends CompletionItem> entry : input.entrySet()) {
            if (!result.containsKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
    }

}
