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

package net.harawata.mybatipse.bean;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.dom.Annotation;

/**
 * @author Iwao AVE!
 */
public class BeanPropertyInfo
{
	private Map<String, String> readableFields;

	private Map<String, String> writableFields;

	private Map<String, Map<String, Annotation>> fieldAnnotations;

	public BeanPropertyInfo(
		Map<String, String> readableFields,
		Map<String, String> writableFields)
	{
		this(readableFields, writableFields, new HashMap<String, Map<String, Annotation>>());
	}

	public BeanPropertyInfo(
		Map<String, String> readableFields,
		Map<String, String> writableFields,
		Map<String, Map<String, Annotation>> fieldAnnotations)
	{
		super();
		this.readableFields = readableFields;
		this.writableFields = writableFields;
		this.fieldAnnotations = fieldAnnotations;
	}

	public Map<String, String> getReadableFields()
	{
		return readableFields;
	}

	public Map<String, String> getWritableFields()
	{
		return writableFields;
	}

	public Map<String, Map<String, Annotation>> getFieldAnnotations()
	{
		return fieldAnnotations;
	}

}
