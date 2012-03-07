package org.netbeans.modules.groovy.editor.api.parser;

import org.netbeans.api.project.*;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyResourceLoader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SimpleMessage;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.netbeans.api.java.classpath.ClassPath;
import org.netbeans.modules.groovy.editor.api.AstUtilities;
import org.netbeans.modules.groovy.editor.api.GroovyUtils;
import org.netbeans.modules.groovy.editor.api.GroovyCompilerErrorID;
import org.netbeans.modules.parsing.api.Task;
import org.netbeans.modules.parsing.spi.ParseException;
import org.netbeans.spi.java.classpath.support.ClassPathSupport;
import org.openide.filesystems.FileObject;
import org.openide.util.Exceptions;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.control.*;
import org.netbeans.api.java.source.*;
import org.netbeans.api.lexer.Token;
import org.netbeans.api.lexer.TokenSequence;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.csl.api.Error;
import org.netbeans.modules.csl.api.OffsetRange;
import org.netbeans.modules.csl.api.Severity;
import org.netbeans.modules.csl.spi.GsfUtilities;
import org.netbeans.modules.groovy.editor.api.lexer.GroovyTokenId;
import org.netbeans.modules.groovy.editor.api.lexer.LexUtilities;
import org.netbeans.modules.groovy.editor.java.ElementSearch;
import org.netbeans.modules.parsing.api.Snapshot;
import org.netbeans.modules.parsing.spi.Parser;
import org.netbeans.modules.parsing.spi.SourceModificationEvent;
import org.openide.filesystems.URLMapper;

/**
 *
 * @author Martin Adamek
 */
public class EditorParser  extends Parser {

    private static final Logger LOG = Logger.getLogger(EditorParser.class.getName());
    private static final AtomicLong PARSING_TIME = new AtomicLong(0);
    private static final AtomicInteger PARSING_COUNT = new AtomicInteger(0);
    private static final ClassPath EMPTY_CLASSPATH = ClassPathSupport.createClassPath(new URL[]{});
    private static long maximumParsingTime;
    private GroovyParserResult lastResult;
    private AtomicBoolean cancelled = new AtomicBoolean();
    public Project project;

    public EditorParser() {
        super();
    }

    @Override
    public void addChangeListener(ChangeListener changeListener) {
        // FIXME parsing API
    }

    @Override
    public void removeChangeListener(ChangeListener changeListener) {
        // FIXME parsing API
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    @Override
    public void cancel() {
        //LOG.log(Level.FINEST, "Parser cancelled");
        System.out.println(" &&&&&&&&&&&&&&&&&&&&&&&&  CANCELED 0--------- ");
        cancelled.set(true);
        //lastResult = null;        
    }

    @Override
    public void cancel(Parser.CancelReason reason, SourceModificationEvent event) {

//        System.out.println(" &&&&&&&&&&&&&&&&&&&&&&&&  CANCELED 1--------- " + reason);
        cancelled.set(true);

    }

    @Override
    public Result getResult(Task task) throws ParseException {
        System.out.println(" ((((((((((((  getResilt(Task) --------- task=" + task);
        assert lastResult != null : "getResult() called prior parse()"; //NOI18N
        return lastResult;
    }

    @Override
    public void parse(Snapshot snapshot, Task task, SourceModificationEvent event) throws ParseException {
        cancelled.set(false);


        Context context = new Context(snapshot, event, task);
        System.out.println(" ********** ------ parse() --------- task=" + task + "; sourceChaged=" + event.sourceChanged() + "; CaretOffset=" + context.caretOffset);

        final Set<Error> errors = new HashSet<Error>();
        context.errorHandler = new ParseErrorHandler() {

            @Override
            public void error(Error error) {
                errors.add(error);
            }
        };
        lastResult = null;
        lastResult = parseBuffer(context, Sanitize.NONE);
//        System.out.println(" WE'VE GOT AT LAST PARSERESULT !!!!!!!!!!!!!!!!!!");
        if (lastResult != null) {
//            System.out.println(" LASTRESULT != NULL !!!!!!!!!!!!!!!!!!");
            lastResult.setErrors(errors);
        } else {
//            System.out.println(" LASTRESULT == NULL !!!!!!!!!!!!!!!!!!");
            // FIXME just temporary
            lastResult = createParseResult(snapshot, null, null,context.getClasspathInfo(),context.getJavaSource());
        }
    }

    @SuppressWarnings("unchecked")
    GroovyParserResult parseBuffer(final Context context, final Sanitize sanitizing) {
        System.out.println("------------------ parseBuffer 0 --------- " + context.getTask());

        if (isCancelled()) {
            return null;
        }
        
//        System.out.println("PARSE BUFFER sanitizing = " + sanitizing);
        boolean sanitizedSource = false;
        String source = context.source;

        if (!((sanitizing == Sanitize.NONE) || (sanitizing == Sanitize.NEVER))) {
            boolean ok = sanitizeSource(context, sanitizing);

            if (ok) {
                assert context.sanitizedSource != null;
                sanitizedSource = true;
                source = context.sanitizedSource;
                if (context.sanitizedSource != null) {
//                    System.out.println(" --- 2) PARSE BUFFER context.sanitizedSource.length = " + context.sanitizedSource.length());
//                    System.out.println(" --- ----- " + source);
                }
            } else {
                // Try next trick
//                System.out.println(" --- 3) PARSE BUFFER sanitizing (Next Trick) = " + sanitizing);
                return sanitize(context, sanitizing);
            }
        }

        if (sanitizing == Sanitize.NONE) {
            context.errorOffset = -1;
        }

        // *** -------------------------------------------------------
        prepareCompilationUnit(context, source);
        /*
         * if ( context.completionTask && lastResult != null && (sanitizing ==
         * Sanitize.NONE) || (sanitizing == Sanitize.NEVER)) {
         * System.out.println("PARSE BUFFER LAST RESULT != NULL "); return
         * lastResult; }
         */
        CompilationUnit compilationUnit = tryCompile(context, source);
        // *** -------------------------------------------------------

        if (this.isCancelled()) {
            return null;
        }
        
        
        CompileUnit compileUnit = compilationUnit.getAST();
        List<ModuleNode> modules = compileUnit.getModules();
        TypeElement typeElement = EditorParser.getTypeElement("org.learn.SimpleG3", context.javaSource);
        EditorParser.getResources(context.javaSource, typeElement);
        
        
//compilationUnit.getAST().
        ModuleNode module = null;

        String fileName = context.getFileName();
        for (ModuleNode moduleNode : modules) {
            if (fileName.equals(moduleNode.getContext().getName())) {
                module = moduleNode;
            }
        }
        System.out.println("BEFORE HANDLE ERROR COLLECTOR errorCount=" + compilationUnit.getErrorCollector().getErrorCount());

        handleErrorCollector(compilationUnit.getErrorCollector(), context, module, false, sanitizing);

        if (module != null) {
            context.sanitized = sanitizing;
            GroovyParserResult r = createParseResult(context.snapshot, module, compilationUnit.getErrorCollector(),context.getClasspathInfo(),context.getJavaSource());
            r.setSanitized(context.sanitized, context.sanitizedRange, context.sanitizedContents);
            return r;
        } else {
//            System.out.println("@@@@@@@@@@@@@@@@@ START COMPILE (SANITIZE) :  ");
            return sanitize(context, sanitizing);
        }


    }// parseBuffer
public static FileObject sroot;
public static FileObject srootFO;

    protected void prepareCompilationUnit(Context context, String source) {
        FileObject fo = context.snapshot.getSource().getFileObject();
srootFO = fo;        
//        project = FileOwnerQuery.getOwner(fo);
        
        ClassPath bootPath = fo == null ? EMPTY_CLASSPATH : ClassPath.getClassPath(fo, ClassPath.BOOT);
        ClassPath compilePath = fo == null ? EMPTY_CLASSPATH : ClassPath.getClassPath(fo, ClassPath.COMPILE);
        ClassPath sourcePath = fo == null ? EMPTY_CLASSPATH : ClassPath.getClassPath(fo, ClassPath.SOURCE);
        FileObject[] roots = sourcePath.getRoots();
sroot = roots[0];

        ClassPath cp = ClassPathSupport.createProxyClassPath(bootPath, compilePath, sourcePath);

        context.configuration = new CompilerConfiguration();
        context.classLoader = new ParsingClassLoader(cp, context.configuration);
        context.transformationLoader = new TransformationClassLoader(CompilationUnit.class.getClassLoader(),
                cp, context.configuration);

        ClasspathInfo cpInfo = ClasspathInfo.create(
                bootPath == null ? EMPTY_CLASSPATH : bootPath,
                compilePath == null ? EMPTY_CLASSPATH : compilePath,
                sourcePath);
        context.javaSource = JavaSource.create(cpInfo);
        context.classpathInfo = cpInfo;
        boolean completionTask = context.task.getClass().getName().contains("org.netbeans.modules.csl.editor.completion.GsfCompletionProvider");
        context.completionTask = completionTask;
    }

    protected CompilationUnit tryCompile(Context context, String source) {

        CompilationUnit compilationUnit = context.getCompilationUnit(this, context, source);
        if (isCancelled()) {
            this.cancel();
            return compilationUnit;
        }

        long startCompile = 0;
        long endCompile = 0;
        try {
            System.out.println("------------------ START COMPILE 0 ---------");

            try {
                System.out.println("------------------ START COMPILE 1 ---------");
                startCompile = System.currentTimeMillis();
                System.out.println("------------------ BEFORE COMPILE !!!  ---------");
                if (context.completionTask) {
                    long startSem = System.currentTimeMillis();


                    // compilationUnit.compile(Phases.SEMANTIC_ANALYSIS);


                    compilationUnit.compile(Phases.SEMANTIC_ANALYSIS);
                    long endSem = System.currentTimeMillis();

                    System.out.println("------------------ SEMANTIC_ANALYSIS OF COMPLETION  --------- TIME(ms)=" + (endSem - startSem));
                    //resolvePendings(compilationUnit);
                    compilationUnit.compile(Phases.INSTRUCTION_SELECTION);
                    long endPend = System.currentTimeMillis();
                    System.out.println("------------------ CANONICALIZATION OF COMPLETION  --------- TIME(ms)=" + (endPend - endSem));
                    //System.out.println("------------------ RESOLVE PENDINGS OF COMPLETION  --------- TIME(ms)=" + (endPend - endSem));

//                    System.out.println("------------------ CANONICALIZATION OF COMPLETION  --------- TIME(ms)=" + (endCan - endPend));                                        
//                    long endCan = System.currentTimeMillis();

                } else {
                    long startSem = System.currentTimeMillis();

                    //compilationUnit.compile(Phases.SEMANTIC_ANALYSIS);
                    long endSem = System.currentTimeMillis();
                    System.out.println("============== SEMANTIC_ANALYSIS --------- TIME(MS)=" + (endSem - startSem));
                    System.out.println("============== START INSTRUCTION_SELECTION --------- TIME(MS)=" + (endSem - startSem));
/*                    compilationUnit.compile(Phases.CONVERSION);                    
                    CompileUnit cAST = compilationUnit.getAST();
                    
                    List<ModuleNode> cmodules = cAST.getModules();
                    for ( ModuleNode mn : cmodules) {
                        
                        System.out.println("==== CONVERSION getMainClassName " + mn.getMainClassName());                        
                        for ( ClassNode cn : mn.getClasses()) {
                            System.out.println("==== CONVERSION ClassNode = " + cn);                                                    
                            List<AnnotationNode> anodes = cn.getAnnotations();
                            if ( "org.learn.MyCompl1".equals(cn.getName())) {
                                int r = -1;
                                for ( int i=0; i < anodes.size();i++) {
                                    AnnotationNode an = anodes.get(i);
                                    System.out.println("==== CONVERSION annotation classnode = " + an.getClassNode());                                                    
                                    System.out.println("==== CONVERSION annotation.toString = " + an);                                                                                        
                                    if ( an.getClassNode().getName().contains("TypeCheckedMy")) {
                                        r = i;
                                        break;
                                    }
                                }
                                if ( r >= 0 ) {
                                    AnnotationNode an = anodes.get(r);                                    
                                    if ( an.getClassNode().getName().contains("TypeCheckedMy")) {
                                        ClassNode acn = cAST.getClass("org.netbeans.modules.groovy.editor.stc.TypeChecked_IDE");
                                        if ( acn != null) {
                                            System.out.println("==== CONVERSION new annotation.classNode = " + acn + "; acn.getClass=" + acn.getClass());                                                                                        
                                            //AnnotationNode tc = new AnnotationNode(acn);
                                            //anodes.set(r, tc);
                                        }
                                        else
                                        System.out.println("==== CONVERSION new annotation.classNode == NULL ");
                                        
                                        
                                        
                                    }
                                    
                                }
                                
                            }
                        }
                    }
                    * 
                    */
                    compilationUnit.compile(Phases.INSTRUCTION_SELECTION);
                    long endCan = System.currentTimeMillis();
                    System.out.println("============== INSTRUCTION_SELECTION --------- TIME(MS)=" + (endCan - startSem));
                    /*
                     * ClassNode node = null; try { CompileUnit ast =
                     * ((CompilationUnitStrong)compilationUnit).getAST(); node =
                     * ((CompilationUnitStrong.CompileUnit)ast).myTemp;
                     *
                     * System.out.println("111 CLASSNODE name=" +
                     * node.getName()+ "; getTypeClass()=" +
                     * node.getTypeClass()); } catch (Throwable ex) {
                     * System.out.println("111 CLASSNODE TROWABLE NAME=" +
                     * node.getName()); }
                     */
                }

                endCompile = System.currentTimeMillis();

                System.out.println("------------------ END COMPILE NORMAL ---------" + (endCompile - startCompile) + "; startTime= " + endCompile);

                //compilationUnit.compile(Phases.PARSING);
            } catch (CancellationException ex) {
                System.out.println("------------------ CancellationException ---------");

                // cancelled probably
                if (isCancelled()) {
                    this.cancel();
                    return compilationUnit;
                }
                throw ex;
            }
        } catch (Throwable e) {
            //((EditorCompilationUnit)compilationUnit).getAST().
            endCompile = System.currentTimeMillis();
            System.out.println("------------------ END COMPILE Throwable msg --" + e.getMessage() + "; TIME(ms)=" + (endCompile - startCompile));

        }
        return compilationUnit;
    }

    protected GroovyParserResult createParseResult(Snapshot snapshot, ModuleNode rootNode, ErrorCollector errorCollector, ClasspathInfo cpInfo, JavaSource javaSource) {
        GroovyParserResult parserResult = new GroovyParserResult(this, snapshot, rootNode, errorCollector);
        parserResult.setClassPath(cpInfo);
        parserResult.setJavaSource(javaSource);
        return parserResult;
    }
/*
    private boolean sanitizeSource(Context context, Sanitize sanitizing) {
        System.out.println(" --- 1) START sanitizeSource sanitizing = " + sanitizing);
        if (sanitizing == Sanitize.MISSING_END) {
            if (context.sanitizedSource != null) {
                //context.sanitizedSource = context.source + "}";
                context.sanitizedSource = context.sanitizedSource + "}";

                int start = context.sanitizedSource.length();
                context.sanitizedRange = new OffsetRange(start, start + 1);
                //context.sanitizedContents = "";
                return true;
            } else {
                context.sanitizedSource = context.source + "}";
                int start = context.source.length();
                context.sanitizedRange = new OffsetRange(start, start + 1);
                context.sanitizedContents = "";
                return true;

            }
        }

        int offset = context.caretOffset;

        // Let caretOffset represent the offset of the portion of the buffer we'll be operating on
        if ((sanitizing == Sanitize.ERROR_DOT) || (sanitizing == Sanitize.ERROR_LINE)) {
            offset = context.errorOffset;
//            System.out.println("SANITIZESOURCE 1) sanitizeSource  errorOffset=" + offset);

        }

        // Don't attempt cleaning up the source if we don't have the buffer position we need
        if (offset == -1) {
            return false;
        }

        // The user might be editing around the given caretOffset.
        // See if it looks modified
        // Insert an end statement? Insert a } marker?
        String doc = context.source;
        if (context.sanitizedSource != null) {
//            System.out.println("--- DOC TAKEN FROM sanitized. sanitizing= " + sanitizing);
            doc = context.sanitizedSource;
//            System.out.println("SANITIZESOURCE 2) sanitizeSource  DOC=" + doc);

        } else {
//            System.out.println("--- DOC TAKEN FROM NOT sanitized. sanitizing= " + sanitizing);
            doc = context.source;
        }

        if (offset > doc.length()) {
            return false;
        }

        try {
            // Sometimes the offset shows up on the next line
            if (GroovyUtils.isRowEmpty(doc, offset) || GroovyUtils.isRowWhite(doc, offset)) {
                offset = GroovyUtils.getRowStart(doc, offset) - 1;
                if (offset < 0) {
                    offset = 0;
                }
            }

            if (!(GroovyUtils.isRowEmpty(doc, offset) || GroovyUtils.isRowWhite(doc, offset))) {
                if ((sanitizing == Sanitize.EDITED_LINE) || (sanitizing == Sanitize.ERROR_LINE)) {

                    if (sanitizing == Sanitize.ERROR_LINE) {
                        // groovy-only, this is not done in Ruby or JavaScript sanitization
                        // look backwards if there is unfinished line with trailing dot and remove that dot
                        TokenSequence<? extends GroovyTokenId> ts = (context.document != null)
                                ? LexUtilities.getPositionedSequence(context.document, offset)
                                : null;

                        if (ts != null) {
                            Token<? extends GroovyTokenId> token = LexUtilities.findPreviousNonWsNonComment(ts);
                            if (token.id() == GroovyTokenId.DOT) {
                                int removeStart = ts.offset();
                                int removeEnd = removeStart + 1;
                                StringBuilder sb = new StringBuilder(doc.length());
                                sb.append(doc.substring(0, removeStart));
                                sb.append(' ');

                                if (removeEnd < doc.length()) {
                                    sb.append(doc.substring(removeEnd, doc.length()));
                                }
                                assert sb.length() == doc.length();
                                context.sanitizedRange = new OffsetRange(removeStart, removeEnd);
                                context.sanitizedSource = sb.toString();
                                context.sanitizedContents = doc.substring(removeStart, removeEnd);
                                return true;
                            }
                        }
                    }

                    // See if I should try to remove the current line, since it has text on it.
                    int lineEnd = GroovyUtils.getRowLastNonWhite(doc, offset);

                    if (lineEnd != -1) {
                        lineEnd++; // lineEnd is exclusive, not inclusive
                        StringBuilder sb = new StringBuilder(doc.length());
                        int lineStart = GroovyUtils.getRowStart(doc, offset);
                        if (lineEnd >= lineStart + 2) {
                            sb.append(doc.substring(0, lineStart));
                            sb.append("//");
                            int rest = lineStart + 2;
                            if (rest < doc.length()) {
                                sb.append(doc.substring(rest, doc.length()));
                            }
                        } else {
                            // A line with just one character - can't replace with a comment
                            // Just replace the char with a space
                            sb.append(doc.substring(0, lineStart));
                            sb.append(" ");
                            int rest = lineStart + 1;
                            if (rest < doc.length()) {
                                sb.append(doc.substring(rest, doc.length()));
                            }

                        }

                        assert sb.length() == doc.length();

                        context.sanitizedRange = new OffsetRange(lineStart, lineEnd);
                        context.sanitizedSource = sb.toString();
                        context.sanitizedContents = doc.substring(lineStart, lineEnd);
                        return true;
                    }
                } else {
                    assert sanitizing == Sanitize.ERROR_DOT || sanitizing == Sanitize.EDITED_DOT;
                    // Try nuking dots/colons from this line
                    // See if I should try to remove the current line, since it has text on it.
                    int lineStart = GroovyUtils.getRowStart(doc, offset);
                    int lineEnd = offset - 1;
                    while (lineEnd >= lineStart && lineEnd < doc.length()) {
                        if (!Character.isWhitespace(doc.charAt(lineEnd))) {
                            break;
                        }
                        lineEnd--;
                    }
                    if (lineEnd > lineStart) {
                        StringBuilder sb = new StringBuilder(doc.length());
                        String line = doc.substring(lineStart, lineEnd + 1);
                        int removeChars = 0;
                        int removeEnd = lineEnd + 1;

                        if (line.endsWith("?.") || line.endsWith(".&")) { // NOI18N
                            removeChars = 2;
                        } else if (line.endsWith(".") || line.endsWith("(")) { // NOI18N
                            removeChars = 1;
                        } else if (line.endsWith(",")) { // NOI18N                            removeChars = 1;
                            removeChars = 1;
                        } else if (line.endsWith(", ")) { // NOI18N
                            removeChars = 2;
                        } else if (line.endsWith(",)")) { // NOI18N
                            // Handle lone comma in parameter list - e.g.
                            // type "foo(a," -> you end up with "foo(a,|)" which doesn't parse - but
                            // the line ends with ")", not "," !
                            // Just remove the comma
                            removeChars = 1;
                            removeEnd--;
                        } else if (line.endsWith(", )")) { // NOI18N
                            // Just remove the comma
                            removeChars = 1;
                            removeEnd -= 2;
                        }

                        if (removeChars == 0) {
                            return false;
                        }

                        int removeStart = removeEnd - removeChars;

                        sb.append(doc.substring(0, removeStart));

                        for (int i = 0; i < removeChars; i++) {
                            sb.append(' ');
                        }

                        if (removeEnd < doc.length()) {
                            sb.append(doc.substring(removeEnd, doc.length()));
                        }
                        assert sb.length() == doc.length();

                        context.sanitizedRange = new OffsetRange(removeStart, removeEnd);

                        context.sanitizedSource = sb.toString();
                        context.sanitizedContents = doc.substring(removeStart, removeEnd);
                        return true;
                    }
                }
            }
        } catch (BadLocationException ble) {
            Exceptions.printStackTrace(ble);
        }

        return false;
    }

    @SuppressWarnings("fallthrough")
    private GroovyParserResult sanitize(final Context context,
            final Sanitize sanitizing) {
        switch (sanitizing) {
            case NEVER:
                return createParseResult(context.snapshot, null, null);

            case NONE:

                // We've currently tried with no sanitization: try first level
                // of sanitization - removing dots/colons at the edited offset.
                // First try removing the dots or double colons around the failing position
                if (context.caretOffset != -1) {
                    return parseBuffer(context, Sanitize.EDITED_DOT);
                }
            case EDITED_DOT:
                if (context.errorOffset != -1 && context.errorOffset != context.caretOffset) {
                    return parseBuffer(context, Sanitize.ERROR_LINE);
                }
            //break;
            // Fall through to try the next trick
            case ERROR_LINE:

                // Messing with the error line didn't work - we could try "around" the error line
                // but I'm not attempting that now.
                // Finally try removing the whole line around the user editing position
                // (which could be far from where the error is showing up - but if you're typing
                // say a new "def" statement in a class, this will show up as an error on a mismatched
                // "end" statement rather than here
                if (context.errorOffset != -1) {
                    //return parseBuffer(context, Sanitize.EDITED_LINE);
                    return parseBuffer(context, Sanitize.ERROR_LINE);
                }
                break;
            default:
                // We're out of tricks - just return the failed parse result
                return createParseResult(context.snapshot, null, null);
        }
        return createParseResult(context.snapshot, null, null);
    }
*/
    
    private boolean sanitizeSource(Context context, Sanitize sanitizing) {

        if (sanitizing == Sanitize.MISSING_END) {
            context.sanitizedSource = context.source + "}";
            int start = context.source.length();
            context.sanitizedRange = new OffsetRange(start, start+1);
            context.sanitizedContents = "";
            return true;
        }

        int offset = context.caretOffset;

        // Let caretOffset represent the offset of the portion of the buffer we'll be operating on
        if ((sanitizing == Sanitize.ERROR_DOT) || (sanitizing == Sanitize.ERROR_LINE)) {
            offset = context.errorOffset;
        }

        // Don't attempt cleaning up the source if we don't have the buffer position we need
        if (offset == -1) {
            return false;
        }

        // The user might be editing around the given caretOffset.
        // See if it looks modified
        // Insert an end statement? Insert a } marker?
        String doc = context.source;
        if (offset > doc.length()) {
            return false;
        }

        try {
            // Sometimes the offset shows up on the next line
            if (GroovyUtils.isRowEmpty(doc, offset) || GroovyUtils.isRowWhite(doc, offset)) {
                offset = GroovyUtils.getRowStart(doc, offset)-1;
                if (offset < 0) {
                    offset = 0;
                }
            }

            if (!(GroovyUtils.isRowEmpty(doc, offset) || GroovyUtils.isRowWhite(doc, offset))) {
                if ((sanitizing == Sanitize.EDITED_LINE) || (sanitizing == Sanitize.ERROR_LINE)) {

                    if (sanitizing == Sanitize.ERROR_LINE) {
                        // groovy-only, this is not done in Ruby or JavaScript sanitization
                        // look backwards if there is unfinished line with trailing dot and remove that dot
                        TokenSequence<? extends GroovyTokenId> ts = (context.document != null)
                                ? LexUtilities.getPositionedSequence(context.document, offset)
                                : null;

                        if (ts != null) {
                            Token<? extends GroovyTokenId> token = LexUtilities.findPreviousNonWsNonComment(ts);
                            if (token.id() == GroovyTokenId.DOT) {
                                int removeStart = ts.offset();
                                int removeEnd = removeStart + 1;
                                StringBuilder sb = new StringBuilder(doc.length());
                                sb.append(doc.substring(0, removeStart));
                                sb.append(' ');
                                if (removeEnd < doc.length()) {
                                    sb.append(doc.substring(removeEnd, doc.length()));
                                }
                                assert sb.length() == doc.length();
                                context.sanitizedRange = new OffsetRange(removeStart, removeEnd);
                                context.sanitizedSource = sb.toString();
                                context.sanitizedContents = doc.substring(removeStart, removeEnd);
                                return true;
                            }
                        }
                    }

                    // See if I should try to remove the current line, since it has text on it.
                    int lineEnd = GroovyUtils.getRowLastNonWhite(doc, offset);

                    if (lineEnd != -1) {
                        lineEnd++; // lineEnd is exclusive, not inclusive
                        StringBuilder sb = new StringBuilder(doc.length());
                        int lineStart = GroovyUtils.getRowStart(doc, offset);
                        if (lineEnd >= lineStart+2) {
                            sb.append(doc.substring(0, lineStart));
                            sb.append("//");
                            int rest = lineStart + 2;
                            if (rest < doc.length()) {
                                sb.append(doc.substring(rest, doc.length()));
                            }
                        } else {
                            // A line with just one character - can't replace with a comment
                            // Just replace the char with a space
                            sb.append(doc.substring(0, lineStart));
                            sb.append(" ");
                            int rest = lineStart + 1;
                            if (rest < doc.length()) {
                                sb.append(doc.substring(rest, doc.length()));
                            }

                        }

                        assert sb.length() == doc.length();

                        context.sanitizedRange = new OffsetRange(lineStart, lineEnd);
                        context.sanitizedSource = sb.toString();
                        context.sanitizedContents = doc.substring(lineStart, lineEnd);
                        return true;
                    }
                } else {
                    assert sanitizing == Sanitize.ERROR_DOT || sanitizing == Sanitize.EDITED_DOT;
                    // Try nuking dots/colons from this line
                    // See if I should try to remove the current line, since it has text on it.
                    int lineStart = GroovyUtils.getRowStart(doc, offset);
                    int lineEnd = offset-1;
                    while (lineEnd >= lineStart && lineEnd < doc.length()) {
                        if (!Character.isWhitespace(doc.charAt(lineEnd))) {
                            break;
                        }
                        lineEnd--;
                    }
                    if (lineEnd > lineStart) {
                        StringBuilder sb = new StringBuilder(doc.length());
                        String line = doc.substring(lineStart, lineEnd + 1);
                        int removeChars = 0;
                        int removeEnd = lineEnd+1;

                        if (line.endsWith("?.") || line.endsWith(".&")) { // NOI18N
                            removeChars = 2;
                        } else if (line.endsWith(".") || line.endsWith("(")) { // NOI18N
                            removeChars = 1;
                        } else if (line.endsWith(",")) { // NOI18N                            removeChars = 1;
                            removeChars = 1;
                        } else if (line.endsWith(", ")) { // NOI18N
                            removeChars = 2;
                        } else if (line.endsWith(",)")) { // NOI18N
                            // Handle lone comma in parameter list - e.g.
                            // type "foo(a," -> you end up with "foo(a,|)" which doesn't parse - but
                            // the line ends with ")", not "," !
                            // Just remove the comma
                            removeChars = 1;
                            removeEnd--;
                        } else if (line.endsWith(", )")) { // NOI18N
                            // Just remove the comma
                            removeChars = 1;
                            removeEnd -= 2;
                        }

                        if (removeChars == 0) {
                            return false;
                        }

                        int removeStart = removeEnd-removeChars;

                        sb.append(doc.substring(0, removeStart));

                        for (int i = 0; i < removeChars; i++) {
                            sb.append(' ');
                        }

                        if (removeEnd < doc.length()) {
                            sb.append(doc.substring(removeEnd, doc.length()));
                        }
                        assert sb.length() == doc.length();

                        context.sanitizedRange = new OffsetRange(removeStart, removeEnd);
                        context.sanitizedSource = sb.toString();
                        context.sanitizedContents = doc.substring(removeStart, removeEnd);
                        return true;
                    }
                }
            }
        } catch (BadLocationException ble) {
            Exceptions.printStackTrace(ble);
        }

        return false;
    }

    @SuppressWarnings("fallthrough")
    private GroovyParserResult sanitize(final Context context,
        final Sanitize sanitizing) {

        switch (sanitizing) {
        case NEVER:
            return createParseResult(context.snapshot, null, null,context.getClasspathInfo(),context.getJavaSource());

        case NONE:

            // We've currently tried with no sanitization: try first level
            // of sanitization - removing dots/colons at the edited offset.
            // First try removing the dots or double colons around the failing position
            if (context.caretOffset != -1) {
                return parseBuffer(context, Sanitize.EDITED_DOT);
            }

        // Fall through to try the next trick
        case EDITED_DOT:

            // We've tried editing the caret location - now try editing the error location
            // (Don't bother doing this if errorOffset==caretOffset since that would try the same
            // source as EDITED_DOT which has no better chance of succeeding...)
            if (context.errorOffset != -1 && context.errorOffset != context.caretOffset) {
                return parseBuffer(context, Sanitize.ERROR_DOT);
            }

        // Fall through to try the next trick
        case ERROR_DOT:

            // We've tried removing dots - now try removing the whole line at the error position
            if (context.errorOffset != -1) {
                return parseBuffer(context, Sanitize.ERROR_LINE);
            }

        // Fall through to try the next trick
        case ERROR_LINE:

            // Messing with the error line didn't work - we could try "around" the error line
            // but I'm not attempting that now.
            // Finally try removing the whole line around the user editing position
            // (which could be far from where the error is showing up - but if you're typing
            // say a new "def" statement in a class, this will show up as an error on a mismatched
            // "end" statement rather than here
            if (context.caretOffset != -1) {
                return parseBuffer(context, Sanitize.EDITED_LINE);
            }

        // Fall through to try the next trick
        case EDITED_LINE:
            return parseBuffer(context, Sanitize.MISSING_END);

        // Fall through for default handling
        case MISSING_END:
        default:
            // We're out of tricks - just return the failed parse result
            return createParseResult(context.snapshot, null, null,context.getClasspathInfo(),context.getJavaSource());
        }
    }
    
    /**
     * My.
     * @param task
     * @return 
     */
    protected int getCompilePhase(Task task) {
        if (task == null) {
            return Phases.INSTRUCTION_SELECTION;
        }
        String stask = task.toString();
        String comps = "org.netbeans.modules.csl.editor.completion.GsfCompletionProvider$JavaCompletionQuery";


        if (stask.startsWith(comps)) {
            return Phases.CANONICALIZATION;
        }
        comps = "org.netbeans.modules.csl.editor.semantic";

        if (stask.startsWith(comps)) {
            return Phases.CANONICALIZATION;
        }

        //comps = "org.netbeans.modules.csl.navigation.CaretListeningTask";
        comps = "org.netbeans.modules.csl.navigation";
        if (stask.startsWith(comps)) {
            return Phases.CANONICALIZATION;
        }
        comps = "org.netbeans.modules.groovy.editor.api.parser.GroovyVirtualSourceProvider";
        if (stask.startsWith(comps)) {
            return Phases.CANONICALIZATION;
        }

        return Phases.INSTRUCTION_SELECTION;


    }

    private static void logParsingTime(Context context, long start, boolean cancelled) {
        long diff = System.currentTimeMillis() - start;
        long full = PARSING_TIME.addAndGet(diff);
        if (cancelled) {
            LOG.log(Level.FINEST, "Compilation cancelled in {0} for file {3}; total time spent {1}; total count {2}",
                    new Object[]{diff, full, PARSING_COUNT.intValue(), context.snapshot.getSource().getFileObject()});
        } else {
            LOG.log(Level.FINEST, "Compilation finished in {0} for file {3}; total time spent {1}; total count {2}",
                    new Object[]{diff, full, PARSING_COUNT.intValue(), context.snapshot.getSource().getFileObject()});
        }

        synchronized (EditorParser.class) {
            if (diff > maximumParsingTime) {
                maximumParsingTime = diff;
                LOG.log(Level.FINEST, "Maximum parsing time has been updated to {0}; file {1}",
                        new Object[]{diff, context.snapshot.getSource().getFileObject()});
            }
        }
    }

    private static String asString(CharSequence sequence) {
        if (sequence instanceof String) {
            return (String) sequence;
        } else {
            return sequence.toString();
        }
    }

    private static void notifyError(Context context, String key, Severity severity, String description, String details,
            int offset, Sanitize sanitizing) {
        notifyError(context, key, severity, description, details, offset, offset, sanitizing);
    }

    private static void notifyError(Context context, String key, Severity severity, String description, String displayName,
            int startOffset, int endOffset, Sanitize sanitizing) {

        LOG.log(Level.FINEST, "---------------------------------------------------");
        LOG.log(Level.FINEST, "key         : {0}\n", key);
        LOG.log(Level.FINEST, "description : {0}\n", description);
        LOG.log(Level.FINEST, "displayName : {0}\n", displayName);
        LOG.log(Level.FINEST, "startOffset : {0}\n", startOffset);
        LOG.log(Level.FINEST, "endOffset   : {0}\n", endOffset);

        // FIXME: we silently drop errors which have no description here.
        // There might be still a way to recover.
        if (description == null) {
            System.out.println("ERRORS NO DESCRIPTION=");
            LOG.log(Level.FINEST, "dropping error");
            return;
        }

        // TODO: we might need a smarter way to provide a key in the long run.
        if (key == null) {
            key = description;
        }

        // We gotta have a display name.
        if (displayName == null) {
            displayName = description;
        }

        Error error =
                new GroovyError(key, displayName, description, context.snapshot.getSource().getFileObject(),
                startOffset, endOffset, severity, getIdForErrorMessage(description));

        context.errorHandler.error(error);

        if (sanitizing == Sanitize.NONE) {
            context.errorOffset = startOffset;
        }
    }

    static GroovyCompilerErrorID getIdForErrorMessage(String errorMessage) {
        String ERR_PREFIX = "unable to resolve class "; // NOI18N

        if (errorMessage != null) {
            if (errorMessage.startsWith(ERR_PREFIX)) {
                return GroovyCompilerErrorID.CLASS_NOT_FOUND;
            }
        }

        return GroovyCompilerErrorID.UNDEFINED;
    }

    private void handleErrorCollector(ErrorCollector errorCollector, Context context,
            ModuleNode moduleNode, boolean ignoreErrors, Sanitize sanitizing) {
        System.out.println("START handleErrorCollector ignoreErrors=" + ignoreErrors + "; sanitizing=" + sanitizing + "; collector=" + errorCollector);
        if (!ignoreErrors && errorCollector != null) {
            List errors = errorCollector.getErrors();
            //Object lastError = errors.
            System.out.println("START 1 handleErrorCollector count=" + errors);
            if (errors != null) {
                Message lastError = errorCollector.getLastError();
                System.out.println("ERRORS in handleErrorCollector count=" + errors.size());
                for (Object object : errors) {
                    System.out.println(" --- 1) ERROR in handleErrorCollector object=" + object);
                    LOG.log(Level.FINEST, "Error found in collector: {0}", object);
                    if (object instanceof SyntaxErrorMessage) {
                        System.out.println(" --- 2) ERROR SyntaxErrorMessage in handleErrorCollector object=" + object);

                        SyntaxException ex = ((SyntaxErrorMessage) object).getCause();

                        String sourceLocator = ex.getSourceLocator();
                        String name = null;
                        if (moduleNode != null) {
                            name = moduleNode.getContext().getName();
                        } else if (context.snapshot.getSource().getFileObject() != null) {
                            name = context.snapshot.getSource().getFileObject().getNameExt();
                        }

                        if (sourceLocator != null && name != null && sourceLocator.equals(name)) {
                            int startLine = ex.getStartLine();
                            int startColumn = ex.getStartColumn();
                            int line = ex.getLine();
                            int endColumn = ex.getEndColumn();
                            // FIXME parsing API
                            int startOffset = 0;
                            int endOffset = 0;
                            if (context.document != null) {
                                startOffset = AstUtilities.getOffset(context.document, startLine > 0 ? startLine : 1, startColumn > 0 ? startColumn : 1);
                                endOffset = AstUtilities.getOffset(context.document, line > 0 ? line : 1, endColumn > 0 ? endColumn : 1);
                                //// My
                                if (moduleNode == null && lastError == object) {
                                    context.errorOffset = startOffset;
                                }
                                System.out.println(" ---  ERROR OFFSETS in handleErrorCollector startOffset=" + startOffset + "; endOffset = " + endOffset + "; startLine=" + startLine + ";startColumn=" + startColumn);
                            }
                            notifyError(context, null, Severity.ERROR, ex.getMessage(), null, startOffset, endOffset, sanitizing);
                        }
                    } else if (object instanceof SimpleMessage) {

                        String message = ((SimpleMessage) object).getMessage();
                        System.out.println(" --- 3) ERROR SimpleMessage in handleErrorCollector =" + message);
                        notifyError(context, null, Severity.ERROR, message, null, -1, sanitizing);
                    } else {
                        System.out.println(" --- 4) ERROR OTHER in handleErrorCollector sanitizing = " + sanitizing);
                        notifyError(context, null, Severity.ERROR, "Error", null, -1, sanitizing);
                    }
                }
            }
        }
    }

    public static Set<FileObject> getResources(JavaSource javaSource, TypeElement typeElement) {
        if ( typeElement == null || javaSource == null ) {
            return null;
        }
        Set<ClassIndex.SearchKind> kind = new HashSet<ClassIndex.SearchKind>();
        kind.add(ClassIndex.SearchKind.TYPE_REFERENCES);
        kind.add(ClassIndex.SearchKind.METHOD_REFERENCES);
        Set<ClassIndex.SearchScopeType> scope = new HashSet<ClassIndex.SearchScopeType>();

        scope.add(ClassIndex.SearchScope.DEPENDENCIES);
        scope.add(ClassIndex.SearchScope.SOURCE);
        
        ElementHandle<TypeElement> handle = ElementHandle.create(typeElement);
        //Set<ElementHandle<TypeElement>> dtypes = javaSource.getClasspathInfo().getClassIndex().ggetDeclaredTypes(simpleName, ClassIndex.NameKind.SIMPLE_NAME, scope);
        Set<FileObject> foSet = javaSource.getClasspathInfo().getClassIndex().getResources(handle, kind, scope);
        if (foSet != null || !foSet.isEmpty()) {
            for (FileObject fo : foSet) {

                System.out.println("========= JAVASOURCE JAVASOURCE File name=" + fo.getName());

            }
        }
        return foSet;
    }

    public static TypeElement getTypeElement(final String name, final JavaSource javaSource) {
        final List l = new ArrayList();
        try {
            org.netbeans.api.java.source.Task<CompilationController> task = new org.netbeans.api.java.source.Task<CompilationController>() {

                public void run(CompilationController controller) throws Exception {
                    Elements elements = controller.getElements();
                    TypeElement typeElement = ElementSearch.getClass(elements, name);
                    System.out.println("TYPEELEMENT ??????????? FOR name=" + name + "; typeElement=" + typeElement);
                    l.add(typeElement);
                }
            };

            javaSource.runUserActionTask(task, true);
        } catch (Exception ex) {
        }
        return l.isEmpty() ? null : (TypeElement) l.get(0);
    }

    /** Attempts to sanitize the input buffer */
    public static enum Sanitize {

        /**
         * Only parse the current file accurately, don't try heuristics
         */
        NEVER,
        /** Perform no sanitization */
        NONE,
        /** Try to remove the trailing . or :: at the caret line */
        EDITED_DOT,
        /** Try to remove the trailing . or :: at the error position, or the prior
         * line, or the caret line */
        ERROR_DOT,
        /** Try to cut out the error line */
        ERROR_LINE,
        /** Try to cut out the current edited line, if known */
        EDITED_LINE,
        /** Attempt to add an "end" to the end of the buffer to make it compile */
        MISSING_END,
    }

    /** Parsing context */
    public static final class Context {

        private final Snapshot snapshot;
        private final SourceModificationEvent event;
        private final Task task;
        private final BaseDocument document;
        private ParseErrorHandler errorHandler;
        private int errorOffset;
        private String source;
        private String sanitizedSource;
        private OffsetRange sanitizedRange = OffsetRange.NONE;
        private String sanitizedContents;
        private int caretOffset;
        private Sanitize sanitized = Sanitize.NONE;
        private boolean completionTask;
        private CompilerConfiguration configuration;
        private GroovyClassLoader classLoader;
        private GroovyClassLoader transformationLoader;
        private JavaSource javaSource;
        private ClasspathInfo classpathInfo;
        //CompilationUnit compilationUnit;
        /*My        public Context(Snapshot snapshot, SourceModificationEvent event) {
            this.snapshot = snapshot;
            this.event = event;
            this.source = asString(snapshot.getText());
            this.caretOffset = GsfUtilities.getLastKnownCaretOffset(snapshot, event);
            // FIXME parsing API
            this.document = LexUtilities.getDocument(snapshot.getSource(), true);
        }
*/

        /**
         * My.
         * I added task parameter
         * @param snapshot
         * @param event
         * @param task 
         */
        public Context(Snapshot snapshot, SourceModificationEvent event, Task task) {
            this.snapshot = snapshot;
            this.event = event;
            this.task = task;
            this.source = asString(snapshot.getText());
            this.caretOffset = GsfUtilities.getLastKnownCaretOffset(snapshot, event);
            // FIXME parsing API
            this.document = LexUtilities.getDocument(snapshot.getSource(), true);

            this.completionTask = false;
        }

        @Override
        public String toString() {
            return "GroovyParser.Context(" + snapshot.getSource().getFileObject() + ")"; // NOI18N
        }

        public ClasspathInfo getClasspathInfo() {
            return classpathInfo;
        }

        public JavaSource getJavaSource() {
            return javaSource;
        }

        public Task getTask() {
            return this.task;
        }

        public OffsetRange getSanitizedRange() {
            return sanitizedRange;
        }

        Sanitize getSanitized() {
            return sanitized;
        }

        public String getSanitizedSource() {
            return sanitizedSource;
        }

        public int getErrorOffset() {
            return errorOffset;
        }

        protected String getFileName() {
            String fileName = "";
            if (snapshot.getSource().getFileObject() != null) {
                fileName = snapshot.getSource().getFileObject().getNameExt();
            }
            return fileName;
        }

        protected CompilationUnit getCompilationUnit(EditorParser parser, Context context, String currentSource) {
            String fileName = "";
            if (snapshot.getSource().getFileObject() != null) {
                fileName = snapshot.getSource().getFileObject().getNameExt();
            }

            CompilationUnit compilationUnit = null;
            compilationUnit = new EditorCompilationUnit(parser, configuration,
                    null, classLoader, transformationLoader, javaSource);


            /*
             * if (completionTask) { System.out.println(" *********
             * getCompilationUnit() completionTask=" + completionTask);
             * compilationUnit = new CompletionCompilationUnit(parser,
             * configuration, null, classLoader, transformationLoader,
             * javaSource, completionTask); } else { compilationUnit = new
             * CompilationUnitStrong(parser, configuration, null, classLoader,
             * transformationLoader, javaSource); }
             */
            InputStream inputStream = new ByteArrayInputStream(currentSource.getBytes());

            compilationUnit.addSource(fileName, inputStream);
            return compilationUnit;
        }
    }//class Context

    private static interface ParseErrorHandler {

        void error(Error error);
    }

    private static class TransformationClassLoader extends GroovyClassLoader {

        public TransformationClassLoader(ClassLoader parent, ClassPath cp, CompilerConfiguration config) {
            super(parent, config);
            for (ClassPath.Entry entry : cp.entries()) {
                this.addURL(entry.getURL());
            }
        }
    }

    private static class ParsingClassLoader extends GroovyClassLoader {

        private final CompilerConfiguration config;
        private final ClassPath path;
        private final GroovyResourceLoader resourceLoader = new GroovyResourceLoader() {

            @Override
            public URL loadGroovySource(final String filename) throws MalformedURLException {
                URL file = (URL) AccessController.doPrivileged(new PrivilegedAction() {

                    @Override
                    public Object run() {
                        return getSourceFile(filename);
                    }
                });
                return file;
            }
        };

        public ParsingClassLoader(ClassPath path, CompilerConfiguration config) {
            super(path.getClassLoader(true), config);
            this.config = config;
            this.path = path;
        }

//        @Override
//        public Class loadClass(String name, boolean lookupScriptFiles,
//                boolean preferClassOverScript, boolean resolve) throws ClassNotFoundException, CompilationFailedException {
//
//            boolean assertsEnabled = false;
//            assert assertsEnabled = true;
//            if (assertsEnabled) {
//                Class clazz = super.loadClass(name, lookupScriptFiles, preferClassOverScript, resolve);
//                assert false : "Class " + clazz + " loaded by GroovyClassLoader";
//            }
//
//            // if it is a class (java or compiled groovy) it is resolved via java infr.
//            // if it is groovy it is resolved with resource loader with compile unit
//            throw new ClassNotFoundException();
//        }
        @Override
        public GroovyResourceLoader getResourceLoader() {
            return resourceLoader;
        }

        private URL getSourceFile(String name) {
            // this is slightly faster then original implementation
            FileObject fo = path.findResource(name.replace('.', '/') + config.getDefaultScriptExtension());
            if (fo == null || fo.isFolder()) {
                return null;
            }
            return URLMapper.findURL(fo, URLMapper.EXTERNAL);
        }
    }
}
