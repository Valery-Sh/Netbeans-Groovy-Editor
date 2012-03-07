package org.netbeans.modules.groovy.editor.api.elements;

import java.util.Set;
import org.netbeans.modules.csl.api.ElementKind;
import org.netbeans.modules.groovy.editor.api.GroovyIndex;
import org.netbeans.modules.parsing.spi.indexing.support.IndexResult;

/**
 * A class describing a Groovy class that is in "textual form" (signature, filename, etc.)
 * obtained from the code index.
 *
 * @author Tor Norbye
 * @author Martin Adamek
 */
public final class IndexedClassElement extends IndexedElement implements ClassElement {

    /** This class is a module rather than a proper class */
    public static final int MODULE = 1 << 6;

    private final String simpleName;

    protected IndexedClassElement(GroovyIndex index, IndexResult result, String fqn, String simpleName, String attributes, int flags) {
        super(index, result, fqn, attributes, flags);
        this.simpleName = simpleName;
    }

    public static IndexedClassElement create(GroovyIndex index, String simpleName, String fqn, IndexResult result,
        String attributes, int flags) {
        IndexedClassElement c = new IndexedClassElement(index, result, fqn, simpleName, attributes, flags);
        return c;
    }

    // XXX Is this necessary?
    @Override
    public String getSignature() {
        return classFqn;
    }

    @Override
    public String getName() {
        return simpleName;
    }

    @Override
    public ElementKind getKind() {
        return (flags & MODULE) != 0 ? ElementKind.MODULE : ElementKind.CLASS;
    }

    @Override
    public Set<String> getIncludes() {
        return null;
    }
    
    @Override 
    public boolean equals(Object o) {
        if (o instanceof IndexedClassElement && classFqn != null) {
            return classFqn.equals(((IndexedClassElement) o).classFqn);
        }
        return super.equals(o);
    }
    
    @Override
    public int hashCode() {
        return classFqn == null ? super.hashCode() : classFqn.hashCode();
    }
    
    public static String decodeFlags(int flags) {
        StringBuilder sb = new StringBuilder();
        sb.append(IndexedElement.decodeFlags(flags));

        if ((flags & MODULE) != 0) {
            sb.append("|MODULE");
        }
        if (sb.length() > 0) {
            sb.append("|");
        }
        
        return sb.toString();
    }

    @Override
    public String getFqn() {
        return classFqn;
    }
}
