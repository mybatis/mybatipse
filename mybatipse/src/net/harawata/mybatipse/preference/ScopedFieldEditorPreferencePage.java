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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.preference.FieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IWorkbenchPropertyPage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

/**
 * @see https://www.eclipse.org/articles/Article-Mutatis-mutandis/overlay-pages.html
 * @author Iwao AVE!
 */
public abstract class ScopedFieldEditorPreferencePage extends FieldEditorPreferencePage
	implements IWorkbenchPropertyPage
{
	public static final String USE_PROJECT_SETTINGS = "useProjectSettings";

	private Button useWorkspaceSettingsButton;

	private Button useProjectSettingsButton;

	private List<FieldEditor> editors = new ArrayList<FieldEditor>();

	private IAdaptable element;

	protected Control createContents(Composite parent)
	{
		if (isProjectPropertyPage())
		{
			createProjectSpecificControls(parent);
		}
		return super.createContents(parent);
	}

	private void createProjectSpecificControls(Composite parent)
	{
		Composite comp = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		comp.setLayout(layout);
		comp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		Composite radioGroup = new Composite(comp, SWT.NONE);
		radioGroup.setLayout(new GridLayout());
		radioGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		useWorkspaceSettingsButton = createRadioButton(radioGroup, "Use workspace settings");
		useProjectSettingsButton = createRadioButton(radioGroup, "Use project settings");

		if (getPreferenceStore().getBoolean(USE_PROJECT_SETTINGS))
		{
			useProjectSettingsButton.setSelection(true);
		}
		else
		{
			useWorkspaceSettingsButton.setSelection(true);
		}
	}

	private Button createRadioButton(Composite parent, String label)
	{
		final Button button = new Button(parent, SWT.RADIO);
		button.setText(label);
		button.addSelectionListener(new SelectionAdapter()
		{
			public void widgetSelected(SelectionEvent e)
			{
				updateFieldEditors();
			}
		});
		return button;
	}

	private void updateFieldEditors()
	{
		boolean enabled = useProjectSettingsButton.getSelection();
		updateFieldEditors(enabled);
	}

	protected void updateFieldEditors(boolean enabled)
	{
		Composite parent = getFieldEditorParent();
		for (FieldEditor editor : editors)
		{
			editor.setEnabled(enabled, parent);
		}
	}

	@Override
	public void createControl(Composite parent)
	{
		super.createControl(parent);
		if (isProjectPropertyPage())
		{
			updateFieldEditors();
		}
	}

	@Override
	public boolean performOk()
	{
		boolean result = super.performOk();
		if (result && isProjectPropertyPage())
		{
			// try
			// {
			ScopedPreferenceStore store = (ScopedPreferenceStore)getPreferenceStore();
			store.setValue(USE_PROJECT_SETTINGS, useProjectSettingsButton.getSelection());
			// store.save();
			// }
			// catch (IOException e)
			// {
			// e.printStackTrace();
			// }
		}
		return result;
	}

	@Override
	protected void performDefaults()
	{
		if (isProjectPropertyPage())
		{
			useWorkspaceSettingsButton.setSelection(true);
			useProjectSettingsButton.setSelection(false);
			updateFieldEditors();
		}
		super.performDefaults();
	}

	@Override
	protected void addField(FieldEditor editor)
	{
		editors.add(editor);
		super.addField(editor);
	}

	@Override
	public IAdaptable getElement()
	{
		return element;
	}

	@Override
	public void setElement(IAdaptable element)
	{
		this.element = element;
		setPreferenceStore(
			new ScopedPreferenceStore(new ProjectScope(getProject()), getPluginId()));
	}

	protected IProject getProject()
	{
		return element instanceof IJavaProject ? ((IJavaProject)element).getProject()
			: (IProject)element;
	}

	public boolean isProjectPropertyPage()
	{
		return element != null;
	}

	public ScopedFieldEditorPreferencePage(int style)
	{
		super(style);
	}

	public ScopedFieldEditorPreferencePage(String title, int style)
	{
		super(title, style);
	}

	protected abstract String getPluginId();
}
