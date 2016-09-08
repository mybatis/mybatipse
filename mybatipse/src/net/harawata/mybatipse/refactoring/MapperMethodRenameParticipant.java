/*-******************************************************************************
 * Copyright (c) 2016 Iwao AVE!.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    ave - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.refactoring;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.refactoring.collector.StatementRenameEditCollector;

/**
 * @author Iwao AVE!
 */
public class MapperMethodRenameParticipant extends RenameParticipant
{
	private IMethod method;

	@Override
	public RefactoringStatus checkConditions(IProgressMonitor monitor,
		CheckConditionsContext context) throws OperationCanceledException
	{
		return new RefactoringStatus();
	}

	@Override
	public Change createChange(IProgressMonitor monitor)
		throws CoreException, OperationCanceledException
	{
		if (monitor == null)
		{
			monitor = new NullProgressMonitor();
		}
		monitor.beginTask("Searching mapper statement changes.", 100);

		try
		{
			IType type = method.getDeclaringType();
			try
			{
				if (type.isInterface() && (method.getFlags() & Flags.AccDefaultMethod) == 0)
				{
					final Map<IFile, List<ReplaceEdit>> editsPerFiles = new HashMap<IFile, List<ReplaceEdit>>();
					final IJavaProject project = type.getJavaProject();
					final String namespace = type.getFullyQualifiedName();
					final String oldId = method.getElementName();
					final String newId = getArguments().getNewName();
					ElementRenameInfo info = new ElementRenameInfo();
					info.setProject(project);
					info.setNamespace(namespace);
					info.setOldId(oldId);
					info.setNewId(newId);
					StatementRenameEditCollector collector = new StatementRenameEditCollector(info,
						isSelect());
					collector.collect(editsPerFiles, monitor);
					if (!editsPerFiles.isEmpty())
					{
						final CompositeChange changes = new CompositeChange("Update mapper statement ID");
						for (Entry<IFile, List<ReplaceEdit>> editsPerFile : editsPerFiles.entrySet())
						{
							IFile file = editsPerFile.getKey();
							TextChange change = getTextChange(file);
							if (change == null)
							{
								change = new TextFileChange(file.getName(), file);
								TextEdit editRoot = new MultiTextEdit();
								change.setEdit(editRoot);
								changes.add(change);
							}
							for (ReplaceEdit edit : editsPerFile.getValue())
							{
								change.getEdit().addChild(edit);
							}
						}
						return changes;
					}
				}
			}
			catch (JavaModelException e)
			{
				Activator.log(Status.ERROR, e.getMessage(), e);
			}
		}
		finally
		{
			monitor.done();
		}
		return null;
	}

	protected boolean isSelect() throws JavaModelException
	{
		IAnnotation[] annotations = method.getAnnotations();
		for (IAnnotation annotation : annotations)
		{
			String name = annotation.getElementName();
			if ("Select".equals(name) || "SelectProvider".equals(name))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public String getName()
	{
		return "MapperMethodRenameParticipant";
	}

	@Override
	protected boolean initialize(Object element)
	{
		this.method = (IMethod)element;
		return true;
	}

}
