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

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

import net.harawata.mybatipse.Activator;

/**
 * @author Iwao AVE!
 */
public class ToXmlHyperlink implements IHyperlink
{
	private ITextViewer textViewer;

	private IFile file;

	private IRegion srcRegion;

	private String linkLabel;

	private IRegion destRegion;

	public ToXmlHyperlink(
		ITextViewer textViewer,
		IRegion srcRegion,
		String linkLabel,
		IRegion destRegion)
	{
		super();
		this.textViewer = textViewer;
		this.srcRegion = srcRegion;
		this.linkLabel = linkLabel;
		this.destRegion = destRegion;
	}

	public ToXmlHyperlink(IFile file, IRegion srcRegion, String linkLabel, IRegion destRegion)
	{
		this.file = file;
		this.srcRegion = srcRegion;
		this.linkLabel = linkLabel;
		this.destRegion = destRegion;
	}

	public IRegion getHyperlinkRegion()
	{
		return srcRegion;
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
		if (file == null)
		{
			textViewer.setSelectedRange(destRegion.getOffset(), destRegion.getLength());
			textViewer.revealRange(destRegion.getOffset(), destRegion.getLength());
		}
		else
		{
			try
			{
				IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				if (window != null)
				{
					IEditorPart editorPart = IDE.openEditor(window.getActivePage(), file);
					if (editorPart instanceof MultiPageEditorPart)
					{
						MultiPageEditorPart multiPageEditorPart = (MultiPageEditorPart)editorPart;
						IEditorPart[] editors = multiPageEditorPart
							.findEditors(editorPart.getEditorInput());
						if (editors.length == 1 && editors[0] instanceof ITextEditor)
						{
							((ITextEditor)editors[0]).selectAndReveal(destRegion.getOffset(),
								destRegion.getLength());
						}
					}
				}
			}
			catch (Exception e)
			{
				Activator.log(Status.WARNING, e.getMessage(), e);
			}
		}
	}
}
