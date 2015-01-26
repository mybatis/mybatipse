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

package net.harawata.mybatipse.mybatis;

import java.io.IOException;
import java.text.MessageFormat;

import javax.xml.xpath.XPathExpressionException;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.hyperlink.ToJavaHyperlink;
import net.harawata.mybatipse.hyperlink.ToXmlHyperlink;
import net.harawata.mybatipse.util.XpathUtil;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegionList;
import org.eclipse.wst.sse.core.utils.StringUtils;
import org.eclipse.wst.xml.core.internal.document.ElementImpl;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMNode;
import org.eclipse.wst.xml.core.internal.regions.DOMRegionContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * @author Iwao AVE!
 */
@SuppressWarnings("restriction")
public class XmlHyperlinkDetector extends AbstractHyperlinkDetector
{
	public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region,
		boolean canShowMultipleHyperlinks)
	{
		IHyperlink[] hyperlinks = null;
		if (textViewer != null && region != null)
		{
			IDocument document = textViewer.getDocument();

			if (document != null)
			{
				Node currentNode = getCurrentNode(document, region.getOffset());
				if (currentNode != null && currentNode.getNodeType() == Node.ELEMENT_NODE)
				{
					Element element = (Element)currentNode;
					IStructuredDocumentRegion documentRegion = ((IStructuredDocument)document).getRegionAtCharacterOffset(region.getOffset());
					ITextRegion textRegion = documentRegion.getRegionAtCharacterOffset(region.getOffset());
					ITextRegion nameRegion = null;
					ITextRegion valueRegion = null;
					String tagName = element.getTagName();
					String attrName = null;
					String attrValue = null;
					if (DOMRegionContext.XML_TAG_ATTRIBUTE_VALUE.equals(textRegion.getType()))
					{
						ITextRegionList regions = documentRegion.getRegions();
						int index = regions.indexOf(textRegion);
						if (index >= 4)
						{
							nameRegion = regions.get(index - 2);
							valueRegion = textRegion;
							attrName = documentRegion.getText(nameRegion);
							attrValue = StringUtils.strip(documentRegion.getText(valueRegion));
						}
					}
					if (attrName != null && attrValue != null)
					{
						try
						{
							if ("namespace".equals(attrName))
							{
								hyperlinks = linkToJavaMapperType(document,
									getLinkRegion(documentRegion, valueRegion), currentNode);
							}
							else if ("id".equals(attrName)
								&& ("select".equals(tagName) || "update".equals(tagName)
									|| "insert".equals(tagName) || "delete".equals(tagName)))
							{
								hyperlinks = linkToJavaMapperMethod(document, attrValue,
									getLinkRegion(documentRegion, valueRegion), currentNode);
							}
							else if ("property".equals(attrName))
							{
								hyperlinks = linkToJavaProperty(document, currentNode, attrValue,
									getLinkRegion(documentRegion, valueRegion));
							}
							else if ("type".equals(attrName) || "resultType".equals(attrName)
								|| "parameterType".equals(attrName) || "ofType".equals(attrName))
							{
								hyperlinks = linkToJavaType(document, attrValue,
									getLinkRegion(documentRegion, valueRegion));
							}
							else if ("refid".equals(attrName))
							{
								hyperlinks = linkToReference(textViewer, document, element.getOwnerDocument(),
									attrName, attrValue, getLinkRegion(documentRegion, valueRegion), "sql");
							}
							else if ("select".equals(attrName))
							{
								hyperlinks = linkToReference(textViewer, document, element.getOwnerDocument(),
									attrName, attrValue, getLinkRegion(documentRegion, valueRegion), "select");
							}
							else if ("extends".equals(attrName) || "resultMap".equals(attrName))
							{
								// TODO: multiple result maps
								hyperlinks = linkToReference(textViewer, document, element.getOwnerDocument(),
									attrName, attrValue, getLinkRegion(documentRegion, valueRegion), "resultMap");
							}
						}
						catch (Exception e)
						{
							Activator.log(Status.ERROR, "Failed to create hyperlinks.", e);
						}
					}
				}
			}
		}
		return hyperlinks;
	}

	private IHyperlink[] linkToReference(ITextViewer textViewer, IDocument document,
		Document domDoc, String attrName, String attrValue, Region linkRegion, String targetElement)
		throws XPathExpressionException, CoreException, IOException
	{
		if (attrValue.indexOf('$') > -1)
			return null;

		int lastDot = attrValue.lastIndexOf('.');
		if (lastDot == -1)
		{
			// Internal reference.
			Node node = XpathUtil.xpathNode(domDoc, "//" + targetElement + "[@id='" + attrValue
				+ "']");
			ElementImpl elem = (ElementImpl)node;
			if (elem != null)
			{
				IRegion destRegion = new Region(elem.getStartOffset(), elem.getEndOffset()
					- elem.getStartOffset());
				return new IHyperlink[]{
					new ToXmlHyperlink(textViewer, linkRegion, attrValue, destRegion)
				};
			}
		}
		else if (lastDot + 1 < attrValue.length())
		{
			// External reference.
			IJavaProject project = MybatipseXmlUtil.getJavaProject(document);
			String namespace = attrValue.substring(0, lastDot);
			String elementId = attrValue.substring(lastDot + 1);
			IFile mapperFile = MapperNamespaceCache.getInstance().get(project, namespace, null);

			IDOMDocument mapperDocument = MybatipseXmlUtil.getMapperDocument(mapperFile);
			IDOMNode domNode = (IDOMNode)XpathUtil.xpathNode(mapperDocument, "//" + targetElement
				+ "[@id='" + elementId + "']");
			if (domNode != null)
			{
				IRegion destRegion = new Region(domNode.getStartOffset(), domNode.getEndOffset()
					- domNode.getStartOffset());
				return new IHyperlink[]{
					new ToXmlHyperlink(mapperFile, linkRegion, attrValue, destRegion)
				};
			}
		}
		return null;
	}

	private IHyperlink[] linkToJavaMapperType(IDocument document, Region linkRegion, Node node)
		throws JavaModelException, XPathExpressionException
	{
		String qualifiedName = MybatipseXmlUtil.getNamespace(node.getOwnerDocument());
		IJavaProject project = MybatipseXmlUtil.getJavaProject(document);
		IType javaType = project.findType(qualifiedName);
		if (javaType != null)
		{
			return new IHyperlink[]{
				new ToJavaHyperlink(javaType, linkRegion, javaLinkLabel("Mapper interface"))
			};
		}
		return null;
	}

	private IHyperlink[] linkToJavaType(IDocument document, String typeName, Region linkRegion)
		throws JavaModelException
	{
		IJavaProject project = MybatipseXmlUtil.getJavaProject(document);
		IType javaType = project.findType(typeName);
		if (javaType == null)
		{
			String found = TypeAliasCache.getInstance().resolveAlias(project, typeName, null);
			if (found != null)
			{
				javaType = project.findType(found);
			}
		}
		if (javaType != null)
		{
			return new IHyperlink[]{
				new ToJavaHyperlink(javaType, linkRegion, javaLinkLabel("class"))
			};
		}
		return null;
	}

	private IHyperlink[] linkToJavaMapperMethod(IDocument document, String methodName,
		Region linkRegion, Node node) throws JavaModelException, XPathExpressionException
	{
		String qualifiedName = MybatipseXmlUtil.getNamespace(node.getOwnerDocument());
		IJavaProject project = MybatipseXmlUtil.getJavaProject(document);
		IType javaType = project.findType(qualifiedName);
		if (javaType != null)
		{
			for (IMethod method : javaType.getMethods())
			{
				if (methodName.equals(method.getElementName()))
				{
					return new IHyperlink[]{
						new ToJavaHyperlink(method, linkRegion, javaLinkLabel("Mapper method"))
					};
				}
			}
		}
		return null;
	}

	private IHyperlink[] linkToJavaProperty(IDocument document, Node currentNode,
		String propertyName, Region linkRegion) throws JavaModelException
	{
		String qualifiedName = MybatipseXmlUtil.findEnclosingType(currentNode);
		// Ignore default type aliases.
		if (MybatipseXmlUtil.isDefaultTypeAlias(qualifiedName))
			return null;

		IJavaProject project = MybatipseXmlUtil.getJavaProject(document);
		IType javaType = project.findType(qualifiedName);
		if (javaType == null)
		{
			String resolvedAlias = TypeAliasCache.getInstance().resolveAlias(project, qualifiedName,
				null);
			if (resolvedAlias != null)
			{
				javaType = project.findType(resolvedAlias);
			}
		}
		if (javaType != null)
		{
			// TODO: should search setter first?
			// TODO: field of super type, nested property
			IField field = javaType.getField(propertyName);
			if (field != null)
			{
				return new IHyperlink[]{
					new ToJavaHyperlink(field, linkRegion, javaLinkLabel("property"))
				};
			}
		}
		return null;
	}

	private String javaLinkLabel(String target)
	{
		return MessageFormat.format("Open {0} in Java Editor", target);
	}

	private Region getLinkRegion(IStructuredDocumentRegion documentRegion, ITextRegion valueRegion)
	{
		int offset = documentRegion.getStartOffset() + valueRegion.getStart();
		int length = valueRegion.getTextLength();
		return new Region(offset, length);
	}

	/**
	 * Returns the node the cursor is currently on in the document. null if no node is selected
	 * 
	 * @param offset
	 * @return Node either element, doctype, text, or null
	 */
	private Node getCurrentNode(IDocument document, int offset)
	{
		// get the current node at the offset (returns either: element,
		// doctype, text)
		IndexedRegion inode = null;
		IStructuredModel sModel = null;
		try
		{
			sModel = StructuredModelManager.getModelManager().getExistingModelForRead(document);
			if (sModel != null)
			{
				inode = sModel.getIndexedRegion(offset);
				if (inode == null)
				{
					inode = sModel.getIndexedRegion(offset - 1);
				}
			}
		}
		finally
		{
			if (sModel != null)
				sModel.releaseFromRead();
		}

		if (inode instanceof Node)
		{
			return (Node)inode;
		}
		return null;
	}

}
