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

package net.harawata.mybatipse;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;

/**
 * @author Iwao AVE!
 */
public class MybatipseConstants
{
	public static final String PLUGIN_ID = "net.harawata.mybatipse"; //$NON-NLS-1$

	public static final String CONTENT_TYPE_CONFIG = "net.harawata.mybatipse.Config"; //$NON-NLS-1$

	public static final String CONTENT_TYPE_MAPPER = "net.harawata.mybatipse.Mapper"; //$NON-NLS-1$

	public static final String CONTENT_TYPE_SPRING_CONFIG = "net.harawata.mybatipse.SpringConfig"; //$NON-NLS-1$

	public static final String PREF_CUSTOM_TYPE_ALIASES = "prefCustomTypeAliases"; //$NON-NLS-1$

	public static final String DEBUG_BEAN_PROPERTY_CACHE = PLUGIN_ID + "/debug/beanPropertyCache";

	public static final IContentType mapperContentType;

	public static final IContentType configContentType;

	public static final IContentType springConfigContentType;

	static
	{
		IContentTypeManager contentTypeManager = Platform.getContentTypeManager();
		mapperContentType = contentTypeManager.getContentType(CONTENT_TYPE_MAPPER);
		configContentType = contentTypeManager.getContentType(CONTENT_TYPE_CONFIG);
		springConfigContentType = contentTypeManager.getContentType(CONTENT_TYPE_SPRING_CONFIG);
	}

	private MybatipseConstants()
	{
	}
}
