package org.netbeans.modules.groovy.editor.api.completion;

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.Document;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.Expression;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.csl.api.CodeCompletionContext;
import org.netbeans.modules.csl.spi.ParserResult;
import org.netbeans.modules.groovy.editor.api.AstPath;
import org.netbeans.modules.groovy.editor.api.AstUtilities;
import org.netbeans.modules.groovy.editor.api.elements.AstRootElement;
import org.netbeans.modules.groovy.editor.api.lexer.GroovyTokenId;
import org.netbeans.modules.groovy.editor.api.lexer.LexUtilities;
import org.netbeans.modules.groovy.editor.api.parser.GroovyParserResult;

/**
 *
 * @author V. Shyshkin
 */
public class CompletionHandlerContext {

    protected static final String TYPECHECKEDNAME = "groovy.transform.TypeChecked";
    protected boolean canceled;
    protected CodeCompletionContext codeContext;
    protected GroovyParserResult parserResult;
    protected int lexOffset;
    protected int astOffset;
    protected int dotLexOffset;
    protected int dotAstOffset;
    protected Expression dotAstExpression;
    protected String prefix;
    protected boolean scriptMode;
    protected AstPath targetPath;
    protected AstPath dotPath;
    protected boolean dotCompletion;
    protected boolean optionalDotCompletion;
    protected boolean spreadDotCompletion;
    protected boolean memberDotCompletion;
    protected BaseDocument document;
    protected CompletionTarget completionTarget;
    protected ClassNode targetClassNode;
    protected MethodNode targetMethodNode;
    protected boolean classTypeChecked;
    protected boolean methodTypeChecked;
    protected PackageInfo packageInfo;

    public CompletionHandlerContext(CodeCompletionContext context) {
        codeContext = context;
        parserResult = (GroovyParserResult) codeContext.getParserResult();
        lexOffset = codeContext.getCaretOffset();
        astOffset = AstUtilities.getAstOffset(parserResult, lexOffset);
        prefix = codeContext.getPrefix();
        prefix = prefix == null ? "" : prefix;
        canceled = true;

        dotLexOffset = -1;
        dotAstOffset = -1;

        Document d = parserResult.getSnapshot().getSource().getDocument(false);
        if (d == null) {
            canceled = false;
            return;
        }

        document = (BaseDocument) d;
        document.readLock(); // Read-lock due to Token hierarchy use
        try {
            checkPackageInfo();
            checkDotCompletion();
            if (!dotCompletion) {
                //this.completionTarget = getPackageTarget();
                checkNotDotCompletion();
            }
            //getLexTarget();
        } finally {
            document.readUnlock();
        }

    }

    public boolean isClassTypeChecked() {
        return classTypeChecked;
    }

    public void setClassTypeChecked(boolean classTypeChecked) {
        this.classTypeChecked = classTypeChecked;
    }

    public boolean isMethodTypeChecked() {
        return methodTypeChecked;
    }

    public void setMethodTypeChecked(boolean methodTypeChecked) {
        this.methodTypeChecked = methodTypeChecked;
    }

    public ClassNode getTargetClassNode() {
        return targetClassNode;
    }

    public void setTargetClassNode(ClassNode targetClassNode) {
        this.targetClassNode = targetClassNode;
    }

    protected void getLexTarget() {
        int position = lexOffset;
//        System.out.println("111) !!!!! getDotCompletionContext request.lexOffset=" + request.lexOffset + "; request.astOffset" + request.astOffset);
        TokenSequence<?> ts = LexUtilities.getGroovyTokenSequence(document, position);

//        int difference = ts.move(position);

        // get the active token:
        Token<? extends GroovyTokenId> tokenInPosition = null;
        if (ts.isValid() && ts.moveNext() && ts.offset() >= 0) {
            tokenInPosition = (Token<? extends GroovyTokenId>) ts.token();
        }

    }

    protected TypeTarget getTypeTarget(TokenSequence ts, int position) {
        List<List<String>> packages = new ArrayList<List<String>>();
        List<String> current = new ArrayList<String>();
        
        String afterCaretIdent = null;


        TypeTarget tp = new TypeTarget(this);

        ts.move(position);
        if (ts.isValid() && ts.moveNext() && ts.offset() < document.getLength()) {
            if (ts.token().id() == GroovyTokenId.IDENTIFIER) {
                afterCaretIdent = ts.token().text().toString();
            }
        }

        while (ts.isValid() && ts.movePrevious() && ts.offset() >= 0) {
            Token<? extends GroovyTokenId> t = (Token<? extends GroovyTokenId>) ts.token();
            if (t.id() == GroovyTokenId.DOT || t.id() == GroovyTokenId.IDENTIFIER
                    || t.id() == GroovyTokenId.WHITESPACE || t.id() == GroovyTokenId.NLS) {
                if (t.id() == GroovyTokenId.IDENTIFIER) {
                    current.add(t.text().toString());
                }
                continue;
            } else if (t.id() == GroovyTokenId.COMMA) {
                packages.add(current);
                current = new ArrayList<String>();
                continue;
            } else if (t.id() == GroovyTokenId.LITERAL_implements) {
                if (tp.getTargetClause() == null) {
                    tp.setTargetClause("implements");
                }
                packages.add(current);
                for (List<String> l : packages) {
                    tp.add("implements", l);
                }
                packages.clear();
                current = new ArrayList<String>();
                continue;
            } else if (t.id() == GroovyTokenId.LITERAL_extends) {
                if (tp.getTargetClause() == null) {
                    tp.setTargetClause("extends");
                }
                packages.add(current);
                for (List<String> l : packages) {
                    tp.add("extends", l);
                }
                packages.clear();
                current = new ArrayList<String>();
                continue;
            } else if (t.id() == GroovyTokenId.LITERAL_static) {
                tp.setStaticInner(true);
                continue;
            } else if (t.id() == GroovyTokenId.LITERAL_class) {
                if (tp.getTargetClause() == null) {
                    tp.setTargetClause("class");
                }
                tp.add("class", current);
                break;
            } else if (t.id() == GroovyTokenId.LITERAL_interface) {
                if (tp.getTargetClause() == null) {
                    tp.setTargetClause("interface");
                }
                tp.add("interface", current);
                break;
            } else if (t.id() == GroovyTokenId.LITERAL_enum) {
                if (tp.getTargetClause() == null) {
                    tp.setTargetClause("enum");
                }
                tp.add("enum", current);
                break;
            } else {
                return null;
            }
        }
        if (tp.contains("class") || tp.contains("interface") || tp.contains("enum")) {
            return tp;
        }
        return null;
    }

    private void checkPackageInfo() {
        buildPackageInfo();
    }

    protected void buildPackageInfo() {
        int position = lexOffset;

        packageInfo = new PackageInfo(this);

        TokenSequence<?> ts = LexUtilities.getGroovyTokenSequence(document, position);

        ts.moveStart();
        // get the active token:
        Token<? extends GroovyTokenId> active = null;
        if (ts.isValid() && ts.moveNext() && ts.offset() <= document.getLength()) {
            active = (Token<? extends GroovyTokenId>) ts.token();
        }
        GroovyTokenId id = (GroovyTokenId) ts.token().id();
        while (id == GroovyTokenId.WHITESPACE
                || id == GroovyTokenId.NLS
                || id == GroovyTokenId.BLOCK_COMMENT
                || id == GroovyTokenId.LINE_COMMENT) {
            if (ts.isValid() && ts.moveNext() && ts.offset() <= document.getLength()) {
                id = (GroovyTokenId) ts.token().id();
                continue;
            } else {
                id = null;
                break;
            }
        }
        if (id == null || id != GroovyTokenId.LITERAL_package) {
            return;
        }


        int startOffset = ts.offset();
        int endOffset = startOffset + 7; // length of "package" string

        List<String> packages = new ArrayList<String>();

        boolean foundDot = false;
        boolean priorDotOrIdent = false;

        while (ts.isValid() && ts.moveNext() && ts.offset() <= document.getLength()) {
            if (priorDotOrIdent) {
                endOffset = ts.offset();
                priorDotOrIdent = false;
            }

            if (ts.token().id() == GroovyTokenId.WHITESPACE) {
                continue;
            }
            if (ts.token().id() == GroovyTokenId.LINE_COMMENT || ts.token().id() == GroovyTokenId.BLOCK_COMMENT) {
                continue;
            }

            if (ts.token().id() == GroovyTokenId.NLS && !foundDot) {
                break;
            }

            if (ts.token().id() == GroovyTokenId.NLS) {
                foundDot = false;
                continue;
            }

            if (ts.token().id() == GroovyTokenId.IDENTIFIER) {
                foundDot = false;
                packages.add(ts.token().text().toString());
                priorDotOrIdent = true;
                continue;
            }
            if (ts.token().id() == GroovyTokenId.DOT) {
                foundDot = true;
                priorDotOrIdent = true;
                continue;
            }

            break;
        }//while        

        /*
         * if ( ts.isValid() && ts.offset() <= document.getLength() ) {
         * endOffset = ts.offset(); }
         */

        packageInfo = new PackageInfo(this, true, packages, startOffset, endOffset);

    }

    protected ImportTarget getImportTarget(TokenSequence ts, int position) {
        boolean b = false;
        boolean foundStatic = false;
        boolean foundNLS = false;
        boolean foundImport = false;
        String afterCaretIdent = null;
        //boolean isStatic = false;
        List<String> packages = new ArrayList<String>();

        ts.move(position);
        if (ts.isValid() && ts.moveNext() && ts.offset() < document.getLength()) {
            if (ts.token().id() == GroovyTokenId.IDENTIFIER) {
                afterCaretIdent = ts.token().text().toString();
            }
        }

        while (ts.isValid() && ts.movePrevious() && ts.offset() >= 0) {
            if (ts.token().id() == GroovyTokenId.WHITESPACE) {
                continue;
            }
            if (ts.token().id() == GroovyTokenId.LINE_COMMENT || ts.token().id() == GroovyTokenId.BLOCK_COMMENT) {
                continue;
            }
            if (ts.token().id() == GroovyTokenId.NLS) {
                foundNLS = true;
                continue;
            }

            if (ts.token().id() != GroovyTokenId.DOT && foundNLS) {
                return null;
            }
            if (ts.token().id() == GroovyTokenId.IDENTIFIER || ts.token().id() == GroovyTokenId.STAR) {
                foundNLS = false;
                packages.add(0, ts.token().text().toString());
                continue;
            }
            if (ts.token().id() == GroovyTokenId.DOT) {
                foundNLS = false;
                continue;
            }
            if (ts.token().id() == GroovyTokenId.LITERAL_static) {
                foundNLS = false;
                foundStatic = true;
                continue;
            }

            if (ts.token().id() == GroovyTokenId.LITERAL_import) {
                foundImport = true;
                break;
            }
            return null;
        }//while        

        if (!foundImport) {
            return null;
        }
        return new ImportTarget(this, foundStatic, packages, afterCaretIdent);
    }

    protected ModifierTarget getModifierTarget(TokenSequence ts, int position) {
        boolean b = false;

        boolean insideTypeDefinition = true;
        String foundType = null;

        //boolean isStatic = false;
        List<String> modifiers = new ArrayList<String>();

        ts.move(position);

        while (ts.isValid() && ts.moveNext() && ts.offset() < document.getLength()) {

            Token<? extends GroovyTokenId> t = (Token<? extends GroovyTokenId>) ts.token();


            if (t.id() == GroovyTokenId.LINE_COMMENT || t.id() == GroovyTokenId.BLOCK_COMMENT
                    || t.id() == GroovyTokenId.WHITESPACE) {
                continue;
            }
            if (t.id() == GroovyTokenId.NLS) {
                if (modifiers.isEmpty()) {
                    /*
                     * ^ NLS abstract class
                     */
                    insideTypeDefinition = false;
                }
                continue;
            }
            System.out.println("(1)--- GroovyTokenId === " + t.id());
            if (t.id() == GroovyTokenId.LITERAL_class) {
                foundType = "class";
            } else if (t.id() == GroovyTokenId.LITERAL_interface) {
                foundType = "interface";
            } else if (t.id() == GroovyTokenId.LITERAL_enum) {
                foundType = "enum";
            } else if (t.id() == GroovyTokenId.LITERAL_public) {
                modifiers.add("public");
                System.out.println("(1.0)--- GroovyTokenId === " + t.id() + "; size=" + modifiers.size());
            } else if (t.id() == GroovyTokenId.LITERAL_private) {
                modifiers.add("private");
            } else if (t.id() == GroovyTokenId.LITERAL_protected) {
                modifiers.add("protected");
            } else if (t.id() == GroovyTokenId.FINAL) {
                modifiers.add("final");
            } else if (t.id() == GroovyTokenId.ABSTRACT) {
                modifiers.add("abstract");
            } else if (t.id() == GroovyTokenId.LITERAL_static) {
                modifiers.add("static");
            } else {
                return null;
            }
            if (foundType != null) {
                break;
            }

        }
        if (foundType == null) {
            return null;
        }

        ts.move(position);

        int afterCaretModifierCount = modifiers.size();

        if (ts.isValid() && ts.moveNext() && ts.offset() < document.getLength()) {
            //Token<? extends GroovyTokenId> t = (Token<? extends GroovyTokenId>) ts.token();
            while (ts.isValid() && ts.movePrevious() && ts.offset() >= 0) {
                if (ts.token().id() == GroovyTokenId.WHITESPACE) {
                    continue;
                }
                if (ts.token().id() == GroovyTokenId.NLS) {
                    continue;
                }
                if (ts.token().id() == GroovyTokenId.LINE_COMMENT || ts.token().id() == GroovyTokenId.BLOCK_COMMENT) {
                    continue;
                }
                if (ts.token().id() == GroovyTokenId.LITERAL_public) {
                    modifiers.add(0, "public");
                } else if (ts.token().id() == GroovyTokenId.LITERAL_private) {
                    modifiers.add(0, "private");
                } else if (ts.token().id() == GroovyTokenId.LITERAL_protected) {
                    modifiers.add(0, "protected");
                } else if (ts.token().id() == GroovyTokenId.FINAL) {
                    modifiers.add(0, "final");
                } else if (ts.token().id() == GroovyTokenId.ABSTRACT) {
                    modifiers.add(0, "abstract");
                } else if (ts.token().id() == GroovyTokenId.LITERAL_static) {
                    modifiers.add(0, "static");
                } else {
                    break;
                }
            }//while        
        }

        if (modifiers.size() != afterCaretModifierCount) {
            insideTypeDefinition = true;
        }
        for (int i = 0; i < modifiers.size(); i++) {
            System.out.println("*** " + modifiers.get(i) + " ***");
        }
        return new ModifierTarget(this, foundType, modifiers, insideTypeDefinition);
    }

    protected OutsideClassTarget getOutsideClassTarget(TokenSequence ts, int position) {
        return null;
    }

    public PackageInfo getPackageInfo() {
        return this.packageInfo;
    }

    protected PackageTarget getPackageTarget(TokenSequence ts, int position) {
        boolean b = false;
        boolean foundNLS = false;
        boolean foundPackage = false;
        String afterCaretIdent = null;
        //boolean isStatic = false;
        List<String> packages = new ArrayList<String>();

        ts.move(position);
        if (ts.isValid() && ts.moveNext() && ts.offset() < document.getLength()) {
            if (ts.token().id() == GroovyTokenId.IDENTIFIER) {
                afterCaretIdent = ts.token().text().toString();
            }
        }

        while (ts.isValid() && ts.movePrevious() && ts.offset() >= 0) {
            if (ts.token().id() == GroovyTokenId.WHITESPACE) {
                continue;
            }
            if (ts.token().id() == GroovyTokenId.LINE_COMMENT || ts.token().id() == GroovyTokenId.BLOCK_COMMENT) {
                continue;
            }
            if (ts.token().id() == GroovyTokenId.NLS) {
                foundNLS = true;
                continue;
            }

            if (ts.token().id() != GroovyTokenId.DOT && foundNLS) {
                return null;
            }
            if (ts.token().id() == GroovyTokenId.IDENTIFIER || ts.token().id() == GroovyTokenId.STAR) {
                foundNLS = false;
                packages.add(0, ts.token().text().toString());
                continue;
            }
            if (ts.token().id() == GroovyTokenId.DOT) {
                foundNLS = false;
                continue;
            }

            if (ts.token().id() == GroovyTokenId.LITERAL_package) {
                foundPackage = true;
                break;
            }
            return null;
        }//while        

        if (!foundPackage) {
            return null;
        }
        return new PackageTarget(this, packages, afterCaretIdent);
    }

    public CodeCompletionContext getCodeContext() {
        return codeContext;
    }

    public GroovyParserResult getParserResult() {
        return parserResult;
    }

    public boolean isDotCompletion() {
        return dotCompletion;
    }

    public boolean isMemberDotCompletion() {
        return memberDotCompletion;
    }

    public boolean isOptionalDotCompletion() {
        return optionalDotCompletion;
    }

    public boolean isSpreadDotCompletion() {
        return spreadDotCompletion;
    }

    public boolean isCanceled() {
        return this.canceled;
    }

    public int getAstOffset() {
        return astOffset;
    }

    public int getLexOffset() {
        return lexOffset;
    }

    public String getPrefix() {
        return prefix;
    }

    public boolean isScriptMode() {
        return scriptMode;
    }

    public AstPath getTargetPath() {
        return targetPath;
    }

    public AstPath getDotPath() {
        return dotPath;
    }

    public CompletionTarget getCompletionTarget() {
        return completionTarget;
    }

    private void checkTypeChecked() {
        buildTypeChecked();
    }

    protected void buildTypeChecked() {
        AstRootElement root = ((GroovyParserResult) parserResult).getRootElement();
        if (root == null) { // may be syntax errors
            return;
        }
        ModuleNode module = root.getModuleNode();
        if (module == null) {
            return;
        }
        if (targetClassNode == null) {
            return;
        }

        List<AnnotationNode> anodes = targetClassNode.getAnnotations();
        for (AnnotationNode an : anodes) {
            if (an.getClassNode().getName().equals(TYPECHECKEDNAME)) {
                classTypeChecked = true;
                break;
            }
        }
        if (targetMethodNode == null) {
            return;
        }
        anodes = targetMethodNode.getAnnotations();
        for (AnnotationNode an : anodes) {
            if (an.getClassNode().getName().equals(TYPECHECKEDNAME)) {
                methodTypeChecked = true;
                break;
            }
        }
    }

    private void checkDotCompletion() {
        buildDotCompletion();
    }

    protected void buildDotCompletion() {
        int position = lexOffset;
        TokenSequence<?> ts = LexUtilities.getGroovyTokenSequence(document, position);
        System.out.println("!!buildDotCompletion 1");

        ts.move(position);

        // get the active token:
        Token<? extends GroovyTokenId> active = null;
        if (ts.isValid() && ts.moveNext() && ts.offset() >= 0) {
            active = (Token<? extends GroovyTokenId>) ts.token();
        }
        System.out.println("!!buildDotCompletion 2 active.id()=" + active.id() + "; lexOffset=" + lexOffset);

        boolean foundSpaceOrNLS = false;
        GroovyTokenId id = null;
        while (ts.isValid() && ts.movePrevious() && ts.offset() >= 0) {
            id = (GroovyTokenId) ts.token().id();
            System.out.println("!!buildDotCompletion 2.1 id=" + id + "; ts.offset=" + ts.offset());

            if (id == GroovyTokenId.WHITESPACE || id == GroovyTokenId.NLS) {
                System.out.println("!!buildDotCompletion 2.2 id=" + id + "; ts.offset=" + ts.offset());

                foundSpaceOrNLS = true;
                continue;
            }
            System.out.println("!!buildDotCompletion 2.3 id=" + id + "; ts.offset=" + ts.offset());

            break;
        }


        id = (GroovyTokenId) ts.token().id();
        System.out.println("!!buildDotCompletion 3 id=" + id);
        if (id != GroovyTokenId.DOT && id != GroovyTokenId.OPTIONAL_DOT
                && id != GroovyTokenId.MEMBER_POINTER
                && id != GroovyTokenId.SPREAD_DOT
                && id != GroovyTokenId.IDENTIFIER) {
            //canceled = true;
            return;
        }
        System.out.println("!!buildDotCompletion 4");

        if (id == GroovyTokenId.IDENTIFIER && foundSpaceOrNLS) {
            //canceled = true;
            return;
        }
        System.out.println("!!buildDotCompletion 5");

        if (id == GroovyTokenId.IDENTIFIER) {
            if (!(ts.isValid() && ts.movePrevious() && ts.offset() >= 0)) {
                //canceled = true;
                return;
            }
            System.out.println("!!buildDotCompletion 5.1 id=" + ts.token().id() + "; text=" + ts.token().text());
            while (id == GroovyTokenId.WHITESPACE || id == GroovyTokenId.NLS) {
                if (!(ts.isValid() && ts.movePrevious() && ts.offset() >= 0)) {
                    return;
                }
            }

            /*
             * while (ts.isValid() && ts.movePrevious() && ts.offset() >= 0) {
             * id = (GroovyTokenId) ts.token().id(); if (id ==
             * GroovyTokenId.WHITESPACE || id == GroovyTokenId.NLS) { continue;
             * } break; }
             */
            System.out.println("!!buildDotCompletion 6 id=" + ts.token().id() + "; text=" + ts.token().text());

            id = (GroovyTokenId) ts.token().id();
            if (id != GroovyTokenId.DOT && id != GroovyTokenId.OPTIONAL_DOT
                    && id != GroovyTokenId.MEMBER_POINTER
                    && id != GroovyTokenId.SPREAD_DOT) {
                //canceled = true;
                return;
            }
            System.out.println("!!buildDotCompletion 7  id=" + ts.token().id() + "; text=" + ts.token().text());

        }

        dotCompletion = true;

        System.out.println("!!buildDotCompletion 8 ts.token().id() =" + ts.token().id());

        if (ts.token().id() == GroovyTokenId.OPTIONAL_DOT) {
            optionalDotCompletion = true;
        } else if (ts.token().id() == GroovyTokenId.MEMBER_POINTER) {
            memberDotCompletion = true;
        } else if (ts.token().id() == GroovyTokenId.SPREAD_DOT) {
            spreadDotCompletion = true;
        }
        System.out.println("!!buildDotCompletion 9 ts.token().id() =" + ts.token().id());

        while (ts.isValid() && ts.movePrevious() && ts.offset() >= 0) {
            id = (GroovyTokenId) ts.token().id();
            if (id != GroovyTokenId.WHITESPACE && id != GroovyTokenId.NLS) {
                break;
            }
        }
        System.out.println("!!buildDotCompletion 10 ts.token().id() =" + ts.token().id());

        String ctg = ts.token().id().primaryCategory();

        canceled = checkTarget(id, ctg);
        System.out.println("!!buildDotCompletion 11 ts.token().id() =" + ts.token().id() + " ;canceled=" + canceled);

        if (canceled) {
            return;
        }
        System.out.println("!!buildDotCompletion 12 ts.token().id() =" + ts.token().id());


        dotLexOffset = ts.offset();
        dotAstOffset = AstUtilities.getAstOffset(parserResult, dotLexOffset);
        dotPath = getPath(parserResult, document, dotAstOffset);

        if (dotPath == null) {
            System.out.println("!!buildDotCompletion CANCELED ");

            canceled = true;
            return;
        }
        CompletionTarget target;
        if (dotPath.leaf() instanceof Expression) {
            dotAstExpression = (Expression) dotPath.leaf();
        } else {
            target = this.getImportTarget(ts, lexOffset);
            System.out.println("!!buildDotCompletion 13 ");

            if (target != null) {
                this.completionTarget = target;
                System.out.println("!!buildDotCompletion 14 ");

            } else {

                target = this.getPackageTarget(ts, lexOffset);
                if (target != null) {
                    this.completionTarget = target;
                } else {
                    target = this.getTypeTarget(ts, lexOffset);
                    if (target != null) {
                        this.completionTarget = target;
                    }
                }

            }
        }

        System.out.println("222) !!!!! getDotCompletionContext lexOffset=" + lexOffset + "; astOffset" + astOffset);

    }

    private void checkNotDotCompletion() {
        buildNotDotCompletion();
    }

    protected void buildNotDotCompletion() {
        int position = lexOffset;
        TokenSequence<?> ts = LexUtilities.getGroovyTokenSequence(document, position);
        System.out.println("!!buildNotDotCompletion 1");

        completionTarget = this.getPackageTarget(ts, lexOffset);
        if (completionTarget == null) {
            completionTarget = this.getImportTarget(ts, lexOffset);
        }
        if (completionTarget == null) {
            completionTarget = this.getTypeTarget(ts, lexOffset);
        }
        if (completionTarget == null) {
            completionTarget = this.getModifierTarget(ts, lexOffset);
        }


        //      ts.move(position);

        // get the active token:
/*
         * Token<? extends GroovyTokenId> active = null; if (ts.isValid() &&
         * ts.moveNext() && ts.offset() >= 0) { active = (Token<? extends
         * GroovyTokenId>) ts.token(); }
         * System.out.println("!!buildDotCompletion 2 active.id()=" +
         * active.id() + "; lexOffset=" + lexOffset);
         *
         * boolean foundNLS = false; boolean foundSpace = false;
         *
         * GroovyTokenId id = null; while (ts.isValid() && ts.movePrevious() &&
         * ts.offset() >= 0) { id = (GroovyTokenId) ts.token().id();
         * System.out.println("!!buildNotDotCompletion 2.1 id=" + id + ";
         * ts.offset=" + ts.offset());
         *
         * if (id == GroovyTokenId.WHITESPACE) {
         * System.out.println("!!buildNotDotCompletion 2.2 id=" + id + ";
         * ts.offset=" + ts.offset());
         *
         * foundSpace = true; continue; } else if (id == GroovyTokenId.NLS) {
         * System.out.println("!!buildNotDotCompletion 2.2 id=" + id + ";
         * ts.offset=" + ts.offset());
         *
         * foundNLS = true; continue; }
         *
         * System.out.println("!!buildNotDotCompletion 2.3 id=" + id + ";
         * ts.offset=" + ts.offset());
         *
         * break; } System.out.println("!!buildNotDotCompletion 3");
         *
         * id = (GroovyTokenId) ts.token().id(); GroovyTokenId keywordId = null;
         * GroovyTokenId operatorId = null; GroovyTokenId identifierId = null;
         *
         *
         * if ("keyword".equals(id.primaryCategory())) { keywordId = id; } if
         * ("operator".equals(id.primaryCategory())) { operatorId = id; } if
         * ("identifier".equals(id.primaryCategory())) { identifierId = id; }
         *
         * System.out.println("!!buildDotCompletion 4");
         *
         * // if (id == GroovyTokenId.IDENTIFIER && foundSpaceOrNLS) { //
         * return; // }
         *
         * System.out.println("!!buildDotCompletion 5");
         *
         * if (id == GroovyTokenId.IDENTIFIER) { if (!(ts.isValid() &&
         * ts.movePrevious() && ts.offset() >= 0)) { //canceled = true; return;
         * } while (ts.isValid() && ts.movePrevious() && ts.offset() >= 0) { id
         * = (GroovyTokenId) ts.token().id(); if (id == GroovyTokenId.WHITESPACE
         * || id == GroovyTokenId.NLS) { continue; } break; }
         * System.out.println("!!buildDotCompletion 6");
         *
         * id = (GroovyTokenId) ts.token().id(); if (id != GroovyTokenId.DOT &&
         * id != GroovyTokenId.OPTIONAL_DOT && id !=
         * GroovyTokenId.MEMBER_POINTER && id != GroovyTokenId.SPREAD_DOT) {
         * //canceled = true; return; } System.out.println("!!buildDotCompletion
         * 7");
         *
         * }
         *
         * dotCompletion = true;
         *
         * System.out.println("!!buildDotCompletion 8 ts.token().id() =" +
         * ts.token().id());
         *
         * if (ts.token().id() == GroovyTokenId.OPTIONAL_DOT) {
         * optionalDotCompletion = true; } else if (ts.token().id() ==
         * GroovyTokenId.MEMBER_POINTER) { memberDotCompletion = true; } else if
         * (ts.token().id() == GroovyTokenId.SPREAD_DOT) { spreadDotCompletion =
         * true; } System.out.println("!!buildDotCompletion 9 ts.token().id() ="
         * + ts.token().id());
         *
         * while (ts.isValid() && ts.movePrevious() && ts.offset() >= 0) { id =
         * (GroovyTokenId) ts.token().id(); if (id != GroovyTokenId.WHITESPACE
         * && id != GroovyTokenId.NLS) { break; } }
         * System.out.println("!!buildDotCompletion 10 ts.token().id() =" +
         * ts.token().id());
         *
         * String ctg = ts.token().id().primaryCategory();
         *
         * canceled = checkTarget(id, ctg);
         * System.out.println("!!buildDotCompletion 11 ts.token().id() =" +
         * ts.token().id() + " ;canceled=" + canceled);
         *
         * if (canceled) { return; } System.out.println("!!buildDotCompletion 12
         * ts.token().id() =" + ts.token().id());
         *
         *
         * dotLexOffset = ts.offset(); dotAstOffset =
         * AstUtilities.getAstOffset(parserResult, dotLexOffset); dotPath =
         * getPath(parserResult, document, dotAstOffset);
         *
         * if (dotPath == null) { canceled = true; return; } CompletionTarget
         * target; if (dotPath.leaf() instanceof Expression) { dotAstExpression
         * = (Expression) dotPath.leaf(); } else { target =
         * this.getImportTarget(ts, lexOffset); if (target != null) {
         * this.completionTarget = target; } else {
         *
         * target = this.getPackageTarget(ts, lexOffset); if (target != null) {
         * this.completionTarget = target; } else { target =
         * this.getTypeTarget(ts, lexOffset); if (target != null) {
         * this.completionTarget = target; } }
         *
         * }
         * }
         *
         * System.out.println("222) !!!!! getDotCompletionContext lexOffset=" +
         * lexOffset + "; astOffset" + astOffset);
         */
    }

    protected boolean checkTarget(GroovyTokenId id, String ctg) {
        boolean b = false;
        if ("operator".equals(ctg) || "keyword".equals(ctg)) {
            b = true; // to cancel
        } else if ("separator".equals(ctg)) {
            if (id == GroovyTokenId.LPAREN || id == GroovyTokenId.LBRACE
                    || id == GroovyTokenId.LBRACKET || id == GroovyTokenId.COMMA) {
                b = true;
            }
        }

        return b;
    }

    private AstPath getPath(ParserResult info, BaseDocument doc, int astOffset) {
        ASTNode root = AstUtilities.getRoot(info);
        System.out.println("COMPLETION HANDLER::getPath");

        // in some cases we can not repair the code, therefore root == null
        // therefore we can not complete. See # 131317

        if (root == null) {
            return null;
        }
        return new AstPath(root, astOffset, doc);
    }
}
