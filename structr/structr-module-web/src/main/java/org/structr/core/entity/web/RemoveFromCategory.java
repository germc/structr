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
package org.structr.core.entity.web;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang.StringUtils;
import org.structr.common.CurrentRequest;
import org.structr.core.entity.AbstractNode;

/**
 * Include a node of this type to remove objects from categories of the
 * currently logged in user.
 *
 * See {@link AddToCategory}
 * 
 * @author axel
 */
public class RemoveFromCategory extends WebNode {

    /**
     * Render view
     *
     * @param out
     * @param startNode
     * @param editUrl
     * @param editNodeId
     */
    @Override
    public void renderView(StringBuilder out, final AbstractNode startNode,
            final String editUrl, final Long editNodeId) {

        // if this page is requested to be edited, render edit frame
        if (editNodeId != null && getId() == editNodeId.longValue()) {

            renderEditFrame(out, editUrl);

        } else {

            HttpServletRequest request = CurrentRequest.getRequest();

            if (request == null) {
                return;
            }

            HttpSession session = request.getSession();

            if (session == null) {
                return;
            }

            String usernameFromSession = (String) session.getAttribute(USERNAME_KEY);
//            String usernameFromSession = CurrentSession.getGlobalUsername();
            Boolean loggedIn = usernameFromSession != null;

            if (!loggedIn) {
                // Return silently when not logged in
                logger.log(Level.WARNING, "Not logged in");
                return;
            }

            Boolean sessionBlocked = (Boolean) session.getAttribute(SESSION_BLOCKED);

            if (Boolean.TRUE.equals(sessionBlocked)) {
                // Return silently when not user is blocked
                logger.log(Level.WARNING, "Session blocked");
                return;
            }

            // Get values from config page, or defaults
            String categoryParameterName = getCategoryParameterName() != null ? getCategoryParameterName() : defaultCategoryParameterName;

            String categoryName = request.getParameter(categoryParameterName);
            String objectId = request.getParameter("id");

            if (StringUtils.isEmpty(categoryName)) {
                // Don't process form if no category name was given
                logger.log(Level.WARNING, "No category given");
                return;
            }

            if (StringUtils.isEmpty(objectId)) {
                // Don't process form if no object id was given
                logger.log(Level.WARNING, "No object id given");
                return;
            }

            user.removeFromCategory(categoryName, objectId);

            // TODO: Give some feedback?

        }
    }
    private static final Logger logger = Logger.getLogger(LoginCheck.class.getName());
    private final static String ICON_SRC = "/images/tag_blue_add.png";

    @Override
    public String getIconSrc() {
        return ICON_SRC;
    }
    protected final static String defaultCategoryParameterName = "category";
    /** Name of category parameter */
    public final static String CATEGORY_PARAMETER_NAME_KEY = "categoryParameterName";

    /**
     * Return name of category parameter
     *
     * @return
     */
    public String getCategoryParameterName() {
        return getStringProperty(CATEGORY_PARAMETER_NAME_KEY);
    }

    /**
     * Set name of category parameter
     *
     * @param value
     */
    public void setCategoryParameterName(final String value) {
        setProperty(CATEGORY_PARAMETER_NAME_KEY, value);
    }
}
