/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
package org.netbeans.modules.groovy.editor.api;
/**
 *
 * @author Valery
 */
public class GroovyIndexQuery {

/*    
        public Collection<? extends IndexResult> query(
            final String fieldName,
            final String fieldValue,
            final Kind kind,
            final String... fieldsToLoad
    ) throws IOException {
        ////Parameters.notNull("fieldName", fieldName); //NOI18N
        ////Parameters.notNull("fieldValue", fieldValue); //NOI18N
        ////Parameters.notNull("kind", kind); //NOI18N
        try {
            return Utilities.runPriorityIO(new Callable<Collection<? extends IndexResult>>() {

                @Override
                public Collection<? extends IndexResult> call() throws Exception {
                    Iterable<? extends Pair<URL, DocumentIndex>> indices = indexerQuery.getIndices(roots);
                    // check if there are stale indices
                    for (Pair<URL, DocumentIndex> pair : indices) {
                        final DocumentIndex index = pair.second;
                        final Collection<? extends String> staleFiles = index.getDirtyKeys();
                       //// if (LOG.isLoggable(Level.FINE)) {
                       ////     LOG.fine("Index: " + index + ", staleFiles: " + staleFiles); //NOI18N
                       //// }
                        if (staleFiles != null && staleFiles.size() > 0) {
                            final URL root = pair.first;
                            LinkedList<URL> list = new LinkedList<URL>();
                            for (String staleFile : staleFiles) {
                                try {
                                    list.add(Util.resolveUrl(root, staleFile));
                                } catch (MalformedURLException ex) {
                                    ////LOG.log(Level.WARNING, null, ex);
                                }
                            }
                            IndexingManager.getDefault().refreshIndexAndWait(root, list, true, true);
                        }
                    }
                    final List<IndexResult> result = new LinkedList<IndexResult>();
                    for (Pair<URL, DocumentIndex> pair : indices) {
                        final DocumentIndex index = pair.second;
                        final URL root = pair.first;
                        final Collection<? extends org.netbeans.modules.parsing.lucene.support.IndexDocument> pr = index.query(
                                fieldName,
                                fieldValue,
                                translateQueryKind(kind),
                                fieldsToLoad);
/*                        if (LOG.isLoggable(Level.FINE)) {
                            LOG.fine("query(\"" + fieldName + "\", \"" + fieldValue + "\", " + kind + ", " + printFiledToLoad(fieldsToLoad) + ") invoked at " + getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(this)) + "[indexer=" + indexerQuery.getIndexerId() + "]:"); //NOI18N
                            for (org.netbeans.modules.parsing.lucene.support.IndexDocument idi : pr) {
                                LOG.fine(" " + idi); //NOI18N
                            }
                            LOG.fine("----"); //NOI18N

                        }
                        for (org.netbeans.modules.parsing.lucene.support.IndexDocument di : pr) {
                            result.add(new IndexResult(di, root));
                        }
                    }
                    return result;
                }
            });
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }
*/
}
