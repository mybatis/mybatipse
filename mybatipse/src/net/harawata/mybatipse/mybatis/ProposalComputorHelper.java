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

import javax.xml.xpath.XPathExpressionException;

import org.eclipse.core.resources.IFile;
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
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.bean.BeanPropertyCache;
import net.harawata.mybatipse.bean.JavaCompletionProposal;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.HasSelectAnnotation;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.MapperMethodInfo;
import net.harawata.mybatipse.util.NameUtil;
import net.harawata.mybatipse.util.XpathUtil;

/**
 * @author Iwao AVE!
 */
public class ProposalComputorHelper
{
	private static final char[] JAVA_LANG = "java.lang".toCharArray();

	public static String[] options = {
		"jdbcType", "javaType", "typeHandler", "mode", "resultMap", "numericScale"
	};

	public static String[] jdbcTypes = {
		"ARRAY", "BIGINT", "BINARY", "BIT", "BLOB", "BOOLEAN", "CHAR", "CLOB", "CURSOR", "DATE",
		"DECIMAL", "DOUBLE", "FLOAT", "INTEGER", "LONGVARBINARY", "LONGVARCHAR", "NUMERIC", "NCHAR",
		"NCLOB", "NULL", "NVARCHAR", "OTHER", "REAL", "SMALLINT", "STRUCT", "TIME", "TIMESTAMP",
		"TINYINT", "UNDEFINED", "VARBINARY", "VARCHAR"
	};

	public static String[] settingNames = {
		"autoMappingBehavior", "cacheEnabled", "proxyFactory", "lazyLoadingEnabled",
		"aggressiveLazyLoading", "multipleResultSetsEnabled", "useColumnLabel", "useGeneratedKeys",
		"defaultExecutorType", "defaultStatementTimeout", "mapUnderscoreToCamelCase",
		"safeRowBoundsEnabled", "localCacheScope", "jdbcTypeForNull", "lazyLoadTriggerMethods",
		"safeResultHandlerEnabled", "defaultScriptingLanguage", "callSettersOnNulls", "logPrefix",
		"logImpl", "configurationFactory", "vfsImpl"
	};

	public static List<ICompletionProposal> proposeReference(IJavaProject project,
		Document domDoc, String matchString, int start, int length, String targetElement,
		String idToExclude)
	{
		List<ICompletionProposal> results = new ArrayList<ICompletionProposal>();
		try
		{
			final int lastDot = matchString.lastIndexOf('.');
			final String namespacePart = lastDot == -1 ? "" : matchString.substring(0, lastDot);
			final String matchStr = matchString.substring(lastDot + 1);
			final char[] matchChrs = matchStr.toCharArray();
			int replacementStart = lastDot == -1 ? start : start + lastDot + 1;
			int replacementLength = lastDot == -1 ? length : length - lastDot - 1;

			final String currentNamespace = MybatipseXmlUtil.getNamespace(domDoc);
			final String exclude = idToExclude != null && idToExclude.length() > 0
				&& namespacePart.equals(currentNamespace) ? idToExclude : null;

			final Document xmlMapper;
			if (namespacePart.length() == 0)
			{
				xmlMapper = domDoc;
			}
			else
			{
				IFile mapperFile = MapperNamespaceCache.getInstance().get(project, namespacePart, null);
				xmlMapper = mapperFile == null ? null : MybatipseXmlUtil.getMapperDocument(mapperFile);
			}

			if (xmlMapper != null)
			{
				NodeList nodes = XpathUtil.xpathNodes(xmlMapper, "//" + targetElement + "/@id");
				proposeXmlElements(results, nodes, matchChrs, replacementStart, replacementLength,
					exclude);
			}

			proposeNamespaces(results, project, domDoc, namespacePart, currentNamespace, matchChrs,
				start, length);

			if ("select".equals(targetElement))
			{
				proposeJavaSelect(results, project,
					namespacePart.length() > 0 ? namespacePart : currentNamespace, matchStr,
					replacementStart, replacementLength);
			}
		}
		catch (XPathExpressionException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		return results;

	}

	private static void proposeXmlElements(List<ICompletionProposal> results, NodeList nodes,
		char[] matchChrs, int start, int length, String exclude)
	{
		for (int j = 0; j < nodes.getLength(); j++)
		{
			String id = nodes.item(j).getNodeValue();
			if ((matchChrs.length == 0 || CharOperation.camelCaseMatch(matchChrs, id.toCharArray()))
				&& (exclude == null || !exclude.equals(id)))
			{
				int cursorPos = id.length();
				ICompletionProposal proposal = new JavaCompletionProposal(id.toString(), start, length,
					cursorPos, Activator.getIcon(), id, null, null, 200);
				results.add(proposal);
			}
		}
	}

	private static void proposeNamespaces(List<ICompletionProposal> results, IJavaProject project,
		Document domDoc, String partialNamespace, String currentNamespace, char[] matchChrs,
		int start, int length)
	{
		for (String namespace : MapperNamespaceCache.getInstance()
			.getCacheMap(project, null)
			.keySet())
		{
			if (!namespace.equals(currentNamespace) && namespace.startsWith(partialNamespace)
				&& !namespace.equals(partialNamespace))
			{
				char[] simpleName = CharOperation.lastSegment(namespace.toCharArray(), '.');
				if (matchChrs.length == 0 || CharOperation.camelCaseMatch(matchChrs, simpleName))
				{
					StringBuilder replacementStr = new StringBuilder().append(namespace).append('.');
					int cursorPos = replacementStr.length();
					String displayString = new StringBuilder().append(simpleName)
						.append(" - ")
						.append(namespace)
						.toString();
					results
						.add(new JavaCompletionProposal(replacementStr.toString(), start, length, cursorPos,
							Activator.getIcon("/icons/mybatis-ns.png"), displayString, null, null, 100));
				}
			}
		}
	}

	private static void proposeJavaSelect(List<ICompletionProposal> results, IJavaProject project,
		String namespace, String matchString, int start, int length)
	{
		List<MapperMethodInfo> methodInfos = new ArrayList<MapperMethodInfo>();
		JavaMapperUtil.findMapperMethod(methodInfos, project, namespace, matchString, false,
			new HasSelectAnnotation());
		for (MapperMethodInfo methodInfo : methodInfos)
		{
			String methodName = methodInfo.getMethodName();
			results.add(new JavaCompletionProposal(methodName, start, length, methodName.length(),
				Activator.getIcon(), methodName, null, null, 200));
		}
	}

	public static List<ICompletionProposal> proposeOptionName(int offset, int length,
		String matchString)
	{
		List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		for (String option : options)
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

	public static List<ICompletionProposal> proposeSettingName(int offset, int length,
		String matchString)
	{
		List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		for (String settingName : settingNames)
		{
			if (matchString.length() == 0
				|| CharOperation.camelCaseMatch(matchString.toCharArray(), settingName.toCharArray()))
			{
				proposals.add(new CompletionProposal(settingName, offset, length, settingName.length(),
					Activator.getIcon(), null, null, null));
			}
		}
		return proposals;
	}

	public static List<ICompletionProposal> proposeJdbcType(int offset, int length,
		String matchString)
	{
		List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		for (String jdbcType : jdbcTypes)
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

	public static List<ICompletionProposal> proposeJavaType(IJavaProject project, final int start,
		final int length, boolean includeAlias, String matchString)
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
		final boolean searchReadable, final String matchString)
	{
		List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		if (paramMap.size() == 1)
		{
			// If there is only one parameter with no @Param,
			// properties should be directly referenced.
			String paramType = paramMap.values().iterator().next();
			proposals = proposePropertyFor(project, offset, length, paramType, searchReadable, -1,
				matchString);
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
					proposals = proposePropertyFor(project, offset, length, qualifiedName, searchReadable,
						dotPos, matchString);
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
		String interfaceFqn = "org.apache.ibatis.type.TypeHandler";
		return proposeImplementation(project, start, length, matchString, interfaceFqn);
	}

	public static List<ICompletionProposal> proposeCacheType(IJavaProject project,
		final int start, final int length, String matchString)
	{
		String interfaceFqn = "org.apache.ibatis.cache.Cache";
		return proposeImplementation(project, start, length, matchString, interfaceFqn);
	}

	public static List<ICompletionProposal> proposeObjectFactory(IJavaProject project,
		final int start, final int length, String matchString)
	{
		String interfaceFqn = "org.apache.ibatis.reflection.factory.ObjectFactory";
		return proposeImplementation(project, start, length, matchString, interfaceFqn);
	}

	public static List<ICompletionProposal> proposeObjectWrapperFactory(IJavaProject project,
		final int start, final int length, String matchString)
	{
		String interfaceFqn = "org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory";
		return proposeImplementation(project, start, length, matchString, interfaceFqn);
	}

	private static List<ICompletionProposal> proposeImplementation(IJavaProject project,
		final int start, final int length, String matchString, String interfaceFqn)
	{
		final List<ICompletionProposal> results = new ArrayList<ICompletionProposal>();
		IType interfaceType;
		IJavaSearchScope scope;
		try
		{
			interfaceType = project.findType(interfaceFqn);
			if (interfaceType == null)
				return results;
			scope = SearchEngine.createHierarchyScope(interfaceType);
			final Map<String, String> aliasMap = TypeAliasCache.getInstance()
				.searchTypeAliases(project, matchString);
			TypeNameRequestor requestor = new JavaTypeNameRequestor()
			{
				@Override
				public void acceptType(int modifiers, char[] packageName, char[] simpleTypeName,
					char[][] enclosingTypeNames, String path)
				{
					// Ignore abstract classes.
					if (Flags.isAbstract(modifiers) || Arrays.equals(JAVA_LANG, packageName))
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
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		return results;
	}

	public static List<ICompletionProposal> proposePropertyFor(IJavaProject project, int offset,
		int length, String qualifiedName, boolean searchReadable, int currentIdx,
		String matchString)
	{
		if (MybatipseXmlUtil.isDefaultTypeAlias(qualifiedName))
			return Collections.emptyList();
		Map<String, String> fields = BeanPropertyCache.searchFields(project, qualifiedName,
			matchString, searchReadable, currentIdx, false);
		return BeanPropertyCache.buildFieldNameProposal(fields, matchString, offset, length);
	}

	abstract static class JavaTypeNameRequestor extends TypeNameRequestor
	{
		private int relevance = 100;

		protected void addJavaTypeProposal(final List<ICompletionProposal> results, final int start,
			final int length, char[] packageName, char[] simpleTypeName, char[][] enclosingTypeNames)
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
