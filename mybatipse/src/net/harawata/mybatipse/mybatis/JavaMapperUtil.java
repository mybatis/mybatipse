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

package net.harawata.mybatipse.mybatis;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import net.harawata.mybatipse.Activator;

/**
 * @author Iwao AVE!
 */
public class JavaMapperUtil
{
	public static final String TYPE_ROW_BOUNDS = "org.apache.ibatis.session.RowBounds";

	public static final String ANNOTATION_PARAM = "org.apache.ibatis.annotations.Param";

	public static final List<String> statementAnnotations = Arrays.asList("Select", "Insert",
		"Update", "Delete", "SelectProvider", "InsertProvider", "UpdateProvider", "DeleteProvider");

	public static void findMapperMethod(List<MapperMethodInfo> methodInfos, IJavaProject project,
		String mapperFqn, String matchString, boolean exactMatch, AnnotationFilter annotationFilter)
	{
		try
		{
			IType mapperType = project.findType(mapperFqn);
			if (mapperType == null || !mapperType.isInterface())
				return;
			if (mapperType.isBinary())
			{
				findMapperMethodBinary(methodInfos, project, matchString, exactMatch, annotationFilter,
					mapperType);
			}
			else
			{
				findMapperMethodSource(methodInfos, project, mapperFqn, matchString, exactMatch,
					annotationFilter, mapperType);
			}
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, "Failed to find type " + mapperFqn, e);
		}
	}

	private static void findMapperMethodSource(List<MapperMethodInfo> methodInfos,
		IJavaProject project, String mapperFqn, String matchString, boolean exactMatch,
		AnnotationFilter annotationFilter, IType mapperType)
	{
		ICompilationUnit compilationUnit = (ICompilationUnit)mapperType
			.getAncestor(IJavaElement.COMPILATION_UNIT);
		ASTParser parser = ASTParser.newParser(AST.JLS4);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(compilationUnit);
		parser.setResolveBindings(true);
		// parser.setIgnoreMethodBodies(true);
		CompilationUnit astUnit = (CompilationUnit)parser.createAST(null);
		astUnit.accept(new JavaMapperVisitor(methodInfos, project, mapperFqn, matchString,
			exactMatch, annotationFilter));
	}

	private static void findMapperMethodBinary(List<MapperMethodInfo> methodInfos,
		IJavaProject project, String matchString, boolean exactMatch,
		AnnotationFilter annotationFilter, IType mapperType) throws JavaModelException
	{
		for (IMethod method : mapperType.getMethods())
		{
			if (hasUnacceptableStatementAnnotation(method, annotationFilter))
				continue;
			String methodName = method.getElementName();
			if (matches(methodName, matchString, exactMatch))
			{
				Map<String, String> paramMap = new HashMap<String, String>();
				MapperMethodInfo methodInfo = new MapperMethodInfo(methodName, paramMap);
				methodInfos.add(methodInfo);

				if (matchString.length() == 0
					|| CharOperation.camelCaseMatch(matchString.toCharArray(), methodName.toCharArray()))
				{
					ILocalVariable[] parameters = method.getParameters();
					for (int i = 0; i < parameters.length; i++)
					{
						String paramFqn = parameters[i].getElementName();
						for (IAnnotation annotation : parameters[i].getAnnotations())
						{
							if (ANNOTATION_PARAM.equals(annotation.getElementName()))
							{
								IMemberValuePair[] valuePairs = annotation.getMemberValuePairs();
								if (valuePairs.length == 1)
								{
									IMemberValuePair valuePair = valuePairs[0];
									String paramValue = (String)valuePair.getValue();
									paramMap.put(paramValue, paramFqn);
								}
							}
						}
						paramMap.put("param" + (i + 1), paramFqn); //$NON-NLS-1$
					}
				}
			}
		}
		String[] superInterfaces = mapperType.getSuperInterfaceNames();
		for (String superInterface : superInterfaces)
		{
			if (!Object.class.getName().equals(superInterface))
			{
				findMapperMethod(methodInfos, project, superInterface, matchString, exactMatch,
					annotationFilter);
			}
		}
	}

	private static boolean matches(String methodName, String matchString, boolean exactMatch)
	{
		return exactMatch && methodName.equals(matchString)
			|| (!exactMatch && (matchString.length() == 0
				|| CharOperation.camelCaseMatch(matchString.toCharArray(), methodName.toCharArray())));
	}

	private static boolean hasUnacceptableStatementAnnotation(IMethod method,
		AnnotationFilter annotationFilter) throws JavaModelException
	{
		if (annotationFilter == null)
			return false;

		IAnnotation[] annotations = method.getAnnotations();
		annotationFilter.reset();
		for (IAnnotation annotation : annotations)
		{
			String annotationName = annotation.getElementName();
			annotationFilter.check(annotationName);
		}
		return !annotationFilter.acceptable();
	}

	public static class JavaMapperVisitor extends ASTVisitor
	{
		private List<MapperMethodInfo> methodInfos;

		private IJavaProject project;

		private String mapeprFqn;

		private String matchString;

		private boolean exactMatch;

		private AnnotationFilter annotationFilter;

		private int nestLevel;

		@Override
		public boolean visit(TypeDeclaration node)
		{
			ITypeBinding binding = node.resolveBinding();
			if (binding == null)
				return false;

			if (mapeprFqn.equals(binding.getQualifiedName()))
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
		public boolean visit(MethodDeclaration node)
		{
			if (nestLevel != 1)
				return false;
			// Resolve binding first to support Lombok generated methods.
			// node.getModifiers() returns incorrect access modifiers for them.
			// https://github.com/harawata/stlipse/issues/2
			IMethodBinding method = node.resolveBinding();
			if (method != null)
			{
				if (annotationFilter != null)
				{
					IAnnotationBinding[] methodAnnotations = method.getAnnotations();
					annotationFilter.reset();
					for (IAnnotationBinding annotation : methodAnnotations)
					{
						String annotationName = annotation.getName();
						annotationFilter.check(annotationName);
					}
					if (!annotationFilter.acceptable())
						return false;
				}

				String methodName = node.getName().toString();
				if (matches(methodName, matchString, exactMatch))
				{
					Map<String, String> paramMap = new HashMap<String, String>();
					MapperMethodInfo methodInfo = new MapperMethodInfo(methodName, paramMap);
					methodInfos.add(methodInfo);
					collectMethodParams(node, paramMap);
				}
			}
			return false;
		}

		private void collectMethodParams(MethodDeclaration node, Map<String, String> paramMap)
		{
			@SuppressWarnings("unchecked")
			List<SingleVariableDeclaration> paramDecls = node.parameters();
			for (int i = 0; i < paramDecls.size(); i++)
			{
				IVariableBinding paramBinding = paramDecls.get(i).resolveBinding();
				String paramFqn = paramBinding.getType().getQualifiedName();
				if (TYPE_ROW_BOUNDS.equals(paramFqn))
					continue;
				IAnnotationBinding[] paramAnnotations = paramBinding.getAnnotations();
				for (IAnnotationBinding annotation : paramAnnotations)
				{
					if (ANNOTATION_PARAM.equals(annotation.getAnnotationType().getQualifiedName()))
					{
						IMemberValuePairBinding[] valuePairs = annotation.getAllMemberValuePairs();
						if (valuePairs.length == 1)
						{
							IMemberValuePairBinding valuePairBinding = valuePairs[0];
							String paramValue = (String)valuePairBinding.getValue();
							paramMap.put(paramValue, paramFqn);
						}
					}
				}
				paramMap.put("param" + (i + 1), paramFqn); //$NON-NLS-1$
			}
		}

		public void endVisit(TypeDeclaration node)
		{
			if (nestLevel == 1 && (!exactMatch || methodInfos.isEmpty()))
			{
				@SuppressWarnings("unchecked")
				List<Type> superInterfaceTypes = node.superInterfaceTypes();
				if (superInterfaceTypes != null && !superInterfaceTypes.isEmpty())
				{
					for (Type superInterfaceType : superInterfaceTypes)
					{
						ITypeBinding binding = superInterfaceType.resolveBinding();
						if (binding != null)
						{
							String superInterfaceFqn = binding.getQualifiedName();
							if (binding.isParameterizedType())
							{
								// strip parameter part
								int paramIdx = superInterfaceFqn.indexOf('<');
								superInterfaceFqn = superInterfaceFqn.substring(0, paramIdx);
							}
							findMapperMethod(methodInfos, project, superInterfaceFqn, matchString, exactMatch,
								annotationFilter);
						}
					}
				}
			}
			nestLevel--;
		}

		private JavaMapperVisitor(
			List<MapperMethodInfo> methodInfos,
			IJavaProject project,
			String mapperFqn,
			String matchString,
			boolean exactMatch,
			AnnotationFilter annotationFilter)
		{
			this.methodInfos = methodInfos;
			this.project = project;
			this.mapeprFqn = mapperFqn;
			this.matchString = matchString;
			this.exactMatch = exactMatch;
			this.annotationFilter = annotationFilter;
		}
	}

	public static interface AnnotationFilter
	{
		void reset();

		void check(String annotationName);

		boolean acceptable();
	}

	public static class RejectStatementAnnotation implements AnnotationFilter
	{
		private boolean acceptable = true;

		@Override
		public void reset()
		{
			acceptable = true;
		}

		@Override
		public void check(String annotationName)
		{
			acceptable &= !statementAnnotations.contains(annotationName);
		}

		@Override
		public boolean acceptable()
		{
			return acceptable;
		}
	}

	public static class MapperMethodInfo
	{
		private String methodName;

		private Map<String, String> params;

		public MapperMethodInfo(String methodName, Map<String, String> params)
		{
			this.methodName = methodName;
			this.params = params;
		}

		public String getMethodName()
		{
			return methodName;
		}

		public Map<String, String> getParams()
		{
			return params;
		}
	}
}
