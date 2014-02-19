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

import javax.xml.xpath.XPathExpressionException;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.hyperlink.ToXmlHyperlink;
import net.harawata.mybatipse.util.XpathUtil;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMNode;

/**
 * @author Iwao AVE!
 */
@SuppressWarnings("restriction")
public class JavaHyperlinkDetector extends AbstractHyperlinkDetector
{
	@Override
	public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region,
		boolean canShowMultipleHyperlinks)
	{
		IHyperlink[] links = null;
		ITextEditor editor = (ITextEditor)getAdapter(ITextEditor.class);
		IEditorInput input = editor.getEditorInput();
		IJavaElement element = (IJavaElement)input.getAdapter(IJavaElement.class);
		ITypeRoot typeRoot = (ITypeRoot)element.getAdapter(ITypeRoot.class);
		try
		{
			IJavaElement[] srcElements = typeRoot.codeSelect(region.getOffset(), region.getLength());
			if (srcElements.length == 1)
			{
				IJavaElement srcElement = srcElements[0];
				switch (srcElement.getElementType())
				{
					case IJavaElement.METHOD:
						links = getLinks(typeRoot, null, "//*[@id='" + srcElement.getElementName() + "']",
							region);
						break;
					case IJavaElement.TYPE:
						links = getLinks(typeRoot, null, "//mapper", region);
						break;
					default:
						break;
				}
			}
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		return links;
	}

	private IHyperlink[] getLinks(ITypeRoot typeRoot, IType triggerType, String expression,
		IRegion srcRegion) throws JavaModelException
	{
		IType primaryType = typeRoot.findPrimaryType();
		if (primaryType.isInterface() && (triggerType == null || primaryType.equals(triggerType)))
		{
			IJavaProject project = primaryType.getJavaProject();
			if (project != null)
			{
				IFile mapperFile = MapperNamespaceCache.getInstance().get(project,
					primaryType.getFullyQualifiedName(), null);
				if (mapperFile != null)
				{
					IDOMDocument mapperDocument = MybatipseXmlUtil.getMapperDocument(mapperFile);
					if (mapperDocument != null)
					{
						try
						{
							IDOMNode domNode = (IDOMNode)XpathUtil.xpathNode(mapperDocument, expression);
							if (domNode != null)
							{
								Region destRegion = new Region(domNode.getStartOffset(), domNode.getEndOffset()
									- domNode.getStartOffset());
								String label = "Open <" + domNode.getNodeName() + "/> in XML mapper.";
								return new IHyperlink[]{
									new ToXmlHyperlink(mapperFile, srcRegion, label, destRegion)
								};
							}
						}
						catch (XPathExpressionException e)
						{
							Activator.log(Status.ERROR, e.getMessage(), e);
						}
					}
				}
			}
		}
		return null;
	}
}
