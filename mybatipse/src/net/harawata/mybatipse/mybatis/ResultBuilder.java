/*-******************************************************************************
 * Copyright (c) 2018 Sc122.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Ken Davidson - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.mybatis;

import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;

public class ResultBuilder
{
	enum JpaAnnotation
	{
		Id,
		Column,
		ManyToMany,
		OneToMany,
		ManyToOne,
		OneToOne
	}

	private String property;

	private String column;

	private Map<String, Annotation> annotations;

	public static ResultBuilder create(String property)
	{
		return new ResultBuilder(property);
	}

	private ResultBuilder(String property)
	{
		this(property, null);
	}

	private ResultBuilder(String property, Map<String, Annotation> annotations)
	{
		this.column = this.property = property;
		this.annotations = annotations;
	}

	public ResultBuilder annotations(Map<String, Annotation> annotations)
	{
		this.annotations = annotations;
		return this;
	}

	public ResultBuilder column(String column)
	{
		this.column = column;
		return this;
	}

	public String build()
	{
		StringBuilder result = new StringBuilder().append("<result property=\"")
			.append(property)
			.append("\" column=\"")
			.append(columnName())
			.append("\" />\n");
		return result.toString();
	}

	private String columnName()
	{
		String colName = column;

		if (annotations != null && annotations.containsKey(JpaAnnotation.Column.name()))
		{
			NormalAnnotation anno = (NormalAnnotation)annotations.get(JpaAnnotation.Column.name());
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
