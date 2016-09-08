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

package net.harawata.mybatipse.refactoring;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

import net.harawata.mybatipse.refactoring.collector.RenameEditCollector;

/**
 * @author Iwao AVE!
 */
public class ElementRenameRefactoring extends Refactoring
{
	protected final RenameEditCollector collector;

	protected final Map<IFile, List<ReplaceEdit>> editsPerFiles = new HashMap<IFile, List<ReplaceEdit>>();

	public ElementRenameRefactoring(RenameEditCollector collector)
	{
		super();
		this.collector = collector;
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm)
		throws CoreException, OperationCanceledException
	{
		return new RefactoringStatus();
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm)
		throws CoreException, OperationCanceledException
	{
		try
		{
			return collector.collect(editsPerFiles, pm);
		}
		finally
		{
			pm.done();
		}
	}

	@Override
	public Change createChange(IProgressMonitor pm)
		throws CoreException, OperationCanceledException
	{
		if (editsPerFiles.isEmpty())
			return null;

		pm.beginTask("Searching for references.", editsPerFiles.size());
		final CompositeChange changes = new CompositeChange("Update mapper element ID");
		int workCount = 0;
		for (Entry<IFile, List<ReplaceEdit>> editsPerFile : editsPerFiles.entrySet())
		{
			IFile file = editsPerFile.getKey();
			TextChange change = new TextFileChange(file.getName(), file);
			TextEdit editRoot = new MultiTextEdit();
			change.setEdit(editRoot);
			for (ReplaceEdit edit : editsPerFile.getValue())
			{
				editRoot.addChild(edit);
			}
			changes.add(change);
			pm.worked(++workCount);
		}
		pm.done();
		return changes;
	}

	@Override
	public String getName()
	{
		return "Rename MyBatis element";
	}
}
