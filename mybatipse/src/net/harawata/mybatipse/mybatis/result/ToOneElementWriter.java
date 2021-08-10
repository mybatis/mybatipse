/*-******************************************************************************
 * Copyright (c) 2019 Sc122.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sc122 - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.mybatis.result;

import net.harawata.mybatipse.bean.BeanPropertyInfo;

/**
 * Writes &lt;association /&gt; element. Association elements can be configured through either
 * an internal or external resultMap. This writer will need to look at other Annotations such as
 * {@code JoinColumn} to get enough information to build the full association entry.
 * 
 * @author kenjdavidson
 */
public class ToOneElementWriter extends JPAResultElementWriter
{

	/**
	 * @param bean
	 * @param property
	 */
	public ToOneElementWriter(BeanPropertyInfo bean, String property)
	{
		super(bean, property);
	}

	@Override
	public String writeElement()
	{
		return String.format("<association property=\"%s\" javaType=\"\" />", property,
			columnName());
	}

}
