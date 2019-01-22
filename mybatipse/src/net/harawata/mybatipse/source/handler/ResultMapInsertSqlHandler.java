/*-******************************************************************************
 * Copyright (c) 2018 Sc122.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sc122 - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.source.handler;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import net.harawata.mybatipse.util.XpathUtil;

/**
 * Generates &lt;sql&gt; to provide quick access to the &lt;insert&gt; statement(s). All columns
 * are pulled from the &lt;resultMap&gt; and placed in the &lt;sql&gt; entry. A default
 * {@code id} of {@code insertResultMap} is used.
 * <p>
 * Note that this is a basic insert statement containing all columns with no table aliasing.
 * <p>
 * TODO provide customization of id through properties.
 * 
 * @author kdavidson
 */
public class ResultMapInsertSqlHandler extends ResultMapSqlSourceHandler
{

	@Override
	protected String buildSqlId(Element resultMap)
	{
		return resultMap.getAttribute("id").replaceAll("Map", "") + "InsertColumns";
	}

	@Override
	protected String buildSqlStatement(Element resultMap)
	{
		final StringBuilder columns = new StringBuilder("\n(");
		final StringBuilder values = new StringBuilder("VALUES (");

		try
		{
			NodeList nodes = XpathUtil.xpathNodes(resultMap, "id|result");

			for (int i = 0; i < nodes.getLength(); i++)
			{
				if (i > 0)
				{
					columns.append("\n, ");
					values.append("\n, ");
				}

				columns.append(nodeColumnValue(nodes.item(i)));
				values.append("#{" + nodeParameterValue(nodes.item(i)) + "}");
			}
		}
		catch (XPathExpressionException e)
		{
			// no child nodes
		}

		columns.append(")");
		values.append(")");

		return columns.toString() + "\n" + values.toString();
	}
}
