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

package org.netbeans.modules.groovy.editor.api;

import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.modules.csl.spi.GsfUtilities;
import org.openide.filesystems.FileObject;


/**
 * Utilities related to NetBeans - finding active editor, opening a file location, etc.
 *
 * @author Tor Norbye
 */
public class NbUtilities {

    private NbUtilities() {
    }

   // Copied from UiUtils. Shouldn't this be in a common library somewhere?
    public static boolean open(final FileObject fo, final int offset, final String search) {
        return GsfUtilities.open(fo, offset, search);
    }

    /**
     * Return substring after last dot.
     * @param fqn fully qualified type name
     * @return singe typename without package, or method without type
     */
    public static String stripPackage(String fqn) {

        if (fqn.contains(".")) {
            int idx = fqn.lastIndexOf(".");
            fqn = fqn.substring(idx + 1);
        }

        // every now and than groovy comes with tailing
        // semicolons. We got to get rid of them.

        return fqn.replace(";", "");
            }

    public static ClasspathInfo getClasspathInfoForFileObject ( FileObject fo) {
        
        ClassPath bootPath = ClassPath.getClassPath(fo, ClassPath.BOOT);
        ClassPath compilePath = ClassPath.getClassPath(fo, ClassPath.COMPILE);
        ClassPath srcPath = ClassPath.getClassPath(fo, ClassPath.SOURCE);

        if (bootPath == null || compilePath == null || srcPath == null) {
            return null;
        }
        
        return ClasspathInfo.create(bootPath, compilePath, srcPath);
    }
    
}
