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

package net.harawata.mybatipse.refactoring;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.ui.texteditor.ITextEditor;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.nature.MyBatisNature;

/**
 * @author Iwao AVE!
 */
public class JavaRefactoringPropertyTester extends PropertyTester
{
	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue)
	{
		ITextEditor editor = (ITextEditor)receiver;
		IJavaElement element = JavaUI.getEditorInputJavaElement(editor.getEditorInput());
		if (element != null)
		{
			if ("isMyBatisProject".equals(property))
			{
				try
				{
					return element.getJavaProject().getProject().hasNature(MyBatisNature.NATURE_ID);
				}
				catch (CoreException e)
				{
					Activator.log(Status.ERROR, e.getMessage(), e);
				}
			}
		}
		return false;
	}
}
