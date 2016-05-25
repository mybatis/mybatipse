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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
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
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.MybatipseConstants;
import net.harawata.mybatipse.util.NameUtil;

/**
 * @author Iwao AVE!
 */
public class JavaMapperUtil
{
	public static void findMapperMethod(MapperMethodStore store, IJavaProject project,
		String mapperFqn, MethodMatcher annotationFilter)
	{
		try
		{
			IType mapperType = project.findType(mapperFqn);
			if (mapperType == null || !mapperType.isInterface())
				return;
			if (mapperType.isBinary())
			{
				findMapperMethodBinary(store, project, annotationFilter, mapperType);
			}
			else
			{
				findMapperMethodSource(store, project, mapperFqn, annotationFilter, mapperType);
			}
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, "Failed to find type " + mapperFqn, e);
		}
	}

	private static void findMapperMethodSource(MapperMethodStore methodStore,
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
		astUnit.accept(new JavaMapperVisitor(methodStore, project, mapperFqn, annotationFilter));
	}

	private static void findMapperMethodBinary(MapperMethodStore methodStore,
		IJavaProject project, MethodMatcher methodMatcher, IType mapperType)
		throws JavaModelException
	{
		for (IMethod method : mapperType.getMethods())
		{
			if (methodMatcher.matches(method))
			{
				methodStore.add(method);
			}
		}

		String[] superInterfaces = mapperType.getSuperInterfaceNames();
		for (String superInterface : superInterfaces)
		{
			if (!Object.class.getName().equals(superInterface))
			{
				findMapperMethod(methodStore, project, superInterface, methodMatcher);
			}
		}
	}

	public static class JavaMapperVisitor extends ASTVisitor
	{
		private MapperMethodStore methodStore;

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
				if (methodMatcher.matches(method))
				{
					methodStore.add(method);
				}
			}
			catch (JavaModelException e)
			{
				Activator.log(Status.ERROR,
					"Failed to visit method " + node.getName().toString() + " in " + mapperFqn, e);
			}
			return false;
		}

		public void endVisit(TypeDeclaration node)
		{
			if (nestLevel == 1 && (!methodMatcher.needExactMatch() || methodStore.isEmpty()))
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
							findMapperMethod(methodStore, project, superInterfaceFqn, methodMatcher);
						}
					}
				}
			}
			nestLevel--;
		}

		private JavaMapperVisitor(
			MapperMethodStore methodStore,
			IJavaProject project,
			String mapperFqn,
			MethodMatcher annotationFilter)
		{
			this.methodStore = methodStore;
			this.project = project;
			this.mapperFqn = mapperFqn;
			this.methodMatcher = annotationFilter;
		}
	}

	public static interface MapperMethodStore
	{
		/**
		 * Called when adding binary method.
		 */
		void add(IMethod method);

		/**
		 * Called when adding source method.
		 */
		void add(IMethodBinding method);

		boolean isEmpty();
	}

	public static class MethodNameStore implements MapperMethodStore
	{
		private List<String> methodNames = new ArrayList<String>();

		public List<String> getMethodNames()
		{
			return methodNames;
		}

		@Override
		public void add(IMethod method)
		{
			methodNames.add(method.getElementName());
		}

		@Override
		public void add(IMethodBinding method)
		{
			methodNames.add(method.getName());
		}

		@Override
		public boolean isEmpty()
		{
			return methodNames.isEmpty();
		}
	}

	public static class MethodReturnTypeStore implements MapperMethodStore
	{
		private String returnType = null;

		public String getReturnType()
		{
			return returnType;
		}

		@Override
		public void add(IMethod method)
		{
			try
			{
				returnType = method.getReturnType();
			}
			catch (JavaModelException e)
			{
				Activator.log(Status.ERROR,
					"Failed to collect return type of method " + method.getElementName() + " in "
						+ method.getDeclaringType().getFullyQualifiedName(),
					e);
			}
		}

		@Override
		public void add(IMethodBinding method)
		{
			ITypeBinding binding = method.getReturnType();
			returnType = binding.getQualifiedName();
		}

		@Override
		public boolean isEmpty()
		{
			return returnType == null;
		}
	}

	public static class MethodParametersStore implements MapperMethodStore
	{
		private IJavaProject project;

		private boolean found;

		private Map<String, String> paramMap = new HashMap<String, String>();

		public Map<String, String> getParamMap()
		{
			return paramMap;
		}

		@Override
		public void add(IMethod method)
		{
			found = true;
			try
			{
				ILocalVariable[] parameters = method.getParameters();
				if (parameters.length == 1)
				{
					putSoleParam(parameters[0].getElementName());
					return;
				}
				for (int i = 0; i < parameters.length; i++)
				{
					String paramFqn = parameters[i].getElementName();
					for (IAnnotation annotation : parameters[i].getAnnotations())
					{
						if (MybatipseConstants.ANNOTATION_PARAM.equals(annotation.getElementName()))
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
					paramMap.put("param" + (i + 1), paramFqn);
				}
			}
			catch (JavaModelException e)
			{
				Activator.log(Status.ERROR,
					"Failed to collect parameters of method " + method.getElementName() + " in "
						+ method.getDeclaringType().getFullyQualifiedName(),
					e);
			}
		}

		@Override
		public void add(IMethodBinding method)
		{
			found = true;
			ITypeBinding[] parameters = method.getParameterTypes();
			for (int i = 0; i < parameters.length; i++)
			{
				String paramFqn = parameters[i].getQualifiedName();
				if (MybatipseConstants.TYPE_ROW_BOUNDS.equals(paramFqn))
					continue;

				IAnnotationBinding[] paramAnnotations = method.getParameterAnnotations(i);
				for (IAnnotationBinding annotation : paramAnnotations)
				{
					if (MybatipseConstants.ANNOTATION_PARAM
						.equals(annotation.getAnnotationType().getQualifiedName()))
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
				paramMap.put("param" + (i + 1), paramFqn);
			}
			if (paramMap.size() == 1)
			{
				// statement has a sole param without @Param
				paramMap.clear();
				putSoleParam(parameters[0].getQualifiedName());
			}
		}

		private void putSoleParam(String paramFqn)
		{
			try
			{
				if (NameUtil.isArray(paramFqn))
				{
					paramMap.put("array", paramFqn);
					return;
				}
				else
				{
					String rawTypeFqn = NameUtil.stripTypeArguments(paramFqn);
					if (!paramFqn.equals(rawTypeFqn))
					{
						// Parameterized type.
						final IType rawType = project.findType(rawTypeFqn);
						final ITypeHierarchy supertypes = rawType
							.newSupertypeHierarchy(new NullProgressMonitor());
						final IType mapType = project.findType("java.util.Map");
						if (supertypes.contains(mapType))
						{
							return;
						}
						final IType listType = project.findType("java.util.List");
						final IType collectionType = project.findType("java.util.Collection");
						if (supertypes.contains(listType))
						{
							paramMap.put("list", paramFqn);
						}
						if (supertypes.contains(collectionType))
						{
							paramMap.put("collection", paramFqn);
							return;
						}
					}
				}
				paramMap.put("_parameter", paramFqn);
			}
			catch (JavaModelException e)
			{
				Activator.log(Status.ERROR, "Error occurred while putting sole param.", e);
			}
		}

		@Override
		public boolean isEmpty()
		{
			return !found;
		}

		public MethodParametersStore(IJavaProject project)
		{
			super();
			this.project = project;
		}
	}

	public static abstract class MethodMatcher
	{
		abstract boolean matches(IMethod method) throws JavaModelException;

		abstract boolean matches(IMethodBinding method) throws JavaModelException;

		abstract boolean needExactMatch();

		protected boolean nameMatches(String elementId, String matchString, boolean exactMatch)
		{
			if (exactMatch)
			{
				return elementId.equals(matchString);
			}
			else
			{
				return matchString.length() == 0
					|| CharOperation.camelCaseMatch(matchString.toCharArray(), elementId.toCharArray());
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
		public boolean matches(IMethodBinding method) throws JavaModelException
		{
			return nameMatches(method.getName(), matchString, exactMatch);
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
			for (IAnnotation annotation : method.getAnnotations())
			{
				String annotationName = annotation.getElementName();
				if (MybatipseConstants.STATEMENT_ANNOTATIONS.contains(annotationName))
				{
					return false;
				}
			}
			return nameMatches(method.getElementName(), matchString, exactMatch);
		}

		@Override
		public boolean matches(IMethodBinding method) throws JavaModelException
		{
			for (IAnnotationBinding annotation : method.getAnnotations())
			{
				String annotationName = annotation.getAnnotationType().getQualifiedName();
				if (MybatipseConstants.STATEMENT_ANNOTATIONS.contains(annotationName))
				{
					return false;
				}
			}
			return nameMatches(method.getName(), matchString, exactMatch);
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

		@Override
		public boolean matches(IMethod method) throws JavaModelException
		{
			for (IAnnotation annotation : method.getAnnotations())
			{
				String annotationName = annotation.getElementName();
				if (MybatipseConstants.ANNOTATION_SELECT.equals(annotationName)
					|| MybatipseConstants.ANNOTATION_SELECT_PROVIDER.equals(annotationName))
				{
					return nameMatches(method.getElementName(), matchString, exactMatch);
				}
			}
			return false;
		}

		@Override
		public boolean matches(IMethodBinding method) throws JavaModelException
		{
			for (IAnnotationBinding annotation : method.getAnnotations())
			{
				String annotationName = annotation.getAnnotationType().getQualifiedName();
				if (MybatipseConstants.ANNOTATION_SELECT.equals(annotationName)
					|| MybatipseConstants.ANNOTATION_SELECT_PROVIDER.equals(annotationName))
				{
					return nameMatches(method.getName(), matchString, exactMatch);
				}
			}
			return false;
		}

		@Override
		boolean needExactMatch()
		{
			return exactMatch;
		}
	}

	public static class ResultsAnnotationWithId extends MethodMatcher
	{
		private String matchString;

		private boolean exactMatch;

		public ResultsAnnotationWithId(String matchString, boolean exactMatch)
		{
			super();
			this.matchString = matchString;
			this.exactMatch = exactMatch;
		}

		@Override
		public boolean matches(IMethod method) throws JavaModelException
		{
			for (IAnnotation annotation : method.getAnnotations())
			{
				String annotationName = annotation.getElementName();
				if (MybatipseConstants.ANNOTATION_RESULTS.equals(annotationName))
				{
					IMemberValuePair[] valuePairs = annotation.getMemberValuePairs();
					for (IMemberValuePair valuePair : valuePairs)
					{
						if ("id".equals(valuePair.getMemberName()))
						{
							String resultsId = (String)valuePair.getValue();
							return nameMatches(resultsId, matchString, exactMatch);
						}
					}
				}
			}
			return false;
		}

		@Override
		public boolean matches(IMethodBinding method) throws JavaModelException
		{
			for (IAnnotationBinding annotation : method.getAnnotations())
			{
				String annotationName = annotation.getAnnotationType().getQualifiedName();
				if (MybatipseConstants.ANNOTATION_RESULTS.equals(annotationName))
				{
					IMemberValuePairBinding[] valuePairs = annotation.getAllMemberValuePairs();
					for (IMemberValuePairBinding valuePair : valuePairs)
					{
						if ("id".equals(valuePair.getName()))
						{
							String resultsId = (String)valuePair.getValue();
							return nameMatches(resultsId, matchString, exactMatch);
						}
					}
				}
			}
			return false;
		}

		@Override
		boolean needExactMatch()
		{
			return exactMatch;
		}
	}
}
