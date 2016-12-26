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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.crypto.Data;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.MybatipseConstants;
import net.harawata.mybatipse.util.NameUtil;

/**
 * @author Iwao AVE!
 */
public class BeanPropertyCache
{
	private static boolean DEBUG = Activator.getDefault().isDebugging() && Boolean
		.parseBoolean(Platform.getDebugOption(MybatipseConstants.DEBUG_BEAN_PROPERTY_CACHE));

	private static final Map<IProject, Map<String, BeanPropertyInfo>> projectCache = new ConcurrentHashMap<IProject, Map<String, BeanPropertyInfo>>();

	private static final Map<IProject, Map<String, Set<String>>> subclassCache = new ConcurrentHashMap<IProject, Map<String, Set<String>>>();

	private static final List<String> ignoredTypes = Arrays.asList(String.class.getName(),
		Byte.class.getName(), Long.class.getName(), Short.class.getName(), Integer.class.getName(),
		Double.class.getName(), Float.class.getName(), Boolean.class.getName(),
		Data.class.getName(), BigInteger.class.getName(), BigDecimal.class.getName(),
		Object.class.getName(), Map.class.getName(), HashMap.class.getName(), List.class.getName(),
		ArrayList.class.getName(), Collection.class.getName(), Iterator.class.getName());

	public static void clearBeanPropertyCache()
	{
		if (DEBUG)
			Activator.log(IStatus.INFO, "Remove bean property caches.");
		projectCache.clear();
		subclassCache.clear();
	}

	public static void clearBeanPropertyCache(IProject project)
	{
		if (DEBUG)
			Activator.log(IStatus.INFO,
				"Remove bean property cache for project " + project.getName());
		projectCache.remove(project);
		subclassCache.remove(project);
	}

	public static void clearBeanPropertyCache(IProject project, String qualifiedName)
	{
		Map<String, BeanPropertyInfo> beans = projectCache.get(project);
		if (beans != null)
		{
			String topLevelClass = removeExtension(qualifiedName);
			beans.remove(topLevelClass);
			if (DEBUG)
				Activator.log(IStatus.INFO, "Remove bean property cache for class " + qualifiedName);
			// Clear cache for inner classes.
			String innerClassPrefix = topLevelClass + ".";
			for (Iterator<Entry<String, BeanPropertyInfo>> it = beans.entrySet().iterator(); it
				.hasNext();)
			{
				Entry<String, BeanPropertyInfo> entry = it.next();
				String fqn = entry.getKey();
				if (fqn.startsWith(innerClassPrefix))
				{
					if (DEBUG)
						Activator.log(IStatus.INFO, "Remove bean property cache for inner class " + fqn);
					it.remove();
					clearSubclassCache(project, fqn);
				}
			}
			clearSubclassCache(project, topLevelClass);
		}
	}

	private static void clearSubclassCache(IProject project, String qualifiedName)
	{
		Map<String, Set<String>> subclassMap = subclassMapForProject(project);
		Set<String> subclasses = subclassMap.remove(qualifiedName);
		if (subclasses != null)
		{
			for (String subclass : subclasses)
			{
				clearBeanPropertyCache(project, subclass);
			}
		}
	}

	public static BeanPropertyInfo getBeanPropertyInfo(IJavaProject javaProject, String fqn)
	{
		if (fqn == null || ignoredTypes.contains(fqn))
		{
			return null;
		}
		String qualifiedName = removeExtension(fqn);
		IProject project = javaProject.getProject();
		Map<String, BeanPropertyInfo> beans = projectCache.get(project);
		if (beans == null)
		{
			beans = new ConcurrentHashMap<String, BeanPropertyInfo>();
			projectCache.put(project, beans);
		}
		Map<String, Set<String>> subclassMap = subclassMapForProject(project);
		BeanPropertyInfo beanProps = beans.get(qualifiedName);
		if (beanProps == null)
		{
			final Map<String, String> readableFields = new LinkedHashMap<String, String>();
			final Map<String, String> writableFields = new LinkedHashMap<String, String>();
			parseBean(javaProject, qualifiedName, readableFields, writableFields, subclassMap);
			beanProps = new BeanPropertyInfo(readableFields, writableFields);
		}
		beans.put(qualifiedName, beanProps);
		return beanProps;
	}

	private static Map<String, Set<String>> subclassMapForProject(IProject project)
	{
		Map<String, Set<String>> subclassMap = subclassCache.get(project.getProject());
		if (subclassMap == null)
		{
			subclassMap = new ConcurrentHashMap<String, Set<String>>();
			subclassCache.put(project.getProject(), subclassMap);
		}
		return subclassMap;
	}

	protected static void parseBean(IJavaProject project, String qualifiedNameWithArgs,
		final Map<String, String> readableFields, final Map<String, String> writableFields,
		final Map<String, Set<String>> subclassMap)
	{
		try
		{
			final String qualifiedName = NameUtil.stripTypeArguments(qualifiedNameWithArgs);
			final IType type = project.findType(qualifiedName);
			if (type != null)
			{
				if (type.isBinary())
				{
					if (DEBUG)
						Activator.log(IStatus.INFO,
							"Parsing properties of a binary class " + qualifiedName);
					parseBinary(project, type, qualifiedName,
						NameUtil.extractTypeParams(qualifiedNameWithArgs), readableFields, writableFields,
						subclassMap);
				}
				else
				{
					if (DEBUG)
						Activator.log(IStatus.INFO,
							"Parsing properties of a source class " + qualifiedName);
					parseSource(project, type, qualifiedName,
						NameUtil.extractTypeParams(qualifiedNameWithArgs), readableFields, writableFields,
						subclassMap);
				}
			}
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, "Failed to find type " + qualifiedNameWithArgs, e);
		}
	}

	protected static void parseBinary(IJavaProject project, final IType type,
		final String qualifiedName, List<String> actualTypeParams,
		final Map<String, String> readableFields, final Map<String, String> writableFields,
		final Map<String, Set<String>> subclassMap) throws JavaModelException
	{
		final List<String> typeParams = new ArrayList<String>();
		final ITypeParameter[] typeParameters = type.getTypeParameters();
		for (ITypeParameter typeParameter : typeParameters)
		{
			typeParams.add(typeParameter.getElementName());
		}

		parseBinaryFields(type, actualTypeParams, typeParams, readableFields, writableFields);

		parseBinaryMethods(type, actualTypeParams, typeParams, readableFields, writableFields);

		String superclassFqn = Signature.toString(type.getSuperclassTypeSignature());
		String currentClassFqn = type.getFullyQualifiedName();
		if (!Object.class.getName().equals(superclassFqn))
		{
			parseSuper(project, superclassFqn, currentClassFqn, typeParams, actualTypeParams,
				readableFields, writableFields, subclassMap);
		}

		String[] superInterfaceTypes = type.getSuperInterfaceTypeSignatures();
		for (String superInterfaceType : superInterfaceTypes)
		{
			if (!superInterfaceType.startsWith("java") && !superInterfaceType.startsWith("javax."))
			{
				parseSuper(project, superInterfaceType, currentClassFqn, typeParams, actualTypeParams,
					readableFields, writableFields, subclassMap);
			}
		}
	}

	protected static void parseSuper(IJavaProject project, String superclassFqn,
		String currentClassFqn, final List<String> typeParams, List<String> actualTypeParams,
		final Map<String, String> readableFields, final Map<String, String> writableFields,
		final Map<String, Set<String>> subclassMap)
	{
		String rawSuperclass = superclassFqn;
		if (superclassFqn.indexOf('<') > -1)
		{
			rawSuperclass = NameUtil.stripTypeArguments(superclassFqn);
			StringBuilder superclassFqnBuilder = new StringBuilder(rawSuperclass).append('<');
			List<String> superclassTypeParams = NameUtil.extractTypeParams(superclassFqn);
			for (int i = 0; i < superclassTypeParams.size(); i++)
			{
				if (i > 0)
					superclassFqnBuilder.append(',');
				superclassFqnBuilder.append(
					NameUtil.resolveTypeParam(actualTypeParams, typeParams, superclassTypeParams.get(i)));
			}
			superclassFqnBuilder.append('>');
			superclassFqn = superclassFqnBuilder.toString();
		}

		Set<String> subclasses = subclassMap.get(superclassFqn);
		if (subclasses == null)
		{
			subclasses = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
			subclassMap.put(superclassFqn, subclasses);
		}
		subclasses.add(currentClassFqn);
		parseBean(project, superclassFqn, readableFields, writableFields, subclassMap);
	}

	protected static void parseBinaryMethods(final IType type,
		final List<String> actualTypeParams, final List<String> typeParams,
		final Map<String, String> readableFields, final Map<String, String> writableFields)
		throws JavaModelException
	{
		for (IMethod method : type.getMethods())
		{
			int flags = method.getFlags();
			if (Flags.isPublic(flags))
			{
				final String methodName = method.getElementName();
				final int parameterCount = method.getParameters().length;
				final String returnType = method.getReturnType();
				if (Signature.C_VOID == returnType.charAt(0))
				{
					if (BeanPropertyVisitor.isSetter(methodName, parameterCount))
					{
						String fieldName = BeanPropertyVisitor.getFieldNameFromAccessor(methodName);
						String paramType = method.getParameterTypes()[0];
						writableFields.put(fieldName, NameUtil.resolveTypeParam(actualTypeParams,
							typeParams, Signature.toString(paramType)));
					}
				}
				else
				{
					if (BeanPropertyVisitor.isGetter(methodName, parameterCount))
					{
						String fieldName = BeanPropertyVisitor.getFieldNameFromAccessor(methodName);
						readableFields.put(fieldName, NameUtil.resolveTypeParam(actualTypeParams,
							typeParams, Signature.toString(returnType)));
					}
				}
			}
		}
	}

	protected static void parseBinaryFields(final IType type, final List<String> actualTypeParams,
		final List<String> typeParams, final Map<String, String> readableFields,
		final Map<String, String> writableFields) throws JavaModelException
	{
		for (IField field : type.getFields())
		{
			int flags = field.getFlags();
			String fieldName = field.getElementName();
			String qualifiedType = Signature.toString(field.getTypeSignature());
			if (!Flags.isFinal(flags))
			{
				// MyBatis can write non-public fields.
				writableFields.put(fieldName,
					NameUtil.resolveTypeParam(actualTypeParams, typeParams, qualifiedType));
			}
			if (Flags.isPublic(flags))
			{
				readableFields.put(fieldName,
					NameUtil.resolveTypeParam(actualTypeParams, typeParams, qualifiedType));
			}
		}
	}

	protected static void parseSource(IJavaProject project, final IType type,
		final String qualifiedName, List<String> typeParams,
		final Map<String, String> readableFields, final Map<String, String> writableFields,
		final Map<String, Set<String>> subclassMap) throws JavaModelException
	{
		ICompilationUnit compilationUnit = (ICompilationUnit)type
			.getAncestor(IJavaElement.COMPILATION_UNIT);
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(compilationUnit);
		parser.setResolveBindings(true);
		// parser.setIgnoreMethodBodies(true);
		CompilationUnit astUnit = (CompilationUnit)parser.createAST(null);
		astUnit.accept(new BeanPropertyVisitor(project, qualifiedName, typeParams, readableFields,
			writableFields, subclassMap));
	}

	public static Map<String, String> searchFields(IJavaProject project, String qualifiedName,
		String matchStr, boolean searchReadable, int currentIdx, boolean isValidation)
	{
		final Map<String, String> results = new LinkedHashMap<String, String>();
		String searchStr;
		final int startIdx = currentIdx + 1;
		final int dotIdx = getDotIndex(matchStr, startIdx);
		if (dotIdx == -1)
			searchStr = matchStr.substring(startIdx);
		else
			searchStr = matchStr.substring(startIdx, dotIdx);
		final int bracePos = searchStr.indexOf("[");
		searchStr = bracePos > -1 ? searchStr.substring(0, bracePos) : searchStr;
		final boolean isPrefixMatch = !isValidation && dotIdx == -1;

		final BeanPropertyInfo beanProperty = getBeanPropertyInfo(project, qualifiedName);
		if (beanProperty != null)
		{
			final Map<String, String> fields = searchReadable ? beanProperty.getReadableFields()
				: beanProperty.getWritableFields();

			for (Entry<String, String> entry : fields.entrySet())
			{
				final String fieldName = entry.getKey();
				final String fieldQualifiedName = entry.getValue();

				if (matched(fieldName, searchStr, isPrefixMatch))
				{
					if (dotIdx > -1)
					{
						return searchFields(project, fieldQualifiedName, matchStr, searchReadable, dotIdx,
							isValidation);
					}
					else
					{
						results.put(fieldName, fieldQualifiedName);
					}
				}
			}
		}
		return results;
	}

	public static List<ICompletionProposal> buildFieldNameProposal(Map<String, String> fields,
		final String input, final int offset, final int replacementLength)
	{
		List<ICompletionProposal> proposalList = new ArrayList<ICompletionProposal>();
		int lastDot = input.lastIndexOf(".");
		String prefix = lastDot > -1 ? input.substring(0, lastDot) : "";
		int relevance = fields.size();
		for (Entry<String, String> fieldEntry : fields.entrySet())
		{
			String fieldName = fieldEntry.getKey();
			String qualifiedName = fieldEntry.getValue();
			StringBuilder replaceStr = new StringBuilder();
			if (lastDot > -1)
				replaceStr.append(prefix).append('.');
			replaceStr.append(fieldName);
			StringBuilder displayStr = new StringBuilder();
			displayStr.append(fieldName).append(" - ").append(qualifiedName);
			ICompletionProposal proposal = new JavaCompletionProposal(replaceStr.toString(), offset,
				replacementLength, replaceStr.length(), Activator.getIcon(), displayStr.toString(),
				null, null, relevance--);
			proposalList.add(proposal);
		}
		return proposalList;
	}

	private static String removeExtension(String src)
	{
		if (src != null && src.endsWith(".java"))
			return src.substring(0, src.length() - 5);
		else
			return src;
	}

	private static int getDotIndex(String str, int startIdx)
	{
		boolean isIndexedProperty = false;
		for (int i = startIdx; i < str.length(); i++)
		{
			char c = str.charAt(i);
			if (!isIndexedProperty && c == '.')
				return i;
			else if (!isIndexedProperty && c == '[')
				isIndexedProperty = true;
			else if (c == ']')
				isIndexedProperty = false;
		}
		return -1;
	}

	private static boolean matched(String fieldName, String searchStr, boolean prefixMatch)
	{
		return (searchStr == null || searchStr.length() == 0)
			|| (prefixMatch ? fieldName.toLowerCase().startsWith(searchStr.toLowerCase())
				: fieldName.equals(searchStr));
	}

}
