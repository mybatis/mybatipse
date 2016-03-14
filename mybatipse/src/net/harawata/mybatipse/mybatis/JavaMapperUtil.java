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
		String mapperFqn, MethodMatcher annotationFilter)
	{
		try
		{
			IType mapperType = project.findType(mapperFqn);
			if (mapperType == null || !mapperType.isInterface())
				return;
			if (mapperType.isBinary())
			{
				findMapperMethodBinary(methodInfos, project, annotationFilter, mapperType);
			}
			else
			{
				findMapperMethodSource(methodInfos, project, mapperFqn, annotationFilter, mapperType);
			}
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, "Failed to find type " + mapperFqn, e);
		}
	}

	private static void findMapperMethodSource(List<MapperMethodInfo> methodInfos,
		IJavaProject project, String mapperFqn, MethodMatcher annotationFilter, IType mapperType)
	{
		ICompilationUnit compilationUnit = (ICompilationUnit)mapperType
			.getAncestor(IJavaElement.COMPILATION_UNIT);
		ASTParser parser = ASTParser.newParser(AST.JLS4);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(compilationUnit);
		parser.setResolveBindings(true);
		// parser.setIgnoreMethodBodies(true);
		CompilationUnit astUnit = (CompilationUnit)parser.createAST(null);
		astUnit.accept(new JavaMapperVisitor(methodInfos, project, mapperFqn, annotationFilter));
	}

	private static void findMapperMethodBinary(List<MapperMethodInfo> methodInfos,
		IJavaProject project, MethodMatcher methodMatcher, IType mapperType)
		throws JavaModelException
	{
		for (IMethod method : mapperType.getMethods())
		{
			if (!methodMatcher.matches(method))
				continue;

			Map<String, String> paramMap = new HashMap<String, String>();
			MapperMethodInfo methodInfo = new MapperMethodInfo(method, paramMap);
			methodInfos.add(methodInfo);

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

		String[] superInterfaces = mapperType.getSuperInterfaceNames();
		for (String superInterface : superInterfaces)
		{
			if (!Object.class.getName().equals(superInterface))
			{
				findMapperMethod(methodInfos, project, superInterface, methodMatcher);
			}
		}
	}

	public static class JavaMapperVisitor extends ASTVisitor
	{
		private List<MapperMethodInfo> methodInfos;

		private IJavaProject project;

		private String mapperFqn;

		private MethodMatcher methodMatcher;

		private int nestLevel;

		@Override
		public boolean visit(TypeDeclaration node)
		{
			ITypeBinding binding = node.resolveBinding();
			if (binding == null)
				return false;

			if (mapperFqn.equals(binding.getQualifiedName()))
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
			if (method == null)
				return false;

			if (methodMatcher == null)
				return false;

			try
			{
				if (methodMatcher.matches((IMethod)method.getJavaElement()))
				{
					Map<String, String> paramMap = new HashMap<String, String>();
					MapperMethodInfo methodInfo = new MapperMethodInfo((IMethod)method.getJavaElement(),
						paramMap);
					methodInfos.add(methodInfo);
					collectMethodParams(node, paramMap);
				}
			}
			catch (JavaModelException e)
			{
				Activator.log(Status.ERROR,
					"Failed to visit method " + node.getName().toString() + " in " + mapperFqn, e);
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
			if (nestLevel == 1 && (!methodMatcher.needExactMatch() || methodInfos.isEmpty()))
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
							findMapperMethod(methodInfos, project, superInterfaceFqn, methodMatcher);
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
			MethodMatcher annotationFilter)
		{
			this.methodInfos = methodInfos;
			this.project = project;
			this.mapperFqn = mapperFqn;
			this.methodMatcher = annotationFilter;
		}
	}

	public static abstract class MethodMatcher
	{
		abstract boolean matches(IMethod method) throws JavaModelException;

		abstract boolean needExactMatch();

		protected boolean hasAnnotations(IAnnotation[] annotations, List<String> annotationsToFind)
		{
			for (IAnnotation annotation : annotations)
			{
				String annotationName = annotation.getElementName();
				if (annotationsToFind.contains(annotationName))
				{
					return true;
				}
			}
			return false;
		}

		protected boolean nameMatches(String methodName, String matchString, boolean exactMatch)
		{
			if (exactMatch)
			{
				return methodName.equals(matchString);
			}
			else
			{
				return matchString.length() == 0
					|| CharOperation.camelCaseMatch(matchString.toCharArray(), methodName.toCharArray());
			}
		}
	}

	public static class MethodNameMatcher extends MethodMatcher
	{
		private String matchString;

		private boolean exactMatch;

		public MethodNameMatcher(String matchString, boolean exactMatch)
		{
			super();
			this.matchString = matchString;
			this.exactMatch = exactMatch;
		}

		@Override
		public boolean matches(IMethod method) throws JavaModelException
		{
			return nameMatches(method.getElementName(), matchString, exactMatch);
		}

		@Override
		boolean needExactMatch()
		{
			return exactMatch;
		}
	}

	public static class RejectStatementAnnotation extends MethodMatcher
	{
		private String matchString;

		private boolean exactMatch;

		public RejectStatementAnnotation(String matchString, boolean exactMatch)
		{
			super();
			this.matchString = matchString;
			this.exactMatch = exactMatch;
		}

		@Override
		public boolean matches(IMethod method) throws JavaModelException
		{
			if (hasAnnotations(method.getAnnotations(), statementAnnotations))
			{
				return false;
			}
			return nameMatches(method.getElementName(), matchString, exactMatch);
		}

		@Override
		boolean needExactMatch()
		{
			return exactMatch;
		}
	}

	public static class HasSelectAnnotation extends MethodMatcher
	{
		private String matchString;

		private boolean exactMatch;

		public HasSelectAnnotation(String matchString, boolean exactMatch)
		{
			super();
			this.matchString = matchString;
			this.exactMatch = exactMatch;
		}

		private static final List<String> selectAnnotations = Arrays.asList("Select",
			"SelectProvider");

		@Override
		public boolean matches(IMethod method) throws JavaModelException
		{
			if (!hasAnnotations(method.getAnnotations(), selectAnnotations))
			{
				return false;
			}
			return nameMatches(method.getElementName(), matchString, exactMatch);
		}

		@Override
		boolean needExactMatch()
		{
			return exactMatch;
		}
	}

	public static class MapperMethodInfo
	{
		private IMethod method;

		private Map<String, String> params;

		public MapperMethodInfo(IMethod method, Map<String, String> params)
		{
			this.method = method;
			this.params = params;
		}

		public IMethod getMethod()
		{
			return method;
		}

		public String getMethodName()
		{
			return method.getElementName();
		}

		public Map<String, String> getParams()
		{
			return params;
		}
	}
}
