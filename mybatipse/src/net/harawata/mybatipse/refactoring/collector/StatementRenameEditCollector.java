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

import javax.xml.xpath.XPathExpressionException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.wst.xml.core.internal.document.AttrImpl;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.MybatipseConstants;
import net.harawata.mybatipse.mybatis.MapperNamespaceCache;
import net.harawata.mybatipse.mybatis.MybatipseXmlUtil;
import net.harawata.mybatipse.refactoring.ElementRenameInfo;
import net.harawata.mybatipse.util.XpathUtil;

/**
 * @author Iwao AVE!
 */
@SuppressWarnings("restriction")
public class StatementRenameEditCollector extends RenameEditCollector
{
	private boolean isSelect;

	private final String fullyQualifiedOldId;

	private final String quotedFullyQualifiedOldId;

	private final String quotedOldId;

	private final String fullyQualifiedNewId;

	public StatementRenameEditCollector(ElementRenameInfo info, boolean isSelect)
	{
		super(info);
		this.isSelect = isSelect;
		quotedOldId = "\"" + info.getOldId() + "\"";
		fullyQualifiedOldId = info.getNamespace() + "." + info.getOldId();
		quotedFullyQualifiedOldId = "\"" + fullyQualifiedOldId + "\"";
		fullyQualifiedNewId = info.getNamespace() + "." + info.getNewId();
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
			monitor.beginTask("Updating statement ID", 100);
			editXmlLocal(result);
			monitor.worked(30);
			if (isSelect)
				editJavaReferences(result);
		}
		catch (XPathExpressionException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		return result;
	}

	protected void editXmlLocal(RefactoringStatus result) throws XPathExpressionException
	{
		for (IFile mapperXml : MapperNamespaceCache.getInstance()
			.get(info.getProject(), info.getNamespace(), null))
		{
			final IDOMDocument mapperDocument = MybatipseXmlUtil.getMapperDocument(mapperXml);
			if (mapperDocument == null)
				return;
			List<ReplaceEdit> edits = getEdits(mapperXml);
			Node node = XpathUtil.xpathNode(mapperDocument,
				"//select[@id='" + info.getOldId() + "']/@id|//insert[@id='" + info.getOldId()
					+ "']/@id|//update[@id='" + info.getOldId() + "']/@id|//delete[@id='"
					+ info.getOldId() + "']/@id");
			if (node instanceof AttrImpl)
			{
				AttrImpl attrImpl = (AttrImpl)node;
				isSelect |= "select".equals(attrImpl.getOwnerElement().getNodeName());
				edits.add(new ReplaceEdit(attrImpl.getValueRegionStartOffset(),
					attrImpl.getValueRegion().getTextLength(), "\"" + info.getNewId() + "\""));
			}
			if (isSelect)
			{
				// Local references in XML mapper
				NodeList attrNodes = XpathUtil.xpathNodes(mapperDocument,
					"//*[@select='" + info.getOldId() + "']/@select");
				for (int i = 0; i < attrNodes.getLength(); i++)
				{
					AttrImpl attrImpl = (AttrImpl)attrNodes.item(i);
					edits.add(new ReplaceEdit(attrImpl.getValueRegionStartOffset(),
						attrImpl.getValueRegion().getTextLength(), "\"" + info.getNewId() + "\""));
				}
			}
		}
	}

	protected void editJavaReferences(RefactoringStatus result)
	{
		// Awful...any better way?
		editAnnotation(MybatipseConstants.ANNOTATION_ONE);
		editAnnotation(MybatipseConstants.ANNOTATION_MANY);
	}

	protected void editAnnotation(String annotationType)
	{
		SearchPattern pattern = SearchPattern.createPattern(annotationType,
			IJavaSearchConstants.ANNOTATION_TYPE, IJavaSearchConstants.ANNOTATION_TYPE_REFERENCE,
			SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE);
		SearchParticipant[] participants = {
			SearchEngine.getDefaultSearchParticipant()
		};
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaElement[]{
			info.getProject()
		});
		SearchRequestor requestor = new SearchRequestor()
		{
			@Override
			public void acceptSearchMatch(SearchMatch match) throws CoreException
			{
				Object element = match.getElement();
				if (element instanceof IMethod)
				{
					IMethod method = (IMethod)element;
					String mapperFqn = method.getDeclaringType().getFullyQualifiedName();
					for (IAnnotation anno : method.getAnnotations())
					{
						if ("Results".equals(anno.getElementName()))
						{
							for (IMemberValuePair resultsParam : anno.getMemberValuePairs())
							{
								if ("value".equals(resultsParam.getMemberName()))
								{
									Object resultsValue = resultsParam.getValue();
									if (resultsValue instanceof Object[])
									{
										for (Object resultAnno : (Object[])resultsValue)
										{
											parseResultAnnotation((IAnnotation)resultAnno, mapperFqn,
												(IFile)match.getResource());
										}
									}
									else
									{
										parseResultAnnotation((IAnnotation)resultsValue, mapperFqn,
											(IFile)match.getResource());
									}
									break;
								}
							}
							break;
						}
					}
				}
			}

			protected void parseResultAnnotation(IAnnotation resultAnno, String mapperFqn, IFile file)
				throws JavaModelException
			{
				for (IMemberValuePair resultParam : resultAnno.getMemberValuePairs())
				{
					String name = resultParam.getMemberName();
					if ("one".equals(name) || "many".equals(name))
					{
						IAnnotation annotation = (IAnnotation)resultParam.getValue();
						int oldIdIdx = -1;
						int oldIdLength = info.getOldId().length();
						String newId = info.getNewId();
						String source = annotation.getSource();
						if (info.getNamespace().equals(mapperFqn))
						{
							oldIdIdx = source.indexOf(quotedOldId);
						}
						if (oldIdIdx == -1)
						{
							oldIdIdx = source.indexOf(quotedFullyQualifiedOldId);
							oldIdLength = fullyQualifiedOldId.length();
							newId = fullyQualifiedNewId;
						}
						if (oldIdIdx > -1)
						{
							int offset = annotation.getSourceRange().getOffset() + oldIdIdx + 1;
							List<ReplaceEdit> edits = getEdits(file);
							edits.add(new ReplaceEdit(offset, oldIdLength, newId));
						}
						break;
					}
				}
			}
		};

		try
		{
			new SearchEngine().search(pattern, participants, scope, requestor, monitor);
		}
		catch (CoreException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
	}

}
