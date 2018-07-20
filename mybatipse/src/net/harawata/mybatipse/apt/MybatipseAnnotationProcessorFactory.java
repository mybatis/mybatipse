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

package net.harawata.mybatipse.apt;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.apt.pluggable.core.dispatch.IdeProcessingEnvImpl;

import net.harawata.mybatipse.MybatipseConstants;
import net.harawata.mybatipse.bean.BeanPropertyCache;
import net.harawata.mybatipse.mybatis.ValidatorHelper;
import net.harawata.mybatipse.util.NameUtil;

/**
 * @author Iwao AVE!
 */
@SuppressWarnings("restriction")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
@SupportedAnnotationTypes({
	MybatipseConstants.ANNOTATION_RESULT_MAP, MybatipseConstants.ANNOTATION_RESULTS
})
public class MybatipseAnnotationProcessorFactory extends AbstractProcessor
{
	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env)
	{
		Messager messager = super.processingEnv.getMessager();
		for (TypeElement annotation : annotations)
		{
			String annotationType = annotation.getQualifiedName().toString();
			for (Element element : env.getElementsAnnotatedWith(annotation))
			{
				for (AnnotationMirror annotationMirror : element.getAnnotationMirrors())
				{
					if (annotationType.equals(annotationMirror.getAnnotationType().toString()))
					{
						// TODO: See if it's possible to display the error on the right location
						// Related issue https://bugs.eclipse.org/bugs/show_bug.cgi?id=261600
						if (MybatipseConstants.ANNOTATION_RESULT_MAP.equals(annotationType))
						{
							validateResultMapAnnotation(element, annotationMirror, messager);
						}
						else if (MybatipseConstants.ANNOTATION_RESULTS.equals(annotationType))
						{
							validateResultsAnnotation(element, annotationMirror, messager);
						}
					}
				}
			}
		}
		return false;
	}

	protected void validateResultsAnnotation(Element element, AnnotationMirror annotationMirror,
		Messager messager)
	{
		final IJavaProject project = getJavaProject();
		String declaredReturnType = ((ExecutableElement)element).getReturnType().toString();
		String returnType = NameUtil.manageableReturnType(project, declaredReturnType);
		if (returnType == null)
			return;

		AnnotationValue resultsAnno = getAnnotationValueByKey(annotationMirror, "value");
		if (resultsAnno == null)
			return;
		@SuppressWarnings("unchecked")
		List<AnnotationValue> resultAnnos = (List<AnnotationValue>)resultsAnno.getValue();
		for (AnnotationValue resultAnno : resultAnnos)
		{
			AnnotationMirror resultAnnoMirror = (AnnotationMirror)resultAnno.getValue();
			validateResultProperty(project, element, resultAnnoMirror, messager, returnType);
			validateSelectId(project, element, resultAnnoMirror, messager, "one");
			validateSelectId(project, element, resultAnnoMirror, messager, "many");
		}
	}

	protected void validateSelectId(final IJavaProject project, Element element,
		AnnotationMirror resultAnnoMirror, Messager messager, String key)
	{
		TypeElement mapper = (TypeElement)element.getEnclosingElement();
		AnnotationValue resultOneValue = getAnnotationValueByKey(resultAnnoMirror, key);
		if (resultOneValue != null)
		{
			AnnotationMirror oneAnnoMirror = (AnnotationMirror)resultOneValue.getValue();
			AnnotationValue selectAnnoValue = getAnnotationValueByKey(oneAnnoMirror, "select");
			if (selectAnnoValue != null)
			{
				String selectId = selectAnnoValue.getValue().toString();
				if (!ValidatorHelper.isReferenceValid(project, mapper.getQualifiedName().toString(),
					selectId, "select"))
				{
					messager.printMessage(Kind.ERROR, "Select with id='" + selectId + "' not found.",
						element, oneAnnoMirror, selectAnnoValue);
				}
			}
		}
	}

	protected void validateResultProperty(final IJavaProject project, Element element,
		AnnotationMirror resultAnnoMirror, Messager messager, String returnType)
	{
		AnnotationValue resultPropertyValue = getAnnotationValueByKey(resultAnnoMirror, "property");
		if (resultPropertyValue != null)
		{
			String property = resultPropertyValue.getValue().toString();
			Map<String, String> fields = BeanPropertyCache.searchFields(project, returnType, property,
				true, -1, true);
			if (fields.size() == 0)
			{
				messager.printMessage(Kind.ERROR,
					"Property '" + property + "' not found in class " + returnType, element,
					resultAnnoMirror, resultPropertyValue);
			}
		}
	}

	protected void validateResultMapAnnotation(Element element, AnnotationMirror annotationMirror,
		Messager messager)
	{
		final IJavaProject project = getJavaProject();
		TypeElement mapper = (TypeElement)element.getEnclosingElement();

		AnnotationValue annotationValue = getAnnotationValueByKey(annotationMirror, "value");
		if (annotationValue != null)
		{
			@SuppressWarnings("unchecked")
			List<AnnotationValue> values = (List<AnnotationValue>)annotationValue.getValue();
			for (AnnotationValue value : values)
			{
				String resultMapId = value.getValue().toString();
				if (!ValidatorHelper.isReferenceValid(project, getQualifiedName(mapper), resultMapId,
					"resultMap"))
				{
					messager.printMessage(Kind.ERROR,
						"Result map with id='" + resultMapId + "' not found.", element, annotationMirror,
						annotationValue);
				}
			}
		}
	}

	protected String getQualifiedName(TypeElement element)
	{
		if (NestingKind.MEMBER.equals(element.getNestingKind()))
		{
			return getQualifiedName((TypeElement)element.getEnclosingElement()) + "$"
				+ element.getSimpleName().toString();
		}
		else
		{
			return element.getQualifiedName().toString();
		}
	}

	protected static AnnotationValue getAnnotationValueByKey(AnnotationMirror annotationMirror,
		String key)
	{
		for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationMirror
			.getElementValues()
			.entrySet())
		{
			ExecutableElement executableElement = entry.getKey();
			if (key.equals(executableElement.getSimpleName().toString()))
			{
				return entry.getValue();
			}
		}
		return null;
	}

	private IJavaProject getJavaProject()
	{
		return ((IdeProcessingEnvImpl)super.processingEnv).getJavaProject();
	}
}
