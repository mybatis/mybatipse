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

package net.harawata.mybatipse.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Iwao AVE!
 */
public class NameUtil
{
	/**
	 * @param packageName
	 * @param simpleTypeName
	 * @param enclosingTypeNames
	 * @param useDollarForInnerClassSeparator Simply put, MyBatis uses '$' and Eclipse uses '.'.
	 *          In other words, pass <code>true</code> for auto-completion and <code>false</code>
	 *          for validation.
	 * @return
	 */
	public static String buildQualifiedName(char[] packageName, char[] simpleTypeName,
		char[][] enclosingTypeNames, boolean useDollarForInnerClassSeparator)
	{
		final char innerClassSeparator = useDollarForInnerClassSeparator ? '$' : '.';
		StringBuilder typeFqn = new StringBuilder().append(packageName).append('.');
		for (char[] enclosingTypeName : enclosingTypeNames)
		{
			typeFqn.append(enclosingTypeName).append(innerClassSeparator);
		}
		typeFqn.append(simpleTypeName).toString();
		return typeFqn.toString();
	}

	public static String stripTypeArguments(String src)
	{
		int idx = src.indexOf('<');
		return idx == -1 ? src : src.substring(0, idx);
	}

	public static List<String> extractTypeParams(String src)
	{
		int paramPartStart = src.indexOf('<');
		int paramPartEnd = src.lastIndexOf('>');
		if (paramPartStart == -1 || paramPartEnd == -1 || paramPartEnd - paramPartStart < 2)
			return Collections.emptyList();

		List<String> result = new ArrayList<String>();
		int nestedParamLevel = 0;
		int markStart = paramPartStart + 1;
		for (int i = paramPartStart + 1; i < paramPartEnd; i++)
		{
			char c = src.charAt(i);
			if (nestedParamLevel == 0 && c == ',')
			{
				result.add(src.substring(markStart, i));
				markStart = i + 1;
			}
			else if (c == '<')
			{
				nestedParamLevel++;
			}
			else if (c == '>')
			{
				nestedParamLevel--;
			}
		}
		if (markStart < paramPartEnd)
			result.add(src.substring(markStart, paramPartEnd));
		return result;
	}

	public static String resolveTypeParam(List<String> actualTypeParams, List<String> typeParams,
		String typeParam)
	{
		int typeParamIdx = typeParams.indexOf(typeParam);
		return typeParamIdx == -1 ? typeParam : actualTypeParams.get(typeParamIdx);
	}

	private NameUtil()
	{
	}
}
