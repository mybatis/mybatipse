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

package net.harawata.mybatipse.refactoring.wizard;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import net.harawata.mybatipse.refactoring.ElementRenameInfo;

/**
 * @author Iwao AVE!
 */
public class ElementRenameWizardPage extends UserInputWizardPage
{
	private final ElementRenameInfo info;

	private Text txtNewName;

	public ElementRenameWizardPage(ElementRenameInfo info)
	{
		super(ElementRenameWizardPage.class.getName());
		this.info = info;
	}

	@Override
	public void createControl(Composite parent)
	{
		Composite rootComposite = createRootComposite(parent);
		setControl(rootComposite);

		createLblNewName(rootComposite);
		createTxtNewName(rootComposite);

		validate();
	}

	private Composite createRootComposite(final Composite parent)
	{
		Composite result = new Composite(parent, SWT.NONE);
		GridLayout gridLayout = new GridLayout(2, false);
		gridLayout.marginWidth = 10;
		gridLayout.marginHeight = 10;
		result.setLayout(gridLayout);
		initializeDialogUnits(result);
		Dialog.applyDialogFont(result);
		return result;
	}

	private void createLblNewName(final Composite composite)
	{
		Label lblNewName = new Label(composite, SWT.NONE);
		lblNewName.setText("New ID");
	}

	private void createTxtNewName(Composite composite)
	{
		txtNewName = new Text(composite, SWT.BORDER);
		txtNewName.setText(info.getOldId());
		txtNewName.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		txtNewName.selectAll();
		txtNewName.addKeyListener(new KeyAdapter()
		{
			public void keyReleased(final KeyEvent e)
			{
				info.setNewId(txtNewName.getText());
				validate();
			}
		});
	}

	private void validate()
	{
		String txt = txtNewName.getText();
		setPageComplete(txt.length() > 0 && !txt.equals(info.getOldId()));
	}

}
