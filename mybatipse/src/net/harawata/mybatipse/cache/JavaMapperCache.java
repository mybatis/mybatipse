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

package net.harawata.mybatipse.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.MybatipseConstants;
import net.harawata.mybatipse.mybatis.JavaMapperUtil;

/**
 * @author Iwao AVE!
 */
public class JavaMapperCache
{
	private Map<String, Map<String, MapperInfo>> cache = new ConcurrentHashMap<String, Map<String, MapperInfo>>();

	private static JavaMapperCache INSTANCE = new JavaMapperCache();

	public void clear()
	{
		cache.clear();
	}

	public void remove(String projectName)
	{
		cache.remove(projectName);
	}

	public void remove(IJavaProject project, String mapperFqn)
	{
		if (mapperFqn == null)
			return;

		Map<String, MapperInfo> map = cache.get(project.getElementName());
		if (map == null)
			return;

		for (Iterator<Entry<String, MapperInfo>> iter = map.entrySet().iterator(); iter.hasNext();)
		{
			Entry<String, MapperInfo> entry = iter.next();
			if (mapperFqn.equals(entry.getKey()) || entry.getValue().isSubInterfaceOf(mapperFqn))
			{
				iter.remove();
			}
		}
	}

	/**
	 * Returns if the method exists and there is no statement annotation attached.
	 */
	public boolean methodExists(IJavaProject project, String mapperFqn, String methodName)
	{
		MapperInfo mapperInfo = getMapperInfo(project, mapperFqn);
		return mapperInfo != null && mapperInfo.hasNonStatementMethod(methodName);
	}

	/**
	 * Returns if the method with Select or SelectProvider annotation exists.
	 */
	public boolean selectMethodExists(IJavaProject project, String mapperFqn, String methodName)
	{
		MapperInfo mapperInfo = getMapperInfo(project, mapperFqn);
		return mapperInfo != null && mapperInfo.hasSelectStatementMethod(methodName);
	}

	/**
	 * Returns if the method with Results annotation with the specified id exists.
	 */
	public boolean resultMapExists(IJavaProject project, String mapperFqn, String resultMapId)
	{
		MapperInfo mapperInfo = getMapperInfo(project, mapperFqn);
		return mapperInfo != null && mapperInfo.hasResultMap(resultMapId);
	}

	private MapperInfo getMapperInfo(IJavaProject project, String mapperFqn)
	{
		String projectName = project.getElementName();
		Map<String, MapperInfo> mappers = cache.get(projectName);
		if (mappers == null)
		{
			mappers = new HashMap<String, JavaMapperCache.MapperInfo>();
			cache.put(projectName, mappers);
		}
		MapperInfo mapperInfo = mappers.get(mapperFqn);
		if (mapperInfo == null)
		{
			mapperInfo = new MapperInfo();
			mappers.put(mapperFqn, mapperInfo);
			try
			{
				IType mapperType = project.findType(mapperFqn.replace('$', '.'));
				if (mapperType != null && mapperType.isInterface())
				{
					parseMapper(project, mapperType, mapperInfo);
				}
			}
			catch (JavaModelException e)
			{
				Activator.log(Status.ERROR, "Failed to parse mapper " + mapperFqn);
				return null;
			}
		}
		return mapperInfo;
	}

	private static void parseMapper(final IJavaProject project, final IType mapperType,
		final MapperInfo mapperInfo) throws JavaModelException
	{
		if (mapperType.isBinary())
		{
			parseBinaryMapper(project, mapperType, mapperInfo);
		}
		else
		{
			parseSourceMapper(project, mapperType, mapperInfo);
		}
	}

	private static void parseBinaryMapper(final IJavaProject project, final IType mapperType,
		final MapperInfo mapperInfo) throws JavaModelException
	{
		for (IMethod method : mapperType.getMethods())
		{
			if (Flags.isDefaultMethod(method.getFlags()))
				continue;

			String statementAnno = null;
			for (IAnnotation annotation : method.getAnnotations())
			{
				String annoName = annotation.getElementName();
				if (MybatipseConstants.STATEMENT_ANNOTATIONS.contains(annoName))
				{
					statementAnno = annoName;
				}
				else if (MybatipseConstants.ANNOTATION_RESULTS.equals(annoName))
				{
					IMemberValuePair[] valuePairs = annotation.getMemberValuePairs();
					for (IMemberValuePair valuePair : valuePairs)
					{
						if ("id".equals(valuePair.getMemberName()))
						{
							String resultsId = (String)valuePair.getValue();
							if (resultsId != null)
								mapperInfo.addResultMap(resultsId, method);
						}
					}
				}
			}
			mapperInfo.addMethod(method, statementAnno);

			String[] superInterfaces = mapperType.getSuperInterfaceNames();
			for (String superInterface : superInterfaces)
			{
				if (!Object.class.getName().equals(superInterface))
				{
					IType superInterfaceType = project.findType(superInterface.replace('$', '.'));
					parseMapper(project, superInterfaceType, mapperInfo);
				}
			}
		}
	}

	private static void parseSourceMapper(final IJavaProject project, final IType mapperType,
		final MapperInfo mapperInfo) throws JavaModelException
	{
		ICompilationUnit compilationUnit = (ICompilationUnit)mapperType
			.getAncestor(IJavaElement.COMPILATION_UNIT);
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(compilationUnit);
		parser.setResolveBindings(true);
		parser.setIgnoreMethodBodies(true);
		CompilationUnit astUnit = (CompilationUnit)parser.createAST(null);
		astUnit.accept(new ASTVisitor()
		{
			private int nestLevel;

			@Override
			public boolean visit(TypeDeclaration node)
			{
				ITypeBinding binding = node.resolveBinding();
				if (binding == null)
					return false;

				if (mapperType.getFullyQualifiedName().equals(binding.getBinaryName()))
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

				IMethodBinding method = node.resolveBinding();
				if (method == null)
					return false;

				String statementAnno = null;
				for (IAnnotationBinding annotation : method.getAnnotations())
				{
					String annotationName = annotation.getAnnotationType().getQualifiedName();
					if (MybatipseConstants.STATEMENT_ANNOTATIONS.contains(annotationName))
					{
						statementAnno = annotationName;
					}
					else if (MybatipseConstants.ANNOTATION_RESULTS.equals(annotationName))
					{
						String resultsId = JavaMapperUtil
							.getAnnotationMemberValue((IAnnotation)annotation.getJavaElement(), "id");
						if (resultsId != null)
							mapperInfo.addResultMap(resultsId, (IMethod)method.getJavaElement());
					}
				}
				mapperInfo.addMethod((IMethod)method.getJavaElement(), statementAnno);
				return false;
			}

			public void endVisit(TypeDeclaration node)
			{
				if (nestLevel == 1)
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
								try
								{
									IType superInterface = (IType)binding.getJavaElement();
									mapperInfo.addSuperInterface(superInterface.getFullyQualifiedName());
									parseMapper(project, superInterface, mapperInfo);
								}
								catch (JavaModelException e)
								{
									Activator.log(Status.ERROR,
										"Failed to parse mapper " + binding.getQualifiedName(), e);
								}
							}
						}
					}
				}
				nestLevel--;
			}
		});
	}

	static class MapperInfo
	{
		// key = method name
		private Map<String, MapperMethodInfo> mapperMethods = new ConcurrentHashMap<String, JavaMapperCache.MapperMethodInfo>();

		// key = results id
		private Map<String, IMethod> resultMaps = new ConcurrentHashMap<String, IMethod>();

		private List<String> superInterfaces = new ArrayList<String>();

		public void addMethod(IMethod method, String statementAnnotation)
		{
			MapperMethodInfo overloaded = mapperMethods.put(method.getElementName(),
				new MapperMethodInfo(method, statementAnnotation));
			if (overloaded != null)
			{
				// should check synthetic?
				Activator.log(Status.WARNING,
					"The method '" + method.getElementName() + "' declared in '"
						+ method.getDeclaringType().getFullyQualifiedName()
						+ "' is overloaded. MyBatis does not support overloading method.");
			}
		}

		public void addResultMap(String id, IMethod method)
		{
			resultMaps.put(id, method);
		}

		public void addSuperInterface(String superInterfaceFqn)
		{
			superInterfaces.add(superInterfaceFqn);
		}

		public boolean hasNonStatementMethod(String methodName)
		{
			MapperMethodInfo methodInfo = mapperMethods.get(methodName);
			return methodInfo != null && !methodInfo.hasStatementAnno();
		}

		public boolean hasSelectStatementMethod(String methodName)
		{
			MapperMethodInfo methodInfo = mapperMethods.get(methodName);
			return methodInfo != null && methodInfo.hasSelectAnno();
		}

		public boolean hasResultMap(String id)
		{
			return resultMaps.containsKey(id);
		}

		public boolean isSubInterfaceOf(String interfaceFqn)
		{
			return superInterfaces.contains(interfaceFqn);
		}
	}

	static class MapperMethodInfo
	{
		private IMethod methodRef;

		private String statementAnno;

		public MapperMethodInfo(IMethod methodRef, String statementAnno)
		{
			super();
			this.methodRef = methodRef;
			this.statementAnno = statementAnno;
		}

		public boolean hasStatementAnno()
		{
			return MybatipseConstants.STATEMENT_ANNOTATIONS.contains(statementAnno);
		}

		public boolean hasSelectAnno()
		{
			return MybatipseConstants.ANNOTATION_SELECT.equals(statementAnno)
				|| MybatipseConstants.ANNOTATION_SELECT_PROVIDER.equals(statementAnno);
		}
	}

	public static JavaMapperCache getInstance()
	{
		return INSTANCE;
	}

	private JavaMapperCache()
	{
		super();
	}
}
