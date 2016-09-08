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

package net.harawata.mybatipse.refactoring.wizard;

import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

import net.harawata.mybatipse.refactoring.ElementRenameInfo;

/**
 * @author Iwao AVE!
 */
public class ElementRenameWizard extends RefactoringWizard
{
	private final ElementRenameInfo info;

	public ElementRenameWizard(Refactoring refactoring, ElementRenameInfo info)
	{
		super(refactoring, DIALOG_BASED_USER_INTERFACE);
		this.info = info;
	}

	@Override
	protected void addUserInputPages()
	{
		setDefaultPageTitle(getRefactoring().getName());
		addPage(new ElementRenameWizardPage(info));
	}

}
