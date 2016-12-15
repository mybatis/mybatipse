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

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.ui.refactoring.RenameSupport;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.mybatis.JavaMapperUtil;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.MethodNameMatcher;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.SingleMethodStore;
import net.harawata.mybatipse.refactoring.ElementRenameInfo;
import net.harawata.mybatipse.refactoring.ElementRenameRefactoring;
import net.harawata.mybatipse.refactoring.wizard.ElementRenameWizard;

/**
 * @author Iwao AVE!
 */
public abstract class ElementRenameHandler extends AbstractHandler
{
	protected IWorkbenchWindow workbenchWindow;

	protected IEditorPart editor;

	protected void invokeJdtRename(IMethod method)
	{
		try
		{
			RenameSupport renameSupport = RenameSupport.create(method, null,
				RenameSupport.UPDATE_REFERENCES);
			renameSupport.openDialog(workbenchWindow.getShell());
		}
		catch (CoreException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
	}

	protected IMethod findMapperMethod(ElementRenameInfo refactoringInfo)
	{
		SingleMethodStore methodStore = new SingleMethodStore();
		JavaMapperUtil.findMapperMethod(methodStore, refactoringInfo.getProject(),
			refactoringInfo.getNamespace(), new MethodNameMatcher(refactoringInfo.getOldId(), true));
		IMethod method = methodStore.getMethod();
		return method;
	}

	protected void runRefactoringWizard(ElementRenameInfo refactoringInfo,
		ElementRenameRefactoring refactoring)
	{
		ElementRenameWizard wizard = new ElementRenameWizard(refactoring, refactoringInfo);
		RefactoringWizardOpenOperation op = new RefactoringWizardOpenOperation(wizard);
		try
		{
			String titleForFailedChecks = ""; //$NON-NLS-1$
			op.run(getShell(), titleForFailedChecks);
		}
		catch (final InterruptedException irex)
		{
			// operation was cancelled
		}
	}

	protected Shell getShell()
	{
		Shell result = null;
		if (editor != null)
		{
			result = editor.getSite().getShell();
		}
		else
		{
			result = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
		}
		return result;
	}

}
