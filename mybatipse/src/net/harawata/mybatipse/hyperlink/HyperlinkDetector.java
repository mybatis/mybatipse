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

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;

import net.harawata.mybatipse.mybatis.JavaMapperUtil;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.MethodMatcher;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.SingleMethodStore;

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
}
