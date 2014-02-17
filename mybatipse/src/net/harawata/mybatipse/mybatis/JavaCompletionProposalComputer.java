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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.harawata.mybatipse.Activator;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IAnnotatable;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;

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
		List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		if (context instanceof JavaContentAssistInvocationContext)
		{
			JavaContentAssistInvocationContext javaContext = (JavaContentAssistInvocationContext)context;
			ICompilationUnit unit = javaContext.getCompilationUnit();
			try
			{
				if (unit == null || !unit.isStructureKnown() || !unit.findPrimaryType().isInterface())
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
					if (method.getParameters().length == 0)
						return Collections.emptyList();
					String text = annotation.getSource();
					int offsetInText = offset - annotation.getSourceRange().getOffset() - 1;
					ExpressionProposalParser parser = new ExpressionProposalParser(text, offsetInText);
					if (parser.isProposable())
					{
						String matchString = parser.getMatchString();
						offset -= matchString.length();
						int length = matchString.length() + parser.getReplacementLength();
						final IJavaProject project = javaContext.getProject();
						String proposalTarget = parser.getProposalTarget();
						if (proposalTarget == null || proposalTarget.length() == 0)
							proposals = ProposalComputorHelper.proposeOptionName(offset, length, matchString);
						else if ("property".equals(proposalTarget))
						{
							CompilationUnit astNode = JavaMapperUtil.getAstNode(unit);
							Map<String, String> paramMap = JavaMapperUtil.getMethodParameters(astNode, method);
							proposals = ProposalComputorHelper.proposeParameters(project, offset, length,
								paramMap, matchString);
						}
						else if ("jdbcType".equals(proposalTarget))
							proposals = ProposalComputorHelper.proposeJdbcType(offset, length, matchString);
						else if ("javaType".equals(proposalTarget))
							proposals = ProposalComputorHelper.proposeJavaType(project, offset, length, true,
								matchString);
						else if ("typeHandler".equals(proposalTarget))
							proposals = ProposalComputorHelper.proposeTypeHandler(project, offset, length,
								matchString);
						return proposals;
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
}
