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
package org.structr.core.entity.app;

import org.structr.common.SessionValue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang.StringUtils;
import org.structr.common.CurrentRequest;
import org.structr.common.CurrentSession;
import org.structr.core.entity.AbstractNode;

/**
 * Render a select field.
 * 
 * When connected with a node via a DATA relationship, all direct children of
 * this node are treated as options list.
 *
 * @author Axel Morgner
 */
public class SelectField extends FormField implements InteractiveNode
{
	private static final Logger logger = Logger.getLogger(SelectField.class.getName());

	protected SessionValue<Object> errorSessionValue = null;
	protected SessionValue<String> sessionValue = null;
	private String mappedName = null;

	@Override
	public String getIconSrc()
	{
		return "/images/textfield.png";
	}

	@Override
	public void renderView(final StringBuilder out, final AbstractNode startNode, final String editUrl, final Long editNodeId)
	{
		// if this page is requested to be edited, render edit frame
		if(editNodeId != null && getId() == editNodeId.longValue())
		{

			renderEditFrame(out, editUrl);

			// otherwise, render subnodes in edit mode
		} else
		{

			if(hasTemplate())
			{
				template.setCallingNode(this);
				template.renderView(out, startNode, editUrl, editNodeId);

			} else
			{
				logger.log(Level.WARNING, "Encountered SelectField without template: {0}", this);

				// TODO: default template for TextField?
			}
		}
	}

	// ----- interface InteractiveNode -----
	@Override
	public String getValue()
	{
		HttpServletRequest request = CurrentRequest.getRequest();
		String valueFromLastRequest = null;
		String name = getName();
		String ret = null;

		// only return value from last request if we were redirected before
		if(CurrentSession.isRedirected())
		{
			valueFromLastRequest = getLastValue().get();

		} else
		{
			// otherwise, clear value in session
			getLastValue().set(null);
		}

		if(request == null)
		{
			return valueFromLastRequest;
		}

		if(request != null)
		{
			ret = request.getParameter(name);
			if(ret != null)
			{

				// Parameter is there
				if(ret.length() == 0)
				{
					// Empty value
					return null;

				} else
				{
					// store value in session, in case we get a redirect afterwards
					getLastValue().set(ret);
					return ret;
				}

			} else
			{
				// Parameter is not in request
				return valueFromLastRequest;
			}

		}

		return null;
	}

	@Override
	public String getStringValue()
	{
		Object value = getValue();
		return (value != null ? value.toString() : null);
	}

	@Override
	public Class getParameterType()
	{
		return (String.class);
	}

	@Override
	public void setMappedName(String mappedName)
	{
		this.mappedName = mappedName;
	}

	@Override
	public String getMappedName()
	{
		if(StringUtils.isNotBlank(mappedName))
		{
			return (mappedName);
		}

		return (getName());
	}

	@Override
	public void setErrorValue(Object errorValue)
	{
		getErrorMessageValue().set(errorValue);
	}

	@Override
	public Object getErrorValue()
	{
		return(getErrorMessageValue().get());
	}

	@Override
	public String getErrorMessage()
	{
		Object errorValue = getErrorValue();
		if(errorValue != null)
		{
			return(errorValue.toString());
		}

		return(null);
	}

	// ----- private methods -----
	private SessionValue<Object> getErrorMessageValue()
	{
		if(errorSessionValue == null)
		{
			errorSessionValue = new SessionValue<Object>(createUniqueIdentifier("errorMessage"));
		}

		return(errorSessionValue);
	}

	private SessionValue<String> getLastValue()
	{
		if(sessionValue == null)
		{
			sessionValue = new SessionValue<String>(createUniqueIdentifier("lastValue"));
		}

		return(sessionValue);
	}

        // apperently not used
//        private List<AbstractNode> getDataNodes(final User user) {
//
//            List<AbstractNode> dataNodes = new LinkedList<AbstractNode>();
//
//            List<StructrRelationship> dataRels = this.getRelationships(RelType.DATA, Direction.INCOMING);
//
//            for (StructrRelationship rel : dataRels) {
//
//                AbstractNode node = rel.getStartNode();
//                dataNodes.addAll(node.getDirectChildNodes());
//
//            }
//
//            return dataNodes;
//
//        }
}
