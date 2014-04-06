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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.crypto.Data;

import net.harawata.mybatipse.Activator;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jface.text.contentassist.ICompletionProposal;

/**
 * @author Iwao AVE!
 */
public class BeanPropertyCache
{
	private static final Map<IProject, Map<String, BeanPropertyInfo>> projectCache = new ConcurrentHashMap<IProject, Map<String, BeanPropertyInfo>>();

	private static final List<String> ignoredTypes = Arrays.asList(String.class.getName(),
		Byte.class.getName(), Long.class.getName(), Short.class.getName(), Integer.class.getName(),
		Double.class.getName(), Float.class.getName(), Boolean.class.getName(),
		Data.class.getName(), BigInteger.class.getName(), BigDecimal.class.getName(),
		Object.class.getName(), Map.class.getName(), HashMap.class.getName(), List.class.getName(),
		ArrayList.class.getName(), Collection.class.getName(), Iterator.class.getName());

	public static void clearBeanPropertyCache()
	{
		projectCache.clear();
	}

	public static void clearBeanPropertyCache(IProject project)
	{
		projectCache.remove(project);
	}

	public static void clearBeanPropertyCache(IProject project, String qualifiedName)
	{
		Map<String, BeanPropertyInfo> beans = projectCache.get(project);
		if (beans != null)
		{
			String topLevelClass = removeExtension(qualifiedName);
			beans.remove(topLevelClass);
			// Clear cache for inner classes.
			String innerClassPrefix = topLevelClass + ".";
			for (Iterator<Entry<String, BeanPropertyInfo>> it = beans.entrySet().iterator(); it.hasNext();)
			{
				Entry<String, BeanPropertyInfo> entry = it.next();
				if (entry.getKey().startsWith(innerClassPrefix))
				{
					it.remove();
				}
			}
		}
	}

	public static BeanPropertyInfo getBeanPropertyInfo(IJavaProject project, String fqn)
	{
		if (fqn == null || ignoredTypes.contains(fqn))
		{
			return null;
		}
		String qualifiedName = removeExtension(fqn);
		Map<String, BeanPropertyInfo> beans = projectCache.get(project.getProject());
		if (beans == null)
		{
			beans = new ConcurrentHashMap<String, BeanPropertyInfo>();
			projectCache.put(project.getProject(), beans);
		}
		BeanPropertyInfo beanProps = beans.get(qualifiedName);
		if (beanProps == null)
		{
			final Map<String, String> readableFields = new LinkedHashMap<String, String>();
			final Map<String, String> writableFields = new LinkedHashMap<String, String>();
			parseBean(project, qualifiedName, readableFields, writableFields);
			beanProps = new BeanPropertyInfo(readableFields, writableFields);
		}
		beans.put(qualifiedName, beanProps);
		return beanProps;
	}

	protected static void parseBean(IJavaProject project, String qualifiedName,
		final Map<String, String> readableFields, final Map<String, String> writableFields)
	{
		try
		{
			final IType type = project.findType(qualifiedName);
			if (type != null)
			{
				if (type.isBinary())
				{
					parseBinary(project, type, readableFields, writableFields);
				}
				else
				{
					parseSource(project, type, qualifiedName, readableFields, writableFields);
				}
			}
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, "Failed to find type " + qualifiedName, e);
		}
	}

	protected static void parseBinary(IJavaProject project, final IType type,
		final Map<String, String> readableFields, final Map<String, String> writableFields)
		throws JavaModelException
	{
		parseBinaryFields(type, readableFields, writableFields);

		parseBinaryMethods(type, readableFields, writableFields);

		String superclass = Signature.toString(type.getSuperclassTypeSignature());
		if (!Object.class.getName().equals(superclass))
		{
			parseBean(project, superclass, readableFields, writableFields);
		}
	}

	protected static void parseBinaryMethods(final IType type,
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
						writableFields.put(fieldName, Signature.toString(paramType));
					}
				}
				else
				{
					if (BeanPropertyVisitor.isGetter(methodName, parameterCount))
					{
						String fieldName = BeanPropertyVisitor.getFieldNameFromAccessor(methodName);
						readableFields.put(fieldName, Signature.toString(returnType));
					}
				}
			}
		}
	}

	protected static void parseBinaryFields(final IType type,
		final Map<String, String> readableFields, final Map<String, String> writableFields)
		throws JavaModelException
	{
		for (IField field : type.getFields())
		{
			int flags = field.getFlags();
			if (Flags.isPublic(flags))
			{
				String fieldName = field.getElementName();
				String qualifiedType = Signature.toString(field.getTypeSignature());
				readableFields.put(fieldName, qualifiedType);
				if (!Flags.isFinal(flags))
				{
					writableFields.put(fieldName, qualifiedType);
				}
			}
		}
	}

	protected static void parseSource(IJavaProject project, final IType type,
		final String qualifiedName, final Map<String, String> readableFields,
		final Map<String, String> writableFields) throws JavaModelException
	{
		ICompilationUnit compilationUnit = (ICompilationUnit)type.getAncestor(IJavaElement.COMPILATION_UNIT);
		ASTParser parser = ASTParser.newParser(AST.JLS4);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(compilationUnit);
		parser.setResolveBindings(true);
		// parser.setIgnoreMethodBodies(true);
		CompilationUnit astUnit = (CompilationUnit)parser.createAST(null);
		astUnit.accept(new BeanPropertyVisitor(project, qualifiedName, readableFields,
			writableFields));
	}

	public static Map<String, String> searchFields(IJavaProject project, String qualifiedName,
		String matchStr, boolean includeReadOnly, int currentIdx, boolean isValidation)
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
			final Map<String, String> fields = includeReadOnly ? beanProperty.getReadableFields()
				: beanProperty.getWritableFields();

			for (Entry<String, String> entry : fields.entrySet())
			{
				final String fieldName = entry.getKey();
				final String fieldQualifiedName = entry.getValue();

				if (matched(fieldName, searchStr, isPrefixMatch))
				{
					if (dotIdx > -1)
					{
						return searchFields(project, fieldQualifiedName, matchStr, includeReadOnly, dotIdx,
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
