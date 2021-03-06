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

import java.util.List;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.Index;
import org.neo4j.kernel.Traversal;
import org.structr.common.RelType;
import org.structr.core.Command;
import org.structr.core.Services;
import org.structr.core.entity.AbstractNode;
import org.structr.core.node.FindNodeCommand;
import org.structr.core.node.NodeServiceCommand;
import org.structr.core.node.XPath;

/**
 * Searches for a user node by her/his name in the database and returns the result.
 *
 * @author amorgner
 */
public class SearchUserCommand extends NodeServiceCommand {

    @Override
    public Object execute(Object... parameters) {
        GraphDatabaseService graphDb = (GraphDatabaseService) arguments.get("graphDb");
        Index<Node> index = (Index<Node>) arguments.get("index");

        Command findNode = Services.command(FindNodeCommand.class);

        if (graphDb != null) {

            switch (parameters.length) {

                case 1:

                    // we have only a simple user name
                    if (parameters[0] instanceof String) {

                        String userName = (String) parameters[0];

                        for (Node n : index.get(AbstractNode.NAME_KEY, userName)) {

                            AbstractNode s = (AbstractNode) findNode.execute(n);

                            // FIXME: remove hardcoded reference to User class name
                            if (s.getType().equals("User")) {
                                return s;
                            }
                        }
                    }
                    break;

                case 2:

                    // we have user name and domain
                    if (parameters[0] instanceof String && parameters[1] instanceof String) {

                        String userName = (String) parameters[0];
                        String rootNodePath = (String) parameters[1];

                        List<AbstractNode> nodes = (List<AbstractNode>) findNode.execute(new XPath(rootNodePath));

                        // we take the first one
                        if (nodes != null && nodes.size() > 0) {

                            AbstractNode r = nodes.get(0);

                            Node startNode = null;

                            if (r != null) {
                                startNode = r.getNode();

                                if (startNode != null) {
                                    startNode = graphDb.getReferenceNode();
                                }

                                for (Node n : getSubnodes(startNode)) {

                                    AbstractNode s = (AbstractNode) findNode.execute(n);

                                    // FIXME: remove hardcoded reference to User class name
                                    // TODO: implement better algorithm for user retrieval
                                    if (s.getType().equals("User") && userName.equals(s.getName())) {
                                        return s;
                                    }
                                }
                            }
                        }

                    }
                    break;

                default:
                    break;

            }
        }

        return null;
    }

    private Iterable<Node> getSubnodes(Node rootNode) {
        return Traversal.description().breadthFirst().relationships(RelType.HAS_CHILD, Direction.OUTGOING).prune(Traversal.pruneAfterDepth(999)).traverse(rootNode).nodes();
    }
}
