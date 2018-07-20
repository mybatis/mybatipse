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
import org.eclipse.jdt.core.dom.MethodDeclaration;
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
import org.w3c.dom.Text;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.quickassist.JavaQuickAssistProcessor.MapperMethod;
import net.harawata.mybatipse.util.XpathUtil;

@SuppressWarnings("restriction")
final class MoveStatementToXmlQuickAssist extends QuickAssistCompletionProposal
{
	private IFile xmlMapperFile;

	private MapperMethod method;

	private CompilationUnit astNode;

	@Override
	public void apply(IDocument document)
	{
		if (method.getResultsAnno() != null && method.getResultsId() == null)
		{
			Activator.openDialog(MessageDialog.ERROR, "Cannot move result map to XML mapper",
				"You must specify 'id' in @Results before moving the statement.");
			return;
		}
		try
		{
			if (addXmlStatement(xmlMapperFile))
			{
				deleteStatementAnnotation(document);
			}
		}
		catch (Exception e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
	}

	private void deleteStatementAnnotation(IDocument document) throws BadLocationException
	{
		method.getStatementAnno().delete();
		TextEdit textEdit = astNode.rewrite(document, null);
		textEdit.apply(document);
	}

	private boolean addXmlStatement(IFile xmlMapperFile)
		throws IOException, CoreException, UnsupportedEncodingException, XPathExpressionException
	{
		IStructuredModel model = StructuredModelManager.getModelManager()
			.getModelForEdit(xmlMapperFile);
		if (model == null)
		{
			Activator.openDialog(MessageDialog.ERROR, "Cannot move statement to XML mapper",
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
				String id = method.getMethodDeclaration().getName().getFullyQualifiedName();
				Node domNode = XpathUtil.xpathNode(mapperDoc, "//*[@id='" + id + "']");
				if (domNode != null)
				{
					Activator.openDialog(MessageDialog.ERROR, "Cannot move statement to XML mapper",
						"An element with id '" + id + "' is already defined in "
							+ xmlMapperFile.getProjectRelativePath().toString());
					return false;
				}
				Element root = mapperDoc.getDocumentElement();
				Element element = createStatementElement(mapperDoc, delimiter);
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

	private Element createStatementElement(IDOMDocument mapperDoc, String delimiter)
	{
		String statement = method.getStatementAnnoName().toLowerCase();
		Element element = mapperDoc.createElement(statement);
		MethodDeclaration methodDeclaration = method.getMethodDeclaration();
		element.setAttribute("id", methodDeclaration.getName().toString());
		if (method.isSelect())
		{
			if (method.getResultMap() != null)
			{
				element.setAttribute("resultMap", method.getResultMap());
			}
			else if (method.getResultsId() != null)
			{
				element.setAttribute("resultMap", method.getResultsId());
			}
			else
			{
				element.setAttribute("resultType", method.getReturnTypeStr());
			}
		}
		Text sqlText = mapperDoc.createTextNode(delimiter + method.getStatement() + delimiter);
		element.appendChild(sqlText);
		return element;
	}

	MoveStatementToXmlQuickAssist(
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
