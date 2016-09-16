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

package net.harawata.mybatipse.mybatis;

import java.util.List;

import javax.xml.xpath.XPathExpressionException;

import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.HasSelectAnnotation;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.MapperMethodStore;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.MethodMatcher;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.ResultsAnnotationWithId;
import net.harawata.mybatipse.util.XpathUtil;

/**
 * @author Iwao AVE!
 */
@SuppressWarnings("restriction")
public class ValidatorHelper
{
	public static boolean isReferenceValid(IJavaProject project, String localNamespace,
		IDOMDocument localDoc, String reference, String targetElement)
	{
		try
		{
			if (reference.indexOf("${") > -1)
				return true;

			IDOMDocument domDoc = null;
			String namespace = null;
			String id = null;

			if (reference.indexOf('.') == -1)
			{
				// Local reference
				id = reference;
				namespace = localNamespace;
				if (localDoc == null)
				{
					domDoc = MybatipseXmlUtil.getMapperDocument(project, localNamespace);
				}
				else
				{
					domDoc = localDoc;
				}
			}
			else
			{
				// External reference
				int lastDot = reference.lastIndexOf('.');
				namespace = reference.substring(0, lastDot);
				id = reference.substring(lastDot + 1);
				domDoc = MybatipseXmlUtil.getMapperDocument(project, namespace);
			}

			// Check Java mapper
			if ("select".equals(targetElement))
			{
				if (mapperMethodExists(project, namespace, new HasSelectAnnotation(id, true)))
				{
					return true;
				}
			}
			else if ("resultMap".equals(targetElement))
			{
				if (mapperMethodExists(project, namespace, new ResultsAnnotationWithId(id, true)))
				{
					return true;
				}
			}

			// Check XML mapper
			if (domDoc != null)
			{
				String xpath = "count(//" + targetElement + "[@id='" + id + "']) > 0";
				return XpathUtil.xpathBool(domDoc, xpath);
			}
		}
		catch (XPathExpressionException e)
		{
			Activator.log(Status.ERROR, "Error occurred while validating reference: " + reference, e);
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, "Error occurred while validating reference: " + reference, e);
		}
		return false;
	}

	public static boolean mapperMethodExists(IJavaProject project, String qualifiedName,
		MethodMatcher methodMatcher) throws JavaModelException
	{
		MapperMethodStore methodStore = new MapperMethodStore()
		{
			boolean found;

			@Override
			public void add(IMethod method)
			{
				found = true;
			}

			@Override
			public void add(IMethodBinding method, List<SingleVariableDeclaration> params)
			{
				found = true;
			}

			@Override
			public boolean isEmpty()
			{
				return !found;
			}
		};
		JavaMapperUtil.findMapperMethod(methodStore, project, qualifiedName, methodMatcher);
		return !methodStore.isEmpty();
	}
}
