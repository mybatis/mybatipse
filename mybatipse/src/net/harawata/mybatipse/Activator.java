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

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.osgi.framework.BundleContext;

import net.harawata.mybatipse.mybatis.TypeAliasCache;
import net.harawata.mybatipse.preference.ScopedFieldEditorPreferencePage;

/**
 * @author Iwao AVE!
 */
public class Activator extends AbstractUIPlugin
{
	private static Activator plugin;

	private IResourceChangeListener resourceChangeListener;

	public Activator()
	{
	}

	public void start(BundleContext context) throws Exception
	{
		super.start(context);

		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		resourceChangeListener = new MybatipseResourceChangeListener();
		workspace.addResourceChangeListener(resourceChangeListener,
			IResourceChangeEvent.POST_CHANGE | IResourceChangeEvent.PRE_BUILD);

		plugin = this;
	}

	public void stop(BundleContext context) throws Exception
	{
		plugin = null;

		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		if (workspace != null && resourceChangeListener != null)
		{
			workspace.removeResourceChangeListener(resourceChangeListener);
		}

		super.stop(context);
	}

	public static Activator getDefault()
	{
		return plugin;
	}

	public static IPreferenceStore getPreferenceStore(final IProject project)
	{
		ScopedPreferenceStore store = null;
		if (project != null)
		{
			store = new ScopedPreferenceStore(new ProjectScope(project), PLUGIN_ID);
		}
		if (store == null
			|| !store.getBoolean(ScopedFieldEditorPreferencePage.USE_PROJECT_SETTINGS))
		{
			store = new ScopedPreferenceStore(InstanceScope.INSTANCE, PLUGIN_ID);
		}
		store.addPropertyChangeListener(new IPropertyChangeListener()
		{
			@Override
			public void propertyChange(PropertyChangeEvent event)
			{
				if (project == null)
					TypeAliasCache.getInstance().clear();
				else
					TypeAliasCache.getInstance().remove(project);
			}
		});
		return store;
	}

	public static ImageDescriptor getImageDescriptor(String path)
	{
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}

	public static Image getIcon()
	{
		return getImageDescriptor("/icons/mybatis.png").createImage(); //$NON-NLS-1$
	}

	public static Image getIcon(String path)
	{
		return getImageDescriptor(path).createImage();
	}

	public static void log(Status status)
	{
		if (getDefault() != null && getDefault().getLog() != null)
			getDefault().getLog().log(status);
	}

	public static void log(int severity, String message)
	{
		log(new Status(severity, PLUGIN_ID, message));
	}

	public static void log(int severity, String message, Throwable t)
	{
		log(severity, 0, message, t);
	}

	public static void log(int severity, int code, String message, Throwable t)
	{
		log(new Status(severity, PLUGIN_ID, code, message, t));
	}

	public static boolean openDialog(int kind, String title, String message)
	{
		Shell shell = Display.getDefault().getActiveShell();
		return MessageDialog.open(kind, shell, title, message, SWT.SHEET);
	}
}
