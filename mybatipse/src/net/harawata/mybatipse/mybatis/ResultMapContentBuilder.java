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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jdt.core.dom.Annotation;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.harawata.mybatipse.bean.BeanPropertyInfo;
import net.harawata.mybatipse.mybatis.result.AssociationElementWriter;
import net.harawata.mybatipse.mybatis.result.CollectionElementWriter;
import net.harawata.mybatipse.mybatis.result.ColumnElementWriter;
import net.harawata.mybatipse.mybatis.result.IdElementWriter;
import net.harawata.mybatipse.mybatis.result.ResultElementWriter;
import net.harawata.mybatipse.mybatis.result.ResultMapElementWriter;

public class ResultMapContentBuilder
{

	/**
	 * Provides a quick and dirty method of getting the appropriate resultMap element name based
	 * on what {@link Annotation}(s) might be available.
	 * 
	 * @author kenjdavidson
	 */
	enum AnnotationMapping
	{
		None,
		Id,
		Column,
		ManyToMany,
		OneToMany,
		ManyToOne,
		OneToOne;
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
				ResultMapElementWriter w = getElementWriter(bean, prop.getKey());
				result.append(w.writeElement()).append("\n");
			}
		}
		return result.toString();
	}

	private ResultMapElementWriter getElementWriter(BeanPropertyInfo bean, String fieldName)
	{
		ResultMapElementWriter writer = null;

		Map<String, Annotation> annotations = bean.getFieldAnnotations().get(fieldName);

		if (annotations.containsKey(AnnotationMapping.Id.name()))
		{
			writer = new IdElementWriter(bean, fieldName);
		}
		else if (annotations.containsKey(AnnotationMapping.Column.name()))
		{
			writer = new ColumnElementWriter(bean, fieldName);
		}
		else if (annotations.containsKey(AnnotationMapping.OneToMany.name())
			|| annotations.containsKey(AnnotationMapping.ManyToMany.name()))
		{
			writer = new CollectionElementWriter(bean, fieldName);
		}
		else if (annotations.containsKey(AnnotationMapping.ManyToOne.name())
			|| annotations.containsKey(AnnotationMapping.OneToOne.name()))
		{
			writer = new AssociationElementWriter(bean, fieldName);
		}
		else
		{
			writer = new ResultElementWriter(bean, fieldName);
		}

		return writer;
	}

	private Set<String> validateCurrentNodes()
	{
		Set<String> existing = new HashSet<>();
		if (properties != null)
		{
			for (int i = 0; i < properties.getLength(); i++)
			{
				Node node = properties.item(i);
				existing.add(node.getNodeValue());
			}
		}
		return existing;
	}

}
