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

package net.harawata.mybatipse.hyperlink;

import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.IHyperlink;

import net.harawata.mybatipse.Activator;

/**
 * @author Iwao AVE!
 */
public class ToJavaHyperlink implements IHyperlink
{
	private IJavaElement type;

	private IRegion region;

	private String linkLabel;

	public ToJavaHyperlink(IJavaElement type, IRegion region, String linkLabel)
	{
		super();
		this.type = type;
		this.region = region;
		this.linkLabel = linkLabel;
	}

	public IRegion getHyperlinkRegion()
	{
		return region;
	}

	public String getTypeLabel()
	{
		return linkLabel;
	}

	public String getHyperlinkText()
	{
		return linkLabel;
	}

	public void open()
	{
		try
		{
			JavaUI.openInEditor(type);
		}
		catch (Exception e)
		{
			Activator.log(Status.WARNING,
				"Failed to open Java editor for type: " + type.getElementName(), e);
		}
	}
}
