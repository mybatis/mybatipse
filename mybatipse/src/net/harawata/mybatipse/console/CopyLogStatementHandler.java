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

package net.harawata.mybatipse.console;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * @author Iwao AVE!
 */
public class CopyLogStatementHandler extends AbstractHandler
{
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException
	{
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof TextSelection)
		{
			try
			{
				String statement = MybatisLogBinder.bind(((TextSelection)selection).getText());
				if (statement != null && statement.length() > 0)
				{
					Clipboard clipboard = new Clipboard(Display.getCurrent());
					clipboard.setContents(new Object[]{
						statement
					}, new Transfer[]{
						TextTransfer.getInstance()
					});
				}
			}
			catch (IllegalArgumentException e)
			{
				Shell shell = Display.getDefault().getActiveShell();
				MessageDialog.openInformation(shell, "Error", e.getMessage());
			}
		}
		return null;
	}
}
