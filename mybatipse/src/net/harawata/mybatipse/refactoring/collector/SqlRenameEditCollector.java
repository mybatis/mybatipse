/*-******************************************************************************
 * Copyright (c) 2016 Iwao AVE!.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Iwao AVE! - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.refactoring.collector;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.xpath.XPathExpressionException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.search.core.text.TextSearchEngine;
import org.eclipse.search.ui.text.FileTextSearchScope;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegion;
import org.eclipse.wst.xml.core.internal.document.AttrImpl;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.mybatis.MapperNamespaceCache;
import net.harawata.mybatipse.mybatis.MybatipseXmlUtil;
import net.harawata.mybatipse.refactoring.ElementRenameInfo;
import net.harawata.mybatipse.refactoring.XmlAttrEditRequestor;
import net.harawata.mybatipse.util.XpathUtil;

/**
 * @author Iwao AVE!
 */
@SuppressWarnings("restriction")
public class SqlRenameEditCollector extends RenameEditCollector
{
	public SqlRenameEditCollector(ElementRenameInfo info)
	{
		super(info);
	}

	@Override
	public RefactoringStatus collect(Map<IFile, List<ReplaceEdit>> editsPerFiles,
		IProgressMonitor monitor)
	{
		this.editsPerFiles = editsPerFiles;
		this.monitor = monitor;
		RefactoringStatus result = new RefactoringStatus();
		try
		{
			monitor.beginTask("Updating sql ID", 100);
			editLocal(result);
			monitor.worked(30);
			editFullyQualifiedRef(result);
		}
		catch (XPathExpressionException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		return result;
	}

	private void editLocal(RefactoringStatus result) throws XPathExpressionException
	{
		for (IFile sourceXmlFile : MapperNamespaceCache.getInstance()
			.get(info.getProject(), info.getNamespace(), null))
		{
			IDOMDocument sourceXmlDoc = MybatipseXmlUtil.getMapperDocument(sourceXmlFile);
			if (sourceXmlDoc == null)
				return;
			List<ReplaceEdit> edits = getEdits(sourceXmlFile);
			// Source <sql /> element
			Node node = XpathUtil.xpathNode(sourceXmlDoc, "//sql[@id='" + info.getOldId() + "']/@id");
			if (node instanceof AttrImpl)
			{
				AttrImpl attrImpl = (AttrImpl)node;
				edits.add(new ReplaceEdit(attrImpl.getValueRegionStartOffset(),
					attrImpl.getValueRegion().getTextLength(), "\"" + info.getNewId() + "\""));
			}
			// Local <include /> elements
			NodeList references = XpathUtil.xpathNodes(sourceXmlDoc,
				"//*[@refid='" + info.getOldId() + "']/@refid");
			for (int i = 0; i < references.getLength(); i++)
			{
				AttrImpl attrImpl = (AttrImpl)references.item(i);
				ITextRegion valueRegion = attrImpl.getValueRegion();
				edits.add(new ReplaceEdit(attrImpl.getValueRegionStartOffset(),
					valueRegion.getTextLength(), "\"" + info.getNewId() + "\""));
			}
		}
	}

	private void editFullyQualifiedRef(RefactoringStatus result)
	{
		String[] fileNamePatterns = new String[]{
			"*.xml"
		};
		FileTextSearchScope scope = FileTextSearchScope.newSearchScope(new IResource[]{
			info.getProject().getProject()
		}, fileNamePatterns, false);
		XmlAttrEditRequestor requestor = new XmlAttrEditRequestor("refid",
			"\"" + info.getNamespace() + "." + info.getNewId() + "\"", editsPerFiles);
		Pattern pattern = Pattern
			.compile("\"" + info.getNamespace() + "." + info.getOldId() + "\"");
		TextSearchEngine.create().search(scope, requestor, pattern, monitor);
	}
}
