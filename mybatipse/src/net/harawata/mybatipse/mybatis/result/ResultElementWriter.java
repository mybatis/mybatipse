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
 * Writes default &lt;result property="" column="" /&gt; element. Where property and column
 * match based on field name.
 * 
 * @author kenjdavidson
 */
public class ResultElementWriter implements ResultMapElementWriter
{
	BeanPropertyInfo bean;

	String property;

	public ResultElementWriter(BeanPropertyInfo bean, String property)
	{
		this.bean = bean;
		this.property = property;
	}

	@Override
	public String writeElement()
	{
		return String.format("<result property=\"%s\" column=\"%s\"/>", property, property);
	}

}
