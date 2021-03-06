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
package org.structr.core.log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.neo4j.graphdb.GraphDatabaseService;
import org.structr.common.RelType;
import org.structr.core.Command;
import org.structr.core.Services;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.StructrRelationship;
import org.structr.core.entity.SuperUser;
import org.structr.core.entity.User;
import org.structr.core.entity.log.Activity;
import org.structr.core.entity.log.LogNodeList;
import org.structr.core.node.CreateNodeCommand;
import org.structr.core.node.CreateRelationshipCommand;
import org.structr.core.node.GraphDatabaseCommand;
import org.structr.core.node.NodeAttribute;
import org.structr.core.node.NodeFactoryCommand;
import org.structr.core.node.RunnableNodeService;
import org.structr.core.node.StructrTransaction;
import org.structr.core.node.TransactionCommand;

/**
 * A logging service that will asynchronously persist log messages of type
 * {@see org.structr.core.entity.log.Activity}.
 * 
 * FIXME: javadoc..
 *
 * @author Christian Morgner
 */
public class LogService extends RunnableNodeService {

    private static final Logger logger = Logger.getLogger(LogService.class.getName());
    private static final ConcurrentHashMap<User, LogNodeList<Activity>> loggerCache = new ConcurrentHashMap<User, LogNodeList<Activity>>();
    private static final ConcurrentLinkedQueue queue = new ConcurrentLinkedQueue();
    private static final long DefaultInterval = TimeUnit.SECONDS.toMillis(10);
    private static final int DefaultThreshold = 10;
    private long interval = DefaultInterval;
    private int threshold = DefaultThreshold;
    // the global log (will be created)
    private LogNodeList<Activity> globalLogNodeList = null;
    private boolean run = false;

    public LogService() {
        super("LogService");

        // logging is a low-priority task
        this.setPriority(Thread.MIN_PRIORITY);
    }

    @Override
    public void run() {
        try {

            logger.log(Level.INFO, "Starting LogService");

            // initialize global log..
            //getGlobalLog();

            while (run || !queue.isEmpty()) {

                logger.log(Level.FINER, "Checking queue..");

                flushQueue();

                try {
                    Thread.sleep(interval);

                } catch (Throwable t) {
                    logger.log(Level.INFO, "LogService interrupted while sleeping");
                }
            }
        } catch (Throwable t) {
            t.printStackTrace(System.out);
        }
    }

    private void flushQueue() {
        // queue is not empty AND ((queue size is a above threshold) OR (service is to be stopped))
        if (!queue.isEmpty() && ((queue.size() > threshold) || !run)) {

            logger.log(Level.FINEST, "+++ LogService active ... +++");

            while (!queue.isEmpty()) {
                Object o = queue.poll();
                if (o != null && o instanceof Activity) {

                    Activity activity = (Activity) o;
                    User user = activity.getUser();

                    // Commit to database so node will have id and owner
                    activity.commit(user);

                    // append to global log
                    LogNodeList globalLog = getGlobalLog();
                    if (globalLog != null) {
                        globalLog.add(activity);
                        logger.log(Level.FINEST, "Added activity {0} to global log.", activity.getId());
                    }

                    // append to user-specific log
                    LogNodeList userLog = getUserLog(activity.getOwnerNode());
                    if (userLog != null) {
                        userLog.add(activity);
                        logger.log(Level.FINEST, "Added activity {0} to {1}''s log.", new Object[]{activity.getId(), user.getName()});
                    }

                }

                // cooperative multitasking :)
                Thread.yield();
            }

            logger.log(Level.FINEST, "+++ LogService inactive. +++");
        }
    }

    public LogNodeList getUserLog(final User user) {

        if (user == null || user instanceof SuperUser) {
            return null;
        }

        LogNodeList userLogNodeList = loggerCache.get(user);

        if (userLogNodeList == null) {


            for (StructrRelationship rel : user.getOutgoingChildRelationships()) {
                if (rel.getEndNode() instanceof LogNodeList) {

                    // Take first LogNodeList below root node
                    userLogNodeList = (LogNodeList) rel.getEndNode();
                    // store in cache
                    loggerCache.put(user, userLogNodeList);

                    return userLogNodeList;
                }
            }

            userLogNodeList = (LogNodeList) Services.command(TransactionCommand.class).execute(new StructrTransaction() {

                @Override
                public Object execute() throws Throwable {
//                    LogNodeList newLogNodeList = null;

                    // Create a new activity list as child node of the respective user
                    Command createNode = Services.command(CreateNodeCommand.class);
                    Command createRel = Services.command(CreateRelationshipCommand.class);

                    LogNodeList<Activity> newLogNodeList = (LogNodeList<Activity>) createNode.execute(
                            new NodeAttribute(AbstractNode.TYPE_KEY, LogNodeList.class.getSimpleName()),
                            new NodeAttribute(AbstractNode.NAME_KEY, user.getName() + "'s Activity Log"));

//                    newLogNodeList = new LogNodeList<Activity>();
//                    newLogNodeList.init(s);

                    createRel.execute(user, newLogNodeList, RelType.HAS_CHILD);

                    return (newLogNodeList);
                }
            });

            // store in cache
            loggerCache.put(user, userLogNodeList);
        }

        return userLogNodeList;
    }

    public LogNodeList getGlobalLog() {

        if (globalLogNodeList == null) {

            GraphDatabaseService graphDb = (GraphDatabaseService) Services.command(GraphDatabaseCommand.class).execute();

            if (graphDb == null) {
                logger.log(Level.SEVERE, "Graph database not running.");
                return null;
            }

            Command factory = Services.command(NodeFactoryCommand.class);

            final AbstractNode rootNode = (AbstractNode) factory.execute(graphDb.getReferenceNode());
            if (rootNode != null) {
                for (StructrRelationship rel : rootNode.getOutgoingChildRelationships()) {
                    if (rel.getEndNode() instanceof LogNodeList) {

                        // Take first LogNodeList below root node
                        globalLogNodeList = (LogNodeList) rel.getEndNode();
                        return globalLogNodeList;
                    }
                }
            }

            // Don't create a new global log when log service is not running
            if (!this.run) {
                logger.log(Level.FINEST, "LogService not running.");
                return null;
            }

            globalLogNodeList = (LogNodeList) Services.command(TransactionCommand.class).execute(new StructrTransaction() {

                @Override
                public Object execute() throws Throwable {

                    LogNodeList newGlobalLogNodeList = null;

                    // if we arrive here, no global log node exists yet
                    Command createNode = Services.command(CreateNodeCommand.class);
                    Command createRel = Services.command(CreateRelationshipCommand.class);

                    newGlobalLogNodeList = (LogNodeList<Activity>) createNode.execute(
                            new NodeAttribute(AbstractNode.TYPE_KEY, LogNodeList.class.getSimpleName()),
                            new NodeAttribute(AbstractNode.NAME_KEY, "Global Activity Log"));

                    // load reference node and link new node to it..
                    createRel.execute(rootNode, newGlobalLogNodeList, RelType.HAS_CHILD);

                    return newGlobalLogNodeList;
                }
            });
        }

        return globalLogNodeList;
    }

    // <editor-fold defaultstate="collapsed" desc="interface RunnableService">
    @Override
    public void startService() {
        this.run = true;
        super.start();
    }

    @Override
    public void stopService() {
        shutdown();
    }

    @Override
    public boolean isRunning() {
        return (this.run);
    }

    @Override
    public void injectArguments(Command command) {
        command.setArgument("queue", queue);
        command.setArgument("service", this);
    }

    @Override
    public void initialize(Map<String, Object> context) {
        // try to parse polling interval, set to default otherwise
        if (context.containsKey(Services.LOG_SERVICE_INTERVAL)) {
            try {
                interval = Long.parseLong(context.get(Services.LOG_SERVICE_INTERVAL).toString());

            } catch (Throwable t) {
                interval = DefaultInterval;
            }
        }

        // try to parse flushing threshold, set to default otherwise
        if (context.containsKey(Services.LOG_SERVICE_THRESHOLD)) {
            try {
                threshold = Integer.parseInt(context.get(Services.LOG_SERVICE_THRESHOLD).toString());

            } catch (Throwable t) {
                threshold = DefaultThreshold;
            }
        }
    }

    @Override
    public void shutdown() {
        this.run = false;

        // set prio to max
        this.setPriority(Thread.MAX_PRIORITY);

        // flush queue
        flushQueue();

        try {
            // wait a little bit
            sleep(1000);

            this.interrupt();

        } catch (Throwable t) { /* ignore */ }
    }
    // </editor-fold>
}
