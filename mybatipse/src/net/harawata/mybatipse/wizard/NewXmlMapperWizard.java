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

package net.harawata.mybatipse.wizard;

import java.io.IOException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.eclipse.wst.xml.core.internal.provisional.format.FormatProcessorXML;
import org.w3c.dom.Element;

import net.harawata.mybatipse.Activator;

/**
 * @author Iwao AVE!
 */
@SuppressWarnings("restriction")
public class NewXmlMapperWizard extends Wizard implements INewWizard
{
	private IStructuredSelection selection;

	private IWorkbench workbench;

	private NewXmlMapperWizardPage wizardPage;

	public NewXmlMapperWizard()
	{
		setWindowTitle("New MyBatis XML Mapper");
	}

	@Override
	public void addPages()
	{
		wizardPage = new NewXmlMapperWizardPage(selection);
		addPage(wizardPage);
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection)
	{
		this.workbench = workbench;
		this.selection = selection;
	}

	@Override
	public boolean performFinish()
	{
		IFile file = wizardPage.createNewFile();
		if (file == null)
			return false;

		try
		{
			setNamespace(file, guessNamespace(file));
			new FormatProcessorXML().formatFile(file);
		}
		catch (Exception e)
		{
			Activator.log(Status.WARNING, e.getMessage(), e);
		}
		try
		{
			IDE.openEditor(workbench.getActiveWorkbenchWindow().getActivePage(), file);
		}
		catch (PartInitException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		return true;
	}

	private String guessNamespace(IFile file)
	{
		IProject project = file.getProject();
		IJavaProject javaProject = JavaCore.create(project);
		if (javaProject != null)
		{
			IPath fullPath = file.getFullPath();
			try
			{
				for (IClasspathEntry entry : javaProject.getRawClasspath())
				{
					if (entry.getPath().isPrefixOf(fullPath))
					{
						IPath relativePath = fullPath.makeRelativeTo(entry.getPath());
						return relativePath.removeFileExtension().toString().replace('/', '.');
					}
				}
			}
			catch (JavaModelException e)
			{
				Activator.log(Status.ERROR,
					"Failed to get raw classpath for project: " + javaProject.getElementName(), e);
			}
		}
		// empty string to raise warning
		return "";
	}

	private void setNamespace(IFile mapperFile, String namespace)
		throws IOException, CoreException
	{
		IStructuredModel model = StructuredModelManager.getModelManager()
			.getModelForEdit(mapperFile);
		if (model == null)
			return;

		try
		{
			model.beginRecording(this);
			model.aboutToChangeModel();
			if (model instanceof IDOMModel)
			{
				IDOMDocument mapperDoc = ((IDOMModel)model).getDocument();
				Element mapperNode = mapperDoc.getDocumentElement();
				mapperNode.setAttribute("namespace", namespace);
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
	}
}
