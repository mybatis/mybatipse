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

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.bean.BeanPropertyCache;
import net.harawata.mybatipse.bean.JavaCompletionProposal;
import net.harawata.mybatipse.util.NameUtil;

import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameRequestor;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

/**
 * @author Iwao AVE!
 */
public class ProposalComputorHelper
{
	public static List<ICompletionProposal> proposeOptionName(int offset, int length,
		String matchString)
	{
		List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		for (String option : ExpressionProposalParser.options)
		{
			if (matchString.length() == 0
				|| CharOperation.camelCaseMatch(matchString.toCharArray(), option.toCharArray()))
			{
				String replacementString = option + "=";
				proposals.add(new CompletionProposal(replacementString, offset, length,
					replacementString.length(), Activator.getIcon(), option, null, null));
			}
		}
		return proposals;
	}

	public static List<ICompletionProposal> proposeJdbcType(int offset, int length,
		String matchString)
	{
		List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		for (String jdbcType : ExpressionProposalParser.jdbcTypes)
		{
			if (matchString.length() == 0
				|| CharOperation.prefixEquals(matchString.toCharArray(), jdbcType.toCharArray(), false))
			{
				proposals.add(new CompletionProposal(jdbcType, offset, length, jdbcType.length(),
					Activator.getIcon(), null, null, null));
			}
		}
		return proposals;
	}

	public static List<ICompletionProposal> proposeJavaType(IJavaProject project,
		final int start, final int length, boolean includeAlias, String matchString)
	{
		final List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		if (includeAlias)
		{
			Map<String, String> aliasMap = TypeAliasCache.getInstance().searchTypeAliases(project,
				matchString);
			for (Entry<String, String> entry : aliasMap.entrySet())
			{
				String qualifiedName = entry.getKey();
				String alias = entry.getValue();
				proposals.add(new JavaCompletionProposal(alias, start, length, alias.length(),
					Activator.getIcon("/icons/mybatis-alias.png"), alias + " - " + qualifiedName, null,
					null, 200));
			}
		}

		int includeMask = IJavaSearchScope.SOURCES | IJavaSearchScope.REFERENCED_PROJECTS;
		// Include application libraries only when package is specified (for better performance).
		boolean pkgSpecified = matchString != null && matchString.indexOf('.') > 0;
		if (pkgSpecified)
			includeMask |= IJavaSearchScope.APPLICATION_LIBRARIES;
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaProject[]{
			project
		}, includeMask);
		TypeNameRequestor requestor = new JavaTypeNameRequestor()
		{
			@Override
			public void acceptType(int modifiers, char[] packageName, char[] simpleTypeName,
				char[][] enclosingTypeNames, String path)
			{
				if (Flags.isAbstract(modifiers) || Flags.isInterface(modifiers))
					return;

				addJavaTypeProposal(proposals, start, length, packageName, simpleTypeName,
					enclosingTypeNames);
			}
		};
		try
		{
			searchJavaType(matchString, scope, requestor);
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		return proposals;
	}

	public static List<ICompletionProposal> proposeParameters(IJavaProject project,
		final int offset, final int length, final Map<String, String> paramMap,
		final String matchString)
	{
		List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		if (paramMap.size() == 1)
		{
			// If there is only one parameter with no @Param,
			// properties should be directly referenced.
			String paramType = paramMap.values().iterator().next();
			proposals = proposePropertyFor(project, offset, length, paramType, true, -1, matchString);
		}
		else if (paramMap.size() > 1)
		{
			int dotPos = matchString.indexOf('.');
			if (dotPos == -1)
			{
				for (Entry<String, String> paramEntry : paramMap.entrySet())
				{
					String paramName = paramEntry.getKey();
					if (matchString.length() == 0
						|| CharOperation.camelCaseMatch(matchString.toCharArray(), paramName.toCharArray()))
					{
						String displayStr = paramName + " - " + paramEntry.getValue();
						proposals.add(new CompletionProposal(paramName, offset, length, paramName.length(),
							Activator.getIcon(), displayStr, null, null));
					}
				}
			}
			else
			{
				String paramName = matchString.substring(0, dotPos);
				String qualifiedName = paramMap.get(paramName);
				if (qualifiedName != null)
				{
					proposals = proposePropertyFor(project, offset, length, qualifiedName, true, dotPos,
						matchString);
				}
			}
		}
		return proposals;
	}

	public static void searchJavaType(String matchString, IJavaSearchScope scope,
		TypeNameRequestor requestor) throws JavaModelException
	{
		char[] searchPkg = null;
		char[] searchType = null;
		if (matchString != null && matchString.length() > 0)
		{
			char[] match = matchString.toCharArray();
			int lastDotPos = matchString.lastIndexOf('.');
			if (lastDotPos == -1)
			{
				searchType = match;
			}
			else
			{
				if (lastDotPos + 1 < match.length)
				{
					searchType = CharOperation.lastSegment(match, '.');
				}
				searchPkg = Arrays.copyOfRange(match, 0, lastDotPos);
			}
		}
		SearchEngine searchEngine = new SearchEngine();
		searchEngine.searchAllTypeNames(searchPkg, SearchPattern.R_PREFIX_MATCH, searchType,
			SearchPattern.R_CAMELCASE_MATCH, IJavaSearchConstants.CLASS, scope, requestor,
			IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, null);
	}

	public static List<ICompletionProposal> proposeTypeHandler(IJavaProject project,
		final int start, final int length, String matchString)
	{
		try
		{
			final List<ICompletionProposal> results = new ArrayList<ICompletionProposal>();
			IType typeHandler;
			IJavaSearchScope scope;
			typeHandler = project.findType("org.apache.ibatis.type.TypeHandler");
			if (typeHandler == null)
				return Collections.emptyList();
			final Map<String, String> aliasMap = TypeAliasCache.getInstance().searchTypeAliases(
				project, matchString);
			scope = SearchEngine.createStrictHierarchyScope(project, typeHandler, true, false, null);
			TypeNameRequestor requestor = new JavaTypeNameRequestor()
			{
				@Override
				public void acceptType(int modifiers, char[] packageName, char[] simpleTypeName,
					char[][] enclosingTypeNames, String path)
				{
					// Ignore abstract classes.
					if (Flags.isAbstract(modifiers))
						return;

					addJavaTypeProposal(results, start, length, packageName, simpleTypeName,
						enclosingTypeNames);

					String qualifiedName = NameUtil.buildQualifiedName(packageName, simpleTypeName,
						enclosingTypeNames, true);
					String alias = aliasMap.get(qualifiedName);
					if (alias != null)
					{
						results.add(new JavaCompletionProposal(alias, start, length, alias.length(),
							Activator.getIcon("/icons/mybatis-alias.png"), alias + " - " + qualifiedName,
							null, null, 200));
					}
				}
			};
			searchJavaType(matchString, scope, requestor);
			return results;
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		return Collections.emptyList();
	}

	public static List<ICompletionProposal> proposeCacheType(IJavaProject project,
		final int start, final int length, String matchString)
	{
		final List<ICompletionProposal> results = new ArrayList<ICompletionProposal>();
		IType cache;
		IJavaSearchScope scope;
		try
		{
			cache = project.findType("org.apache.ibatis.cache.Cache");
			if (cache == null)
				return results;
			scope = SearchEngine.createHierarchyScope(cache);
			TypeNameRequestor requestor = new JavaTypeNameRequestor()
			{
				@Override
				public void acceptType(int modifiers, char[] packageName, char[] simpleTypeName,
					char[][] enclosingTypeNames, String path)
				{
					// Ignore abstract classes.
					if (Flags.isAbstract(modifiers))
						return;

					addJavaTypeProposal(results, start, length, packageName, simpleTypeName,
						enclosingTypeNames);
				}
			};
			searchJavaType(matchString, scope, requestor);
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		return results;
	}

	public static List<ICompletionProposal> proposePropertyFor(IJavaProject project, int offset,
		int length, String qualifiedName, boolean includeReadonly, int currentIdx,
		String matchString)
	{
		if (MybatipseXmlUtil.isDefaultTypeAlias(qualifiedName))
			return Collections.emptyList();
		Map<String, String> fields = BeanPropertyCache.searchFields(project, qualifiedName,
			matchString, includeReadonly, currentIdx, false);
		return BeanPropertyCache.buildFieldNameProposal(fields, matchString, offset, length);
	}

	abstract static class JavaTypeNameRequestor extends TypeNameRequestor
	{
		private int relevance = 100;

		protected void addJavaTypeProposal(final List<ICompletionProposal> results,
			final int start, final int length, char[] packageName, char[] simpleTypeName,
			char[][] enclosingTypeNames)
		{
			String typeFqn = NameUtil.buildQualifiedName(packageName, simpleTypeName,
				enclosingTypeNames, true);
			String displayStr = new StringBuilder().append(simpleTypeName)
				.append(" - ")
				.append(packageName)
				.toString();
			results.add(new JavaCompletionProposal(typeFqn, start, length, typeFqn.length(),
				Activator.getIcon(), displayStr, null, null, relevance));
		}

		protected JavaTypeNameRequestor init(int relevance)
		{
			this.relevance = relevance;
			return this;
		}
	}

	private ProposalComputorHelper()
	{
		super();
	}
}
