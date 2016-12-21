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

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;

/**
 * @author Iwao AVE!
 */
public class MybatipseConstants
{
	public static final String PLUGIN_ID = "net.harawata.mybatipse"; //$NON-NLS-1$

	public static final String CONTENT_TYPE_CONFIG = "net.harawata.mybatipse.config"; //$NON-NLS-1$

	public static final String CONTENT_TYPE_MAPPER = "net.harawata.mybatipse.mapper"; //$NON-NLS-1$

	public static final String CONTENT_TYPE_SPRING_CONFIG = "net.harawata.mybatipse.springConfig"; //$NON-NLS-1$

	public static final String CONTENT_TYPE_SPRING_CONFIG_STS = "com.springsource.sts.config.ui.beanConfigFile"; //$NON-NLS-1$

	public static final String PREF_CUSTOM_TYPE_ALIASES = "prefCustomTypeAliases"; //$NON-NLS-1$

	public static final String DEBUG_BEAN_PROPERTY_CACHE = PLUGIN_ID + "/debug/beanPropertyCache";

	public static final String ANNOTATION_SELECT = "org.apache.ibatis.annotations.Select";

	public static final String ANNOTATION_SELECT_PROVIDER = "org.apache.ibatis.annotations.SelectProvider";

	public static final String ANNOTATION_UPDATE = "org.apache.ibatis.annotations.Update";

	public static final String ANNOTATION_INSERT = "org.apache.ibatis.annotations.Insert";

	public static final String ANNOTATION_DELETE = "org.apache.ibatis.annotations.Delete";

	public static final String ANNOTATION_RESULTS = "org.apache.ibatis.annotations.Results";

	public static final String ANNOTATION_RESULT = "org.apache.ibatis.annotations.Result";

	public static final String ANNOTATION_PARAM = "org.apache.ibatis.annotations.Param";

	public static final String ANNOTATION_RESULT_MAP = "org.apache.ibatis.annotations.ResultMap";

	public static final String ANNOTATION_ONE = "org.apache.ibatis.annotations.One";

	public static final String ANNOTATION_MANY = "org.apache.ibatis.annotations.Many";

	public static final String ANNOTATION_MAP_KEY = "org.apache.ibatis.annotations.MapKey";

	public static final List<String> STATEMENT_ANNOTATIONS = Arrays.asList(ANNOTATION_SELECT,
		ANNOTATION_INSERT, ANNOTATION_UPDATE, ANNOTATION_DELETE, ANNOTATION_SELECT_PROVIDER,
		"org.apache.ibatis.annotations.InsertProvider",
		"org.apache.ibatis.annotations.UpdateProvider",
		"org.apache.ibatis.annotations.DeleteProvider");

	public static final String TYPE_ROW_BOUNDS = "org.apache.ibatis.session.RowBounds";

	public static final String TYPE_TYPE_HANDLER = "org.apache.ibatis.type.TypeHandler";

	public static final String TYPE_CACHE = "org.apache.ibatis.cache.Cache";

	public static final String TYPE_OBJECT_FACTORY = "org.apache.ibatis.reflection.factory.ObjectFactory";

	public static final String TYPE_OBJECT_WRAPPER_FACTORY = "org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory";

	public static final String TYPE_REFLECTOR_FACTORY = "org.apache.ibatis.reflection.ReflectorFactory";

	public static final String TYPE_VFS = "org.apache.ibatis.io.VFS";

	public static final String TYPE_LANGUAGE_DRIVER = "org.apache.ibatis.scripting.LanguageDriver";

	public static final String GUICE_MYBATIS_MODULE = "org.mybatis.guice.MyBatisModule";

	public static final String SPRING_SQL_SESSION_FACTORY_BEAN = "org.mybatis.spring.SqlSessionFactoryBean";

	public static final IContentType mapperContentType;

	public static final IContentType configContentType;

	public static IContentType springConfigContentType;

	static
	{
		IContentTypeManager contentTypeManager = Platform.getContentTypeManager();
		mapperContentType = contentTypeManager.getContentType(CONTENT_TYPE_MAPPER);
		configContentType = contentTypeManager.getContentType(CONTENT_TYPE_CONFIG);
		springConfigContentType = contentTypeManager.getContentType(CONTENT_TYPE_SPRING_CONFIG);
		if (springConfigContentType == null)
		{
			// this means STS is installed...I guess.
			springConfigContentType = contentTypeManager
				.getContentType(CONTENT_TYPE_SPRING_CONFIG_STS);
		}
	}

	private MybatipseConstants()
	{
	}
}
