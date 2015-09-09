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

package net.harawata.mybatipse.mybatis;

import static net.harawata.mybatipse.MybatipseConstants.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;

import net.harawata.mybatipse.Activator;

/**
 * @author Iwao AVE!
 */
public class ConfigRegistry
{
	private static final ConfigRegistry INSTANCE = new ConfigRegistry();

	private Map<String, Map<IFile, IContentType>> configMap = new ConcurrentHashMap<String, Map<IFile, IContentType>>();

	// public void put(IProject project, IFile configFile)
	// {
	// configMap.put(project.getName(), configFile);
	// TypeAliasCache.getInstance().remove(project);
	// }

	public Map<IFile, IContentType> get(IJavaProject javaProject)
	{
		IProject project = javaProject.getProject();
		Map<IFile, IContentType> files = configMap.get(project.getName());
		if (files == null)
		{
			files = search(javaProject);
			configMap.put(project.getName(), files);
			TypeAliasCache.getInstance().remove(project);
		}
		return files;
	}

	public void clear()
	{
		configMap.clear();
		TypeAliasCache.getInstance().clear();
	}

	public void remove(IProject project)
	{
		configMap.remove(project.getName());
		TypeAliasCache.getInstance().remove(project);
	}

	public void remove(IProject project, IFile file)
	{
		Map<IFile, IContentType> files = configMap.get(project.getName());
		if (files != null)
			files.remove(file);
		TypeAliasCache.getInstance().remove(project);
	}

	/**
	 * Scans the project and returns the MyBatis config file if found.<br>
	 * If there are multiple files in the project, only the first one is returned.
	 * 
	 * @param project
	 * @return MyBatis config file or <code>null</code> if none found.
	 */
	private Map<IFile, IContentType> search(IJavaProject project)
	{
		final Map<IFile, IContentType> configFiles = new ConcurrentHashMap<IFile, IContentType>();
		try
		{
			project.getResource().accept(new ConfigVisitor(configFiles), IContainer.NONE);

			for (IPackageFragmentRoot root : project.getPackageFragmentRoots())
			{
				if (root.getKind() != IPackageFragmentRoot.K_SOURCE)
					continue;
				root.getResource().accept(new ConfigVisitor(configFiles), IContainer.NONE);
			}
		}
		catch (CoreException e)
		{
			Activator.log(Status.ERROR, "Searching MyBatis Config xml failed.", e);
		}

		return configFiles;
	}

	public static ConfigRegistry getInstance()
	{
		return INSTANCE;
	}

	private ConfigRegistry()
	{
		super();
	}

	private class ConfigVisitor implements IResourceProxyVisitor
	{
		private final Map<IFile, IContentType> configFiles;

		private ConfigVisitor(Map<IFile, IContentType> configFiles)
		{
			this.configFiles = configFiles;
		}

		@Override
		public boolean visit(IResourceProxy proxy) throws CoreException
		{
			if (proxy.isDerived())
				return false;

			if (proxy.getType() == IResource.FILE && proxy.getName().endsWith(".xml"))
			{
				IFile file = (IFile)proxy.requestResource();
				IContentDescription contentDesc = file.getContentDescription();
				if (contentDesc != null)
				{
					IContentType contentType = contentDesc.getContentType();
					if (contentType != null && (contentType.isKindOf(configContentType)
						|| contentType.isKindOf(springConfigContentType)))
					{
						configFiles.put(file, contentType);
					}
				}
			}
			return true;
		}
	}
}
