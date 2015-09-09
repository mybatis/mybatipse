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

import java.util.Map;

/**
 * @author Iwao AVE!
 */
public class BeanPropertyInfo
{
	private Map<String, String> readableFields;

	private Map<String, String> writableFields;

	public BeanPropertyInfo(
		Map<String, String> readableFields,
		Map<String, String> writableFields)
	{
		super();
		this.readableFields = readableFields;
		this.writableFields = writableFields;
	}

	public Map<String, String> getReadableFields()
	{
		return readableFields;
	}

	public Map<String, String> getWritableFields()
	{
		return writableFields;
	}
}
