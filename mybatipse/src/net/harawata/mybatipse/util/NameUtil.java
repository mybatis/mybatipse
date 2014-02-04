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

	private NameUtil()
	{
	}
}
