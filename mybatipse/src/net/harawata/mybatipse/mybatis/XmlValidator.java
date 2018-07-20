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
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.validation.AbstractValidator;
import org.eclipse.wst.validation.ValidationResult;
import org.eclipse.wst.validation.ValidationState;
import org.eclipse.wst.validation.ValidatorMessage;
import org.eclipse.wst.validation.internal.provisional.core.IReporter;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMAttr;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMElement;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.bean.BeanPropertyCache;
import net.harawata.mybatipse.bean.SupertypeHierarchyCache;
import net.harawata.mybatipse.cache.JavaMapperCache;

/**
 * @author Iwao AVE!
 */
@SuppressWarnings("restriction")
public class XmlValidator extends AbstractValidator
{
	public static final String MARKER_ID = "net.harawata.mybatipse.XmlProblem";

	public static final String MISSING_TYPE = "missingType";

	public static final String NO_WRITABLE_PROPERTY = "noWritableProperty";

	public static final String MISSING_TYPE_HANDLER = "missingTypeHandler";

	public static final String MISSING_STATEMENT_METHOD = "missingStatementMethod";

	public static final String MISSING_RESULT_MAP = "missingResultMap";

	public static final String MISSING_SQL = "missingSql";

	public static final String MISSING_NAMESPACE = "missingNamespace";

	public static final String NAMESPACE_MANDATORY = "namespaceMandatory";

	public static final String DEPRECATED = "deprecated";

	private static final List<String> validatableTags = Arrays.asList("id", "idArg", "result",
		"arg", "resultMap", "collection", "association", "select", "insert", "update", "delete",
		"include", "cache", "typeAlias", "typeHandler", "objectFactory", "objectWrapperFactory",
		"plugin", "transactionManager", "mapper", "package", "databaseIdProvider");;

	public void cleanup(IReporter reporter)
	{
		// Nothing to do.
	}

	@Override
	public ValidationResult validate(final IResource resource, int kind, ValidationState state,
		IProgressMonitor monitor)
	{
		if (resource.getType() != IResource.FILE)
			return null;
		ValidationResult result = new ValidationResult();
		final IReporter reporter = result.getReporter(monitor);
		validateFile((IFile)resource, reporter, result);
		return result;
	}

	private void validateFile(IFile file, IReporter reporter, ValidationResult result)
	{
		if ((reporter != null) && (reporter.isCancelled() == true))
		{
			throw new OperationCanceledException();
		}
		IStructuredModel model = null;
		try
		{
			file.deleteMarkers(MARKER_ID, false, IResource.DEPTH_ZERO);
			model = StructuredModelManager.getModelManager().getModelForRead(file);
			IDOMModel domModel = (IDOMModel)model;
			IDOMDocument domDoc = domModel.getDocument();
			NodeList nodes = domDoc.getChildNodes();

			IJavaProject project = JavaCore.create(file.getProject());

			for (int k = 0; k < nodes.getLength(); k++)
			{
				Node child = nodes.item(k);
				if (child instanceof IDOMElement)
				{
					validateElement(project, (IDOMElement)child, file, domDoc, reporter, result);
				}
			}
		}
		catch (Exception e)
		{
			Activator.log(Status.WARNING, "Error occurred during validation.", e);
		}
		finally
		{
			if (model != null)
			{
				model.releaseFromRead();
			}
		}
	}

	private void validateElement(IJavaProject project, IDOMElement element, IFile file,
		IDOMDocument doc, IReporter reporter, ValidationResult result)
		throws JavaModelException, XPathExpressionException
	{
		if ((reporter != null) && (reporter.isCancelled() == true))
		{
			throw new OperationCanceledException();
		}
		if (element == null)
			return;

		String tagName = element.getNodeName();

		if (validatableTags.contains(tagName))
		{
			NamedNodeMap attrs = element.getAttributes();
			for (int i = 0; i < attrs.getLength(); i++)
			{
				IDOMAttr attr = (IDOMAttr)attrs.item(i);
				String attrName = attr.getName();
				String attrValue = attr.getValue().trim();

				// TODO: proxyFactory, logImpl, package
				if (("type".equals(attrName) && !"dataSource".equals(tagName))
					|| "resultType".equals(attrName) || "parameterType".equals(attrName)
					|| "ofType".equals(attrName) || "typeHandler".equals(attrName)
					|| "handler".equals(attrName) || "interceptor".equals(attrName)
					|| "class".equals(attrName) || "javaType".equals(attrName))
				{
					String qualifiedName = MybatipseXmlUtil.normalizeTypeName(attrValue);
					validateJavaType(project, file, doc, attr, qualifiedName, result, reporter);
				}
				else if ("property".equals(attrName))
				{
					validateProperty(element, file, doc, result, project, attr, attrValue, reporter);
				}
				else if ("id".equals(attrName) && ("select".equals(tagName) || "update".equals(tagName)
					|| "insert".equals(tagName) || "delete".equals(tagName)))
				{
					validateStatementId(element, file, doc, result, project, attr, attrValue);
				}
				else if ("resultMap".equals(attrName) || "resultMap".equals(attrName))
				{
					validateResultMapId(project, file, doc, result, attr, attrValue, reporter);
				}
				else if ("refid".equals(attrName))
				{
					validateSqlId(project, file, doc, result, attr, attrValue, reporter);
				}
				else if ("select".equals(attrName))
				{
					validateSelectId(project, file, doc, result, attr, attrValue, reporter);
				}
				else if ("namespace".equals(attrName))
				{
					validateNamespace(file, doc, result, attr, attrValue);
				}
				else if ("parameterMap".equals(tagName))
				{
					warnDeprecated(file, doc, result, tagName, attr);
				}
				else if ("parameter".equals(attrName) || "parameterMap".equals(attrName))
				{
					warnDeprecated(file, doc, result, attrName, attr);
				}
			}
		}

		NodeList nodes = element.getChildNodes();
		for (int j = 0; j < nodes.getLength(); j++)
		{
			Node child = nodes.item(j);
			if (child instanceof IDOMElement)
			{
				validateElement(project, (IDOMElement)child, file, doc, reporter, result);
			}
		}
	}

	private void validateNamespace(IFile file, IDOMDocument doc, ValidationResult result,
		IDOMAttr attr, String attrValue)
	{
		if (attrValue == null || attrValue.length() == 0)
		{
			addMarker(result, file, doc.getStructuredDocument(), attr, NAMESPACE_MANDATORY,
				IMarker.SEVERITY_ERROR, IMarker.PRIORITY_HIGH, "Namespace must be specified.");
		}
	}

	private void validateResultMapId(IJavaProject project, IFile file, IDOMDocument doc,
		ValidationResult result, IDOMAttr attr, String attrValue, IReporter reporter)
		throws JavaModelException, XPathExpressionException
	{
		if (attrValue.indexOf(',') == -1)
		{
			validateReference(project, file, doc, result, attr, attrValue, "resultMap", reporter);
		}
		else
		{
			String[] resultMapArr = attrValue.split(",");
			for (String resultMapRef : resultMapArr)
			{
				String ref = resultMapRef.trim();
				if (ref.length() > 0)
				{
					validateReference(project, file, doc, result, attr, ref, "resultMap", reporter);
				}
			}
		}
	}

	private void validateSelectId(IJavaProject project, IFile file, IDOMDocument doc,
		ValidationResult result, IDOMAttr attr, String attrValue, IReporter reporter)
		throws JavaModelException, XPathExpressionException
	{
		validateReference(project, file, doc, result, attr, attrValue, "select", reporter);
	}

	private void validateSqlId(IJavaProject project, IFile file, IDOMDocument doc,
		ValidationResult result, IDOMAttr attr, String attrValue, IReporter reporter)
		throws JavaModelException, XPathExpressionException
	{
		validateReference(project, file, doc, result, attr, attrValue, "sql", reporter);
	}

	private void validateReference(IJavaProject project, IFile file, IDOMDocument doc,
		ValidationResult result, IDOMAttr attr, String attrValue, String targetElement,
		IReporter reporter) throws JavaModelException, XPathExpressionException
	{
		if (!ValidatorHelper.isReferenceValid(project, MybatipseXmlUtil.getNamespace(doc),
			attrValue, targetElement))
		{
			addMarker(result, file, doc.getStructuredDocument(), attr, MISSING_SQL,
				IMarker.SEVERITY_ERROR, IMarker.PRIORITY_HIGH,
				targetElement + " with id='" + attrValue + "' not found.");
		}
	}

	private void validateStatementId(IDOMElement element, IFile file, IDOMDocument doc,
		ValidationResult result, IJavaProject project, IDOMAttr attr, String attrValue)
		throws JavaModelException, XPathExpressionException
	{
		if (attrValue == null)
		{
			return;
		}

		String qualifiedName = MybatipseXmlUtil.getNamespace(doc);
		IType mapperType = project.findType(qualifiedName);
		if (mapperType != null
			&& !JavaMapperCache.getInstance().methodExists(project, qualifiedName, attrValue))
		{
			addMarker(result, file, doc.getStructuredDocument(), attr, MISSING_STATEMENT_METHOD,
				IMarker.SEVERITY_WARNING, IMarker.PRIORITY_HIGH,
				"Method '" + attrValue + "' not found or there is an overload method"
					+ " (same name with different parameters) in mapper interface " + qualifiedName);
		}
	}

	private void validateProperty(IDOMElement element, IFile file, IDOMDocument doc,
		ValidationResult result, IJavaProject project, IDOMAttr attr, String attrValue,
		IReporter reporter) throws JavaModelException
	{
		String qualifiedName = MybatipseXmlUtil.findEnclosingType(element);
		if (qualifiedName == null || MybatipseXmlUtil.isDefaultTypeAlias(qualifiedName))
		{
			return;
		}
		IType type = project.findType(qualifiedName);
		if (type == null)
		{
			qualifiedName = TypeAliasCache.getInstance()
				.resolveAlias(project, qualifiedName, reporter);
			if (qualifiedName != null)
				type = project.findType(qualifiedName);
		}
		if (type == null || !isValidatable(project, type))
		{
			// Skip validation when enclosing type is missing or it's a map.
			return;
		}
		Map<String, String> fields = BeanPropertyCache.searchFields(project, qualifiedName,
			attrValue, false, -1, true);
		if (fields.size() == 0)
		{
			addMarker(result, file, doc.getStructuredDocument(), attr, MISSING_TYPE,
				IMarker.SEVERITY_ERROR, IMarker.PRIORITY_HIGH,
				"Property '" + attrValue + "' not found in class " + qualifiedName);
		}
	}

	private boolean isValidatable(IJavaProject project, IType type) throws JavaModelException
	{
		// Subclass of Map is not validatable.
		return !SupertypeHierarchyCache.getInstance().isMap(type);
	}

	private void validateJavaType(IJavaProject project, IFile file, IDOMDocument doc,
		IDOMAttr attr, String qualifiedName, ValidationResult result, IReporter reporter)
		throws JavaModelException
	{
		if (!MybatipseXmlUtil.isDefaultTypeAlias(qualifiedName)
			&& project.findType(qualifiedName) == null
			&& TypeAliasCache.getInstance().resolveAlias(project, qualifiedName, reporter) == null)
		{
			addMarker(result, file, doc.getStructuredDocument(), attr, MISSING_TYPE,
				IMarker.SEVERITY_ERROR, IMarker.PRIORITY_HIGH,
				"Class/TypeAlias '" + qualifiedName + "' not found.");
		}
	}

	private void warnDeprecated(IFile file, IDOMDocument doc, ValidationResult result,
		String tagName, IDOMAttr attr)
	{
		addMarker(result, file, doc.getStructuredDocument(), attr, DEPRECATED,
			IMarker.SEVERITY_WARNING, IMarker.PRIORITY_HIGH,
			"'" + tagName + "' is deprecated and should not be used.");
	}

	private void addMarker(ValidationResult result, IFile file, IStructuredDocument doc,
		IDOMAttr attr, String problemType, int severity, int priority, String message)
	{
		int start = attr.getValueRegionStartOffset();
		int length = attr.getValueRegionText().length();
		int lineNo = doc.getLineOfOffset(start);
		ValidatorMessage marker = ValidatorMessage.create(message, file);
		marker.setType(MARKER_ID);
		marker.setAttribute(IMarker.SEVERITY, severity);
		marker.setAttribute(IMarker.PRIORITY, priority);
		marker.setAttribute(IMarker.MESSAGE, message);
		marker.setAttribute(IMarker.LINE_NUMBER, lineNo);
		if (start != 0)
		{
			marker.setAttribute(IMarker.CHAR_START, start);
			marker.setAttribute(IMarker.CHAR_END, start + length);
		}
		// Adds custom attributes.
		marker.setAttribute("problemType", problemType);
		marker.setAttribute("errorValue", attr.getValue());
		result.add(marker);
	}

}
