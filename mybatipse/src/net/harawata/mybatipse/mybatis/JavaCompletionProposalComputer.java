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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.harawata.mybatipse.Activator;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IAnnotatable;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.w3c.dom.Document;

/**
 * @author Iwao AVE!
 */
public class JavaCompletionProposalComputer implements IJavaCompletionProposalComputer
{
	private static final List<String> statementAnnotations = Arrays.asList("Select", "Update",
		"Insert", "Delete");

	public void sessionStarted()
	{
		// Nothing todo for now.
	}

	public List<ICompletionProposal> computeCompletionProposals(
		ContentAssistInvocationContext context, IProgressMonitor monitor)
	{
		if (context instanceof JavaContentAssistInvocationContext)
		{
			JavaContentAssistInvocationContext javaContext = (JavaContentAssistInvocationContext)context;
			ICompilationUnit unit = javaContext.getCompilationUnit();
			try
			{
				if (unit == null || !unit.isStructureKnown())
					return Collections.emptyList();
				IType primaryType = unit.findPrimaryType();
				if (primaryType == null || !primaryType.isInterface())
					return Collections.emptyList();

				int offset = javaContext.getInvocationOffset();
				IJavaElement element = unit.getElementAt(offset);
				if (element == null || !(element instanceof IMethod))
					return Collections.emptyList();

				IAnnotation annotation = getAnnotationAt((IAnnotatable)element, offset);
				if (annotation == null)
					return Collections.emptyList();

				IMethod method = (IMethod)element;
				if (isStatementAnnotation(annotation))
				{
					return proposeStatementText(javaContext, unit, offset, annotation, method);
				}
				else if ("ResultMap".equals(annotation.getElementName()))
				{
					String text = annotation.getSource();
					// This can be null right after emptying the literal, for example.
					if (text == null)
						return Collections.emptyList();
					SimpleParser parser = new SimpleParser(text, offset
						- annotation.getSourceRange().getOffset() - 1);
					final IJavaProject project = javaContext.getProject();
					IFile mapperFile = MapperNamespaceCache.getInstance().get(project,
						primaryType.getFullyQualifiedName(), null);
					if (mapperFile != null)
					{
						Document mapperDoc = MybatipseXmlUtil.getMapperDocument(mapperFile);
						String matchString = parser.getMatchString();
						return ProposalComputorHelper.proposeReference(project, mapperDoc, matchString,
							offset - matchString.length(), parser.getReplacementLength(), "resultMap");
					}
				}
			}
			catch (JavaModelException e)
			{
				Activator.log(Status.ERROR, "Something went wrong.", e);
			}
		}
		return Collections.emptyList();
	}

	private List<ICompletionProposal> proposeStatementText(
		JavaContentAssistInvocationContext javaContext, ICompilationUnit unit, int offset,
		IAnnotation annotation, IMethod method) throws JavaModelException
	{
		if (method.getParameters().length == 0)
			return Collections.emptyList();

		String text = annotation.getSource();
		int offsetInText = offset - annotation.getSourceRange().getOffset() - 1;
		ExpressionProposalParser parser = new ExpressionProposalParser(text, offsetInText);
		if (parser.isProposable())
		{
			String matchString = parser.getMatchString();
			offset -= matchString.length();
			int length = parser.getReplacementLength();
			final IJavaProject project = javaContext.getProject();
			String proposalTarget = parser.getProposalTarget();
			if (proposalTarget == null || proposalTarget.length() == 0)
				return ProposalComputorHelper.proposeOptionName(offset, length, matchString);
			else if ("property".equals(proposalTarget))
			{
				CompilationUnit astNode = JavaMapperUtil.getAstNode(unit);
				Map<String, String> paramMap = JavaMapperUtil.getMethodParameters(astNode, method);
				return ProposalComputorHelper.proposeParameters(project, offset, length, paramMap,
					true, matchString);
			}
			else if ("jdbcType".equals(proposalTarget))
				return ProposalComputorHelper.proposeJdbcType(offset, length, matchString);
			else if ("javaType".equals(proposalTarget))
				return ProposalComputorHelper.proposeJavaType(project, offset, length, true,
					matchString);
			else if ("typeHandler".equals(proposalTarget))
				return ProposalComputorHelper.proposeTypeHandler(project, offset, length, matchString);
		}
		return Collections.emptyList();
	}

	private boolean isStatementAnnotation(IAnnotation annotation)
	{
		String annotationName = annotation.getElementName();
		return statementAnnotations.contains(annotationName);
	}

	private IAnnotation getAnnotationAt(IAnnotatable annotatable, int offset)
	{
		try
		{
			IAnnotation[] annotations = annotatable.getAnnotations();
			for (IAnnotation annotation : annotations)
			{
				ISourceRange sourceRange = annotation.getSourceRange();
				if (isInRange(sourceRange, offset))
				{
					return annotation;
				}
			}
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		return null;
	}

	private boolean isInRange(ISourceRange sourceRange, int offset)
	{
		int start = sourceRange.getOffset();
		int end = start + sourceRange.getLength();
		return start <= offset && offset <= end;
	}

	public List<IContextInformation> computeContextInformation(
		ContentAssistInvocationContext context, IProgressMonitor monitor)
	{
		return Collections.emptyList();
	}

	public String getErrorMessage()
	{
		return null;
	}

	public void sessionEnded()
	{
		// Nothing todo for now.
	}

	private class SimpleParser
	{
		private String text;

		private int offset;

		private String matchString;

		private SimpleParser(String text, int offset)
		{
			super();
			this.text = text;
			this.offset = offset;
			parse();
		}

		private void parse()
		{
			int start = text.lastIndexOf('"', offset);
			matchString = text.substring(start + 1, offset + 1);
		}

		public int getReplacementLength()
		{
			int i = offset + 1;
			for (; i < text.length(); i++)
			{
				char c = text.charAt(i);
				if (c == 0x0A || c == 0x0D)
				{
					return i - offset - 1 + matchString.length();
				}
				else if (c == '"')
				{
					return i - offset - 1 + matchString.length();
				}
			}
			return i - offset - 1 + matchString.length();
		}

		public String getMatchString()
		{
			return matchString;
		}
	}
}
