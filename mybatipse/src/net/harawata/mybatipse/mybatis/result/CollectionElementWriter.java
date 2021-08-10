/*-******************************************************************************
 * Copyright (c) 2019 Ken Davidson.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Ken Davidson - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.mybatis.result;

import net.harawata.mybatipse.bean.BeanPropertyInfo;

/**
 * Writes &lt;collection /&gt; element. Collection elements can be configured through either an
 * internal or external resultMap. This writer will need to look at other Annotations such as
 * {@code JoinColumn} to get enough information to build the full association entry.
 * 
 * @author kenjdavidson
 */
public class CollectionElementWriter extends JPAResultElementWriter
{

	/**
	 * @param bean
	 * @param property
	 */
	public CollectionElementWriter(BeanPropertyInfo bean, String property)
	{
		super(bean, property);
	}

	@Override
	public String writeElement()
	{
		String javaType = "", ofType = "";

		// TODO clean this up to manage List/Set/Etc more dynamically
		// For the most part though, this should be a List/Set with single Generic
		String type = bean.getWritableFields().get(property);
		int generic = type.indexOf("<");
		if (generic >= 0)
		{
			javaType = type.substring(0, generic);
			ofType = type.substring(generic + 1, type.length() - 1);
		}

		return String.format("<collection property=\"%s\" javaType=\"%s\" ofType=\"%s\" />",
			property, javaType, ofType);
	}

}
