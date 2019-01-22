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
 * Writes &lt;result /&gt; element.
 * 
 * @author kenjdavidson
 */
public class ColumnElementWriter extends JPAResultElementWriter
{

	/**
	 * @param bean
	 * @param property
	 */
	public ColumnElementWriter(BeanPropertyInfo bean, String property)
	{
		super(bean, property);
		// TODO Auto-generated constructor stub
	}

	@Override
	public String writeElement()
	{
		return String.format("<result property=\"%s\" column=\"%s\"/>", property, columnName());
	}

}
