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

package net.harawata.mybatipse.quickassist;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.xml.xpath.XPathExpressionException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.eclipse.wst.xml.core.internal.provisional.format.FormatProcessorXML;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.mybatis.MapperNamespaceCache;
import net.harawata.mybatipse.quickassist.JavaQuickAssistProcessor.MapperMethod;
import net.harawata.mybatipse.quickassist.JavaQuickAssistProcessor.ResultAnno;
import net.harawata.mybatipse.util.XpathUtil;

@SuppressWarnings("restriction")
final class MoveResultMapToXmlQuickAssist extends QuickAssistCompletionProposal
{
	private IJavaProject project;

	private String mapperFqn;

	private MapperMethod method;

	private CompilationUnit astNode;

	@Override
	public void apply(IDocument document)
	{
		if (method.getResultsId() == null)
		{
			Activator.openDialog(MessageDialog.ERROR, "Cannot move result map to XML mapper",
				"You must specify 'id' in @Results.");
			return;
		}
		IFile xmlMapperFile = MapperNamespaceCache.getInstance().get(project, mapperFqn, null);
		if (xmlMapperFile == null)
		{
			Activator.openDialog(MessageDialog.ERROR, "Cannot move result map to XML mapper",
				"You must create a corresponding XML mapper file first.");
			return;
		}
		try
		{
			if (addXmlResultMap(xmlMapperFile))
			{
				deleteResultsAnnotation(document);
			}
		}
		catch (Exception e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
	}

	private void deleteResultsAnnotation(IDocument document) throws BadLocationException
	{
		method.getResultsAnno().delete();
		TextEdit textEdit = astNode.rewrite(document, null);
		textEdit.apply(document);
	}

	private boolean addXmlResultMap(IFile xmlMapperFile)
		throws IOException, CoreException, UnsupportedEncodingException, XPathExpressionException
	{
		IStructuredModel model = StructuredModelManager.getModelManager()
			.getModelForEdit(xmlMapperFile);
		if (model == null)
		{
			Activator.openDialog(MessageDialog.ERROR, "Cannot move result map to XML mapper",
				"Failed to create a model for the XML mapper "
					+ xmlMapperFile.getProjectRelativePath().toString());
			return false;
		}

		try
		{
			model.beginRecording(this);
			model.aboutToChangeModel();
			if (model instanceof IDOMModel)
			{
				String delimiter = model.getStructuredDocument().getLineDelimiter();
				IDOMDocument mapperDoc = ((IDOMModel)model).getDocument();
				String id = method.getResultsId();
				Node domNode = XpathUtil.xpathNode(mapperDoc, "//resultMap[@id='" + id + "']");
				if (domNode != null)
				{
					Activator.openDialog(MessageDialog.ERROR, "Cannot move result map to XML mapper",
						"A resultMap with id '" + id + "' is already defined in "
							+ xmlMapperFile.getProjectRelativePath().toString());
					return false;
				}
				Element root = mapperDoc.getDocumentElement();
				Element element = createResultMapElement(mapperDoc, delimiter);
				root.appendChild(element);
				root.appendChild(mapperDoc.createTextNode(delimiter));
				new FormatProcessorXML().formatNode(element);
			}
		}
		finally
		{
			model.changedModel();
			if (!model.isSharedForEdit() && model.isSaveNeeded())
			{
				model.save();
			}
			model.endRecording(this);
			model.releaseFromEdit();
		}
		return true;
	}

	private Element createResultMapElement(IDOMDocument mapperDoc, String delimiter)
	{
		Element resultMap = mapperDoc.createElement("resultMap");
		resultMap.setAttribute("id", method.getResultsId());
		resultMap.setAttribute("type", method.getReturnTypeStr());
		for (ResultAnno result : method.getResultAnnos())
		{
			Element element;
			if (result.isId())
			{
				element = mapperDoc.createElement("id");
			}
			else if (result.isAssociation())
			{
				element = mapperDoc.createElement("association");
			}
			else if (result.isCollection())
			{
				element = mapperDoc.createElement("collection");
			}
			else
			{
				element = mapperDoc.createElement("result");
			}
			setAttr(element, "property", result.getProperty());
			setAttr(element, "column", result.getColumn());
			setAttr(element, "select", result.getSelectId());
			setAttr(element, "javaType", result.getJavaType());
			setAttr(element, "jdbcType", result.getJdbcType());
			setAttr(element, "fetchType", result.getFetchType());
			setAttr(element, "typeHandler", result.getTypeHandler());
			resultMap.appendChild(element);
		}
		return resultMap;
	}

	private void setAttr(Element element, String name, String value)
	{
		if (value != null)
			element.setAttribute(name, value);
	}

	MoveResultMapToXmlQuickAssist(
		String displayString,
		IJavaProject project,
		String mapperFqn,
		MapperMethod method,
		CompilationUnit astNode)
	{
		super(displayString);
		this.project = project;
		this.mapperFqn = mapperFqn;
		this.method = method;
		this.astNode = astNode;
	}
}
