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

import org.eclipse.jdt.core.IJavaProject;

/**
 * @author Iwao AVE!
 */
public class ElementRenameInfo
{
	private String namespace;

	private String oldId;

	private String newId;

	private IJavaProject project;

	public String getNamespace()
	{
		return namespace;
	}

	public void setNamespace(String namespace)
	{
		this.namespace = namespace;
	}

	public String getOldId()
	{
		return oldId;
	}

	public void setOldId(String oldId)
	{
		this.oldId = oldId;
	}

	public String getNewId()
	{
		return newId;
	}

	public void setNewId(String newId)
	{
		this.newId = newId;
	}

	public IJavaProject getProject()
	{
		return project;
	}

	public void setProject(IJavaProject project)
	{
		this.project = project;
	}
}
