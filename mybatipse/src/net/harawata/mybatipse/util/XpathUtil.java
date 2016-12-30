/*-****************************************************************************** 
 * Copyright (c) 2014 Iwao AVE!.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Iwao AVE! - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.util;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Iwao AVE!
 */
public class XpathUtil
{
	private static final ThreadLocal<XPathFactory> XPATH_FACTORY = new ThreadLocal<XPathFactory>()
	{
		@Override
		protected XPathFactory initialValue()
		{
			return XPathFactory.newInstance();
		}
	};

	public static boolean xpathBool(Node node, String expression) throws XPathExpressionException
	{
		return ((Boolean)evaluateXpath(expression, node, XPathConstants.BOOLEAN, null))
			.booleanValue();
	}

	public static String xpathString(Node node, String expression) throws XPathExpressionException
	{
		return (String)evaluateXpath(expression, node, XPathConstants.STRING, null);
	}

	public static Node xpathNode(Node node, String expression) throws XPathExpressionException
	{
		return (Node)evaluateXpath(expression, node, XPathConstants.NODE, null);
	}

	public static NodeList xpathNodes(Node node, String expression)
		throws XPathExpressionException
	{
		return xpathNodes(node, expression, null);
	}

	public static NodeList xpathNodes(Node node, String expression, NamespaceContext nsContext)
		throws XPathExpressionException
	{
		return (NodeList)evaluateXpath(expression, node, XPathConstants.NODESET, nsContext);
	}

	public static Object evaluateXpath(String expression, Object node, QName returnType,
		NamespaceContext nsContext) throws XPathExpressionException
	{
		XPath xpath = XPATH_FACTORY.get().newXPath();
		if (nsContext != null)
		{
			xpath.setNamespaceContext(nsContext);
		}
		return xpath.evaluate(expression, node, returnType);
	}
}
