/*-******************************************************************************
 * Copyright (c) 2018 Ken Davidson.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Ken Davidson - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.source.handler;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import net.harawata.mybatipse.util.XpathUtil;

/**
 * Generates &lt;sql&gt; to provide quick access to the &lt;update&gt; statement(s). All columns
 * are pulled from the &lt;resultMap&gt; and placed in the &lt;sql&gt; entry. A default
 * {@code id} of {@code updateResultMap} is used.
 * <p>
 * Note that this is a basic update statement containing all columns with no table aliasing. It
 * all columns except those specified as &lt;id&gt; in the resultMap. Those are expected to be
 * used in the where clause within the &lt;update&gt; element.
 * <p>
 * TODO provide customization of id through properties.
 * 
 * @author kdavidson
 */
public class ResultMapUpdateSqlHandler extends ResultMapSqlSourceHandler
{

	@Override
	protected String buildSqlId(Element resultMap)
	{
		return resultMap.getAttribute("id").replaceAll("Map", "") + "UpdateColumns";
	}

	@Override
	protected String buildSqlStatement(Element resultMap)
	{
		final StringBuilder sql = new StringBuilder("\n");

		try
		{
			// Id Result items are never changed
			NodeList nodes = XpathUtil.xpathNodes(resultMap, "result");

			for (int i = 0; i < nodes.getLength(); i++)
			{
				if (i > 0)
				{
					sql.append("\n, ");
				}

				sql.append(nodeColumnValue(nodes.item(i))).append(" = ");
				sql.append("#{" + nodeParameterValue(nodes.item(i)) + "}");
			}
		}
		catch (XPathExpressionException e)
		{
			// no child nodes
		}

		return sql.toString();
	}

}
