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

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;

import net.harawata.mybatipse.bean.BeanPropertyInfo;

/**
 * Abstract JPAResultElementWriter provides methods for retrieving JPA Annotation information
 * for resultMap output.
 * 
 * @author kenjdavidson
 */
public abstract class JPAResultElementWriter extends ResultElementWriter
{
	private static final String COLUMN = "Column";

	/**
	 * @param bean
	 * @param property
	 */
	public JPAResultElementWriter(BeanPropertyInfo bean, String property)
	{
		super(bean, property);
	}

	@SuppressWarnings("unchecked")
	protected String columnName()
	{
		String colName = property;

		Map<String, Annotation> annotations = bean.getFieldAnnotations().get(property);
		if (annotations != null && annotations.containsKey(COLUMN))
		{
			NormalAnnotation anno = (NormalAnnotation)annotations.get(COLUMN);
			for (MemberValuePair value : (List<MemberValuePair>)anno.values())
			{
				if ("name".equals(value.getName().getIdentifier()))
				{
					colName = ((StringLiteral)value.getValue()).getLiteralValue();
				}
			}
		}

		return colName;
	}
}
