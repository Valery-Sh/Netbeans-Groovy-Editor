/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.api.parser;

import java.util.ArrayList;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.ResolveVisitor;
import org.codehaus.groovy.control.SourceUnit;

/**
 *
 * @author V. Shyshkin
 */
public class EditorResolveVisitor extends ResolveVisitor {
    protected PlaceholderResolveVisitorDelegate completeResolver;
    protected ResolveVisitorDelegate resolver;
    protected boolean completionTask;
    protected EditorCompilationUnit editorCompilationUnit;
//    protected TypeCache typeCache;
    
    public EditorResolveVisitor(CompilationUnit cu) {
        this(cu,false);
    }
    public EditorResolveVisitor(CompilationUnit cu, boolean completionTask) {
        
        super(cu);
//System.out.println("---------------------- CONSTRUCTOR EditorResolveVisitor" );
        
        if ( cu instanceof EditorCompilationUnit ) {
            this.editorCompilationUnit  = (EditorCompilationUnit)cu;
        }
        this.completionTask = completionTask;
        if ( completionTask ) {
            completeResolver = new PlaceholderResolveVisitorDelegate(this,cu,completionTask);
        } else {
        //resolver = new ResolveVisitorDelegate(cu,isCompletionTask);
            resolver = new ResolveVisitorDelegate(cu,this);
        }
    }
    public TypeCache getTypeCache() {
//System.out.println("editorCompilationUnit == null " + (editorCompilationUnit==null));                
        return this.editorCompilationUnit.getTypeCache();
    }
    
/*    public TypeCache getTypeCache() {
        if ( completionTask ) {
            return completeResolver.typeCache;
        } else {
        //resolver = new ResolveVisitorDelegate(cu,isCompletionTask);
            return resolver.typeCache;
        }
        
    }
*/
    static boolean ccc = false;
    @Override
    public void startResolving(ClassNode node, SourceUnit source) {
//System.out.println("---------------------- START RESOLVING 1 EditorResolveVisitor" );        
        if ( completionTask ) {
            
            completeResolver.startResolving(node, source,getTypeCache());
//System.out.println("---------------------- START RESOLVING 1 EditorResolveVisitor" );
            
            //super.startResolving(node, source);
        } else {
System.out.println("---------------------- Editor ResolveVisitor START RESOLVING classNode.name=" + node.getName());
            //typeCache = new TypeCache(editorCompilationUnit);
            resolver.startResolving(node, source, editorCompilationUnit.getTypeCache());
//            if ( node.getName().contains("MyGClass")) {
//                PrintUtils.printClassNode(node);
//            }
System.out.println("---------------------- END ResolveVisitor FOR classNode.name=" + node.getName() );
            
/*            ProjectClassNodes pcn = new ProjectClassNodes(editorCompilationUnit, source);
            pcn.incompleteNodes = new ArrayList<JavaClassNode>();
            pcn.incompleteNodes.addAll(((EditorCompilationUnit.CompileUnit)editorCompilationUnit.getAST()).getIncompleteNodes());
            ((EditorCompilationUnit.CompileUnit)editorCompilationUnit.getAST()).getIncompleteNodes().clear();
            if ( pcn.incompleteNodes != null ) {
                
                pcn.complete();
((EditorCompilationUnit.CompileUnit)editorCompilationUnit.getAST()).getIncompleteNodes().clear();
//                System.out.println("---------------------- END pcn.complete() " + node.getName() + "  -----------" );

            }  
            */
        }
    }
    @Override
    public SourceUnit getSourceUnit() {
        return super.getSourceUnit();
    }
    
}
