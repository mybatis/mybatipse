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
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.eclipse.wst.xml.core.internal.provisional.format.FormatProcessorXML;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.quickassist.JavaQuickAssistProcessor.MapperMethod;
import net.harawata.mybatipse.quickassist.JavaQuickAssistProcessor.ResultAnno;
import net.harawata.mybatipse.util.XpathUtil;

@SuppressWarnings("restriction")
final class MoveResultMapToXmlQuickAssist extends QuickAssistCompletionProposal
{
	private IFile xmlMapperFile;

	private MapperMethod method;

	private CompilationUnit astNode;

	@Override
	public void apply(IDocument document)
	{
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
		if (method.getResultsAnno() != null)
		{
			method.getResultsAnno().delete();
		}
		if (method.getConstructorArgsAnno() != null)
		{
			method.getConstructorArgsAnno().delete();
		}
		TextEdit textEdit = astNode.rewrite(document, null);
		textEdit.apply(document);
	}

	private boolean addXmlResultMap(final IFile xmlMapperFile)
		throws IOException, CoreException, UnsupportedEncodingException, XPathExpressionException
	{
		final IStructuredModel model = StructuredModelManager.getModelManager()
			.getModelForEdit(xmlMapperFile);
		if (model == null || !(model instanceof IDOMModel))
		{
			Activator.openDialog(MessageDialog.ERROR, "Cannot move result map to XML mapper",
				"Failed to create a model for the XML mapper "
					+ xmlMapperFile.getProjectRelativePath().toString());
			return false;
		}
		try
		{
			final IDOMDocument mapperDoc = ((IDOMModel)model).getDocument();
			String id = method.getResultsId();
			if (id == null)
			{
				Shell shell = Display.getDefault().getActiveShell();
				InputDialog dialog = new InputDialog(shell, "Enter result map id",
					"Specify id of the resultMap element", "", new IInputValidator()
					{
						@Override
						public String isValid(String newText)
						{
							if (newText.length() == 0)
							{
								return "Please enter result map id.";
							}
							try
							{
								Node domNode = XpathUtil.xpathNode(mapperDoc,
									"//resultMap[@id='" + newText + "']");
								if (domNode != null)
								{
									return "A resultMap with id '" + newText
										+ "' is already defined. Id must be unique.";
								}
							}
							catch (XPathExpressionException e)
							{
								return "Error occurred while looking for a resultMap with the same id. "
									+ "Did you use some unusual characters or something?";
							}
							return null;
						}
					});
				if (dialog.open() == Window.OK)
					id = dialog.getValue();
				else
					return false;
			}

			model.beginRecording(this);
			model.aboutToChangeModel();
			Element root = mapperDoc.getDocumentElement();
			Element element = createResultMapElement(mapperDoc, id);
			root.appendChild(element);
			String delimiter = model.getStructuredDocument().getLineDelimiter();
			root.appendChild(mapperDoc.createTextNode(delimiter));
			new FormatProcessorXML().formatNode(element);
		}
		finally
		{
			if (model != null)
			{
				model.changedModel();
				if (!model.isSharedForEdit() && model.isSaveNeeded())
				{
					model.save();
				}
				model.endRecording(this);
				model.releaseFromEdit();
			}
		}
		return true;
	}

	private Element createResultMapElement(IDOMDocument mapperDoc, String id)
	{
		Element resultMap = mapperDoc.createElement("resultMap");
		resultMap.setAttribute("id", id);
		resultMap.setAttribute("type", method.getReturnTypeStr());
		if (!method.getConstructorArgs().isEmpty())
		{
			Element constructor = mapperDoc.createElement("constructor");
			for (ResultAnno result : method.getConstructorArgs())
			{
				Element element;
				if (result.isId())
				{
					element = mapperDoc.createElement("idArg");
				}
				else
				{
					element = mapperDoc.createElement("arg");
				}
				setResultAttrs(result, element);
				constructor.appendChild(element);
			}
			resultMap.appendChild(constructor);
		}
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
			setResultAttrs(result, element);
			resultMap.appendChild(element);
		}
		return resultMap;
	}

	protected void setResultAttrs(ResultAnno result, Element element)
	{
		setAttr(element, "property", result.getProperty());
		setAttr(element, "column", result.getColumn());
		setAttr(element, "select", result.getSelectId());
		setAttr(element, "javaType", result.getJavaType());
		setAttr(element, "jdbcType", result.getJdbcType());
		setAttr(element, "fetchType", result.getFetchType());
		setAttr(element, "typeHandler", result.getTypeHandler());
		setAttr(element, "resultMap", result.getResultMap());
	}

	private void setAttr(Element element, String name, String value)
	{
		if (value != null)
			element.setAttribute(name, value);
	}

	MoveResultMapToXmlQuickAssist(
		String displayString,
		IFile xmlMapperFile,
		MapperMethod method,
		CompilationUnit astNode)
	{
		super(displayString);
		this.xmlMapperFile = xmlMapperFile;
		this.method = method;
		this.astNode = astNode;
	}
}
