/*-******************************************************************************
 * Copyright (c) 2016 Iwao AVE!.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Iwao AVE! - initial API and implementation and/or initial documentation
 *******************************************************************************/

package net.harawata.mybatipse.hyperlink;

import java.text.MessageFormat;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;

import net.harawata.mybatipse.mybatis.JavaMapperUtil;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.MethodMatcher;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.SingleMethodStore;
import net.harawata.mybatipse.mybatis.MybatipseXmlUtil;
import net.harawata.mybatipse.mybatis.TypeAliasCache;

/**
 * @author Iwao AVE!
 */
public abstract class HyperlinkDetector extends AbstractHyperlinkDetector
{
	protected IHyperlink linkToJavaMapperMethod(IJavaProject project, String mapperFqn,
		IRegion linkRegion, MethodMatcher methodMatcher)
	{
		SingleMethodStore methodStore = new SingleMethodStore();
		JavaMapperUtil.findMapperMethod(methodStore, project, mapperFqn, methodMatcher);
		if (methodStore.isEmpty())
			return null;
		return new ToJavaHyperlink(methodStore.getMethod(), linkRegion, "Open mapper method.");
	}

	protected IHyperlink linkToJavaProperty(IJavaProject project, String qualifiedName,
		String propertyName, Region linkRegion) throws JavaModelException
	{
		// Ignore default type aliases.
		if (MybatipseXmlUtil.isDefaultTypeAlias(qualifiedName))
			return null;

		IType javaType = project.findType(qualifiedName);
		if (javaType == null)
		{
			String resolvedAlias = TypeAliasCache.getInstance()
				.resolveAlias(project, qualifiedName, null);
			if (resolvedAlias != null)
			{
				javaType = project.findType(resolvedAlias);
			}
		}
		if (javaType != null)
		{
			// TODO: should search setter first?
			// TODO: field of super type, nested property
			IField field = javaType.getField(propertyName);
			if (field != null)
			{
				return new ToJavaHyperlink(field, linkRegion, javaLinkLabel("property"));
			}
		}
		return null;
	}

	protected String javaLinkLabel(String target)
	{
		return MessageFormat.format("Open {0} in Java Editor", target);
	}
}
