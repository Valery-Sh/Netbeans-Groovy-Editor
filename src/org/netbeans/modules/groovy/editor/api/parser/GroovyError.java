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
 * Software is Sun Microsystems, Inc. Portions Copyright 1997-2007 Sun
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

package org.netbeans.modules.groovy.editor.api.parser;

import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.api.annotations.common.NullAllowed;
import org.netbeans.modules.csl.api.Severity;
import org.netbeans.modules.groovy.editor.api.GroovyCompilerErrorID;
import org.openide.filesystems.FileObject;

/**
 * This is a copy of DefaultError with the additional Groovy
 * information.
 *
 * Because groovyc report some errors multiple times we overrides
 * hashCode and equals.
 *
 * http://jira.codehaus.org/browse/GROOVY-3827
 */
public class GroovyError implements org.netbeans.modules.csl.api.Error {

    private final String displayName;
    private final String description;
    private final FileObject file;
    private final int start;
    private final int end;
    private final String key;
    private final Severity severity;
    private final GroovyCompilerErrorID id;

    /** Creates a new instance of GroovyError */
    public GroovyError(
            @NullAllowed String key,
            @NonNull String displayName,
            @NullAllowed String description,
            @NullAllowed FileObject file,
            @NonNull int start,
            @NonNull int end,
            @NonNull Severity severity,
            @NonNull GroovyCompilerErrorID id) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
        this.file = file;
        this.start = start;
        this.end = end;
        this.severity = severity;
        this.id = id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    // TODO rename to getStartOffset
    @Override
    public int getStartPosition() {
        return start;
    }

    // TODO rename to getEndOffset
    @Override
    public int getEndPosition() {
        return end;
    }

    @Override
    public String toString() {
        return "GroovyError[" + displayName + ", " + description + ", " + severity + "]";
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Object[] getParameters() {
        return null;
    }

    @Override
    public Severity getSeverity() {
        return severity;
    }

    @Override
    public FileObject getFile() {
        return file;
    }

    public GroovyCompilerErrorID getId() {
        return id;
    }

    @Override
    public boolean isLineError() {
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final GroovyError other = (GroovyError) obj;
        if ((this.displayName == null) ? (other.displayName != null) : !this.displayName.equals(other.displayName)) {
            return false;
        }
        if ((this.description == null) ? (other.description != null) : !this.description.equals(other.description)) {
            return false;
        }
        if (this.file != other.file && (this.file == null || !this.file.equals(other.file))) {
            return false;
        }
        if (this.start != other.start) {
            return false;
        }
        if (this.end != other.end) {
            return false;
        }
        if ((this.key == null) ? (other.key != null) : !this.key.equals(other.key)) {
            return false;
        }
        if (this.severity != other.severity) {
            return false;
        }
        if (this.id != other.id) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + (this.displayName != null ? this.displayName.hashCode() : 0);
        hash = 53 * hash + (this.description != null ? this.description.hashCode() : 0);
        hash = 53 * hash + (this.file != null ? this.file.hashCode() : 0);
        hash = 53 * hash + this.start;
        hash = 53 * hash + this.end;
        hash = 53 * hash + (this.key != null ? this.key.hashCode() : 0);
        hash = 53 * hash + this.severity.hashCode();
        hash = 53 * hash + this.id.hashCode();
        return hash;
    }

}
