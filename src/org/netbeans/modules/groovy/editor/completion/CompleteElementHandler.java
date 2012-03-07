
package org.netbeans.modules.groovy.editor.completion;

import java.util.*;
import org.netbeans.modules.groovy.editor.api.completion.CompletionItem;
import org.netbeans.modules.groovy.editor.api.completion.MethodSignature;
import java.util.logging.Logger;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.groovy.editor.api.AstUtilities;
import org.netbeans.modules.groovy.editor.api.GroovyIndex;
import org.netbeans.modules.groovy.editor.api.completion.FieldSignature;
import org.netbeans.modules.groovy.editor.api.elements.IndexedClass;
import org.netbeans.modules.parsing.spi.indexing.support.QuerySupport;
import org.openide.filesystems.FileObject;



/**
 *
 * @author Petr Hejl
 */
public final class CompleteElementHandler {

    private static final Logger LOG = Logger.getLogger(CompleteElementHandler.class.getName());

    private final ParserResult info;

    private final GroovyIndex index;

    private CompleteElementHandler(ParserResult info) {
        this.info = info;
System.out.println("0) cccccccccccc CompletionElementHandler GET index START time=" + System.currentTimeMillis());
System.out.println("------------------------------------------------------------------------------------------");

        FileObject fo = info.getSnapshot().getSource().getFileObject();
        if (fo != null) {
            index = null; //// my
            // FIXME index is broken when invoked on start
           //// this.index = GroovyIndex.get(QuerySupport.findRoots(fo,
           ////         Collections.singleton(ClassPath.SOURCE), null, null));
            
        } else {
            index = null;
System.out.println("!!!) cccccccccccc CompletionElementHandler GET index NOT FOUND time=" + System.currentTimeMillis());
            
        }
System.out.println("1) cccccccccccc CompletionElementHandler Get index END time=" + System.currentTimeMillis());
System.out.println("------------------------------------------------------------------------------------------");
        
    }

    public static CompleteElementHandler forCompilationInfo(ParserResult info) {
        return new CompleteElementHandler(info);
    }

    // FIXME ideally there should be something like nice CompletionRequest once public and stable
    // then this class could implement some common interface
    public Map<MethodSignature, ? extends CompletionItem> getMethods(
            ClassNode source, ClassNode node, String prefix, int anchor, boolean nameOnly) {

        //Map<MethodSignature, CompletionItem> meta = new HashMap<MethodSignature, CompletionItem>();

        Map<MethodSignature, CompletionItem> result = getMethodsInner(
                source, node, prefix, anchor, 0, AccessLevel.create(source, node), nameOnly);

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
            ClassNode source, ClassNode node, String prefix, int anchor, int level, Set<AccessLevel> access, boolean nameOnly) {
System.out.println(" cccccccccccc CompletionElementHandler GET getMethodsInnere START time=" + System.currentTimeMillis() + "; node=" + node);
if ( node.isResolved() ) {
    List<MethodNode> mns = node.getMethods();
    for ( MethodNode  mn : mns) {
        System.out.println(" MMMMMMMMM --------------- CompletionElementHandler methodNode=" + mn.getName());        
    }
}
System.out.println(" --------------- CompletionElementHandler ClassNode source=" + source.getName());
System.out.println(" --------------- CompletionElementHandler ClassNode node=" + node.getName());
System.out.println(" --------------- CompletionElementHandler ClassNode prefix=" + node.getName());
        boolean leaf = (level == 0);
        Set<AccessLevel> modifiedAccess = AccessLevel.update(access, source, node);

        Map<MethodSignature, CompletionItem> result = new HashMap<MethodSignature, CompletionItem>();
        CompleteElementHandler.ClassDefinition definition = loadDefinition(node);
        ClassNode typeNode = definition.getNode();

        Map<MethodSignature, ? extends CompletionItem> groovyItems = null;
        //GroovyElementHandler.forCompilationInfo(info)
        //        .getMethods(node, typeNode.getName(), prefix, anchor, leaf, access, nameOnly);

        fillSuggestions(groovyItems, result);
System.out.println(" 1) cccccccccccc CompletionElementHandler GET getMethodsInnere  time=" + System.currentTimeMillis());
        // we can't go groovy and java - helper methods would be visible
        if (groovyItems.isEmpty()) {
            String[] typeParameters = new String[(typeNode.isUsingGenerics() && typeNode.getGenericsTypes() != null)
                    ? typeNode.getGenericsTypes().length : 0];
            for (int i = 0; i < typeParameters.length; i++) {
                GenericsType genType = typeNode.getGenericsTypes()[i];
                if (genType.getUpperBounds() != null) {
                    typeParameters[i] = org.netbeans.modules.groovy.editor.java.Utilities.translateClassLoaderTypeName(
                            genType.getUpperBounds()[0].getName());
                } else {
                    typeParameters[i] = org.netbeans.modules.groovy.editor.java.Utilities.translateClassLoaderTypeName(
                            genType.getName());
                }
            }

            fillSuggestions(JavaElementHandler.forCompilationInfo(info)
                    .getMethods(typeNode.getName(), prefix, anchor, typeParameters,
                            leaf, modifiedAccess, nameOnly), result);
System.out.println(" 2) cccccccccccc CompletionElementHandler GET getMethodsInnere  time=" + System.currentTimeMillis() + "; typeNode.getName()=" + typeNode.getName());
            
        }

        // FIXME not sure about order of the meta methods, perhaps interface
        // methods take precedence
        fillSuggestions(MetaElementHandler.forCompilationInfo(info)
                .getMethods(typeNode.getName(), prefix, anchor, nameOnly), result);

        if (source != null) {
            fillSuggestions(DynamicElementHandler.forCompilationInfo(info)
                    .getMethods(source.getName(), typeNode.getName(), prefix, anchor, nameOnly, leaf, definition.getFileObject()), result);
System.out.println(" 3) cccccccccccc CompletionElementHandler GET getMethodsInnere  time=" + System.currentTimeMillis());
            
        }

        if (typeNode.getSuperClass() != null) {

            fillSuggestions(getMethodsInner(source, typeNode.getSuperClass(),
                    prefix, anchor, level + 1, modifiedAccess, nameOnly), result);
System.out.println(" 4) cccccccccccc CompletionElementHandler GET getMethodsInnere  time=" + System.currentTimeMillis());
            
        } else if (leaf) {
            fillSuggestions(JavaElementHandler.forCompilationInfo(info)
                    .getMethods("java.lang.Object", prefix, anchor, new String[]{}, false, modifiedAccess, nameOnly), result); // NOI18N
System.out.println(" 5) cccccccccccc CompletionElementHandler GET getMethodsInnere  time=" + System.currentTimeMillis());
            
        }

////My       fillSuggestions(JavaElementHandler.forCompilationInfo(info)
//                    .getMethods("javaapplication6.MyCustomer", prefix, anchor, new String[]{}, false, modifiedAccess, nameOnly), result); // NOI18N                    
        
        for (ClassNode inter : typeNode.getInterfaces()) {
            fillSuggestions(getMethodsInner(source, inter,
                    prefix, anchor, level + 1, modifiedAccess, nameOnly), result);
System.out.println(" 6) cccccccccccc CompletionElementHandler GET getMethodsInnere  time=" + System.currentTimeMillis());
            
        }
System.out.println(" END cccccccccccc CompletionElementHandler GET getMethodsInnere END time=" + System.currentTimeMillis());

        return result;
    }

    private Map<FieldSignature, CompletionItem> getFieldsInner(
            ClassNode source, ClassNode node, String prefix, int anchor, int level) {
System.out.println(" cccccccccccc CompletionElementHandler GET getFieldsInnere START time=" + System.currentTimeMillis());

        boolean leaf = (level == 0);

        Map<FieldSignature, CompletionItem> result = new HashMap<FieldSignature, CompletionItem>();
        CompleteElementHandler.ClassDefinition definition = loadDefinition(node);
        ClassNode typeNode = definition.getNode();

        fillSuggestions(GroovyElementHandler.forCompilationInfo(info)
                .getFields(index, typeNode.getName(), prefix, anchor, leaf), result);
        fillSuggestions(JavaElementHandler.forCompilationInfo(info)
                .getFields(typeNode.getName(), prefix, anchor, leaf), result);

        // FIXME not sure about order of the meta methods, perhaps interface
        // methods take precedence
        fillSuggestions(MetaElementHandler.forCompilationInfo(info)
                .getFields(typeNode.getName(), prefix, anchor), result);

        if (source != null) {
            fillSuggestions(DynamicElementHandler.forCompilationInfo(info)
                    .getFields(source.getName(), typeNode.getName(), prefix, anchor, leaf, definition.getFileObject()), result);
        }

        if (typeNode.getSuperClass() != null) {
            fillSuggestions(getFieldsInner(source, typeNode.getSuperClass(), prefix, anchor, level + 1), result);
        } else if (leaf) {
            fillSuggestions(JavaElementHandler.forCompilationInfo(info)
                    .getFields("java.lang.Object", prefix, anchor, false), result); // NOI18N
        }

        for (ClassNode inter : typeNode.getInterfaces()) {
            fillSuggestions(getFieldsInner(source, inter, prefix, anchor, level + 1), result);
        }
System.out.println(" cccccccccccc CompletionElementHandler GET getFieldsInnere END time=" + System.currentTimeMillis());
System.out.println("----------------------------------------------------------------------------------");

        return result;
    }

    private CompleteElementHandler.ClassDefinition loadDefinition(ClassNode node) {
System.out.println("0) ------------- %%%%%%%%%%% CompleteElementHandler.loadDef node name==" + node.getName()+"; time=" + System.currentTimeMillis());        
        if ( true ) { //// My
           return new CompleteElementHandler.ClassDefinition(node, null);
        }
        if (index == null) {
            return new CompleteElementHandler.ClassDefinition(node, null);
        }
System.out.println("1)=== %%%%%%%%%%% CompleteElementHandler index.getClasses .loadDef.node.name=" + node.getName()+"; time=" + System.currentTimeMillis());
        Set<IndexedClass> classes = index.getClasses(node.getName(), QuerySupport.Kind.EXACT, true, false, false);

        if (!classes.isEmpty()) {

            IndexedClass indexed = classes.iterator().next();
            ASTNode astNode = AstUtilities.getForeignNode(indexed);
            if (astNode instanceof ClassNode) {
System.out.println("2) %%%%%%%%%%% CompleteElementHandler.loadDef.FOUND" +"; time=" + System.currentTimeMillis());                            
                return new CompleteElementHandler.ClassDefinition((ClassNode) astNode, indexed);
            }
System.out.println("3) %%%%%%%%%%% CompleteElementHandler.loadDef.NOT FOUND" +"; time=" + System.currentTimeMillis());                        
        }
System.out.println("4) %%%%%%%%%%% CompleteElementHandler.loadDef.NOT FOUND" +"; time=" + System.currentTimeMillis());                        
        return new CompleteElementHandler.ClassDefinition(node, null);
    }

    private static <T> void fillSuggestions(Map<T, ? extends CompletionItem> input, Map<T, ? super CompletionItem> result) {
System.out.println("--- %%%%%%%%%%% CompleteElementHandler.fillSuggestions START"+"; time=" + System.currentTimeMillis());        
        for (Map.Entry<T, ? extends CompletionItem> entry : input.entrySet()) {
            if (!result.containsKey(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
System.out.println("--- %%%%%%%%%%% CompleteElementHandler.fillSuggestions END"+"; time=" + System.currentTimeMillis());                
    }

    private static class ClassDefinition {

        private final ClassNode node;

        private final IndexedClass indexed;

        public ClassDefinition(ClassNode node, IndexedClass indexed) {
            this.node = node;
            this.indexed = indexed;
        }

        public ClassNode getNode() {
            return node;
        }

        public FileObject getFileObject() {
            return indexed != null ? indexed.getFileObject() : null;
        }
    }
}
