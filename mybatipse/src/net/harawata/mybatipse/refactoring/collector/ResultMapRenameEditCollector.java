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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.xpath.XPathExpressionException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.search.core.text.TextSearchEngine;
import org.eclipse.search.ui.text.FileTextSearchScope;
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
import net.harawata.mybatipse.refactoring.XmlAttrEditRequestor;
import net.harawata.mybatipse.util.XpathUtil;

/**
 * @author Iwao AVE!
 */
@SuppressWarnings("restriction")
public class ResultMapRenameEditCollector extends RenameEditCollector
{
	public ResultMapRenameEditCollector(ElementRenameInfo info)
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
			editXmlLocal(result);
			editJavaSource(result);
			editXmlFullyQualifiedRef(result);
			editJavaReferences(result);
		}
		catch (XPathExpressionException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		return result;
	}

	private void editXmlLocal(RefactoringStatus result) throws XPathExpressionException
	{
		for (IFile sourceXmlFile : MapperNamespaceCache.getInstance()
			.get(info.getProject(), info.getNamespace(), null))
		{
			IDOMDocument sourceXmlDoc = MybatipseXmlUtil.getMapperDocument(sourceXmlFile);
			if (sourceXmlDoc == null)
				return;
			List<ReplaceEdit> edits = getEdits(sourceXmlFile);
			// Source <resultMap /> element
			Node node = XpathUtil.xpathNode(sourceXmlDoc,
				"//resultMap[@id='" + info.getOldId() + "']/@id");
			if (node instanceof AttrImpl)
			{
				AttrImpl attrImpl = (AttrImpl)node;
				edits.add(new ReplaceEdit(attrImpl.getValueRegionStartOffset(),
					attrImpl.getValueRegion().getTextLength(), "\"" + info.getNewId() + "\""));
			}
			// Local resultMap references
			NodeList references = XpathUtil.xpathNodes(sourceXmlDoc,
				"//*[@resultMap]/@resultMap|//*[@extends]/@extends");
			for (int i = 0; i < references.getLength(); i++)
			{
				AttrImpl attrImpl = (AttrImpl)references.item(i);
				String attrValue = attrImpl.getValue();
				int commaPos = attrValue.lastIndexOf(',');
				String oldId = info.getOldId();
				if (commaPos == -1)
				{
					if (oldId.equals(attrValue))
					{
						edits.add(new ReplaceEdit(attrImpl.getValueRegionStartOffset(),
							attrImpl.getValueRegion().getTextLength(), "\"" + info.getNewId() + "\""));
					}
				}
				else
				{
					int valueLength = attrValue.length();
					int end = valueLength;
					do
					{
						if (oldId.equals(attrValue.substring(commaPos + 1, end).trim()))
						{
							StringBuilder newValue = new StringBuilder();
							newValue.append('"');
							if (commaPos > 0)
								newValue.append(attrValue.substring(0, commaPos + 1));
							newValue.append(info.getNewId());
							if (end < valueLength)
								newValue.append(attrValue.substring(end, valueLength));
							newValue.append('"');
							edits.add(new ReplaceEdit(attrImpl.getValueRegionStartOffset(),
								attrImpl.getValueRegion().getTextLength(), newValue.toString()));
							break;
						}
						end = commaPos;
						commaPos = attrValue.lastIndexOf(',', commaPos - 1);
					}
					while (end > -1);
				}
			}
		}
	}

	private void editJavaSource(RefactoringStatus result) throws JavaModelException
	{
		final IType mapperType = info.getProject().findType(info.getNamespace());
		if (mapperType == null)
			return;
		final ICompilationUnit compilationUnit = mapperType.getCompilationUnit();
		if (compilationUnit == null)
			return;

		final ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(compilationUnit);
		parser.setResolveBindings(true);
		parser.setIgnoreMethodBodies(true);
		final CompilationUnit astUnit = (CompilationUnit)parser.createAST(null);
		astUnit.accept(new ASTVisitor()
		{
			private String typeFqn;

			@Override
			public boolean visit(TypeDeclaration node)
			{
				typeFqn = node.resolveBinding().getBinaryName();
				return true;
			}

			@Override
			public boolean visit(NormalAnnotation anno)
			{
				if (!info.getNamespace().equals(typeFqn))
					return false;
				String name = anno.getTypeName().getFullyQualifiedName();
				if ("Results".equals(name))
				{
					@SuppressWarnings("unchecked")
					List<MemberValuePair> pairs = anno.values();
					for (MemberValuePair pair : pairs)
					{
						SimpleName key = pair.getName();
						Expression value = pair.getValue();
						if ("id".equals(key.getFullyQualifiedName())
							&& ("\"" + info.getOldId() + "\"").equals(value.toString()))
						{
							List<ReplaceEdit> edits = getEdits((IFile)compilationUnit.getResource());
							edits.add(new ReplaceEdit(value.getStartPosition(), value.getLength(),
								"\"" + info.getNewId() + "\""));
							break;
						}
					}
				}
				return true;
			}
		});
	}

	protected void editJavaReferences(RefactoringStatus result)
	{
		SearchPattern pattern = SearchPattern.createPattern(
			MybatipseConstants.ANNOTATION_RESULT_MAP, IJavaSearchConstants.ANNOTATION_TYPE,
			IJavaSearchConstants.ANNOTATION_TYPE_REFERENCE,
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
					IAnnotation annotation = method.getAnnotation("ResultMap");
					int oldIdIdx = -1;
					int oldIdLength = info.getOldId().length();
					String newId = info.getNewId();
					if (info.getNamespace().equals(mapperFqn))
					{
						oldIdIdx = annotation.getSource()
							.indexOf("\"" + info.getOldId() + "\"", "@ResultMap(".length());
					}
					// @ResultMap("resultMap1,resultMap2") : this format is not supported.
					// @ReusltMap("resultMap1", "resultMap2") : this format is.
					if (oldIdIdx == -1)
					{
						String fullyQualifiedOldId = info.getNamespace() + "." + info.getOldId();
						oldIdIdx = annotation.getSource()
							.indexOf("\"" + fullyQualifiedOldId + "\"", "@ResultMap(".length());
						oldIdLength = fullyQualifiedOldId.length();
						newId = info.getNamespace() + "." + info.getNewId();
					}
					if (oldIdIdx > -1)
					{
						int offset = annotation.getSourceRange().getOffset() + oldIdIdx + 1;
						List<ReplaceEdit> edits = getEdits((IFile)match.getResource());
						edits.add(new ReplaceEdit(offset, oldIdLength, newId));
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

	private void editXmlFullyQualifiedRef(RefactoringStatus result)
	{
		String[] fileNamePatterns = new String[]{
			"*.xml"
		};
		FileTextSearchScope scope = FileTextSearchScope.newSearchScope(new IResource[]{
			info.getProject().getProject()
		}, fileNamePatterns, false);
		XmlAttrEditRequestor requestor = new XmlAttrEditRequestor(
			Arrays.asList("resultMap", "extends"), info.getNamespace() + "." + info.getNewId(),
			editsPerFiles);
		Pattern pattern = Pattern.compile(info.getNamespace() + "." + info.getOldId());
		TextSearchEngine.create().search(scope, requestor, pattern, monitor);
	}
}
