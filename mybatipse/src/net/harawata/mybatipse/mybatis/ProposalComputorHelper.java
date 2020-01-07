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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.xpath.XPathExpressionException;

import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
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
import net.harawata.mybatipse.MybatipseConstants;
import net.harawata.mybatipse.bean.BeanPropertyCache;
import net.harawata.mybatipse.bean.JavaCompletionProposal;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.HasSelectAnnotation;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.MapperMethodStore;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.MethodNameStore;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.ResultsAnnotationWithId;
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

	public static Map<String, List<String>> settings = new HashMap<String, List<String>>()
	{
		{
			List<String> empty = Collections.emptyList();
			put("autoMappingBehavior", Arrays.asList("NONE", "FULL", "PARTIAL"));
			put("autoMappingUnknownColumnBehavior", Arrays.asList("WARNING", "FAILING", "NONE"));
			put("cacheEnabled", Arrays.asList("false", "true"));
			put("proxyFactory", Arrays.asList("CGLIB", "JAVASSIST"));
			put("lazyLoadingEnabled", Arrays.asList("true", "false"));
			put("aggressiveLazyLoading", Arrays.asList("false", "true"));
			put("multipleResultSetsEnabled", Arrays.asList("false", "true"));
			put("useColumnLabel", Arrays.asList("false", "true"));
			put("useGeneratedKeys", Arrays.asList("true", "false"));
			put("defaultExecutorType", Arrays.asList("REUSE", "BATCH", "SIMPLE"));
			put("defaultStatementTimeout", empty);
			put("defaultFetchSize", empty);
			put("mapUnderscoreToCamelCase", Arrays.asList("true", "false"));
			put("localCacheScope", Arrays.asList("STATEMENT", "SESSION"));
			put("jdbcTypeForNull", Arrays.asList("NULL", "VARCHAR", "OTHER"));
			put("lazyLoadTriggerMethods", Arrays.asList("equals,clone,hashCode,toString"));
			put("safeRowBoundsEnabled", Arrays.asList("false", "true"));
			put("safeResultHandlerEnabled", Arrays.asList("false", "true"));
			put("defaultScriptingLanguage", empty);
			put("callSettersOnNulls", Arrays.asList("true", "false"));
			put("useActualParamName", Arrays.asList("false", "true"));
			put("logPrefix", empty);
			put("logImpl", Arrays.asList("SLF4J", "LOG4J", "LOG4J2", "JDK_LOGGING", "COMMONS_LOGGING",
				"STDOUT_LOGGING", "NO_LOGGING"));
			put("configurationFactory", empty);
			put("vfsImpl", empty);
			put("defaultEnumTypeHandler", empty);
			put("returnInstanceForEmptyRow", Arrays.asList("true", "false"));
			put("defaultResultSetType",
				Arrays.asList("FORWARD_ONLY", "SCROLL_INSENSITIVE", "SCROLL_SENSITIVE"));
		}

		private static final long serialVersionUID = 1L;
	};

	public static List<ICompletionProposal> proposeReference(IJavaProject project,
		String currentNamespace, String matchString, int start, int length, String targetElement,
		String idToExclude)
	{
		List<ICompletionProposal> results = new ArrayList<ICompletionProposal>();
		try
		{
			final int lastDot = matchString.lastIndexOf('.');
			final String namespace = lastDot == -1 ? currentNamespace
				: matchString.substring(0, lastDot);
			final String matchStr = matchString.substring(lastDot + 1);
			final char[] matchChrs = matchStr.toCharArray();
			int replacementStart = lastDot == -1 ? start : start + lastDot + 1;
			int replacementLength = lastDot == -1 ? length : length - lastDot - 1;

			final String exclude = idToExclude != null && idToExclude.length() > 0
				&& namespace.equals(currentNamespace) ? idToExclude : null;

			for (Document mapper : MybatipseXmlUtil.getMapperDocument(project, namespace))
			{
				proposeXmlElements(results, mapper, targetElement, matchChrs, replacementStart,
					replacementLength, exclude);
			}

			proposeNamespaces(results, project, matchString, namespace, currentNamespace, matchChrs,
				start, length);

			if ("select".equals(targetElement))
			{
				proposeJavaSelect(results, project,
					namespace.length() > 0 ? namespace : currentNamespace, matchStr, replacementStart,
					replacementLength, exclude);
			}
			else if ("resultMap".equals(targetElement))
			{
				proposeJavaResultMap(results, project,
					namespace.length() > 0 ? namespace : currentNamespace, matchStr, replacementStart,
					replacementLength);
			}
		}
		catch (XPathExpressionException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		return results;
	}

	private static void proposeXmlElements(List<ICompletionProposal> results, Document xmlMapper,
		String targetElement, char[] matchChrs, int start, int length, String exclude)
		throws XPathExpressionException
	{
		NodeList nodes = XpathUtil.xpathNodes(xmlMapper, "//" + targetElement + "/@id");
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
		String matchString, String partialNamespace, String currentNamespace, char[] matchChrs,
		int start, int length)
	{
		for (String namespace : MapperNamespaceCache.getInstance()
			.getCacheMap(project, null)
			.keySet())
		{
			if (namespace.equals(currentNamespace) || namespace.equals(partialNamespace))
				continue;

			final char[] simpleName = CharOperation.lastSegment(namespace.toCharArray(), '.');
			if (namespace.startsWith(matchString)
				|| (namespace.startsWith(partialNamespace)
					&& CharOperation.camelCaseMatch(matchChrs, simpleName))
				|| CharOperation.camelCaseMatch(matchString.toCharArray(), simpleName))
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

	private static void proposeJavaSelect(List<ICompletionProposal> results, IJavaProject project,
		String namespace, String matchString, int start, int length, String idToExclude)
	{
		MethodNameStore methodStore = new MethodNameStore();
		JavaMapperUtil.findMapperMethod(methodStore, project, namespace,
			new HasSelectAnnotation(matchString, false));
		for (String methodName : methodStore.getMethodNames())
		{
			if (idToExclude == null || idToExclude.length() == 0 || !idToExclude.equals(methodName))
			{
				results.add(new JavaCompletionProposal(methodName, start, length, methodName.length(),
					Activator.getIcon(), methodName, null, null, 200));
			}
		}
	}

	private static void proposeJavaResultMap(List<ICompletionProposal> results,
		IJavaProject project, String namespace, String matchString, int start, int length)
	{
		class ResultsIdStore implements MapperMethodStore
		{
			private List<String> resultMapIds = new ArrayList<String>();

			public List<String> getResultMapIds()
			{
				return resultMapIds;
			}

			@Override
			public void add(IMethod method)
			{
				try
				{
					for (IAnnotation annotation : method.getAnnotations())
					{
						String annotationName = annotation.getElementName();
						if (MybatipseConstants.ANNOTATION_RESULTS.equals(annotationName))
						{
							IMemberValuePair[] valuePairs = annotation.getMemberValuePairs();
							for (IMemberValuePair valuePair : valuePairs)
							{
								if ("id".equals(valuePair.getMemberName()))
								{
									resultMapIds.add((String)valuePair.getValue());
								}
							}
						}
					}
				}
				catch (JavaModelException e)
				{
					Activator.log(Status.ERROR, "Failed parse annotation of " + method.getElementName()
						+ " in " + method.getDeclaringType().getFullyQualifiedName(), e);
				}
			}

			@Override
			public void add(IMethodBinding method, List<SingleVariableDeclaration> params)
			{
				for (IAnnotationBinding annotation : method.getAnnotations())
				{
					String annotationName = annotation.getAnnotationType().getQualifiedName();
					if (MybatipseConstants.ANNOTATION_RESULTS.equals(annotationName))
					{
						IMemberValuePairBinding[] valuePairs = annotation.getAllMemberValuePairs();
						for (IMemberValuePairBinding valuePair : valuePairs)
						{
							if ("id".equals(valuePair.getName()))
							{
								resultMapIds.add((String)valuePair.getValue());
							}
						}
					}
				}
			}

			@Override
			public boolean isEmpty()
			{
				return resultMapIds.isEmpty();
			}
		}
		final ResultsIdStore methodStore = new ResultsIdStore();
		JavaMapperUtil.findMapperMethod(methodStore, project, namespace,
			new ResultsAnnotationWithId(matchString, false));
		for (String resultsId : methodStore.getResultMapIds())
		{
			results.add(new JavaCompletionProposal(resultsId, start, length, resultsId.length(),
				Activator.getIcon(), resultsId, null, null, 200));
		}
	}

	public static List<ICompletionProposal> proposeOptionName(int offset, int length,
		String matchString)
	{
		List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		for (String option : options)
		{
			addProposalIfMatch(proposals, matchString, option, option + "=", offset, length, option);
		}
		return proposals;
	}

	public static List<ICompletionProposal> proposeSettingName(int offset, int length,
		String matchString)
	{
		List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		for (String settingName : settings.keySet())
		{
			addProposalIfMatch(proposals, matchString, settingName, settingName, offset, length,
				settingName);
		}
		return proposals;
	}

	public static List<ICompletionProposal> proposeSettingValue(IJavaProject project,
		String settingName, int offset, int length, String matchString)
	{
		List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		List<String> values = settings.get(settingName);
		for (String value : values)
		{
			addProposalIfMatch(proposals, matchString, value, value, offset, length, value);
		}
		if ("vfsImpl".equals(settingName))
		{
			proposals.addAll(proposeImplementation(project, offset, length, matchString,
				MybatipseConstants.TYPE_VFS));
		}
		else if ("defaultScriptingLanguage".equals(settingName))
		{
			proposals.addAll(proposeImplementation(project, offset, length, matchString,
				MybatipseConstants.TYPE_LANGUAGE_DRIVER));
		}
		else if ("defaultEnumTypeHandler".equals(settingName))
		{
			proposals.addAll(proposeImplementation(project, offset, length, matchString,
				MybatipseConstants.TYPE_TYPE_HANDLER));
		}
		return proposals;
	}

	public static List<ICompletionProposal> proposeJdbcType(int offset, int length,
		String matchString)
	{
		List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		for (String jdbcType : jdbcTypes)
		{
			addProposalIfMatch(proposals, matchString, jdbcType, jdbcType, offset, length, jdbcType);
		}
		return proposals;
	}

	public static List<ICompletionProposal> proposeJavaType(IJavaProject project, final int start,
		final int length, boolean includeAlias, String matchString)
	{
		final List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		if (includeAlias)
		{
			Map<String, String> aliasMap = TypeAliasCache.getInstance()
				.searchTypeAliases(project, matchString);
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
		final Map<String, String> additionalParams, final boolean searchReadable,
		final String matchString)
	{
		final List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		if (paramMap == null || paramMap.size() == 0)
		{
			return proposals;
		}

		final int dotPos = matchString.indexOf('.');
		// Proposals for additional params.
		if (additionalParams != null && !additionalParams.isEmpty())
		{
			if (dotPos == -1)
			{
				proposeParamName(proposals, offset, length, matchString, additionalParams);
			}
			else
			{
				proposeParamProperty(proposals, project, offset, length, searchReadable, matchString,
					additionalParams, dotPos);
			}
		}

		// Proposals for statement parameters.
		String paramName = paramMap.keySet().iterator().next();
		String paramType = paramMap.values().iterator().next();
		if (paramMap.size() == 1 && "_parameter".equals(paramName))
		{
			// Sole parameter without @Param.
			proposals.addAll(proposePropertyFor(project, offset, length, paramType, searchReadable,
				-1, matchString));
		}
		else if (dotPos == -1)
		{
			proposeParamName(proposals, offset, length, matchString, paramMap);
		}
		else
		{
			proposeParamProperty(proposals, project, offset, length, searchReadable, matchString,
				paramMap, dotPos);
		}
		return proposals;
	}

	private static void proposeParamProperty(final List<ICompletionProposal> proposals,
		IJavaProject project, final int offset, final int length, final boolean searchReadable,
		final String matchString, final Map<String, String> additionalParams, final int dotPos)
	{
		for (Entry<String, String> paramEntry : additionalParams.entrySet())
		{
			String paramName = paramEntry.getKey();
			if (paramName.length() == dotPos && matchString.startsWith(paramName))
			{
				String paramType = paramEntry.getValue();
				proposals.addAll(proposePropertyFor(project, offset, length, paramType, searchReadable,
					paramName.length(), matchString));
				break;
			}
		}
	}

	private static void proposeParamName(final List<ICompletionProposal> proposals,
		final int offset, final int length, final String matchString,
		final Map<String, String> additionalParams)
	{
		for (Entry<String, String> paramEntry : additionalParams.entrySet())
		{
			String paramName = paramEntry.getKey();
			addProposalIfMatch(proposals, matchString, paramName, paramName, offset, length,
				paramName + " - " + paramEntry.getValue());
		}
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

	public static List<ICompletionProposal> proposeAssignable(IJavaProject project,
		final int start, final int length, String matchString, String supertypeFqn)
	{
		return proposeImplementation(project, start, length, matchString, supertypeFqn);
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

	private static void addProposalIfMatch(final List<ICompletionProposal> proposals,
		final String matchString, String targetStr, String replacementStr, final int offset,
		final int length, String displayStr)
	{
		if (targetStr == null || targetStr.length() == 0)
			return;
		if (matchString.length() == 0
			|| CharOperation.camelCaseMatch(matchString.toCharArray(), targetStr.toCharArray()))
		{
			proposals.add(new CompletionProposal(replacementStr, offset, length,
				replacementStr.length(), Activator.getIcon(), displayStr, null, null));
		}
	}

	private ProposalComputorHelper()
	{
		super();
	}
}
