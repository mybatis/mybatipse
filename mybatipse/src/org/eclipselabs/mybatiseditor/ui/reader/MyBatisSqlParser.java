
package org.eclipselabs.mybatiseditor.ui.reader;

import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMNode;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Peter Hendriks
 */
@SuppressWarnings("restriction")
public class MyBatisSqlParser
{

	private final MyBatisDomReader reader = new MyBatisDomReader();

	public String getSqlText(IStructuredDocument mapperDocument, final String elementName,
		final String attributeValue)
	{
		return new MyBatisDomModelTemplate<String>(StructuredModelManager.getModelManager()
			.getModelForRead(mapperDocument))
		{
			@Override
			protected String doWork(IDOMModel domModel)
			{
				return parseSqlNode(domModel.getDocument(), elementName, attributeValue);
			}
		}.run();
	}

	private String parseSqlNode(IDOMDocument document, String elementName, String attributeValue)
	{
		IDOMNode sqlNode = (IDOMNode)document.getElementById(attributeValue);
		if (sqlNode == null)
		{
			if ("include".equals(elementName))
			{
				sqlNode = reader.findDeclaringNode(document, "sql", attributeValue, false);
			}
			if (sqlNode == null)
			{
				return "";
			}
		}
		return parseSqlNode(document, sqlNode);
	}

	private String parseSqlNode(IDOMDocument document, IDOMNode sqlNode)
	{
		StringBuilder sqlResult = new StringBuilder();
		NodeList childNodes = sqlNode.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); i++)
		{
			IDOMNode childNode = (IDOMNode)childNodes.item(i);
			short nodeType = childNode.getNodeType();
			if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE)
			{
				parseSqlTextNode(sqlResult, childNode);
			}
			else if (nodeType == Node.ELEMENT_NODE)
			{
				if (childNode.getLocalName().equals("include"))
				{
					String refIdValue = XmlUtil.getAttributeValue(childNode, "refid");
					if (refIdValue != null)
					{
						IDOMNode result = reader.findDeclaringNode(document, "sql", refIdValue, false);
						if ((result != null) && !result.equals(sqlNode))
						{
							sqlResult.append(parseSqlNode(result.getModel().getDocument(), result));
						}
					}
				}
				else
				{
					parseSqlXmlNode(sqlResult, childNode);
				}
			}
		}
		return sqlResult.toString();
	}

	private void parseSqlXmlNode(StringBuilder sqlResult, IDOMNode childNode)
	{
		sqlResult.append("<" + childNode.getLocalName());
		NamedNodeMap attributes = childNode.getAttributes();
		if ((attributes != null) && (attributes.getLength() > 0))
		{
			for (int j = 0; j < attributes.getLength(); j++)
			{
				Node attr = attributes.item(j);
				sqlResult.append(" " + attr.getLocalName() + "=\"" + attr.getNodeValue() + "\"");
			}
		}
		sqlResult.append(">");
		sqlResult.append(parseSqlNode(childNode.getModel().getDocument(), childNode));
		sqlResult.append("</" + childNode.getLocalName() + ">");
	}

	private void parseSqlTextNode(StringBuilder sqlResult, IDOMNode childNode)
	{
		String nodeValue = childNode.getNodeValue();
		String nodeValueTrimmed = nodeValue.trim();
		if (nodeValueTrimmed.length() == 0)
		{
			sqlResult.append(nodeValue);
		}
		else
		{
			if (nodeValueTrimmed.length() != nodeValue.length())
			{
				nodeValueTrimmed = " " + nodeValueTrimmed + " ";
			}
			sqlResult.append(nodeValueTrimmed);
		}
	}
}
