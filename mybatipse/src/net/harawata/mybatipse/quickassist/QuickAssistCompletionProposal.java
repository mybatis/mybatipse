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

package net.harawata.mybatipse.quickassist;

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

import net.harawata.mybatipse.Activator;

abstract class QuickAssistCompletionProposal implements IJavaCompletionProposal
{
	private String displayString;

	QuickAssistCompletionProposal(String displayString)
	{
		super();
		this.displayString = displayString;
	}

	@Override
	public Point getSelection(IDocument document)
	{
		return null;
	}

	@Override
	public String getAdditionalProposalInfo()
	{
		return null;
	}

	@Override
	public String getDisplayString()
	{
		return displayString;
	}

	@Override
	public Image getImage()
	{
		return Activator.getIcon();
	}

	@Override
	public IContextInformation getContextInformation()
	{
		return null;
	}

	@Override
	public int getRelevance()
	{
		return 500;
	}
}
