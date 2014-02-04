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

import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;

/**
 * @author Iwao AVE!
 */
public class MyBatisNature implements IProjectNature
{
	public static final String NATURE_ID = "net.harawata.mybatipse.MyBatisNature";

	private IProject project;

	@Override
	public void configure() throws CoreException
	{
		IProjectDescription description = project.getDescription();
		ICommand[] commands = description.getBuildSpec();
		for (ICommand command : commands)
		{
			if (command.getBuilderName().equals(MybatipseIncrementalBuilder.BUILDER_ID))
				return; // Builder already registered.
		}
		// Add the builder to the project.
		ICommand[] newCommands = new ICommand[commands.length + 1];
		System.arraycopy(commands, 0, newCommands, 0, commands.length);
		ICommand command = description.newCommand();
		command.setBuilderName(MybatipseIncrementalBuilder.BUILDER_ID);
		newCommands[newCommands.length - 1] = command;
		description.setBuildSpec(newCommands);
		project.setDescription(description, null);
	}

	@Override
	public void deconfigure() throws CoreException
	{
		IProjectDescription description = project.getDescription();
		ICommand[] commands = description.getBuildSpec();
		for (int i = 0; i < commands.length; i++)
		{
			if (commands[i].getBuilderName().equals(MybatipseIncrementalBuilder.BUILDER_ID))
			{
				// Remove the builder from the project.
				ICommand[] newCommands = new ICommand[commands.length - 1];
				System.arraycopy(commands, 0, newCommands, 0, i);
				System.arraycopy(commands, i + 1, newCommands, i, commands.length - i - 1);
				description.setBuildSpec(newCommands);
				project.setDescription(description, null);
				return;
			}
		}
	}

	@Override
	public IProject getProject()
	{
		return project;
	}

	@Override
	public void setProject(IProject project)
	{
		this.project = project;
	}
}
