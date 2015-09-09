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

package net.harawata.mybatipse.preference;

import static net.harawata.mybatipse.MybatipseConstants.*;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

/**
 * @author Iwao AVE!
 */
public class MybatipsePreferencePage extends ScopedFieldEditorPreferencePage
	implements IWorkbenchPreferencePage
{
	private CustomTypeAliasListEditor customTypeAliases;

	public MybatipsePreferencePage()
	{
		super(GRID);
	}

	@Override
	protected IPreferenceStore doGetPreferenceStore()
	{
		return new ScopedPreferenceStore(InstanceScope.INSTANCE, PLUGIN_ID);
	}

	@Override
	public void init(IWorkbench workbench)
	{
		setDescription("MyBatipse Settings");
	}

	@Override
	protected void createFieldEditors()
	{
		Composite parent = getFieldEditorParent();
		customTypeAliases = new CustomTypeAliasListEditor(PREF_CUSTOM_TYPE_ALIASES,
			"Custom type aliases", parent);
		addField(customTypeAliases);
		// if (isProjectPropertyPage())
		// {
		// customTypeAliases.setItemsAsString(getPreferenceStore().getString(
		// PREF_CUSTOM_TYPE_ALIASES));
		// }
	}

	@Override
	public boolean performOk()
	{
		if (isProjectPropertyPage())
		{
			IPreferenceStore store = getPreferenceStore();
			store.setValue(PREF_CUSTOM_TYPE_ALIASES, customTypeAliases.getItemsAsString());
		}
		return super.performOk();
	}

	@Override
	protected String getPluginId()
	{
		return PLUGIN_ID;
	}
}
