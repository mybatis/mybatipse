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

import static net.harawata.mybatipse.MybatipseConstants.*;

import java.text.MessageFormat;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.wst.validation.ValidationFramework;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.bean.BeanPropertyCache;
import net.harawata.mybatipse.mybatis.ConfigRegistry;
import net.harawata.mybatipse.mybatis.MapperNamespaceCache;
import net.harawata.mybatipse.mybatis.TypeAliasCache;
import net.harawata.mybatipse.mybatis.XmlValidator;

/**
 * @author Iwao AVE!
 */
public class MybatipseIncrementalBuilder extends IncrementalProjectBuilder
{
	public static final String BUILDER_ID = "net.harawata.mybatipse.MapperValidationBuilder";

	private int currentWork;

	@Override
	protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor)
		throws CoreException
	{
		// On full build, validation will be performed by validation builder.
		if (kind != FULL_BUILD)
		{
			// Mybatipse needs to validate all the mapper files, basically.
			validateAllMappers(monitor);
		}
		return null;
	}

	private void validateAllMappers(final IProgressMonitor monitor)
	{
		final int totalWork = countResources();
		monitor.beginTask("Mybatipse validation", totalWork);
		currentWork = 1;

		try
		{
			getProject().accept(new IResourceProxyVisitor()
			{
				private MessageFormat pattern = new MessageFormat("Validating {0}... ({1}/{2})");

				@Override
				public boolean visit(IResourceProxy proxy) throws CoreException
				{
					if (monitor.isCanceled())
					{
						forgetLastBuiltState();
						throw new OperationCanceledException();
					}

					monitor.subTask(pattern.format(new Object[]{
						proxy.getName(), currentWork, totalWork
					}));

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
								|| contentType.isKindOf(mapperContentType)))
							{
								ValidationFramework.getDefault().validate(file, monitor);
							}
						}
					}

					monitor.worked(1);
					currentWork++;
					return true;
				}
			}, IContainer.NONE);
		}
		catch (CoreException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		finally
		{
			monitor.done();
		}
	}

	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException
	{
		try
		{
			IProject project = getProject();
			project.accept(new IResourceProxyVisitor()
			{
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
								file.deleteMarkers(XmlValidator.MARKER_ID, false, IResource.DEPTH_ZERO);
							}
						}
					}
					return true;
				}
			}, IContainer.NONE);

			TypeAliasCache.getInstance().remove(project);
			BeanPropertyCache.clearBeanPropertyCache(project);
			MapperNamespaceCache.getInstance().remove(project);
			ConfigRegistry.getInstance().remove(project);
		}
		catch (CoreException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
	}

	private int countResources()
	{
		try
		{
			CountResourceVisitor visitor = new CountResourceVisitor();
			getProject().accept(visitor, IContainer.NONE);
			return visitor.count;
		}
		catch (CoreException e)
		{
			Activator.log(Status.WARNING, e.getMessage(), e);
			return IProgressMonitor.UNKNOWN;
		}
	}

	private class CountResourceVisitor implements IResourceProxyVisitor
	{
		int count;

		@Override
		public boolean visit(IResourceProxy proxy) throws CoreException
		{
			if (proxy.isDerived())
				return false;
			count++;
			return true;
		}
	}
}
