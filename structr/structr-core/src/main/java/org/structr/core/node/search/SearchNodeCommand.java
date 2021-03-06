/*
 *  Copyright (C) 2011 Axel Morgner, structr <structr@structr.org>
 * 
 *  This file is part of structr <http://structr.org>.
 * 
 *  structr is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 * 
 *  structr is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  You should have received a copy of the GNU General Public License
 *  along with structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.core.node.search;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.index.lucene.QueryContext;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.User;
import org.structr.core.node.NodeServiceCommand;
import org.structr.core.node.StructrNodeFactory;

/**
 * <b>Search for nodes by attributes</b>
 * <p>
 * The execute method takes an arbitraty list of parameters, but the first
 * four parameters are
 * <p>
 * <ol>
 * <li>{@see User} user: return nodes only if readable for the user
 *     <p>if null, don't filter by user
 * <li>{@see AbstractNode} top node: search only below this node
 *     <p>if null, search everywhere (top node = root node)
 * <li>boolean include deleted: if true, return deleted nodes as well
 * <li>boolean public only: if true, return only public nodes
 * <li>List<{@see TextualSearchAttribute}> search attributes: key/value pairs with search operator
 *    <p>if no TextualSearchAttribute is given, return any node matching the other
 *       search criteria
 * </ol>
 *
 * @author amorgner
 */
public class SearchNodeCommand extends NodeServiceCommand {

    private static final Logger logger = Logger.getLogger(SearchNodeCommand.class.getName());
    private static String IMPROBABLE_SEARCH_VALUE = "xeHfc6OG30o3YQzX57_8____r-Wx-RW_70r84_71D-g--P9-3K";

    @Override
    public Object execute(Object... parameters) {
        GraphDatabaseService graphDb = (GraphDatabaseService) arguments.get("graphDb");
        Index<Node> index = (Index<Node>) arguments.get("index");
        StructrNodeFactory nodeFactory = (StructrNodeFactory) arguments.get("nodeFactory");

        List<AbstractNode> finalResult = new LinkedList<AbstractNode>();

        if (graphDb != null) {

            if (parameters == null || parameters.length != 5) {
                logger.log(Level.WARNING, "Exactly 5 parameters are required for advanced search.");
                return Collections.emptyList();
            }

            User user = null;
            if (parameters[0] instanceof User) {
                user = (User) parameters[0];
            }

            // FIXME: filtering by top node is experimental
            AbstractNode topNode = null;
            if (parameters[1] instanceof AbstractNode) {
                topNode = (AbstractNode) parameters[1];
            }

            boolean includeDeleted = false;
            if (parameters[2] instanceof Boolean) {
                includeDeleted = (Boolean) parameters[2];
            }

            boolean publicOnly = false;
            if (parameters[3] instanceof Boolean) {
                publicOnly = (Boolean) parameters[3];
            }

            List<SearchAttribute> searchAttrs = new LinkedList<SearchAttribute>();
            if (parameters[4] instanceof List) {
                searchAttrs = (List<SearchAttribute>) parameters[4];
            }

            for (int i = 4; i < parameters.length; i++) {

                Object o = parameters[i];
                if (o instanceof SearchAttribute) {
                    searchAttrs.add((SearchAttribute) o);
                }

            }

            // At this point, all search attributes are ready

            BooleanQuery query = new BooleanQuery();

            List<BooleanSearchAttribute> booleanAttributes = new LinkedList<BooleanSearchAttribute>();

            String textualQueryString = "";

            for (SearchAttribute attr : searchAttrs) {

                if (attr instanceof SearchAttributeGroup) {

                    SearchAttributeGroup attributeGroup = (SearchAttributeGroup) attr;
                    List<SearchAttribute> groupedAttributes = attributeGroup.getSearchAttributes();

                    String subQueryString = "";

                    if (!(groupedAttributes.isEmpty())) {

                        BooleanQuery subQuery = new BooleanQuery();

                        String subQueryPrefix = (StringUtils.isBlank(textualQueryString) ? "" : attributeGroup.getSearchOperator()) + " ( ";

                        for (SearchAttribute groupedAttr : groupedAttributes) {

                            // TODO: support other than textual search attributes
                            if (groupedAttr instanceof TextualSearchAttribute) {

                                subQuery.add(toQuery((TextualSearchAttribute) groupedAttr), translateToBooleanClauseOccur(groupedAttr.getSearchOperator()));
                                subQueryString += toQueryString((TextualSearchAttribute) groupedAttr, StringUtils.isBlank(subQueryString));

                            }
                        }
                        query.add(subQuery, translateToBooleanClauseOccur(attributeGroup.getSearchOperator()));
                        String subQuerySuffix = " ) ";

                        // Add sub query only if not blank
                        if (StringUtils.isNotBlank(subQueryString)) {
                            textualQueryString += subQueryPrefix + subQueryString + subQuerySuffix;
                        }

                    }

                } else if (attr instanceof TextualSearchAttribute) {

                    query.add(toQuery((TextualSearchAttribute) attr), translateToBooleanClauseOccur(attr.getSearchOperator()));
                    textualQueryString += toQueryString((TextualSearchAttribute) attr, StringUtils.isBlank(textualQueryString));

                } else if (attr instanceof BooleanSearchAttribute) {

                    booleanAttributes.add((BooleanSearchAttribute) attr);

                }

            }

            List<AbstractNode> intermediateResult;
            if (searchAttrs.isEmpty() || StringUtils.isBlank(textualQueryString)) {

                if (topNode != null) {
                    intermediateResult = topNode.getAllChildren();
                } else {
                    intermediateResult = new LinkedList<AbstractNode>();

                }
            } else {

                long t0 = System.currentTimeMillis();
                logger.log(Level.FINE, "Textual Query String: {0}", textualQueryString);

                IndexHits hits = index.query(new QueryContext(textualQueryString));//.sort("name"));

                long t1 = System.currentTimeMillis();
                logger.log(Level.FINE, "Querying index took {0} ms, {1} results retrieved.", new Object[]{t1 - t0, hits.size()});


//            IndexHits hits = index.query(new QueryContext(query.toString()));//.sort("name"));
                intermediateResult = nodeFactory.createNodes(hits, user, includeDeleted, publicOnly);

//                hits.close();

                long t2 = System.currentTimeMillis();
                logger.log(Level.FINE, "Creating structr nodes took {0} ms, {1} nodes made.", new Object[]{t2 - t1, intermediateResult.size()});
            }

            long t2 = System.currentTimeMillis();

            if (booleanAttributes.isEmpty()) {

                // if no further boolean attributes, the final result equals the intermediate result

                finalResult.addAll(intermediateResult);

            } else {

                // Filter intermediate result
                for (AbstractNode node : intermediateResult) {

                    for (BooleanSearchAttribute attr : booleanAttributes) {

                        String key = attr.getKey();
                        Boolean searchValue = attr.getValue();
                        SearchOperator op = attr.getSearchOperator();

                        Object nodeValue = node.getProperty(key);

                        if (op.equals(SearchOperator.NOT)) {

                            if (nodeValue != null && !(nodeValue.equals(searchValue))) {
                                attr.addToResult(node);
                            }

                        } else {

                            if (nodeValue == null && searchValue == null) {
                                attr.addToResult(node);
                            }

                            if (nodeValue != null && nodeValue.equals(searchValue)) {
                                attr.addToResult(node);
                            }
                        }


                    }
                }

                // now sum, intersect or substract all partly results

                for (BooleanSearchAttribute attr : booleanAttributes) {
                    SearchOperator op = attr.getSearchOperator();
                    List<AbstractNode> result = attr.getResult();

                    if (op.equals(SearchOperator.AND)) {

                        intermediateResult = ListUtils.intersection(intermediateResult, result);

                    } else if (op.equals(SearchOperator.AND)) {

                        intermediateResult = ListUtils.sum(intermediateResult, result);

                    } else if (op.equals(SearchOperator.NOT)) {

                        intermediateResult = ListUtils.subtract(intermediateResult, result);

                    }

                }
                finalResult.addAll(intermediateResult);


            }
            long t3 = System.currentTimeMillis();
            logger.log(Level.FINE, "Filtering nodes took {0} ms. Result size now {1}.", new Object[]{t3 - t2, finalResult.size()});

        }

        long t4 = System.currentTimeMillis();

        // sort search results; defaults to name, (@see AbstractNode.compareTo())
        Collections.sort(finalResult);

        long t5 = System.currentTimeMillis();
        logger.log(Level.FINE, "Sorting nodes took {0} ms.", new Object[]{t5 - t4});

        return finalResult;

    }

    private String toQueryString(final TextualSearchAttribute singleAttribute, final boolean isFirst) {

        String key = singleAttribute.getKey();
        String value = singleAttribute.getValue();
        SearchOperator op = singleAttribute.getSearchOperator();

        if (StringUtils.isBlank(value) || value.equals("\"\"")) {
            return " " + key + ":" + IMPROBABLE_SEARCH_VALUE + "";
        }

        // NOT operator should always be applied
        return (isFirst && !(op.equals(SearchOperator.NOT)) ? "" : " " + op + " ") + expand(key, value);

    }

    private Query toQuery(final TextualSearchAttribute singleAttribute) {

        String key = singleAttribute.getKey();
        Object value = singleAttribute.getValue();
//            SearchOperator op = singleAttribute.getSearchOperator();

        if (key == null || value == null) {
            return null;
        }

        boolean valueIsNoStringAndNotNull = (value != null && !(value instanceof String));
        boolean valueIsNoBlankString = (value != null && value instanceof String && StringUtils.isNotBlank((String) value));

        if (StringUtils.isNotBlank(key) && (valueIsNoBlankString || valueIsNoStringAndNotNull)) {
            return new TermQuery(new Term(key, value.toString()));
        }

        return null;
    }

    private BooleanClause.Occur translateToBooleanClauseOccur(final SearchOperator searchOp) {

        if (searchOp.equals(SearchOperator.AND)) {
            return BooleanClause.Occur.MUST;

        } else if (searchOp.equals(SearchOperator.OR)) {
            return BooleanClause.Occur.SHOULD;

        } else if (searchOp.equals(SearchOperator.NOT)) {
            return BooleanClause.Occur.MUST_NOT;
        }

        // Default
        return BooleanClause.Occur.MUST;

    }

    private String expand(final String key, final Object value) {

        if (StringUtils.isBlank(key)) {
            return "";
        }

        String stringValue = null;
        if (value instanceof String) {

            stringValue = (String) value;

            if (StringUtils.isBlank(stringValue)) {
                return "";
            }
        }

        // If value is not a string or starts with operator (exact match, range query, or search operator word),
        // don't expand
        if (stringValue == null || stringValue.startsWith("\"") || stringValue.startsWith("[") || stringValue.startsWith("NOT") || stringValue.startsWith("AND") || stringValue.startsWith("OR")) {
            return " " + key + ":" + value + " ";
        }

        String result = "( ";

        // Split string into words
        String[] words = StringUtils.split(stringValue, " ");

        // Expand key,word to ' (key:word* OR key:"word") '

        int i = 1;
        for (String word : words) {

//            String cleanWord = Search.normalize(word);

//            result += " (" + key + ":" + cleanWord + "* OR " + key + ":\"" + cleanWord + "\")" + (i<words.length ? " AND " : " ) ");
            result += " (" + key + ":" + word + "* OR " + key + ":\"" + word + "\")" + (i < words.length ? " AND " : " ) ");
            i++;

        }

        return result;
    }
}
