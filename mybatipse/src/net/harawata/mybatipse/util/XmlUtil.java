
package net.harawata.mybatipse.util;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * @author Peter Hendriks
 */
public final class XmlUtil
{

	private XmlUtil()
	{
		// No need to instantiate
	}

	public static String getAttributeValue(Node elementNode, String attributeName)
	{
		if (elementNode == null)
		{
			return null;
		}
		NamedNodeMap attributes = elementNode.getAttributes();
		if (attributes == null)
		{
			return null;
		}
		Node attribute = attributes.getNamedItem(attributeName);
		if (attribute == null)
		{
			return null;
		}
		return attribute.getNodeValue();
	}
}
