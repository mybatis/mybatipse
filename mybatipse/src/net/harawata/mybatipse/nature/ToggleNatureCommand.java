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

package net.harawata.mybatipse.nature;

import java.util.Iterator;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import net.harawata.mybatipse.Activator;

/**
 * @author Iwao AVE!
 */
public class ToggleNatureCommand extends AbstractHandler implements IHandler
{
	private static final String TOGGLE_NATURE_PARAM = "net.harawata.mybatipse.ToggleNatureParam";

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException
	{
		String parameter = event.getParameter(TOGGLE_NATURE_PARAM);
		boolean addNature = "true".equals(parameter);
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
		IWorkbenchPage activePage = window.getActivePage();
		ISelection selection = activePage.getSelection();
		if (selection instanceof IStructuredSelection)
		{
			for (@SuppressWarnings("rawtypes")
			Iterator it = ((IStructuredSelection)selection).iterator(); it.hasNext();)
			{
				Object element = it.next();
				IProject project = null;
				if (element instanceof IProject)
				{
					project = (IProject)element;
				}
				else if (element instanceof IAdaptable)
				{
					project = (IProject)((IAdaptable)element).getAdapter(IProject.class);
				}
				if (project != null)
				{
					toggleNature(project, addNature);
				}
			}
		}
		return null;
	}

	private void toggleNature(IProject project, boolean addNature)
	{
		try
		{
			IProjectDescription description = project.getDescription();
			String[] natures = description.getNatureIds();
			for (int i = 0; i < natures.length; i++)
			{
				if (MyBatisNature.NATURE_ID.equals(natures[i]))
				{
					if (!addNature)
					{
						String[] newNatures = new String[natures.length - 1];
						System.arraycopy(natures, 0, newNatures, 0, i);
						System.arraycopy(natures, i + 1, newNatures, i, natures.length - i - 1);
						description.setNatureIds(newNatures);
						project.setDescription(description, null);
					}
					return;
				}
			}
			if (addNature)
			{
				String[] newNatures = new String[natures.length + 1];
				System.arraycopy(natures, 0, newNatures, 0, natures.length);
				newNatures[natures.length] = MyBatisNature.NATURE_ID;
				description.setNatureIds(newNatures);
				project.setDescription(description, null);
			}
		}
		catch (CoreException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
	}

}
