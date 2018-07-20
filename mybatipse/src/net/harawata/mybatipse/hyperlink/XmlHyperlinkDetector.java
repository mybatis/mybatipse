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

package net.harawata.mybatipse.hyperlink;

import java.io.IOException;

import javax.xml.xpath.XPathExpressionException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegionList;
import org.eclipse.wst.sse.core.utils.StringUtils;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMNode;
import org.eclipse.wst.xml.core.internal.regions.DOMRegionContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.HasSelectAnnotation;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.RejectStatementAnnotation;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.ResultsAnnotationWithId;
import net.harawata.mybatipse.mybatis.MapperNamespaceCache;
import net.harawata.mybatipse.mybatis.MybatipseXmlUtil;
import net.harawata.mybatipse.mybatis.TypeAliasCache;
import net.harawata.mybatipse.util.XpathUtil;

/**
 * @author Iwao AVE!
 */
@SuppressWarnings("restriction")
public class XmlHyperlinkDetector extends HyperlinkDetector
{
	public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region,
		boolean canShowMultipleHyperlinks)
	{
		IHyperlink hyperlink = null;
		if (textViewer != null && region != null)
		{
			IDocument document = textViewer.getDocument();

			if (document != null)
			{
				Node currentNode = getCurrentNode(document, region.getOffset());
				if (currentNode != null && currentNode.getNodeType() == Node.ELEMENT_NODE)
				{
					Element element = (Element)currentNode;
					IStructuredDocumentRegion documentRegion = ((IStructuredDocument)document)
						.getRegionAtCharacterOffset(region.getOffset());
					ITextRegion textRegion = documentRegion
						.getRegionAtCharacterOffset(region.getOffset());
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
							IJavaProject project = MybatipseXmlUtil.getJavaProject(document);
							if ("namespace".equals(attrName))
							{
								hyperlink = linkToJavaMapperType(document,
									getLinkRegion(documentRegion, valueRegion), currentNode);
							}
							else if ("id".equals(attrName)
								&& ("select".equals(tagName) || "update".equals(tagName)
									|| "insert".equals(tagName) || "delete".equals(tagName)))
							{
								hyperlink = linkToJavaMapperMethod(project,
									MybatipseXmlUtil.getNamespace(currentNode.getOwnerDocument()),
									getLinkRegion(documentRegion, valueRegion),
									new RejectStatementAnnotation(attrValue, true));
							}
							else if ("property".equals(attrName))
							{
								hyperlink = linkToJavaProperty(project,
									MybatipseXmlUtil.findEnclosingType(currentNode), attrValue,
									getLinkRegion(documentRegion, valueRegion));
							}
							else if ("type".equals(attrName) || "resultType".equals(attrName)
								|| "parameterType".equals(attrName) || "ofType".equals(attrName))
							{
								hyperlink = linkToJavaType(document, attrValue,
									getLinkRegion(documentRegion, valueRegion));
							}
							else if ("refid".equals(attrName))
							{
								hyperlink = linkToReference(textViewer, project, element.getOwnerDocument(),
									attrValue, getLinkRegion(documentRegion, valueRegion), "sql");
							}
							else if ("select".equals(attrName))
							{
								hyperlink = linkToReference(textViewer, project, element.getOwnerDocument(),
									attrValue, getLinkRegion(documentRegion, valueRegion), "select");
							}
							else if ("extends".equals(attrName) || "resultMap".equals(attrName))
							{
								// TODO: multiple result maps
								hyperlink = linkToReference(textViewer, project, element.getOwnerDocument(),
									attrValue, getLinkRegion(documentRegion, valueRegion), "resultMap");
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
		return hyperlink == null ? null : new IHyperlink[]{
			hyperlink
		};
	}

	private IHyperlink linkToReference(ITextViewer textViewer, IJavaProject project,
		Document domDoc, String attrValue, Region linkRegion, String targetElement)
		throws XPathExpressionException, CoreException, IOException
	{
		if (attrValue.indexOf("${") > -1)
			return null;

		final int lastDot = attrValue.lastIndexOf('.');
		final String namespace = lastDot == -1 ? MybatipseXmlUtil.getNamespace(domDoc)
			: attrValue.substring(0, lastDot);
		final String elementId = attrValue.substring(lastDot + 1);

		if (elementId.length() == 0)
			return null;

		final String elementXpath = "//" + targetElement + "[@id='" + elementId + "']";
		for (IFile xmlMapperFile : MapperNamespaceCache.getInstance().get(project, namespace, null))
		{
			IDOMNode node = (IDOMNode)XpathUtil
				.xpathNode(MybatipseXmlUtil.getMapperDocument(xmlMapperFile), elementXpath);
			if (node != null)
			{
				IRegion destRegion = new Region(node.getStartOffset(),
					node.getEndOffset() - node.getStartOffset());
				return new ToXmlHyperlink(xmlMapperFile, linkRegion, attrValue, destRegion);
			}
		}

		// Couldn't find matching XML element. Search java element if applicable.
		if ("select".equals(targetElement))
		{
			return linkToJavaMapperMethod(project, namespace, linkRegion,
				new HasSelectAnnotation(elementId, true));
		}
		else if ("resultMap".equals(targetElement))
		{
			return linkToJavaMapperMethod(project, namespace, linkRegion,
				new ResultsAnnotationWithId(elementId, true));
		}

		return null;
	}

	private IHyperlink linkToJavaMapperType(IDocument document, Region linkRegion, Node node)
		throws JavaModelException, XPathExpressionException
	{
		String qualifiedName = MybatipseXmlUtil.getNamespace(node.getOwnerDocument());
		IJavaProject project = MybatipseXmlUtil.getJavaProject(document);
		IType javaType = project.findType(qualifiedName);
		if (javaType != null)
		{
			return new ToJavaHyperlink(javaType, linkRegion, javaLinkLabel("Mapper interface"));
		}
		return null;
	}

	private IHyperlink linkToJavaType(IDocument document, String typeName, Region linkRegion)
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
			return new ToJavaHyperlink(javaType, linkRegion, javaLinkLabel("class"));
		}
		return null;
	}

	private Region getLinkRegion(IStructuredDocumentRegion documentRegion,
		ITextRegion valueRegion)
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
