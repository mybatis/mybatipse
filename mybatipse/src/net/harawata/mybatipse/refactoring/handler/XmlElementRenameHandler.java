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

package net.harawata.mybatipse.refactoring.handler;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMAttr;
import org.w3c.dom.Element;

import net.harawata.mybatipse.mybatis.MybatipseXmlUtil;
import net.harawata.mybatipse.refactoring.ElementRenameInfo;
import net.harawata.mybatipse.refactoring.ElementRenameRefactoring;
import net.harawata.mybatipse.refactoring.collector.ResultMapRenameEditCollector;
import net.harawata.mybatipse.refactoring.collector.SqlRenameEditCollector;
import net.harawata.mybatipse.refactoring.collector.StatementRenameEditCollector;

/**
 * @author Iwao AVE!
 */
@SuppressWarnings("restriction")
public class XmlElementRenameHandler extends ElementRenameHandler
{
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException
	{
		workbenchWindow = HandlerUtil.getActiveWorkbenchWindow(event);
		if (workbenchWindow == null)
			return null;

		IWorkbenchPage activePage = workbenchWindow.getActivePage();
		if (activePage == null)
			return null;

		editor = HandlerUtil.getActiveEditor(event);
		if (editor == null)
			return null;

		// HandlerUtil.getCurrentSelection(event) does not return
		// the latest 'selection' when the cursor is moved.
		ISelection selection = activePage.getSelection();
		if (selection != null && selection instanceof IStructuredSelection
			&& selection instanceof ITextSelection)
		{
			Object selected = ((IStructuredSelection)selection).getFirstElement();
			if (selected instanceof IDOMAttr)
			{
				IDocument document = editor.getAdapter(IDocument.class);
				final IDOMAttr attr = (IDOMAttr)selected;
				final String attrName = attr.getName();
				final Element tag = attr.getOwnerElement();
				if ("select".equals(attrName) || ("id".equals(attrName)
					&& MybatipseXmlUtil.findEnclosingStatementNode(tag) != null))
				{
					renameStatementId(document, attr);
				}
				else if ("refid".equals(attrName)
					|| ("id".equals(attrName) && "sql".equals(tag.getTagName())))
				{
					renameSqlId(document, attr);
				}
				else if ("resultMap".equals(attrName)
					|| ("id".equals(attrName) && "resultMap".equals(tag.getTagName())))
				{
					int offset = ((ITextSelection)selection).getOffset();
					renameResultMapId(document, attr, offset);
					// OUT param's resultMap option is not supported
				}
			}
		}
		return null;
	}

	protected void renameResultMapId(IDocument document, final IDOMAttr attr, int offset)
	{
		String id = attr.getValue();
		if (id.indexOf(',') > -1)
		{
			// multiple result maps. get the one that has the cursor.
			String valueRegionText = attr.getValueRegionText();
			int selectionOffset = offset - attr.getValueRegionStartOffset();
			int valueOffset = valueRegionText.indexOf(id);
			int start = valueRegionText.lastIndexOf(',', selectionOffset - 1);
			start = start == -1 ? valueOffset : start + 1;
			int end = valueRegionText.indexOf(',', selectionOffset);
			end = end == -1 ? valueOffset + id.length() : end;
			id = valueRegionText.substring(start, end).trim();
		}
		ElementRenameInfo refactoringInfo = createRefactoringInfo(document, attr, id);
		ElementRenameRefactoring refactoring = new ElementRenameRefactoring(
			new ResultMapRenameEditCollector(refactoringInfo));
		runRefactoringWizard(refactoringInfo, refactoring);
	}

	protected void renameSqlId(IDocument document, final IDOMAttr attr)
	{
		final String id = attr.getValue();
		ElementRenameInfo refactoringInfo = createRefactoringInfo(document, attr, id);
		ElementRenameRefactoring refactoring = new ElementRenameRefactoring(
			new SqlRenameEditCollector(refactoringInfo));
		runRefactoringWizard(refactoringInfo, refactoring);
	}

	protected void renameStatementId(IDocument document, final IDOMAttr attr)
	{
		String id = attr.getValue();
		ElementRenameInfo refactoringInfo = createRefactoringInfo(document, attr, id);
		IMethod method = findMapperMethod(refactoringInfo);
		if (method == null)
		{
			ElementRenameRefactoring refactoring = new ElementRenameRefactoring(
				new StatementRenameEditCollector(refactoringInfo, false));
			runRefactoringWizard(refactoringInfo, refactoring);
		}
		else
		{
			invokeJdtRename(method);
		}
	}

	protected ElementRenameInfo createRefactoringInfo(IDocument document, final IDOMAttr attr,
		String id)
	{
		String namespace;
		String oldId;
		int namespaceEnd = id.lastIndexOf('.');
		if (namespaceEnd > -1)
		{
			oldId = id.substring(namespaceEnd + 1);
			namespace = id.substring(0, namespaceEnd);
		}
		else
		{
			oldId = id;
			namespace = MybatipseXmlUtil.getNamespace(attr.getOwnerDocument());
		}
		ElementRenameInfo refactoringInfo = new ElementRenameInfo();
		refactoringInfo.setOldId(oldId);
		refactoringInfo.setNamespace(namespace);
		refactoringInfo.setProject(MybatipseXmlUtil.getJavaProject(document));
		return refactoringInfo;
	}
}
