/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * Oracle and Java are registered trademarks of Oracle and/or its affiliates.
 * Other names may be trademarks of their respective owners.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common
 * Development and Distribution License("CDDL") (collectively, the
 * "License"). You may not use this file except in compliance with the
 * License. You can obtain a copy of the License at
 * http://www.netbeans.org/cddl-gplv2.html
 * or nbbuild/licenses/CDDL-GPL-2-CP. See the License for the
 * specific language governing permissions and limitations under the
 * License.  When distributing the software, include this License Header
 * Notice in each file and include the License file at
 * nbbuild/licenses/CDDL-GPL-2-CP.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the GPL Version 2 section of the License file that
 * accompanied this code. If applicable, add the following below the
 * License Header, with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Contributor(s):
 *
 * The Original Software is NetBeans. The Initial Developer of the Original
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2006 Sun
 * Microsystems, Inc. All Rights Reserved.
 *
 * If you wish your version of this file to be governed by only the CDDL
 * or only the GPL Version 2, indicate your decision by adding
 * "[Contributor] elects to include this software in this distribution
 * under the [CDDL or GPL Version 2] license." If you do not indicate a
 * single choice of license, a recipient has the option to distribute
 * your version of this file under either the CDDL, the GPL Version 2 or
 * to extend the choice of license to its licensees as provided above.
 * However, if you add GPL Version 2 code and therefore, elected the GPL
 * Version 2 license, then the option applies only if the new code is
 * made subject to such option by the copyright holder.
 */
package org.netbeans.modules.groovy.editor.api.elements;

import groovyjarjarasm.asm.Opcodes;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.text.Document;
import org.netbeans.modules.csl.api.Modifier;
import org.netbeans.modules.groovy.editor.api.GroovyIndex;
import org.netbeans.modules.groovy.editor.api.lexer.LexUtilities;
import org.netbeans.modules.parsing.spi.indexing.support.IndexResult;
import org.openide.filesystems.FileObject;

/**
 * A program element coming from the persistent index.
 *
 * @author Tor Norbye
 * @author Martin Adamek
 */
public abstract class IndexedElement extends GroovyElement {

    protected IndexResult result;
    protected final String classFqn;
    protected final GroovyIndex index;
    protected final String attributes;
    protected Set<Modifier> modifiers;
    protected int flags;
    protected int docLength = -1;
    private Document document;
    //private FileObject fileObject;

    protected IndexedElement(GroovyIndex index, IndexResult result, String classFqn, String attributes, int flags) {
        this.index = index;
        this.result = result;
        this.attributes = attributes;
        this.classFqn = classFqn;
        this.flags = flags;
    }

    public abstract String getSignature();

    @Override
    public String toString() {
        return getSignature();
    }

    public GroovyIndex getIndex() {
        return index;
    }

    @Override
    public String getIn() {
        return classFqn;
    }

    public Document getDocument() throws IOException {
        if (document == null) {
            FileObject fo = getFileObject();

            if (fo == null) {
                return null;
            }

            document = LexUtilities.getDocument(fo, true);
        }

        return document;
    }

// FIXME parsing API
//    public ParserFile getFile() {
//        boolean platform = false; // XXX FIND OUT WHAT IT IS!
//
//        return new DefaultParserFile(getFileObject(), null, platform);
//    }

    @Override
    public FileObject getFileObject() {
        return result.getFile();
    }

    @Override
    public final Set<Modifier> getModifiers() {
        if (modifiers == null) {
            Modifier access = null;
            if (isPublic()) {
                access = Modifier.PUBLIC;
            } else if (isProtected()) {
                access = Modifier.PROTECTED;
            } else if (isPrivate()) {
                access = Modifier.PRIVATE;
            }
            boolean isStatic = isStatic();

            if (access != null) {
                if (isStatic) {
                    modifiers = EnumSet.of(access, Modifier.STATIC);
                } else {
                    modifiers = EnumSet.of(access);
                }
            } else if (isStatic) {
                modifiers = EnumSet.of(Modifier.STATIC);
            } else {
                modifiers = Collections.emptySet();
            }
        }
        return modifiers;
    }

    /** Return a string (suitable for persistence) encoding the given flags */
    public static char flagToFirstChar(int flags) {
        char first = (char)(flags >>= 4);
        if (first >= 10) {
            return (char)(first-10+'a');
        } else {
            return (char)(first+'0');
        }
    }

    /** Return a string (suitable for persistence) encoding the given flags */
    public static char flagToSecondChar(int flags) {
        char second = (char)(flags & 0xf);
        if (second >= 10) {
            return (char)(second-10+'a');
        } else {
            return (char)(second+'0');
        }
    }
    
    /** Return a string (suitable for persistence) encoding the given flags */
    public static String flagToString(int flags) {
        return (""+flagToFirstChar(flags)) + flagToSecondChar(flags);
    }
    
    /** Return flag corresponding to the given encoding chars */
    public static int stringToFlag(String s, int startIndex) {
        return stringToFlag(s.charAt(startIndex), s.charAt(startIndex+1));
    }
    
    /** Return flag corresponding to the given encoding chars */
    public static int stringToFlag(char first, char second) {
        int high = 0;
        int low = 0;
        if (first > '9') {
            high = first-'a'+10;
        } else {
            high = first-'0';
        }
        if (second > '9') {
            low = second-'a'+10;
        } else {
            low = second-'0';
        }
        return (high << 4) + low;
    }
    
    public boolean isPublic() {
        return (flags & Opcodes.ACC_PUBLIC) != 0;
    }

    public boolean isPrivate() {
        return (flags & Opcodes.ACC_PRIVATE) != 0;
    }
    
    public boolean isProtected() {
        return (flags & Opcodes.ACC_PROTECTED) != 0;
    }
    
    public boolean isStatic() {
        return (flags & Opcodes.ACC_STATIC) != 0;
    }
    
    public static String decodeFlags(int flags) {
        StringBuilder sb = new StringBuilder();
        if ((flags & Opcodes.ACC_PUBLIC) != 0) {
            sb.append("|PUBLIC");
        }
        if ((flags & Opcodes.ACC_PROTECTED) != 0) {
            sb.append("|PROTECTED");
        }
        if ((flags & Opcodes.ACC_STATIC) != 0) {
            sb.append("|STATIC");
        }
        
        return sb.toString();
    }

}
