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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.search.core.text.TextSearchMatchAccess;
import org.eclipse.search.core.text.TextSearchRequestor;
import org.eclipse.text.edits.ReplaceEdit;

/**
 * @author Iwao AVE!
 */
public class XmlAttrEditRequestor extends TextSearchRequestor
{
	private final List<String> attrNames;

	private final String newValue;

	private final Map<IFile, List<ReplaceEdit>> editsPerFiles;

	public XmlAttrEditRequestor(
		String attrName,
		String newValue,
		Map<IFile, List<ReplaceEdit>> editsPerFiles)
	{
		this(Arrays.asList(attrName), newValue, editsPerFiles);
	}

	public XmlAttrEditRequestor(
		List<String> attrNames,
		String newValue,
		Map<IFile, List<ReplaceEdit>> editsPerFiles)
	{
		super();
		this.attrNames = attrNames;
		this.newValue = newValue;
		this.editsPerFiles = editsPerFiles;
	}

	@Override
	public boolean acceptPatternMatch(TextSearchMatchAccess matchAccess) throws CoreException
	{
		int matchOffset = matchAccess.getMatchOffset();
		if (isActualMatch(matchAccess, matchOffset))
		{
			List<ReplaceEdit> edits = getEdits(matchAccess.getFile());
			edits.add(
				new ReplaceEdit(matchAccess.getMatchOffset(), matchAccess.getMatchLength(), newValue));
		}
		return true;
	}

	protected boolean isActualMatch(TextSearchMatchAccess matchAccess, int matchOffset)
	{
		if (!newValue.startsWith("\"") && (",\" "
			.indexOf(matchAccess.getFileContentChar(matchOffset + matchAccess.getMatchLength())) == -1
			|| ",\" ".indexOf(matchAccess.getFileContentChar(matchOffset - 1)) == -1))
			return false;

		StringBuilder buffer = new StringBuilder();
		boolean nameRegion = false;
		for (int i = 1; i < matchOffset; i++)
		{
			char c = matchAccess.getFileContentChar(matchOffset - i);
			if (!nameRegion)
			{
				if (c == '=')
					nameRegion = true;
			}
			else if (Character.isWhitespace(c) && buffer.length() > 0)
			{
				break;
			}
			else
			{
				buffer.insert(0, c);
			}
		}
		return attrNames.contains(buffer.toString());
	}

	private List<ReplaceEdit> getEdits(final IFile mapperXml)
	{
		List<ReplaceEdit> edits = editsPerFiles.get(mapperXml);
		if (edits == null)
		{
			edits = new ArrayList<ReplaceEdit>();
			editsPerFiles.put(mapperXml, edits);
		}
		return edits;
	}
}
