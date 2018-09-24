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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.harawata.mybatipse.bean.BeanPropertyInfo;

public class ResultMapContentBuilder
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

	private BeanPropertyInfo bean;

	private NodeList properties;

	public static final ResultMapContentBuilder create(BeanPropertyInfo bean)
	{
		return create(bean, null);
	}

	public static final ResultMapContentBuilder create(BeanPropertyInfo bean, NodeList nodes)
	{
		return new ResultMapContentBuilder(bean, nodes);
	}

	private ResultMapContentBuilder(BeanPropertyInfo bean, NodeList existing)
	{
		this.bean = bean;
		this.properties = existing;
	}

	public String build()
	{
		StringBuilder result = new StringBuilder();
		Set<String> existingNodes = validateCurrentNodes();
		for (Entry<String, String> prop : bean.getWritableFields().entrySet())
		{
			if (!existingNodes.contains(prop.getKey()))
			{
				result.append(buildResultItem(prop.getKey()));
			}
		}
		return result.toString();
	}

	private String buildResultItem(String property)
	{
		StringBuilder result = new StringBuilder().append("<")
			.append(resultType(property))
			.append(" property=\"")
			.append(property)
			.append("\" column=\"")
			.append(columnName(property))
			.append("\" />\n");
		return result.toString();
	}

	private Set<String> validateCurrentNodes()
	{
		Set<String> existing = new HashSet<>();
		for (int i = 0; i < properties.getLength(); i++)
		{
			Node node = properties.item(i);
			existing.add(node.getNodeValue());
		}
		return existing;
	}

	private String resultType(String name)
	{
		String type = "result";

		Map<String, Annotation> annotations = bean.getFieldAnnotations().get(name);
		if (annotations != null && annotations.containsKey(JpaAnnotation.Id.name()))
		{
			type = "id";
		}

		return type;
	}

	private String columnName(String name)
	{
		String colName = name;

		Map<String, Annotation> annotations = bean.getFieldAnnotations().get(name);
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
