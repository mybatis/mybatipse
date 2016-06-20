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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.dialogs.WizardNewFileCreationPage;

/**
 * @author Iwao AVE!
 */
public class NewXmlMapperWizardPage extends WizardNewFileCreationPage
{
	private static final String EXT = "xml";

	public NewXmlMapperWizardPage(IStructuredSelection selection)
	{
		super("NewXmlMapperWizardPage", selection);
		setTitle("New MyBatis XML Mapper");
		setDescription("Create a new MyBatis XML Mapper file.");
		setFileExtension(EXT);
		// Propose file name
		Object elem = selection.getFirstElement();
		if (elem != null)
		{
			if (elem instanceof IJavaElement)
			{
				int javaElemType = ((IJavaElement)elem).getElementType();
				if (javaElemType == IJavaElement.COMPILATION_UNIT)
				{
					setFileNameWithExtension(((ICompilationUnit)elem).getElementName());
				}
				else if (javaElemType == IJavaElement.CLASS_FILE)
				{
					setFileNameWithExtension(((IClassFile)elem).getElementName());
				}
				else if (javaElemType == IJavaElement.TYPE)
				{
					setFileNameWithExtension(((IType)elem).getElementName());
				}
			}
			else if (elem instanceof IFile)
			{
				setFileNameWithExtension(
					((IFile)elem).getFullPath().removeFileExtension().lastSegment());
			}
			else
			{
				// Leave empty.
			}
		}
	}

	private void setFileNameWithExtension(String name)
	{
		setFileName(name + "." + EXT);
	}

	@Override
	protected InputStream getInitialContents()
	{
		StringBuilder buffer = new StringBuilder();
		buffer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append(
				"<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n")
			.append("<mapper namespace=\"\">\n")
			.append("</mapper>");
		try
		{
			return new ByteArrayInputStream(buffer.toString().getBytes("utf-8"));
		}
		catch (UnsupportedEncodingException e)
		{
			return null;
		}
	}
}
