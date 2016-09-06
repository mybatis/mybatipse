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

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import net.harawata.mybatipse.util.NameUtil;

/**
 * @author Iwao AVE!
 */
public class BeanPropertyVisitor extends ASTVisitor
{
	private IJavaProject project;

	private final String qualifiedName;

	private final List<String> actualTypeParams;

	private final Map<String, String> readableFields;

	private final Map<String, String> writableFields;

	private final Map<String, Set<String>> subclassMap;

	private List<String> typeParams = new ArrayList<String>();

	private int nestLevel;

	public BeanPropertyVisitor(
		IJavaProject project,
		String qualifiedName,
		List<String> actualTypeParams,
		Map<String, String> readableFields,
		Map<String, String> writableFields,
		Map<String, Set<String>> subclassMap)
	{
		super();
		this.project = project;
		this.qualifiedName = qualifiedName;
		this.actualTypeParams = actualTypeParams;
		this.readableFields = readableFields;
		this.writableFields = writableFields;
		this.subclassMap = subclassMap;
	}

	@Override
	public boolean visit(TypeDeclaration node)
	{
		ITypeBinding binding = node.resolveBinding();
		if (binding == null)
			return false;

		ITypeBinding[] args = binding.getTypeParameters();
		for (ITypeBinding arg : args)
		{
			typeParams.add(arg.getQualifiedName());
		}

		if (qualifiedName.equals(binding.getQualifiedName()))
			nestLevel = 1;
		else if (nestLevel > 0)
			nestLevel++;

		return true;
	}

	@Override
	public boolean visit(AnonymousClassDeclaration node)
	{
		return false;
	}

	@Override
	public boolean visit(FieldDeclaration node)
	{
		if (nestLevel != 1)
			return false;
		int modifiers = node.getModifiers();
		// Check modifers to spare binding overhead.
		if (Modifier.isPublic(modifiers) || !Modifier.isFinal(modifiers))
		{
			@SuppressWarnings("unchecked")
			List<VariableDeclarationFragment> fragments = node.fragments();
			for (VariableDeclarationFragment fragment : fragments)
			{
				String fieldName = fragment.getName().toString();
				String qualifiedName = getQualifiedNameFromType(node.getType());
				if (qualifiedName != null)
				{
					if (Modifier.isPublic(modifiers))
						readableFields.put(fieldName,
							NameUtil.resolveTypeParam(actualTypeParams, typeParams, qualifiedName));
					if (!Modifier.isFinal(modifiers))
						writableFields.put(fieldName,
							NameUtil.resolveTypeParam(actualTypeParams, typeParams, qualifiedName));
				}
			}
		}
		return false;
	}

	@Override
	public boolean visit(MethodDeclaration node)
	{
		if (nestLevel != 1)
			return false;
		// Resolve binding first to support Lombok generated methods.
		// node.getModifiers() returns incorrect access modifiers for them.
		// https://github.com/harawata/stlipse/issues/2
		IMethodBinding method = node.resolveBinding();
		if (method != null && Modifier.isPublic(method.getModifiers()))
		{
			final String methodName = node.getName().toString();
			final int parameterCount = node.parameters().size();
			final Type returnType = node.getReturnType2();
			if (returnType == null)
			{
				// Ignore constructor
			}
			else if (isReturnVoid(returnType))
			{
				if (isSetter(methodName, parameterCount))
				{
					SingleVariableDeclaration param = (SingleVariableDeclaration)node.parameters().get(0);
					String qualifiedName = getQualifiedNameFromType(param.getType());
					String fieldName = getFieldNameFromAccessor(methodName);
					writableFields.put(fieldName,
						NameUtil.resolveTypeParam(actualTypeParams, typeParams, qualifiedName));
				}
			}
			else
			{
				if (isGetter(methodName, parameterCount))
				{
					String fieldName = getFieldNameFromAccessor(methodName);
					String qualifiedName = getQualifiedNameFromType(returnType);
					readableFields.put(fieldName,
						NameUtil.resolveTypeParam(actualTypeParams, typeParams, qualifiedName));
				}
			}
		}
		return false;
	}

	private String getQualifiedNameFromType(Type type)
	{
		ITypeBinding binding = type.resolveBinding();
		if (binding != null)
		{
			return binding.getQualifiedName();
		}
		return null;
	}

	public static boolean isGetter(String methodName, int parameterCount)
	{
		return (methodName.startsWith("get") && methodName.length() > 3)
			|| (methodName.startsWith("is") && methodName.length() > 2) && parameterCount == 0;
	}

	public static boolean isSetter(String methodName, int parameterCount)
	{
		return methodName.startsWith("set") && methodName.length() > 3 && parameterCount == 1;
	}

	private boolean isReturnVoid(Type type)
	{
		return type.isPrimitiveType()
			&& PrimitiveType.VOID.equals(((PrimitiveType)type).getPrimitiveTypeCode());
	}

	@Override
	public void endVisit(TypeDeclaration node)
	{
		if (nestLevel == 1)
		{
			Type superclassType = node.getSuperclassType();
			if (superclassType != null)
			{
				ITypeBinding binding = superclassType.resolveBinding();
				if (binding != null)
				{
					parseSuper(binding.getQualifiedName(), binding);
				}
			}
			@SuppressWarnings("unchecked")
			List<Type> superInterfaces = node.superInterfaceTypes();
			for (Type superInterface : superInterfaces)
			{
				ITypeBinding binding = superInterface.resolveBinding();
				String fqn = binding.getQualifiedName();
				if (binding != null && fqn.indexOf("java.") != 0 && fqn.indexOf("javax.") != 0)
				{
					parseSuper(fqn, binding);
				}
			}
		}
		nestLevel--;
	}

	private void parseSuper(String superclassFqn, ITypeBinding superclassBinding)
	{
		String superclassErasureFqn = superclassFqn;
		if (superclassBinding.isParameterizedType())
		{
			superclassErasureFqn = NameUtil.stripTypeArguments(superclassFqn);
			StringBuilder superclassFqnBuilder = new StringBuilder(superclassErasureFqn).append('<');
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
		Set<String> subclasses = subclassMap.get(superclassErasureFqn);
		if (subclasses == null)
		{
			subclasses = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
			subclassMap.put(superclassErasureFqn, subclasses);
		}
		subclasses.add(qualifiedName);
		BeanPropertyCache.parseBean(project, superclassFqn, readableFields, writableFields,
			subclassMap);
	}

	public static String getFieldNameFromAccessor(String methodName)
	{
		String fieldName = "";
		if (methodName != null)
		{
			if (methodName.startsWith("set") || methodName.startsWith("get"))
			{
				fieldName = Introspector.decapitalize(methodName.substring(3));
			}
			else if (methodName.startsWith("is"))
			{
				fieldName = Introspector.decapitalize(methodName.substring(2));
			}
		}
		return fieldName;
	}
}
