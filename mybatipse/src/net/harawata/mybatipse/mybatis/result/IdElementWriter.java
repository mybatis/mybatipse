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
 * Writes &lt;id /&gt; element.
 * 
 * @author kenjdavidson
 */
public class IdElementWriter extends JPAResultElementWriter
{

	/**
	 * @param bean
	 * @param property
	 */
	public IdElementWriter(BeanPropertyInfo bean, String property)
	{
		super(bean, property);
	}

	@Override
	public String writeElement()
	{
		return String.format("<id property=\"%s\" column=\"%s\"/>", property, columnName());
	}

}
