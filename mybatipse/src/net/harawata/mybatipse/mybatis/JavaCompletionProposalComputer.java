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
import java.util.Map.Entry;

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
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.MybatipseConstants;
import net.harawata.mybatipse.bean.SupertypeHierarchyCache;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.MethodNameMatcher;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.MethodParametersStore;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.MethodReturnTypeStore;
import net.harawata.mybatipse.util.NameUtil;

/**
 * @author Iwao AVE!
 */
public class JavaCompletionProposalComputer implements IJavaCompletionProposalComputer
{
	private static final List<String> inlineStatementAnnotations = Arrays.asList("Select",
		"Update", "Insert", "Delete");

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

				IAnnotation annotation = JavaMapperUtil.getAnnotationAt((IAnnotatable)element, offset);
				if (annotation == null)
					return Collections.emptyList();

				final IJavaProject project = javaContext.getProject();
				final IMethod method = (IMethod)element;
				final String mapperFqn = method.getDeclaringType().getFullyQualifiedName();
				if (isInlineStatementAnnotation(annotation))
				{
					return proposeStatementText(project, unit, offset, annotation, method);
				}
				else
				{
					String elementName = annotation.getElementName();
					if ("ResultMap".equals(elementName))
					{
						return proposeResultMap(project, mapperFqn, offset, annotation);
					}
					else if ("Results".equals(elementName))
					{
						return proposeResults(project, mapperFqn, offset, annotation, method);
					}
					else if ("ConstructorArgs".equals(elementName))
					{
						return proposeConstructorArgs(project, mapperFqn, offset, annotation, method);
					}
					else if ("Options".equals(elementName) || "SelectKey".equals(elementName))
					{
						return proposeKeyProperty(project, mapperFqn, offset, annotation, method);
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

	private List<ICompletionProposal> proposeConstructorArgs(final IJavaProject project,
		final String mapperFqn, int offset, IAnnotation annotation, final IMethod method)
		throws JavaModelException
	{
		final AnnotationParser parser = new AnnotationParser(annotation, offset);
		String key = parser.getKey();
		if ("resultMap".equals(key))
		{
			String matchString = parser.getValue();
			return ProposalComputorHelper.proposeReference(project, mapperFqn, matchString,
				offset - matchString.length(), parser.getValueLength(), "resultMap", null);
		}
		else if ("select".equals(key))
		{
			String matchString = parser.getValue();
			return ProposalComputorHelper.proposeReference(project, mapperFqn, matchString,
				offset - matchString.length(), parser.getValueLength(), "select",
				method.getElementName());
		}
		return Collections.emptyList();
	}

	private List<ICompletionProposal> proposeKeyProperty(IJavaProject project, String mapperFqn,
		int offset, IAnnotation annotation, IMethod method) throws JavaModelException
	{
		AnnotationParser parser = new AnnotationParser(annotation, offset);
		if ("keyProperty".equals(parser.getKey()))
		{
			final MethodParametersStore methodStore = new MethodParametersStore(project);
			JavaMapperUtil.findMapperMethod(methodStore, project, mapperFqn,
				new MethodNameMatcher(method.getElementName(), true));
			if (!methodStore.isEmpty())
			{
				Map<String, String> paramMap = methodStore.getParamMap();
				if (paramMap.isEmpty())
				{
					return Collections.emptyList();
				}
				String actualParamType = null;
				String paramType = null;
				for (Entry<String, String> entry : paramMap.entrySet())
				{
					paramType = entry.getValue();
					// only the first parameter for now
					break;
				}
				if (paramType.indexOf('<') == -1)
				{
					actualParamType = paramType;
				}
				else
				{
					IType rawType = project.findType(NameUtil.stripTypeArguments(paramType));
					if (SupertypeHierarchyCache.getInstance().isCollection(rawType))
					{
						List<String> typeParams = NameUtil.extractTypeParams(paramType);
						if (typeParams.size() == 1)
						{
							actualParamType = typeParams.get(0);
						}
					}
				}
				if (actualParamType != null)
				{
					String matchString = String.valueOf(parser.getValue());
					return ProposalComputorHelper.proposePropertyFor(project,
						offset - matchString.length(), parser.getValueLength(), actualParamType, false, -1,
						matchString);
				}
			}
		}
		return Collections.emptyList();
	}

	private List<ICompletionProposal> proposeResults(IJavaProject project, String mapperFqn,
		int offset, IAnnotation annotation, IMethod method) throws JavaModelException
	{
		final List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		AnnotationParser parser = new AnnotationParser(annotation, offset);
		String annotationAttrName = parser.getKey();
		if ("property".equals(annotationAttrName))
		{
			proposeProperty(proposals, project, mapperFqn, method, offset, parser);
		}
		else if ("select".equals(annotationAttrName))
		{
			String matchString = parser.getValue();
			proposals.addAll(ProposalComputorHelper.proposeReference(project, mapperFqn, matchString,
				offset - matchString.length(), parser.getValueLength(), "select",
				method.getElementName()));
		}
		return proposals;
	}

	private void proposeProperty(List<ICompletionProposal> proposals, IJavaProject project,
		String mapperFqn, IMethod method, int offset, AnnotationParser parser)
		throws JavaModelException
	{
		final MethodReturnTypeStore methodStore = new MethodReturnTypeStore();
		JavaMapperUtil.findMapperMethod(methodStore, project, mapperFqn,
			new MethodNameMatcher(method.getElementName(), true));
		if (!methodStore.isEmpty())
		{
			String returnType = NameUtil.manageableReturnType(project, methodStore.getReturnType());
			if (returnType != null)
			{
				String matchString = String.valueOf(parser.getValue());
				proposals.addAll(
					ProposalComputorHelper.proposePropertyFor(project, offset - matchString.length(),
						parser.getValueLength(), returnType, false, -1, matchString));
			}
		}
	}

	private List<ICompletionProposal> proposeResultMap(IJavaProject project, String mapperFqn,
		int offset, IAnnotation annotation) throws JavaModelException
	{
		String text = annotation.getSource();
		// This can be null right after emptying the literal, for example.
		if (text == null)
			return Collections.emptyList();
		SimpleParser parser = new SimpleParser(text,
			offset - annotation.getSourceRange().getOffset() - 1);
		String matchString = parser.getMatchString();
		return ProposalComputorHelper.proposeReference(project, mapperFqn, matchString,
			offset - matchString.length(), parser.getReplacementLength(), "resultMap", null);
	}

	private List<ICompletionProposal> proposeStatementText(IJavaProject project,
		ICompilationUnit unit, int offset, IAnnotation annotation, IMethod method)
		throws JavaModelException
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
			String proposalTarget = parser.getProposalTarget();
			if (proposalTarget == null || proposalTarget.length() == 0)
				return ProposalComputorHelper.proposeOptionName(offset, length, matchString);
			else if ("property".equals(proposalTarget))
			{
				if (unit == null || !unit.isStructureKnown())
					return Collections.emptyList();
				IType primaryType = unit.findPrimaryType();
				if (primaryType == null || !primaryType.isInterface())
					return Collections.emptyList();

				final MethodParametersStore methodStore = new MethodParametersStore(project);
				String mapperFqn = primaryType.getFullyQualifiedName();
				JavaMapperUtil.findMapperMethod(methodStore, project, mapperFqn,
					new MethodNameMatcher(method.getElementName(), true));
				if (!methodStore.isEmpty())
				{
					return ProposalComputorHelper.proposeParameters(project, offset, length,
						methodStore.getParamMap(), null, true, matchString);
				}
			}
			else if ("jdbcType".equals(proposalTarget))
				return ProposalComputorHelper.proposeJdbcType(offset, length, matchString);
			else if ("javaType".equals(proposalTarget))
				return ProposalComputorHelper.proposeJavaType(project, offset, length, true,
					matchString);
			else if ("typeHandler".equals(proposalTarget))
				return ProposalComputorHelper.proposeAssignable(project, offset, length, matchString,
					MybatipseConstants.TYPE_TYPE_HANDLER);
		}
		return Collections.emptyList();
	}

	private boolean isInlineStatementAnnotation(IAnnotation annotation)
	{
		String annotationName = annotation.getElementName();
		return inlineStatementAnnotations.contains(annotationName);
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

	private class AnnotationParser
	{
		private IAnnotation annotation;

		private int offset;

		private StringBuilder key = new StringBuilder();

		private StringBuilder value = new StringBuilder();

		private int valueLength;

		public AnnotationParser(IAnnotation annotation, int offset) throws JavaModelException
		{
			super();
			this.annotation = annotation;
			this.offset = offset;
			parse();
		}

		private void parse() throws JavaModelException
		{
			ISourceRange annotationRange = annotation.getSourceRange();
			int annotationOffset = annotationRange.getOffset();
			String source = annotation.getSource();
			int back;
			// get the word under the cursor
			for (back = offset - annotationOffset - 1; back > 0; back--)
			{
				char c = source.charAt(back);
				if (c == ',' || c == '"')
				{
					break;
				}
				else if (Character.isWhitespace(c))
				{
					// ignore
				}
				else
				{
					value.insert(0, c);
				}
			}
			valueLength += value.length();
			// get the rest of the word that will be overwritten
			for (int forward = offset - annotationOffset; forward < annotationRange
				.getLength(); forward++)
			{
				char c = source.charAt(forward);
				if (c == ',' || c == '"')
				{
					break;
				}
				else
				{
					valueLength++;
				}
			}
			// move the pointer back to the dquote
			for (; back > 0; back--)
			{
				if (source.charAt(back) == '"')
				{
					break;
				}
			}
			// get the property name
			for (; back > 0; back--)
			{
				char c = source.charAt(back);
				// can handle simple cases only
				if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))
				{
					key.insert(0, c);
				}
				else if (c == '(' || key.length() > 0)
				{
					break;
				}
				else
				{
					// ignore
				}
			}
		}

		public String getKey()
		{
			return key.toString();
		}

		public String getValue()
		{
			return value.toString();
		}

		public int getValueLength()
		{
			return valueLength;
		}
	}
}
