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

package net.harawata.mybatipse;

import static net.harawata.mybatipse.MybatipseConstants.*;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;

import net.harawata.mybatipse.bean.BeanPropertyCache;
import net.harawata.mybatipse.bean.SupertypeHierarchyCache;
import net.harawata.mybatipse.cache.JavaMapperCache;
import net.harawata.mybatipse.mybatis.ConfigRegistry;
import net.harawata.mybatipse.mybatis.MapperNamespaceCache;
import net.harawata.mybatipse.mybatis.TypeAliasCache;

/**
 * @author Iwao AVE!
 */
public class MybatipseResourceChangeListener implements IResourceChangeListener
{
	public void resourceChanged(IResourceChangeEvent event)
	{
		if (event.getType() == IResourceChangeEvent.PRE_BUILD
			&& event.getBuildKind() == IncrementalProjectBuilder.CLEAN_BUILD)
		{
			Object source = event.getSource();
			if (source instanceof IWorkspace)
			{
				ConfigRegistry.getInstance().clear();
				MapperNamespaceCache.getInstance().clear();
				BeanPropertyCache.clearBeanPropertyCache();
				JavaMapperCache.getInstance().clear();
			}
			else if (source instanceof IProject)
			{
				IProject project = (IProject)source;
				ConfigRegistry.getInstance().remove(project);
				MapperNamespaceCache.getInstance().remove(project);
				BeanPropertyCache.clearBeanPropertyCache(project);
				JavaMapperCache.getInstance().remove(project.getName());
			}
		}
		else if (event.getType() != IResourceChangeEvent.POST_CHANGE)
			return;

		IResourceDelta delta = event.getDelta();
		if (delta.getKind() == IResourceDelta.CHANGED
			&& (delta.getFlags() == IResourceDelta.ENCODING
				|| delta.getFlags() == IResourceDelta.MARKERS))
			return;

		IResourceDeltaVisitor visitor = new IResourceDeltaVisitor()
		{
			public boolean visit(IResourceDelta delta)
			{
				IResource resource = delta.getResource();
				if (resource.isDerived())
					return false;

				if (resource.getType() == IResource.FILE)
				{
					if (delta.getKind() == IResourceDelta.CHANGED
						&& (delta.getFlags() == IResourceDelta.ENCODING
							|| delta.getFlags() == IResourceDelta.MARKERS))
						return false;

					IProject project = resource.getProject();
					IFile file = (IFile)resource;
					if (!file.exists())
						return false;
					if ("xml".equals(file.getFileExtension()))
					{
						onXmlChange(delta, resource, project, file);
						return true;
					}
					else if ("java".equals(file.getFileExtension()))
					{
						onJavaChange(delta, project, file);
						return true;
					}
				}
				else if (resource.getType() == IResource.PROJECT)
				{
					if (delta.getKind() == IResourceDelta.REMOVED)
					{
						ConfigRegistry.getInstance().remove((IProject)resource);
						MapperNamespaceCache.getInstance().remove((IProject)resource);
					}
				}
				return true;
			}

			protected void onJavaChange(IResourceDelta delta, IProject project, IFile file)
			{
				ICompilationUnit compilationUnit = (ICompilationUnit)JavaCore.create(file);
				String elementName = compilationUnit.getElementName();
				String simpleTypeName = elementName.substring(0, elementName.length() - 5);
				IType type = compilationUnit.getType(simpleTypeName);
				IJavaProject javaProject = type.getJavaProject();
				if (javaProject == null)
				{
					return;
				}
				String qualifiedName = type.getFullyQualifiedName();

				BeanPropertyCache.clearBeanPropertyCache(project, qualifiedName);
				JavaMapperCache.getInstance().remove(javaProject, qualifiedName);

				if (SupertypeHierarchyCache.getInstance().isSubtype(type,
					MybatipseConstants.GUICE_MYBATIS_MODULE))
				{
					TypeAliasCache.getInstance().remove(project);
				}
				else if (delta.getKind() == IResourceDelta.REMOVED)
				{
					TypeAliasCache.getInstance().removeType(project.getName(), qualifiedName);
				}
				else
				{
					if (TypeAliasCache.getInstance().isInPackage(project.getName(), qualifiedName))
					{
						TypeAliasCache.getInstance().put(project.getName(), type, simpleTypeName);
					}
				}
			}

			protected void onXmlChange(IResourceDelta delta, IResource resource, IProject project,
				IFile file)
			{
				if (delta.getKind() == IResourceDelta.REMOVED || !file.exists())
				{
					// Cannot get content-type. Try removing the cache anyway.
					ConfigRegistry.getInstance().remove(project, file);
					MapperNamespaceCache.getInstance().remove(project.getName(), file);
				}
				else
				{
					try
					{
						IContentDescription contentDesc = file.getContentDescription();
						if (contentDesc != null)
						{
							IContentType contentType = contentDesc.getContentType();
							if (contentType != null)
							{
								if (contentType.isKindOf(configContentType)
									|| contentType.isKindOf(springConfigContentType))
								{
									// In case there are multiple config files in the project,
									// just remove the currently registered config.
									ConfigRegistry.getInstance().remove(project);
								}
								else if (contentType.isKindOf(mapperContentType))
								{
									MapperNamespaceCache.getInstance().put(project.getName(), file);
								}
							}
						}
					}
					catch (CoreException e)
					{
						Activator.log(Status.ERROR, e.getMessage(), e);
					}
				}
			}
		};
		try
		{
			delta.accept(visitor);
		}
		catch (CoreException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
	}
}
