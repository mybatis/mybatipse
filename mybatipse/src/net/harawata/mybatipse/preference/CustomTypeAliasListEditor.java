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

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.preference.ListEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * @author Iwao AVE!
 */
public class CustomTypeAliasListEditor extends ListEditor
{
	protected CustomTypeAliasListEditor()
	{
	}

	public CustomTypeAliasListEditor(String name, String labelText, Composite parent)
	{
		init(name, labelText);
		createControl(parent);
	}

	@Override
	protected String createList(String[] items)
	{
		StringBuilder sb = new StringBuilder();
		for (String item : items)
		{
			sb.append(item).append('\t');
		}
		return sb.toString();
	}

	@Override
	protected String getNewInputObject()
	{
		NewTypeAliasDialog dialog = new NewTypeAliasDialog(getShell());
		if (Dialog.OK == dialog.open())
		{
			return dialog.getEntry();
		}
		return null;
	}

	@Override
	protected String[] parseString(String stringList)
	{
		return stringList.split("\t");
	}

	public void setItemsAsString(String str)
	{
		getList().setItems(parseString(str));
	}

	public String getItemsAsString()
	{
		return createList(getList().getItems());
	}

	class NewTypeAliasDialog extends TitleAreaDialog
	{
		private Text entryText;

		private String entry;

		public NewTypeAliasDialog(Shell parentShell)
		{
			super(parentShell);
		}

		@Override
		protected Control createDialogArea(Composite parent)
		{
			Composite area = (Composite)super.createDialogArea(parent);
			Composite container = new Composite(area, SWT.NONE);
			container.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, true, true));
			GridLayout layout = new GridLayout(2, false);
			container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			container.setLayout(layout);
			createTypeAliasEntry(container);
			setTitle("Enter fully qualified name of a package or a class.");
			setMessage("To assign a custom alias for a class, append the alias name with prefix ':'\n"
				+ " (e.g. com.domain.PersonBean:Person).");
			return area;
		}

		@Override
		protected void okPressed()
		{
			entry = entryText.getText();
			super.okPressed();
		}

		@Override
		protected boolean isResizable()
		{
			return true;
		}

		private void createTypeAliasEntry(Composite container)
		{
			Label entryLabel = new Label(container, SWT.NONE);
			entryLabel.setText("Package or Class");

			GridData entryData = new GridData();
			entryData.grabExcessHorizontalSpace = true;
			entryData.horizontalAlignment = GridData.FILL;

			entryText = new Text(container, SWT.BORDER);
			entryText.setLayoutData(entryData);
		}

		public String getEntry()
		{
			return entry;
		}
	}
}
