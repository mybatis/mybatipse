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

package net.harawata.mybatipse.mybatis;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import net.harawata.mybatipse.mybatis.TypeAliasMap.TypeAliasInfo;

/**
 * @author Iwao AVE!
 */
public class TypeAliasMap extends ConcurrentHashMap<String, TypeAliasInfo>
{
	private static final long serialVersionUID = -7286252877431348258L;

	public void put(String qualifiedName)
	{
		put(null, qualifiedName);
	}

	public void put(String alias, String qualifiedName)
	{
		if (qualifiedName == null || qualifiedName.length() == 0)
			return;

		if (alias == null || alias.length() == 0)
		{
			int lastDot = qualifiedName.lastIndexOf('.');
			alias = lastDot == -1 ? qualifiedName : qualifiedName.substring(lastDot + 1);
		}

		this.put(alias.toLowerCase(Locale.ENGLISH), new TypeAliasInfo(alias, qualifiedName));
	}

	static class TypeAliasInfo
	{
		private String aliasToInsert;

		private String qualifiedName;

		public String getAliasToInsert()
		{
			return aliasToInsert;
		}

		public void setAliasToInsert(String aliasToInsert)
		{
			this.aliasToInsert = aliasToInsert;
		}

		public String getQualifiedName()
		{
			return qualifiedName;
		}

		public void setQualifiedName(String qualifiedName)
		{
			this.qualifiedName = qualifiedName;
		}

		private TypeAliasInfo(String aliasToInsert, String qualifiedName)
		{
			super();
			this.aliasToInsert = aliasToInsert;
			this.qualifiedName = qualifiedName;
		}

		@Override
		public String toString()
		{
			return "[" + aliasToInsert + " : " + qualifiedName + "]";
		}
	}
}
