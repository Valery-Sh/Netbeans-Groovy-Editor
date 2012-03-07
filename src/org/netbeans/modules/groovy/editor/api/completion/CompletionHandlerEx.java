/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.api.completion;

import groovy.lang.MetaMethod;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.reflection.CachedClass;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.csl.api.*;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.groovy.editor.api.AstPath;
import org.netbeans.modules.groovy.editor.api.AstUtilities;
import org.netbeans.modules.groovy.editor.api.elements.AstMethodElement;
import org.openide.util.NbBundle;

/**
 *
 * @author Valery
 */
public class CompletionHandlerEx implements CodeCompletionHandler {

    @Override
    public CodeCompletionResult complete(CodeCompletionContext ccc) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String document(ParserResult pr, ElementHandle eh) {
        String error = NbBundle.getMessage(CompletionHandler.class, "GroovyCompletion_NoJavaDocFound");
        String doctext = null;
/*
        if (eh instanceof AstMethodElement) {
            AstMethodElement ame = (AstMethodElement) eh;

            String base = "";

            String javadoc = getGroovyJavadocBase();
            if (jdkJavaDocBase != null && ame.isGDK() == false) {
                base = jdkJavaDocBase;
            } else if (javadoc != null && ame.isGDK() == true) {
                base = javadoc;
            } else {
                return error;
            }

            MetaMethod mm = ame.getMethod();

            // enable this to troubleshoot subtle differences in JDK/GDK signatures
            printMethod(mm);

            // figure out who originally defined this method

            String className;

            if (ame.isGDK()) {
                className = mm.getDeclaringClass().getName();
            } else {

                String declName = null;

                if (mm != null) {
                    CachedClass cc = mm.getDeclaringClass();
                    if (cc != null) {
                        declName = cc.getName();
                    }
                }

                if (declName != null) {
                    className = declName;
                } else {
                    className = ame.getClz().getName();
                }
            }

            // create path from fq java package name:
            // java.lang.String -> java/lang/String.html
            String classNamePath = className.replace(".", "/");
            classNamePath = classNamePath + ".html"; // NOI18N

            // if the file can be located in the GAPI folder prefer it
            // over the JDK
            if (!ame.isGDK()) {

                URL url;
                File testFile;

                String apiDoc = getGroovyApiDocBase();
                try {
                    url = new URL(apiDoc + classNamePath);
                    testFile = new File(url.toURI());
                } catch (MalformedURLException ex) {
//                    LOG.log(Level.FINEST, "MalformedURLException: {0}", ex);
                    return error;
                } catch (URISyntaxException uriEx) {
//                    LOG.log(Level.FINEST, "URISyntaxException: {0}", uriEx);
                    return error;
                }

                if (testFile != null && testFile.exists()) {
                    base = apiDoc;
                }
            }

            // create the signature-string of the method
            String sig = getMethodSignature(ame.getMethod(), true, ame.isGDK());
            String printSig = getMethodSignature(ame.getMethod(), false, ame.isGDK());

            String urlName = base + classNamePath + "#" + sig;

            try {
//                LOG.log(Level.FINEST, "Trying to load URL = {0}", urlName); // NOI18N
                doctext = HTMLJavadocParser.getJavadocText(
                    new URL(urlName),
                    false,
                    ame.isGDK());
            } catch (MalformedURLException ex) {
//                LOG.log(Level.FINEST, "document(), URL trouble: {0}", ex); // NOI18N
                return error;
            }

            // If we could not find a suitable JavaDoc for the method - say so.
            if (doctext == null) {
                return error;
            }

            doctext = "<h3>" + className + "." + printSig + "</h3><BR>" + doctext;
        }
        */
        return doctext;

    }

    @Override
    public ElementHandle resolveLink(String lnk, ElementHandle eh) {
        // pass the original handle back. That's better than to throw an unsupported-exception.
        return eh;

    }

    @Override
    public String getPrefix(ParserResult parserResult, int i, boolean bln) {
        return null;
    }

    @Override
    public QueryType getAutoQuery(JTextComponent textComponent, String docText) {
        char c = docText.charAt(0);

        if (c == '.') {
            return QueryType.COMPLETION;
        }

        return QueryType.NONE;

    }

    @Override
    public String resolveTemplateVariable(String variable, ParserResult parserResult, int caretOffset, String name, Map parameters) {
        return null;
    }

    @Override
    public Set<String> getApplicableTemplates(Document doc,  int selectionBegin, int selectionEnd) {
        return Collections.emptySet();
    }

    @Override
    public ParameterInfo parameters(ParserResult parserResult, int caretOffset, CompletionProposal proposal) {

        // here we need to calculate the list of parameters for the methods under the caret.
        // proposal seems to be null all the time.

/*        List<String> paramList = new ArrayList<String>();

        AstPath path = getPathFromInfo(caretOffset, parserResult);

        // FIXME parsing API
        BaseDocument doc = (BaseDocument) parserResult.getSnapshot().getSource().getDocument(true);

        if (path != null) {

            ArgumentListExpression ael = getSurroundingArgumentList(path);

            if (ael != null) {

                List<ASTNode> children = AstUtilities.children(ael);

                // populate list with *all* parameters, but let index and offset
                // point to a specific parameter.

                int idx = 1;
                int index = -1;
                int offset = -1;

                for (ASTNode node : children) {
                    OffsetRange range = AstUtilities.getRange(node, doc);
                    paramList.add(node.getText());

                    if (range.containsInclusive(caretOffset)) {
                        offset = range.getStart();
                        index = idx;
                    }

                    idx++;
                }

                // calculate the parameter we are dealing with

                if (paramList != null && !paramList.isEmpty()) {
                    return new ParameterInfo(paramList, index, offset);
                }
            } else {
//                LOG.log(Level.FINEST, "ArgumentListExpression ==  null"); // NOI18N
                return ParameterInfo.NONE;
            }

        } else {
//            LOG.log(Level.FINEST, "path ==  null"); // NOI18N
            return ParameterInfo.NONE;
        }
*/
        return ParameterInfo.NONE;
    }
    
}
