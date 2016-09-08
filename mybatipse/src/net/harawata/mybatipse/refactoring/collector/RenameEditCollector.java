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

package net.harawata.mybatipse.refactoring.collector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.text.edits.ReplaceEdit;

import net.harawata.mybatipse.refactoring.ElementRenameInfo;

/**
 * @author Iwao AVE!
 */
public abstract class RenameEditCollector
{
	protected final ElementRenameInfo info;

	protected Map<IFile, List<ReplaceEdit>> editsPerFiles;

	protected IProgressMonitor monitor;

	public RenameEditCollector(ElementRenameInfo info)
	{
		super();
		this.info = info;
	}

	protected List<ReplaceEdit> getEdits(final IFile file)
	{
		List<ReplaceEdit> edits = editsPerFiles.get(file);
		if (edits == null)
		{
			edits = new ArrayList<ReplaceEdit>();
			editsPerFiles.put(file, edits);
		}
		return edits;
	}

	public abstract RefactoringStatus collect(Map<IFile, List<ReplaceEdit>> editsPerFiles,
		IProgressMonitor monitor);
}
