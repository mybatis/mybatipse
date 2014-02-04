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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.xpath.XPathExpressionException;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.bean.BeanPropertyCache;
import net.harawata.mybatipse.bean.BeanPropertyInfo;
import net.harawata.mybatipse.mybatis.TypeAliasMap.TypeAliasInfo;
import net.harawata.mybatipse.util.NameUtil;
import net.harawata.mybatipse.util.XpathUtil;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.core.search.TypeNameRequestor;
import org.eclipse.jdt.internal.core.PackageFragment;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ContextInformation;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegionList;
import org.eclipse.wst.sse.core.utils.StringUtils;
import org.eclipse.wst.sse.ui.contentassist.CompletionProposalInvocationContext;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMNode;
import org.eclipse.wst.xml.core.internal.regions.DOMRegionContext;
import org.eclipse.wst.xml.ui.internal.contentassist.ContentAssistRequest;
import org.eclipse.wst.xml.ui.internal.contentassist.DefaultXMLCompletionProposalComputer;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Iwao AVE!
 */
@SuppressWarnings("restriction")
public class XmlCompletionProposalComputer extends DefaultXMLCompletionProposalComputer
{
	enum ProposalType
	{
		None,
		MapperNamespace,
		ResultType,
		ResultProperty,
		StatementId,
		TypeHandlerType,
		CacheType,
		ResultMap,
		Include,
		Package,
		TypeAlias,
		SelectId
	}

	@Override
	protected void addTagInsertionProposals(ContentAssistRequest contentAssistRequest,
		int childPosition, CompletionProposalInvocationContext context)
	{
		int offset = contentAssistRequest.getReplacementBeginPosition();
		int length = contentAssistRequest.getReplacementLength();
		Node node = contentAssistRequest.getNode();
		// Node is 'parent' when the cursor is just before the end tag of the parent.
		Node parentNode = node.getNodeType() == Node.ELEMENT_NODE ? node : node.getParentNode();

		if (parentNode.getNodeType() != Node.ELEMENT_NODE)
			return;

		Node typeAttr = null;
		String tagName = parentNode.getNodeName();
		NamedNodeMap tagAttrs = parentNode.getAttributes();
		if ("resultMap".equals(tagName))
			typeAttr = tagAttrs.getNamedItem("type");
		else if ("collection".equals(tagName))
			typeAttr = tagAttrs.getNamedItem("ofType");
		else if ("association".equals(tagName))
			typeAttr = tagAttrs.getNamedItem("javaType");
		if (typeAttr == null)
			return;

		String typeValue = typeAttr.getNodeValue();
		if (typeValue == null || typeValue.length() == 0)
			return;

		IJavaProject project = getJavaProject(contentAssistRequest);
		// Try resolving the alias.
		String qualifiedName = TypeAliasCache.getInstance().resolveAlias(project, typeValue, null);
		if (qualifiedName == null)
		{
			// Assumed to be FQN.
			qualifiedName = typeValue;
		}
		BeanPropertyInfo beanProps = BeanPropertyCache.getBeanPropertyInfo(project, qualifiedName);
		try
		{
			Set<String> existingProps = new HashSet<String>();
			NodeList existingPropNodes = XpathUtil.xpathNodes(parentNode, "*[@property]/@property");
			for (int i = 0; i < existingPropNodes.getLength(); i++)
			{
				existingProps.add(existingPropNodes.item(i).getNodeValue());
			}
			StringBuilder resultTags = new StringBuilder();
			for (Entry<String, String> prop : beanProps.getWritableFields().entrySet())
			{
				String propName = prop.getKey();
				if (!existingProps.contains(propName))
				{
					resultTags.append("<result property=\"")
						.append(propName)
						.append("\" column=\"")
						.append(propName)
						.append("\" />\n");
				}
			}
			contentAssistRequest.addProposal(new CompletionProposal(resultTags.toString(), offset,
				length, resultTags.length(), Activator.getIcon(), "<result /> for properties",
				new ContextInformation("column", "COLUMN"), null));
		}
		catch (XPathExpressionException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
	}

	protected void addAttributeValueProposals(ContentAssistRequest contentAssistRequest,
		CompletionProposalInvocationContext context)
	{
		IDOMNode node = (IDOMNode)contentAssistRequest.getNode();
		String tagName = node.getNodeName();
		IStructuredDocumentRegion open = node.getFirstStructuredDocumentRegion();
		ITextRegionList openRegions = open.getRegions();
		int i = openRegions.indexOf(contentAssistRequest.getRegion());
		if (i < 0)
			return;
		ITextRegion nameRegion = null;
		while (i >= 0)
		{
			nameRegion = openRegions.get(i--);
			if (nameRegion.getType() == DOMRegionContext.XML_TAG_ATTRIBUTE_NAME)
				break;
		}

		// get the attribute in question (first attr name to the left of the cursor)
		String attributeName = null;
		if (nameRegion != null)
			attributeName = open.getText(nameRegion);

		ProposalType proposalType = resolveProposalType(tagName, attributeName);
		if (ProposalType.None.equals(proposalType))
		{
			return;
		}

		IJavaProject project = getJavaProject(contentAssistRequest);
		String currentValue = null;
		if (contentAssistRequest.getRegion().getType() == DOMRegionContext.XML_TAG_ATTRIBUTE_VALUE)
			currentValue = contentAssistRequest.getText();
		else
			currentValue = "";

		String matchString = null;
		int matchStrLen = contentAssistRequest.getMatchString().length();
		int start = contentAssistRequest.getReplacementBeginPosition();
		int length = contentAssistRequest.getReplacementLength();
		if (currentValue.length() > StringUtils.strip(currentValue).length()
			&& (currentValue.startsWith("\"") || currentValue.startsWith("'")) && matchStrLen > 0)
		{
			// Value is surrounded by (double) quotes.
			matchString = currentValue.substring(1, matchStrLen);
			start++;
			length = currentValue.length() - 2;
		}
		else
		{
			matchString = currentValue.substring(0, matchStrLen);
		}

		try
		{
			switch (proposalType)
			{
				case Package:
					proposePackage(contentAssistRequest, project, matchString, start, length);
					break;
				case TypeAlias:
					proposeJavaType(contentAssistRequest, project, matchString, start, length, false);
					break;
				case ResultType:
					proposeJavaType(contentAssistRequest, project, matchString, start, length, true);
					break;
				case ResultProperty:
					proposeProperty(contentAssistRequest, matchString, start, length, node);
					break;
				case TypeHandlerType:
					proposeTypeHandler(contentAssistRequest, project, matchString, start, length);
					break;
				case CacheType:
					proposeCacheType(contentAssistRequest, project, matchString, start, length);
					break;
				case StatementId:
					proposeStatementId(contentAssistRequest, project, matchString, start, length, node);
					break;
				case MapperNamespace:
					proposeMapperNamespace(contentAssistRequest, project, start, length);
					break;
				case ResultMap:
					// TODO: Exclude the current resultMap id when proposing 'extends'
					proposeResultMapReference(contentAssistRequest, project, node, currentValue,
						matchString, matchStrLen, start, length);
					break;
				case Include:
					proposeReference(contentAssistRequest, project, node, matchString, start, length,
						"sql", null);
					break;
				case SelectId:
					proposeReference(contentAssistRequest, project, node, matchString, start, length,
						"select", null);
					break;
				default:
					break;
			}
		}
		catch (Exception e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
	}

	private void proposeResultMapReference(ContentAssistRequest contentAssistRequest,
		IJavaProject project, IDOMNode node, String currentValue, String matchString,
		int matchStrLen, int start, int length) throws XPathExpressionException, IOException,
		CoreException
	{
		String latterMaps = null;
		if (currentValue.indexOf(',') > -1)
		{
			int leftComma = matchString.lastIndexOf(',');
			int rightComma = currentValue.indexOf(',', matchStrLen);
			if (rightComma > -1)
			{
				latterMaps = currentValue.substring(rightComma,
					currentValue.length()
						- (currentValue.endsWith("\"") || currentValue.endsWith("'") ? 1 : 0));
			}
			if (leftComma > -1)
			{
				start += leftComma + 1;
				matchString = matchString.substring(leftComma + 1).trim();
				length -= leftComma + 1;
			}
		}
		proposeReference(contentAssistRequest, project, node, matchString, start, length,
			"resultMap", latterMaps);
	}

	private void proposeReference(ContentAssistRequest contentAssistRequest,
		IJavaProject project, IDOMNode node, String matchString, int start, int length,
		String targetElement, String replacementSuffix) throws XPathExpressionException,
		IOException, CoreException
	{
		int lastDot = matchString.lastIndexOf('.');
		if (lastDot == -1)
		{
			char[] matchChrs = matchString.toCharArray();
			NodeList nodes = XpathUtil.xpathNodes(node.getOwnerDocument(), "//" + targetElement
				+ "/@id");
			proposalFromNodes(contentAssistRequest, nodes, null, matchChrs, start, length,
				replacementSuffix);
			proposeNamespace(contentAssistRequest, project, node, "", matchChrs, start, length,
				replacementSuffix);
		}
		else
		{
			String namespace = matchString.substring(0, lastDot);
			char[] matchChrs = matchString.substring(lastDot + 1).toCharArray();
			IFile mapperFile = MapperNamespaceCache.getInstance().get(project, namespace, null);
			IStructuredModel model = null;
			try
			{
				if (mapperFile != null)
				{
					model = StructuredModelManager.getModelManager().getModelForRead(mapperFile);
					IDOMModel domModel = (IDOMModel)model;
					IDOMDocument mapperDoc = domModel.getDocument();
					NodeList nodes = XpathUtil.xpathNodes(mapperDoc, "//" + targetElement + "/@id");
					proposalFromNodes(contentAssistRequest, nodes, namespace, matchChrs, start, length,
						replacementSuffix);
				}
				proposeNamespace(contentAssistRequest, project, node, namespace, matchChrs, start,
					length, replacementSuffix);
			}
			finally
			{
				if (model != null)
				{
					model.releaseFromRead();
				}
			}
		}
	}

	private void proposalFromNodes(ContentAssistRequest contentAssistRequest, NodeList nodes,
		String namespace, char[] matchChrs, int start, int length, String replacementSuffix)
	{
		for (int j = 0; j < nodes.getLength(); j++)
		{
			String id = nodes.item(j).getNodeValue();
			if (matchChrs.length == 0 || CharOperation.camelCaseMatch(matchChrs, id.toCharArray()))
			{
				StringBuilder replacementStr = new StringBuilder();
				if (namespace != null && namespace.length() > 0)
					replacementStr.append(namespace).append('.');
				replacementStr.append(id);
				int cursorPos = replacementStr.length();
				if (replacementSuffix != null)
					replacementStr.append(replacementSuffix);
				ICompletionProposal proposal = new CompletionProposal(replacementStr.toString(), start,
					length, cursorPos, Activator.getIcon(), id, null, null);
				contentAssistRequest.addProposal(proposal);
			}
		}
	}

	private void proposeNamespace(ContentAssistRequest contentAssistRequest,
		IJavaProject project, IDOMNode node, String partialNamespace, char[] matchChrs, int start,
		int length, String replacementSuffix) throws XPathExpressionException
	{
		String currentNamespace = XpathUtil.xpathString(node.getOwnerDocument(),
			"//mapper/@namespace");

		final List<ICompletionProposal> results = new ArrayList<ICompletionProposal>();
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
					if (replacementSuffix != null)
						replacementStr.append(replacementSuffix);
					String displayString = new StringBuilder().append(simpleName)
						.append(" - ")
						.append(namespace)
						.toString();
					results.add(new CompletionProposal(replacementStr.toString(), start, length,
						cursorPos, Activator.getIcon("/icons/mybatis-ns.png"), displayString, null, null));
				}
			}
		}
		addProposals(contentAssistRequest, results);
	}

	private void proposeMapperNamespace(ContentAssistRequest contentAssistRequest,
		IJavaProject project, int start, int length)
	{
		// Calculate namespace from the file's classpath.
		String namespace = MybatipseXmlUtil.getJavaMapperType(project);
		ICompletionProposal proposal = new CompletionProposal(namespace, start, length,
			namespace.length(), Activator.getIcon("/icons/mybatis-ns.png"), namespace, null, null);
		contentAssistRequest.addProposal(proposal);
	}

	private void proposeStatementId(ContentAssistRequest contentAssistRequest,
		IJavaProject project, String matchString, int start, int length, IDOMNode node)
		throws JavaModelException, XPathExpressionException
	{
		final List<ICompletionProposal> results = new ArrayList<ICompletionProposal>();

		String qualifiedName = MybatipseXmlUtil.getNamespace(node.getOwnerDocument());
		IType type = project.findType(qualifiedName);
		for (IMethod method : type.getMethods())
		{
			String statementId = method.getElementName();
			if (matchString.length() == 0
				|| CharOperation.camelCaseMatch(matchString.toCharArray(), statementId.toCharArray()))
			{
				results.add(new CompletionProposal(statementId, start, length, statementId.length(),
					Activator.getIcon(), statementId, null, null));
			}
		}
		addProposals(contentAssistRequest, results);
	}

	private void proposeProperty(ContentAssistRequest contentAssistRequest, String matchString,
		int start, int length, IDOMNode node) throws JavaModelException
	{
		String javaType = MybatipseXmlUtil.findEnclosingType(node);
		if (javaType != null && !MybatipseXmlUtil.isDefaultTypeAlias(javaType))
		{
			IJavaProject project = getJavaProject(contentAssistRequest);
			IType type = project.findType(javaType);
			if (type == null)
			{
				javaType = TypeAliasCache.getInstance().resolveAlias(project, javaType, null);
				if (javaType == null)
					return;
			}
			Map<String, String> fields = BeanPropertyCache.searchFields(project, javaType,
				matchString, false, -1, false);
			List<ICompletionProposal> proposals = BeanPropertyCache.buildFieldNameProposal(fields,
				matchString, start, length);
			for (ICompletionProposal proposal : proposals)
			{
				contentAssistRequest.addProposal(proposal);
			}
		}
	}

	private void proposePackage(final ContentAssistRequest contentAssistRequest,
		IJavaProject project, String matchString, final int start, final int length)
		throws CoreException
	{
		final List<ICompletionProposal> results = new ArrayList<ICompletionProposal>();
		final Set<String> foundPkgs = new HashSet<String>();
		int includeMask = IJavaSearchScope.SOURCES | IJavaSearchScope.REFERENCED_PROJECTS;
		// Include application libraries only when package is specified (for better performance).
		boolean pkgSpecified = matchString != null && matchString.indexOf('.') > 0;
		if (pkgSpecified)
			includeMask |= IJavaSearchScope.APPLICATION_LIBRARIES | IJavaSearchScope.SYSTEM_LIBRARIES;
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaProject[]{
			project
		}, includeMask);
		SearchRequestor requestor = new SearchRequestor()
		{
			@Override
			public void acceptSearchMatch(SearchMatch match) throws CoreException
			{
				PackageFragment element = (PackageFragment)match.getElement();
				String pkg = element.getElementName();
				if (pkg != null && pkg.length() > 0 && !foundPkgs.contains(pkg))
				{
					foundPkgs.add(pkg);
					results.add(new CompletionProposal(pkg, start, length, pkg.length(),
						Activator.getIcon(), pkg, null, null));
				}
			}
		};
		searchPackage(matchString, scope, requestor);
		addProposals(contentAssistRequest, results);
	}

	private void searchPackage(String matchString, IJavaSearchScope scope,
		SearchRequestor requestor) throws CoreException
	{
		SearchPattern pattern = SearchPattern.createPattern(matchString + "*",
			IJavaSearchConstants.PACKAGE, IJavaSearchConstants.DECLARATIONS,
			SearchPattern.R_PREFIX_MATCH);
		SearchEngine searchEngine = new SearchEngine();
		searchEngine.search(pattern, new SearchParticipant[]{
			SearchEngine.getDefaultSearchParticipant()
		}, scope, requestor, null);
	}

	private void proposeCacheType(final ContentAssistRequest contentAssistRequest,
		IJavaProject project, String matchString, final int start, final int length)
		throws JavaModelException
	{
		final List<ICompletionProposal> results = new ArrayList<ICompletionProposal>();
		IType cache;
		IJavaSearchScope scope;
		cache = project.findType("org.apache.ibatis.cache.Cache");
		if (cache == null)
			return;
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
		addProposals(contentAssistRequest, results);
	}

	private void proposeTypeHandler(final ContentAssistRequest contentAssistRequest,
		IJavaProject project, String matchString, final int start, final int length)
		throws JavaModelException
	{
		final List<ICompletionProposal> results = new ArrayList<ICompletionProposal>();
		IType typeHandler;
		IJavaSearchScope scope;
		typeHandler = project.findType("org.apache.ibatis.type.TypeHandler");
		if (typeHandler == null)
			return;
		scope = SearchEngine.createHierarchyScope(typeHandler);
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
		addProposals(contentAssistRequest, results);
	}

	private void proposeJavaType(final ContentAssistRequest contentAssistRequest,
		IJavaProject project, String matchString, final int start, final int length,
		boolean includeAlias) throws JavaModelException
	{
		final List<ICompletionProposal> results = new ArrayList<ICompletionProposal>();
		if (includeAlias)
		{
			TypeAliasMap typeAliasMap = TypeAliasCache.getInstance().getTypeAliasMap(project, null);
			for (TypeAliasInfo typeAliasInfo : typeAliasMap.values())
			{
				String alias = typeAliasInfo.getAliasToInsert();
				String qualifiedName = typeAliasInfo.getQualifiedName();
				char[] aliasChrs = alias.toCharArray();
				char[] matchChrs = matchString.toCharArray();
				if (matchString.length() == 0 || CharOperation.camelCaseMatch(matchChrs, aliasChrs)
					|| CharOperation.prefixEquals(matchChrs, aliasChrs, false))
				{
					results.add(new CompletionProposal(alias, start, length, alias.length(),
						Activator.getIcon("/icons/mybatis-alias.png"), alias + " - " + qualifiedName, null,
						null));
				}
			}
			addProposals(contentAssistRequest, results);
		}

		results.clear();

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

				addJavaTypeProposal(results, start, length, packageName, simpleTypeName,
					enclosingTypeNames);
			}
		};
		searchJavaType(matchString, scope, requestor);
		addProposals(contentAssistRequest, results);
	}

	private void searchJavaType(String matchString, IJavaSearchScope scope,
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

	private void addProposals(final ContentAssistRequest contentAssistRequest,
		List<ICompletionProposal> proposals)
	{
		Collections.sort(proposals, new CompletionProposalComparator());
		for (ICompletionProposal proposal : proposals)
		{
			contentAssistRequest.addProposal(proposal);
		}
	}

	private ProposalType resolveProposalType(String tag, String attr)
	{
		// TODO: proxyFactory, logImpl
		if ("mapper".equals(tag) && "namespace".equals(attr))
			return ProposalType.MapperNamespace;
		else if ("type".equals(attr) && "typeAlias".equals(tag))
			return ProposalType.TypeAlias;
		else if ("type".equals(attr) && "cache".equals(tag))
			return ProposalType.CacheType;
		else if ("type".equals(attr) && "objectFactory".equals(tag))
			return ProposalType.None; // TODO propose object factory
		else if ("type".equals(attr) && "objectWrapperFactory".equals(tag))
			return ProposalType.None; // TODO propose object wrapper factory
		else if ("type".equals(attr) || "resultType".equals(attr) || "parameterType".equals(attr)
			|| "ofType".equals(attr) || "javaType".equals(attr))
			return ProposalType.ResultType;
		else if ("property".equals(attr))
			return ProposalType.ResultProperty;
		else if ("package".equals(tag) && "name".equals(attr))
			return ProposalType.Package;
		else if ("keyProperty".equals(attr))
			return ProposalType.None; // TODO propose key property?
		else if ("typeHandler".equals(attr) || "handler".equals(attr))
			return ProposalType.TypeHandlerType;
		else if ("resultMap".equals(attr) || "extends".equals(attr))
			return ProposalType.ResultMap;
		else if ("refid".equals(attr))
			return ProposalType.Include;
		else if ("select".equals(attr))
			return ProposalType.SelectId;
		else if ("id".equals(attr)
			&& ("select".equals(tag) || "update".equals(tag) || "insert".equals(tag) || "delete".equals(tag)))
			return ProposalType.StatementId;
		return ProposalType.None;
	}

	abstract class JavaTypeNameRequestor extends TypeNameRequestor
	{
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
			results.add(new CompletionProposal(typeFqn, start, length, typeFqn.length(),
				Activator.getIcon(), displayStr, null, null));
		}
	}

	private IJavaProject getJavaProject(ContentAssistRequest request)
	{
		if (request != null)
		{
			IStructuredDocumentRegion region = request.getDocumentRegion();
			if (region != null)
			{
				IDocument document = region.getParentDocument();
				return MybatipseXmlUtil.getJavaProject(document);
			}
		}
		return null;
	}

	private class CompletionProposalComparator implements Comparator<ICompletionProposal>
	{
		@Override
		public int compare(ICompletionProposal p1, ICompletionProposal p2)
		{
			return p1.getDisplayString().compareToIgnoreCase(p2.getDisplayString());
		}
	}
}
