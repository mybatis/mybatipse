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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.harawata.mybatipse.util.XpathUtil;

/**
 * Generates &lt;sql&gt; to provide quick access to the &lt;select&gt; statement(s). All columns
 * are pulled from the &lt;resultMap&gt; and placed in the &lt;sql&gt; entry. A default
 * {@code id} of {@code selectResultMap} is used.
 * <p>
 * Note that this is a basic select statement containing all columns with no table aliasing.
 * <p>
 * TODO provide customization of id through properties.
 * 
 * @author kdavidson
 */
public class ResultMapSelectSqlHandler extends ResultMapSqlSourceHandler
{

	@Override
	protected String buildSqlId(Element resultMap)
	{
		return resultMap.getAttribute("id").replaceAll("Map", "") + "SelectColumns";
	}

	@Override
	protected String buildSqlStatement(Element resultMap)
	{
		final StringBuilder sql = new StringBuilder("\n");

		try
		{
			NodeList nodes = XpathUtil.xpathNodes(resultMap, "id|result");

			for (int i = 0; i < nodes.getLength(); i++)
			{
				Node node = nodes.item(i).getAttributes().getNamedItem("column") != null
					? nodes.item(i).getAttributes().getNamedItem("column")
					: nodes.item(i).getAttributes().getNamedItem("property");

				if (node != null)
				{
					if (i > 0)
					{
						sql.append("\n, ");
					}

					sql.append(node.getNodeValue());
				}
			}
		}
		catch (XPathExpressionException e)
		{
			// no child nodes
		}

		return sql.toString();
	}

}
