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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.xpath.XPathExpressionException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.core.PackageFragment;
import org.eclipse.jdt.internal.ui.text.java.JavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocumentRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.ITextRegionList;
import org.eclipse.wst.sse.ui.contentassist.CompletionProposalInvocationContext;
import org.eclipse.wst.sse.ui.internal.contentassist.ContentAssistUtils;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMNode;
import org.eclipse.wst.xml.core.internal.regions.DOMRegionContext;
import org.eclipse.wst.xml.ui.internal.contentassist.ContentAssistRequest;
import org.eclipse.wst.xml.ui.internal.contentassist.DefaultXMLCompletionProposalComputer;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.MybatipseConstants;
import net.harawata.mybatipse.bean.BeanPropertyCache;
import net.harawata.mybatipse.bean.BeanPropertyInfo;
import net.harawata.mybatipse.bean.SupertypeHierarchyCache;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.MethodNameStore;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.MethodParametersStore;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.RejectStatementAnnotation;
import net.harawata.mybatipse.util.NameUtil;
import net.harawata.mybatipse.util.XpathUtil;

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
		JdbcType,
		SelectId,
		KeyProperty,
		ForEachCollection,
		ParamPropertyPartial,
		ObjectFactory,
		ObjectWrapperFactory,
		ReflectorFactory,
		SettingName,
		SettingValue
	}

	@Override
	protected ContentAssistRequest computeCompletionProposals(String matchString,
		ITextRegion completionRegion, IDOMNode treeNode, IDOMNode xmlnode,
		CompletionProposalInvocationContext context)
	{
		ContentAssistRequest contentAssistRequest = super.computeCompletionProposals(matchString,
			completionRegion, treeNode, xmlnode, context);
		if (contentAssistRequest != null)
			return contentAssistRequest;

		String regionType = completionRegion.getType();
		if (DOMRegionContext.XML_CDATA_TEXT.equals(regionType))
		{
			Node parentNode = xmlnode.getParentNode();
			int offset = context.getInvocationOffset();
			ITextViewer viewer = context.getViewer();
			contentAssistRequest = new ContentAssistRequest(xmlnode, parentNode,
				ContentAssistUtils.getStructuredDocumentRegion(viewer, offset), completionRegion,
				offset, 0, matchString);
			proposeStatementText(contentAssistRequest, parentNode);
		}
		return contentAssistRequest;
	}

	@Override
	protected void addTagInsertionProposals(ContentAssistRequest contentAssistRequest,
		int childPosition, CompletionProposalInvocationContext context)
	{
		int offset = contentAssistRequest.getReplacementBeginPosition();
		int length = contentAssistRequest.getReplacementLength();
		Node node = contentAssistRequest.getNode();
		// Current node can be 'parent' when the cursor is just before the end tag of the parent.
		Node parentNode = node.getNodeType() == Node.ELEMENT_NODE ? node : node.getParentNode();
		if (parentNode.getNodeType() != Node.ELEMENT_NODE)
			return;

		String tagName = parentNode.getNodeName();
		NamedNodeMap tagAttrs = parentNode.getAttributes();
		// Result mapping proposals.
		if ("resultMap".equals(tagName))
			generateResults(contentAssistRequest, offset, length, parentNode,
				tagAttrs.getNamedItem("type"));
		else if ("collection".equals(tagName))
			generateResults(contentAssistRequest, offset, length, parentNode,
				tagAttrs.getNamedItem("ofType"));
		else if ("association".equals(tagName))
			generateResults(contentAssistRequest, offset, length, parentNode,
				tagAttrs.getNamedItem("javaType"));

		proposeStatementText(contentAssistRequest, parentNode);
	}

	private void proposeStatementText(ContentAssistRequest contentAssistRequest, Node parentNode)
	{
		int offset = contentAssistRequest.getReplacementBeginPosition();
		String text = contentAssistRequest.getText();
		int offsetInText = offset - contentAssistRequest.getStartOffset() - 1;
		ExpressionProposalParser parser = new ExpressionProposalParser(text, offsetInText);
		if (parser.isProposable())
		{
			String matchString = parser.getMatchString();
			offset -= matchString.length();
			int length = parser.getReplacementLength();
			final IJavaProject project = getJavaProject(contentAssistRequest);
			String proposalTarget = parser.getProposalTarget();

			if (proposalTarget == null || proposalTarget.length() == 0)
				addProposals(contentAssistRequest,
					ProposalComputorHelper.proposeOptionName(offset, length, matchString));
			else if ("property".equals(proposalTarget))
				addProposals(contentAssistRequest,
					proposeParameter(project, offset, length, parentNode, true, matchString));
			else if ("jdbcType".equals(proposalTarget))
				addProposals(contentAssistRequest,
					ProposalComputorHelper.proposeJdbcType(offset, length, matchString));
			else if ("javaType".equals(proposalTarget))
				addProposals(contentAssistRequest,
					ProposalComputorHelper.proposeJavaType(project, offset, length, true, matchString));
			else if ("typeHandler".equals(proposalTarget))
				addProposals(contentAssistRequest, ProposalComputorHelper.proposeAssignable(project,
					offset, length, matchString, MybatipseConstants.TYPE_TYPE_HANDLER));
		}
	}

	private List<ICompletionProposal> proposeParameter(IJavaProject project, final int offset,
		final int length, Node parentNode, final boolean searchReadable, final String matchString)
	{
		final List<ICompletionProposal> proposals = new ArrayList<ICompletionProposal>();
		final Node statementNode = MybatipseXmlUtil.findEnclosingStatementNode(parentNode);
		if (statementNode == null)
			return proposals;

		String statementId = null;
		String paramType = null;
		NamedNodeMap statementAttrs = statementNode.getAttributes();
		for (int i = 0; i < statementAttrs.getLength(); i++)
		{
			Node attr = statementAttrs.item(i);
			String attrName = attr.getNodeName();
			if ("id".equals(attrName))
				statementId = attr.getNodeValue();
			else if ("parameterType".equals(attrName))
				paramType = attr.getNodeValue();
		}
		if (statementId == null || statementId.length() == 0)
			return proposals;

		try
		{
			// Look for the corresponding Java mapper method.
			String mapperFqn = MybatipseXmlUtil.getNamespace(statementNode.getOwnerDocument());
			final MethodParametersStore methodStore = new MethodParametersStore(project);
			JavaMapperUtil.findMapperMethod(methodStore, project, mapperFqn,
				new RejectStatementAnnotation(statementId, true));
			if (methodStore.isEmpty())
			{
				// Couldn't find the Java method. See if paramType is specified.
				if (paramType != null)
				{
					String resolved = TypeAliasCache.getInstance().resolveAlias(project, paramType, null);
					proposals.addAll(ProposalComputorHelper.proposePropertyFor(project, offset, length,
						resolved != null ? resolved : paramType, searchReadable, -1, matchString));
				}
			}
			else
			{
				final Map<String, String> additionalParams = new LinkedHashMap<String, String>();
				// parse foreach elements
				parseForeachNodes(project,
					XpathUtil.xpathNodes(parentNode, "ancestor-or-self::foreach"),
					methodStore.getParamMap(), additionalParams);
				// collect bind parameters
				NodeList bindNames = XpathUtil.xpathNodes(statementNode, "bind/@name");
				for (int i = 0; i < bindNames.getLength(); i++)
				{
					String bindName = bindNames.item(i).getNodeValue();
					additionalParams.put(bindName, "java.lang.Object");
				}
				proposals.addAll(ProposalComputorHelper.proposeParameters(project, offset, length,
					methodStore.getParamMap(), additionalParams, searchReadable, matchString));
			}
		}
		catch (XPathExpressionException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		return proposals;
	}

	private void parseForeachNodes(IJavaProject project, NodeList foreachNodes,
		Map<String, String> paramMap, Map<String, String> additionalParams)
		throws XPathExpressionException, JavaModelException
	{
		Map<String, String> map = new LinkedHashMap<String, String>();
		for (int i = 0; i < foreachNodes.getLength(); i++)
		{
			Node foreachNode = foreachNodes.item(i);
			String collection = XpathUtil.xpathString(foreachNode, "@collection");
			String item = XpathUtil.xpathString(foreachNode, "@item");
			String index = XpathUtil.xpathString(foreachNode, "@index");
			// Is collection a variable from outer foreach?
			String collectionFqn = resolveCollectionFqn(project, map, collection);
			if (collectionFqn == null)
			{
				// Is collection a statement parameter?
				collectionFqn = resolveCollectionFqn(project, paramMap, collection);
			}
			if (collectionFqn == null)
			{
				// Is it a parameter property?
				for (String paramFqn : paramMap.values())
				{
					Collection<String> fieldFqns = BeanPropertyCache
						.searchFields(project, paramFqn, collection, true, -1, true)
						.values();
					if (!fieldFqns.isEmpty())
					{
						collectionFqn = fieldFqns.iterator().next();
					}
				}
			}
			if (collectionFqn != null)
			{
				putItemAndIndex(project, map, collectionFqn, item, index);
			}
		}
		// reverse order of the proposals
		ListIterator<Entry<String, String>> iter = new ArrayList<Entry<String, String>>(
			map.entrySet()).listIterator(map.size());
		while (iter.hasPrevious())
		{
			Entry<String, String> entry = iter.previous();
			additionalParams.put(entry.getKey(), entry.getValue());
		}
	}

	private String resolveCollectionFqn(IJavaProject project,
		final Map<String, String> foreachParams, String collection)
	{
		String collectionFqn = null;
		for (Entry<String, String> param : foreachParams.entrySet())
		{
			String paramName = param.getKey();
			String paramFqn = param.getValue();
			if (paramName.equals(collection))
			{
				collectionFqn = paramFqn;
			}
			else if (collection.startsWith(paramName + "."))
			{
				Collection<String> fieldFqns = BeanPropertyCache
					.searchFields(project, paramFqn, collection, true, paramName.length(), false)
					.values();
				if (!fieldFqns.isEmpty())
				{
					collectionFqn = fieldFqns.iterator().next();
				}
			}
		}
		return collectionFqn;
	}

	private void putItemAndIndex(IJavaProject project, Map<String, String> foreachParams,
		String collectionFqn, String item, String index) throws JavaModelException
	{
		if (NameUtil.isArray(collectionFqn))
		{
			foreachParams.put(index, "int");
			foreachParams.put(item, collectionFqn.substring(0, collectionFqn.length() - 2));
			return;
		}
		String rawTypeFqn = NameUtil.stripTypeArguments(collectionFqn);
		if (rawTypeFqn.equals(collectionFqn))
		{
			return;
		}
		IType rawType = project.findType(rawTypeFqn);
		if (SupertypeHierarchyCache.getInstance().isCollection(rawType))
		{
			List<String> typeParams = NameUtil.extractTypeParams(collectionFqn);
			if (typeParams.size() == 1)
			{
				// Only a Collection with one type param is supported for now.
				// Check if it's a collection of Map.Entry.
				String typeParam = typeParams.get(0);
				String typeParamRawTypeFqn = NameUtil.stripTypeArguments(typeParam);
				if (!typeParamRawTypeFqn.equals(typeParam))
				{
					IType typeParamRawType = project.findType(typeParamRawTypeFqn);
					if (SupertypeHierarchyCache.getInstance()
						.isSubtype(typeParamRawType, "java.util.Map.Entry"))
					{
						putMapItemAndIndex(foreachParams, item, index,
							NameUtil.extractTypeParams(typeParam));
						return;
					}
				}
				foreachParams.put(index, "int");
				foreachParams.put(item, typeParam);
				return;
			}
		}
		if (SupertypeHierarchyCache.getInstance().isMap(rawType))
		{
			putMapItemAndIndex(foreachParams, item, index, NameUtil.extractTypeParams(collectionFqn));
		}
	}

	private void putMapItemAndIndex(Map<String, String> foreachParams, String item, String index,
		List<String> typeParams)
	{
		if (typeParams.size() == 2)
		{
			// Only a Map/Entry with 2 type params is supported for now.
			foreachParams.put(index, typeParams.get(0));
			foreachParams.put(item, typeParams.get(1));
		}
	}

	private void generateResults(ContentAssistRequest contentAssistRequest, int offset,
		int length, Node parentNode, Node typeAttr)
	{
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
			qualifiedName = MybatipseXmlUtil.normalizeTypeName(typeValue);
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
			contentAssistRequest
				.addProposal(new CompletionProposal(resultTags.toString(), offset, length,
					resultTags.length(), Activator.getIcon(), "<result /> for properties", null, null));
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

		String currentValue = null;
		String matchString = contentAssistRequest.getMatchString();
		int start = contentAssistRequest.getReplacementBeginPosition();
		if (contentAssistRequest.getRegion().getType() == DOMRegionContext.XML_TAG_ATTRIBUTE_VALUE)
		{
			currentValue = contentAssistRequest.getText();
			int valueStart = 0;
			int valueEnd = currentValue.length();
			// Avoid deleting the next line when there is no closing quote.
			int newLine = currentValue.indexOf('\r');
			if (newLine == -1)
			{
				newLine = currentValue.indexOf('\n');
			}
			if (newLine > -1)
			{
				// No end quote: attr="value[cursor]>
				valueEnd = currentValue.lastIndexOf('>', newLine);
				if (valueEnd == -1)
					valueEnd = newLine;
			}
			char firstChar = currentValue.charAt(0);
			if (firstChar == '"' || firstChar == '\'')
			{
				valueStart = 1;
				matchString = matchString.substring(1, matchString.length());
				start++;
			}
			char lastChar = currentValue.charAt(valueEnd - 1);
			if (valueStart == 1 && valueStart < valueEnd && firstChar == lastChar)
			{
				valueEnd--;
			}
			currentValue = currentValue.substring(valueStart, valueEnd);
			if (matchString.length() > currentValue.length())
			{
				// Cursor after the end quote: attr="value"[cursor]>
				return;
			}
		}
		else
		{
			currentValue = "";
		}
		int length = currentValue.length();

		IJavaProject project = getJavaProject(contentAssistRequest);
		try
		{
			switch (proposalType)
			{
				case Package:
					proposePackage(contentAssistRequest, project, matchString, start, length);
					break;
				case TypeAlias:
					addProposals(contentAssistRequest,
						ProposalComputorHelper.proposeJavaType(project, start, length, false, matchString));
					break;
				case ResultType:
					addProposals(contentAssistRequest,
						ProposalComputorHelper.proposeJavaType(project, start, length, true, matchString));
					break;
				case JdbcType:
					addProposals(contentAssistRequest,
						ProposalComputorHelper.proposeJdbcType(start, length, matchString));
					break;
				case ResultProperty:
					proposeProperty(contentAssistRequest, matchString, start, length, node);
					break;
				case TypeHandlerType:
					addProposals(contentAssistRequest, ProposalComputorHelper.proposeAssignable(project,
						start, length, matchString, MybatipseConstants.TYPE_TYPE_HANDLER));
					break;
				case CacheType:
					addProposals(contentAssistRequest, ProposalComputorHelper.proposeAssignable(project,
						start, length, matchString, MybatipseConstants.TYPE_CACHE));
					break;
				case SettingName:
					addProposals(contentAssistRequest,
						ProposalComputorHelper.proposeSettingName(start, length, matchString));
					break;
				case SettingValue:
					String settingName = XpathUtil.xpathString(node, "@name");
					addProposals(contentAssistRequest, ProposalComputorHelper.proposeSettingValue(project,
						settingName, start, length, matchString));
					break;
				case ObjectFactory:
					addProposals(contentAssistRequest, ProposalComputorHelper.proposeAssignable(project,
						start, length, matchString, MybatipseConstants.TYPE_OBJECT_FACTORY));
					break;
				case ObjectWrapperFactory:
					addProposals(contentAssistRequest, ProposalComputorHelper.proposeAssignable(project,
						start, length, matchString, MybatipseConstants.TYPE_OBJECT_WRAPPER_FACTORY));
					break;
				case ReflectorFactory:
					addProposals(contentAssistRequest, ProposalComputorHelper.proposeAssignable(project,
						start, length, matchString, MybatipseConstants.TYPE_REFLECTOR_FACTORY));
					break;
				case StatementId:
					proposeStatementId(contentAssistRequest, project, matchString, start, length, node);
					break;
				case MapperNamespace:
					proposeMapperNamespace(contentAssistRequest, project, start, length);
					break;
				case ResultMap:
					String ownId = "resultMap".equals(tagName) && "extends".equals(attributeName)
						? XpathUtil.xpathString(node, "@id")
						: null;
					addProposals(contentAssistRequest, proposeResultMapReference(project,
						node.getOwnerDocument(), start, currentValue, matchString.length(), ownId));
					break;
				case Include:
					addProposals(contentAssistRequest,
						ProposalComputorHelper.proposeReference(project,
							MybatipseXmlUtil.getNamespace(node.getOwnerDocument()), matchString, start,
							length, "sql", null));
					break;
				case SelectId:
					addProposals(contentAssistRequest,
						ProposalComputorHelper.proposeReference(project,
							MybatipseXmlUtil.getNamespace(node.getOwnerDocument()), matchString, start,
							length, "select", null));
					break;
				case KeyProperty:
					addProposals(contentAssistRequest,
						proposeParameter(project, start, length, node, false, matchString));
					break;
				case ForEachCollection:
					addProposals(contentAssistRequest,
						proposeParameter(project, start, length, node.getParentNode(), true, matchString));
					break;
				case ParamPropertyPartial:
					AttrTextParser parser = new AttrTextParser(currentValue, matchString.length());
					addProposals(contentAssistRequest,
						proposeParameter(project, start + parser.getMatchStringStart(),
							parser.getReplacementLength(), node.getParentNode(), true,
							parser.getMatchString()));
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

	private List<ICompletionProposal> proposeResultMapReference(IJavaProject project,
		Document domDoc, int start, String currentValue, int offsetInCurrentValue, String exclude)
		throws XPathExpressionException, IOException, CoreException
	{
		int leftComma = currentValue.lastIndexOf(',', offsetInCurrentValue);
		int rightComma = currentValue.indexOf(',', offsetInCurrentValue);
		String newMatchString = currentValue.substring(leftComma + 1, offsetInCurrentValue).trim();
		int newStart = start + offsetInCurrentValue - newMatchString.length();
		int newLength = currentValue.length() - (offsetInCurrentValue - newMatchString.length())
			- (rightComma > -1 ? currentValue.length() - rightComma : 0);
		return ProposalComputorHelper.proposeReference(project,
			MybatipseXmlUtil.getNamespace(domDoc), newMatchString, newStart, newLength, "resultMap",
			exclude);
	}

	private void proposeMapperNamespace(ContentAssistRequest contentAssistRequest,
		IJavaProject project, int start, int length)
	{
		String namespace = MybatipseXmlUtil.getNamespaceFromActiveEditor(project);
		ICompletionProposal proposal = new CompletionProposal(namespace, start, length,
			namespace.length(), Activator.getIcon("/icons/mybatis-ns.png"), namespace, null, null);
		contentAssistRequest.addProposal(proposal);
	}

	private void proposeStatementId(ContentAssistRequest contentAssistRequest,
		IJavaProject project, String matchString, int start, int length, IDOMNode node)
		throws JavaModelException, XPathExpressionException
	{
		final List<ICompletionProposal> results = new ArrayList<ICompletionProposal>();
		final MethodNameStore methodStore = new MethodNameStore();
		String qualifiedName = MybatipseXmlUtil.getNamespace(node.getOwnerDocument());
		JavaMapperUtil.findMapperMethod(methodStore, project, qualifiedName,
			new RejectStatementAnnotation(matchString, false));
		// Collect IDs that are already declared in the XML mapper(s). #87
		List<String> existingIds = new ArrayList<>();
		for (IDOMDocument xmlMapper : MybatipseXmlUtil.getMapperDocument(project, qualifiedName))
		{
			NodeList idNodes = XpathUtil.xpathNodes(xmlMapper,
				"//select/@id|//insert/@id|//update/@id|//delete/@id");
			for (int i = 0; i < idNodes.getLength(); i++)
			{
				existingIds.add(idNodes.item(i).getNodeValue());
			}
		}
		for (String methodName : methodStore.getMethodNames())
		{
			boolean idExists = existingIds.contains(methodName);
			results.add(new JavaCompletionProposal(methodName, start, length,
				Activator.getIcon(idExists ? "/icons/mybatis-alias.png" : "/icons/mybatis.png"),
				methodName, idExists ? 100 : 200));
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
			addProposals(contentAssistRequest, ProposalComputorHelper.proposePropertyFor(project,
				start, length, javaType, false, -1, matchString));
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
		else if ("name".equals(attr) && "setting".equals(tag))
			return ProposalType.SettingName;
		else if ("value".equals(attr) && "setting".equals(tag))
			return ProposalType.SettingValue;
		else if ("type".equals(attr) && "objectFactory".equals(tag))
			return ProposalType.ObjectFactory;
		else if ("type".equals(attr) && "objectWrapperFactory".equals(tag))
			return ProposalType.ObjectWrapperFactory;
		else if ("type".equals(attr) && "reflectorFactory".equals(tag))
			return ProposalType.ReflectorFactory;
		else if ("type".equals(attr) || "resultType".equals(attr) || "parameterType".equals(attr)
			|| "ofType".equals(attr) || "javaType".equals(attr))
			return ProposalType.ResultType;
		else if ("jdbcType".equals(attr))
			return ProposalType.JdbcType;
		else if ("property".equals(attr))
			return ProposalType.ResultProperty;
		else if ("package".equals(tag) && "name".equals(attr))
			return ProposalType.Package;
		else if ("typeHandler".equals(attr) || "handler".equals(attr))
			return ProposalType.TypeHandlerType;
		else if ("resultMap".equals(attr) || "extends".equals(attr))
			return ProposalType.ResultMap;
		else if ("refid".equals(attr))
			return ProposalType.Include;
		else if ("select".equals(attr))
			return ProposalType.SelectId;
		else if ("keyProperty".equals(attr))
			return ProposalType.KeyProperty;
		else if ("collection".equals(attr))
			return ProposalType.ForEachCollection;
		else if ("test".equals(attr) || ("bind".equals(tag) && "value".equals(attr)))
			return ProposalType.ParamPropertyPartial;
		else if ("id".equals(attr) && ("select".equals(tag) || "update".equals(tag)
			|| "insert".equals(tag) || "delete".equals(tag)))
			return ProposalType.StatementId;
		return ProposalType.None;
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
			if (p1 instanceof IJavaCompletionProposal && p2 instanceof IJavaCompletionProposal)
			{
				int relevance1 = ((IJavaCompletionProposal)p1).getRelevance();
				int relevance2 = ((IJavaCompletionProposal)p2).getRelevance();
				int diff = relevance2 - relevance1;
				if (diff != 0)
					return diff;
			}
			return 0;
		}
	}

	private class AttrTextParser
	{
		private String text;

		private int offset;

		private String matchString;

		public AttrTextParser(String text, int offset)
		{
			super();
			this.text = text;
			this.offset = offset;
			parse();
		}

		private void parse()
		{
			for (int i = offset - 1; i > 0; i--)
			{
				char c = text.charAt(i);
				if (!(Character.isJavaIdentifierPart(c) || c == '[' || c == ']' || c == '.'))
				{
					matchString = text.substring(i + 1, offset);
					return;
				}
			}
			matchString = text.substring(0, offset);
		}

		public int getMatchStringStart()
		{
			return offset - matchString.length();
		}

		public int getReplacementLength()
		{
			int i = offset;
			for (; i < text.length(); i++)
			{
				char c = text.charAt(i);
				if (!(Character.isJavaIdentifierPart(c) || c == '[' || c == ']' || c == '.'))
				{
					break;
				}
			}
			return i - offset + matchString.length();
		}

		public String getMatchString()
		{
			return matchString;
		}
	}
}
