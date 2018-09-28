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

import static net.harawata.mybatipse.MybatipseConstants.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPathExpressionException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.core.search.TypeNameRequestor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.validation.internal.provisional.core.IReporter;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMAttr;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.MybatipseConstants;
import net.harawata.mybatipse.bean.SupertypeHierarchyCache;
import net.harawata.mybatipse.mybatis.TypeAliasMap.TypeAliasInfo;
import net.harawata.mybatipse.util.NameUtil;
import net.harawata.mybatipse.util.XpathUtil;

/**
 * @author Iwao AVE!
 */
@SuppressWarnings("restriction")
public class TypeAliasCache
{
	private static final TypeAliasCache INSTANCE = new TypeAliasCache();

	private static final List<String> declaredTypes = Arrays.asList(
		MybatipseConstants.GUICE_MYBATIS_MODULE,
		MybatipseConstants.SPRING_SQL_SESSION_FACTORY_BEAN);

	private final Map<String, TypeAliasMap> projectCache = new ConcurrentHashMap<String, TypeAliasMap>();

	private final Map<String, Set<String>> packageCache = new ConcurrentHashMap<String, Set<String>>();

	private final Map<String, Set<IType>> superTypeCache = new ConcurrentHashMap<String, Set<IType>>();

	public String resolveAlias(IJavaProject javaProject, String alias, IReporter reporter)
	{
		Map<String, TypeAliasInfo> aliasMap = getTypeAliasMap(javaProject, reporter);
		TypeAliasInfo typeAliasInfo = aliasMap.get(alias.toLowerCase(Locale.ENGLISH));
		return typeAliasInfo == null ? null
			: MybatipseXmlUtil.normalizeTypeName(typeAliasInfo.getQualifiedName());
	}

	public void removeType(String projectName, String qualifiedName)
	{
		TypeAliasMap aliasMap = projectCache.get(projectName);
		if (aliasMap == null)
			return;
		Iterator<Entry<String, TypeAliasInfo>> iterator = aliasMap.entrySet().iterator();
		while (iterator.hasNext())
		{
			Entry<String, TypeAliasInfo> entry = iterator.next();
			if (qualifiedName.equals(entry.getValue().getQualifiedName()))
			{
				iterator.remove();
			}
		}
	}

	public void put(String projectName, IType type, String simpleTypeName)
	{
		String qualifiedName = type.getFullyQualifiedName('.');
		removeType(projectName, qualifiedName);
		try
		{
			TypeAliasMap aliasMap = projectCache.get(projectName);
			if (aliasMap == null)
				return;
			Set<IType> superTypes = superTypeCache.get(projectName);
			putAlias(aliasMap, type, superTypes, type.getFlags());
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, "Error while resolving alias for type " + qualifiedName, e);
		}
	}

	public void remove(IProject project)
	{
		String projectName = project.getName();
		projectCache.remove(projectName);
	}

	public void clear()
	{
		projectCache.clear();
	}

	public boolean isInPackage(String projectName, String packageName)
	{
		Set<String> packages = packageCache.get(projectName);
		if (packages == null)
			return false;
		for (String pkg : packages)
		{
			if (packageName.startsWith(pkg))
			{
				return true;
			}
		}
		return false;
	}

	public Map<String, String> searchTypeAliases(IJavaProject javaProject, String matchString)
	{
		Map<String, String> results = new HashMap<String, String>();
		TypeAliasMap typeAliasMap = getTypeAliasMap(javaProject, null);
		for (TypeAliasInfo typeAliasInfo : typeAliasMap.values())
		{
			String alias = typeAliasInfo.getAliasToInsert();
			String qualifiedName = typeAliasInfo.getQualifiedName();
			char[] aliasChrs = alias.toCharArray();
			char[] matchChrs = matchString.toCharArray();
			if (matchString.length() == 0 || CharOperation.camelCaseMatch(matchChrs, aliasChrs)
				|| CharOperation.prefixEquals(matchChrs, aliasChrs, false))
			{
				results.put(qualifiedName, alias);
			}
		}
		return results;
	}

	private TypeAliasMap getTypeAliasMap(IJavaProject javaProject, IReporter reporter)
	{
		String projectName = javaProject.getElementName();
		TypeAliasMap aliasMap = projectCache.get(projectName);
		if (aliasMap == null)
		{
			Map<IFile, IContentType> configFiles = ConfigRegistry.getInstance().get(javaProject);
			aliasMap = new TypeAliasMap();
			projectCache.put(projectName, aliasMap);
			packageCache.remove(projectName);
			superTypeCache.remove(projectName);
			Set<String> packages = new TreeSet<String>();
			packageCache.put(projectName, packages);
			Set<IType> superTypes = new HashSet<IType>();
			superTypeCache.put(projectName, superTypes);

			// Lookup the settings.
			IPreferenceStore store = Activator.getPreferenceStore(javaProject.getProject());
			String storedValue = store.getString(PREF_CUSTOM_TYPE_ALIASES);
			StringTokenizer tokenizer = new StringTokenizer(storedValue, "\t");
			while (tokenizer.hasMoreElements())
			{
				String token = (String)tokenizer.nextElement();
				if (token == null)
					continue;
				token = token.trim();
				if (token.length() == 0)
					continue;
				try
				{
					int colonIdx = token.indexOf(':');
					if (colonIdx == -1)
					{
						IType type = javaProject.findType(token);
						if (type == null)
							packages.add(token);
						else
						{
							String alias = getAliasAnnotationValue(type);
							aliasMap.put(alias, token);
						}
					}
					else if (colonIdx > 0 && colonIdx < token.length() - 2)
					{
						String qualifiedName = token.substring(0, colonIdx);
						IType type = javaProject.findType(qualifiedName);
						if (type == null)
						{
							Activator.log(Status.WARNING,
								"Missing '" + qualifiedName + "' specified in the custom type alias setting.");
						}
						else
						{
							// Look for @Alias first.
							String alias = getAliasAnnotationValue(type);
							if (alias == null)
								alias = token.substring(colonIdx + 1);
							aliasMap.put(alias, qualifiedName);
						}
					}
				}
				catch (JavaModelException e)
				{
					Activator.log(Status.ERROR, e.getMessage(), e);
				}
			}

			// Parse config xml files (mybatis and spring).
			for (Entry<IFile, IContentType> configFile : configFiles.entrySet())
			{
				parseConfigFiles(javaProject, configFile.getKey(), configFile.getValue(), aliasMap,
					packages, superTypes, reporter);
			}

			// Search calls registering type aliases in java code.
			scanJavaConfig(javaProject, aliasMap, packages, reporter);

			// Scan classes in the packages.
			if (!packages.isEmpty())
			{
				collectTypesInPackages(javaProject, packages, aliasMap, superTypes, reporter);
			}
		}
		return aliasMap;
	}

	/**
	 * <p>
	 * Trying to find calls registering type aliases.<br>
	 * But only simple calls can be supported.
	 * </p>
	 */
	private void scanJavaConfig(IJavaProject project, final TypeAliasMap aliasMap,
		final Set<String> packages, IReporter reporter)
	{
		try
		{
			IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaProject[]{
				project
			}, IJavaSearchScope.SOURCES | IJavaSearchScope.REFERENCED_PROJECTS
				| IJavaSearchScope.APPLICATION_LIBRARIES);
			SearchParticipant[] participants = new SearchParticipant[]{
				SearchEngine.getDefaultSearchParticipant()
			};
			SearchEngine searchEngine = new SearchEngine();

			final Set<ITypeRoot> typeRoots = new HashSet<ITypeRoot>();

			// Collect classes that contain mybatis-guice calls.
			IType mybatisModuleType = project.findType(MybatipseConstants.GUICE_MYBATIS_MODULE);
			if (mybatisModuleType == null || !mybatisModuleType.exists())
				return;

			IMethod addSimpleAliasMethod = mybatisModuleType.getMethod("addSimpleAlias", new String[]{
				"Ljava.lang.Class;"
			});
			SearchPattern pattern = SearchPattern.createPattern(addSimpleAliasMethod,
				IJavaSearchConstants.REFERENCES | IJavaSearchConstants.IGNORE_DECLARING_TYPE
					| IJavaSearchConstants.IMPLEMENTORS);
			searchEngine.search(pattern, participants, scope, new MethodSearchRequestor(typeRoots),
				null);

			IMethod addSimpleAliasesMethodWithPackage = mybatisModuleType
				.getMethod("addSimpleAliases", new String[]{
					"Ljava.lang.String;"
			});
			pattern = SearchPattern.createPattern(addSimpleAliasesMethodWithPackage,
				IJavaSearchConstants.REFERENCES | IJavaSearchConstants.IGNORE_DECLARING_TYPE);
			searchEngine.search(pattern, participants, scope, new MethodSearchRequestor(typeRoots),
				null);

			// IMethod addSimpleAliasesMethodWithPackageAndTest = mybatisModuleType.getMethod(
			// "addSimpleAliases", new String[]{
			// "Ljava.util.Collection<Ljava.lang.Class<*>;>;"
			// });
			// pattern = SearchPattern.createPattern(addSimpleAliasesMethodWithPackageAndTest,
			// IJavaSearchConstants.REFERENCES | IJavaSearchConstants.IGNORE_DECLARING_TYPE);
			// searchEngine.search(pattern, participants, scope, new MethodSearchRequestor(typeRoots),
			// null);

			// Searches Spring java config if necessary.
			// if (typeRoots.isEmpty())
			// {
			// IType sqlSessionFactoryBeanType = project.findType(SPRING_BEAN_FQN);
			// if (sqlSessionFactoryBeanType == null || !sqlSessionFactoryBeanType.exists())
			// return;
			//
			// IMethod setTypeAliasesPackageMethod = sqlSessionFactoryBeanType.getMethod(
			// "setTypeAliasesPackage", new String[]{
			// "Ljava.lang.String;"
			// });
			// pattern = SearchPattern.createPattern(setTypeAliasesPackageMethod,
			// IJavaSearchConstants.REFERENCES | IJavaSearchConstants.IGNORE_DECLARING_TYPE
			// | IJavaSearchConstants.IMPLEMENTORS);
			// searchEngine.search(pattern, participants, scope, new MethodSearchRequestor(typeRoots),
			// null);
			// }

			for (ITypeRoot typeRoot : typeRoots)
			{
				ASTParser parser = ASTParser.newParser(AST.JLS8);
				parser.setSource(typeRoot);
				parser.setResolveBindings(true);
				CompilationUnit astUnit = (CompilationUnit)parser.createAST(null);
				astUnit.accept(new JavaConfigVisitor(aliasMap, packages));
			}
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		catch (CoreException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
	}

	private void parseConfigFiles(IJavaProject project, IFile configFile, IContentType configType,
		final TypeAliasMap aliasMap, Set<String> packages, Set<IType> superTypeList,
		IReporter reporter)
	{
		IStructuredModel model = null;
		try
		{
			model = StructuredModelManager.getModelManager().getModelForRead(configFile);
			IDOMModel domModel = (IDOMModel)model;
			IDOMDocument domDoc = domModel.getDocument();

			if (reporter != null && reporter.isCancelled())
			{
				throw new OperationCanceledException();
			}

			if (configContentType.equals(configType))
			{
				// Parse <typeAlias /> tags.
				parseTypeAliasElements(domDoc, aliasMap);

				if (reporter != null && reporter.isCancelled())
				{
					throw new OperationCanceledException();
				}

				// Parse <packags /> tags.
				parsePackageElements(domDoc, packages);
			}
			else if (springConfigContentType.equals(configType))
			{
				parseTypeAliasesPackage(packages, domDoc);

				if (reporter != null && reporter.isCancelled())
				{
					throw new OperationCanceledException();
				}

				parseTypeAliasesSuperType(project, superTypeList, domDoc);
			}
		}
		catch (XPathExpressionException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		catch (IOException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		catch (CoreException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		finally
		{
			// Activator.log(Status.WARNING, "Error occurred during validation.", e);
			if (model != null)
			{
				model.releaseFromRead();
			}
		}
	}

	private void parseTypeAliasesPackage(Set<String> packages, IDOMDocument domDoc)
		throws XPathExpressionException
	{
		// There can be multiple SqlSessionFactoryBeans.
		NodeList nodes = XpathUtil.xpathNodes(domDoc,
			"//beans:bean/beans:property[@name='typeAliasesPackage']/@value",
			new SpringConfigNamespaceContext());
		// NodeList nodes = XpathUtil.xpathNodes(domDoc,
		// "//*[namespace-uri() = 'http://www.springframework.org/schema/beans']"
		// + "[local-name() = 'property'][@name='typeAliasesPackage']/@value");
		for (int i = 0; i < nodes.getLength(); i++)
		{
			String value = nodes.item(i).getNodeValue();
			String[] arr = value.split("[,; \t\n]");
			for (String pkg : arr)
			{
				if (pkg != null && pkg.length() > 0)
					packages.add(pkg.trim());
			}
		}
	}

	private void parseTypeAliasesSuperType(IJavaProject project, Set<IType> superTypes,
		IDOMDocument domDoc) throws XPathExpressionException
	{
		// There can be multiple SqlSessionFactoryBeans.
		NodeList nodes = XpathUtil.xpathNodes(domDoc,
			"//beans:bean/beans:property[@name='typeAliasesSuperType']/@value",
			new SpringConfigNamespaceContext());
		// NodeList nodes = XpathUtil.xpathNodes(domDoc,
		// "//*[namespace-uri() = 'http://www.springframework.org/schema/beans']"
		// + "[local-name() = 'property'][@name='typeAliasesSuperType']/@value");
		for (int i = 0; i < nodes.getLength(); i++)
		{
			String value = nodes.item(i).getNodeValue();
			try
			{
				superTypes.add(project.findType(value.trim()));
			}
			catch (JavaModelException e)
			{
				Activator.log(Status.ERROR, e.getMessage(), e);
			}
		}
	}

	private void collectTypesInPackages(final IJavaProject project, Set<String> packages,
		final TypeAliasMap aliasMap, final Set<IType> superTypes, IReporter reporter)
	{
		int includeMask = IJavaSearchScope.SOURCES | IJavaSearchScope.REFERENCED_PROJECTS
			| IJavaSearchScope.APPLICATION_LIBRARIES | IJavaSearchScope.SYSTEM_LIBRARIES;
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(new IJavaProject[]{
			project
		}, includeMask);
		TypeNameRequestor requestor = new TypeNameRequestor()
		{
			@Override
			public void acceptType(int modifiers, char[] packageName, char[] simpleTypeName,
				char[][] enclosingTypeNames, String path)
			{
				if (Flags.isAbstract(modifiers) || Flags.isAnnotation(modifiers))
					return;

				String qualifiedName = NameUtil.buildQualifiedName(packageName, simpleTypeName,
					enclosingTypeNames, false);
				try
				{
					IType foundType = project.findType(qualifiedName);
					putAlias(aliasMap, foundType, superTypes, modifiers);
				}
				catch (JavaModelException e)
				{
					Activator.log(Status.WARNING, "Error occurred while searching type alias.", e);
				}
			}
		};

		SearchEngine searchEngine = new SearchEngine();
		for (String pkg : packages)
		{
			if (reporter != null && reporter.isCancelled())
			{
				throw new OperationCanceledException();
			}
			try
			{
				int pkgMatchRule = pkg.indexOf('*') > -1 ? SearchPattern.R_PATTERN_MATCH
					: SearchPattern.R_PREFIX_MATCH;
				searchEngine.searchAllTypeNames(pkg.toCharArray(), pkgMatchRule, null,
					SearchPattern.R_CAMELCASE_MATCH, IJavaSearchConstants.TYPE, scope, requestor,
					IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, null);
			}
			catch (JavaModelException e)
			{
				Activator.log(Status.ERROR, e.getMessage(), e);
			}
		}
	}

	private void putAlias(TypeAliasMap aliasMap, IType foundType, Set<IType> superTypes,
		int modifiers) throws JavaModelException
	{
		if (Flags.isAbstract(modifiers) || Flags.isAnnotation(modifiers))
			return;

		if (superTypes.isEmpty() || isSuperTypeMatched(foundType, superTypes))
		{
			String alias = getAliasAnnotationValue(foundType);
			if (alias == null)
			{
				alias = foundType.getElementName();
			}
			aliasMap.put(alias, foundType.getFullyQualifiedName('.'));
		}
	}

	private boolean isSuperTypeMatched(IType foundType, Set<IType> superTypeSet)
		throws JavaModelException
	{
		for (IType superType : superTypeSet)
		{
			if (SupertypeHierarchyCache.getInstance().isSubtype(foundType, superType))
			{
				return true;
			}
		}
		return false;
	}

	private String getAliasAnnotationValue(IType foundType) throws JavaModelException
	{
		String alias = null;
		IAnnotation[] annotations = foundType.getAnnotations();
		for (IAnnotation annotation : annotations)
		{
			if ("Alias".equals(annotation.getElementName()))
			{
				IMemberValuePair[] params = annotation.getMemberValuePairs();
				if (params.length > 0)
				{
					alias = (String)params[0].getValue();
				}
			}
		}
		return alias;
	}

	private void parsePackageElements(IDOMDocument domDoc, Set<String> packages)
		throws XPathExpressionException
	{
		NodeList pkgNameNodes = XpathUtil.xpathNodes(domDoc, "//typeAliases/package/@name");
		for (int i = 0; i < pkgNameNodes.getLength(); i++)
		{
			String pkg = pkgNameNodes.item(i).getNodeValue();
			packages.add(pkg);
		}
	}

	private void parseTypeAliasElements(IDOMDocument domDoc, final TypeAliasMap aliasMap)
		throws XPathExpressionException
	{
		NodeList nodes = XpathUtil.xpathNodes(domDoc, "//typeAliases/typeAlias");
		for (int i = 0; i < nodes.getLength(); i++)
		{
			String type = null;
			String alias = null;
			NamedNodeMap attrs = nodes.item(i).getAttributes();
			for (int j = 0; j < attrs.getLength(); j++)
			{
				IDOMAttr attr = (IDOMAttr)attrs.item(j);
				String attrName = attr.getName();
				if ("type".equals(attrName))
					type = attr.getValue();
				else if ("alias".equals(attrName))
					alias = attr.getValue();
			}
			aliasMap.put(alias, type);
		}
	}

	public static TypeAliasCache getInstance()
	{
		return INSTANCE;
	}

	private TypeAliasCache()
	{
		super();
	}

	private class JavaConfigVisitor extends ASTVisitor
	{
		private TypeAliasMap aliasMap;

		private Set<String> packages;

		private JavaConfigVisitor(TypeAliasMap aliasMap, Set<String> packages)
		{
			this.aliasMap = aliasMap;
			this.packages = packages;
		}

		@Override
		public boolean visit(MethodInvocation node)
		{
			String invokedMethod = node.getName().getIdentifier();
			if ("addSimpleAlias".equals(invokedMethod))
			{
				if (!declaredIn(node, MybatipseConstants.GUICE_MYBATIS_MODULE))
					return false;

				@SuppressWarnings("rawtypes")
				List args = node.arguments();
				if (args.size() != 1)
				{
					Activator.log(Status.WARNING,
						"Unexpected parameter count (possible API change): " + invokedMethod);
				}
				else
				{
					Expression expression = (Expression)args.get(0);
					ITypeBinding argType = expression.resolveTypeBinding();
					ITypeBinding[] classTypes = argType.getTypeArguments();
					String qualifiedName = classTypes[0].getQualifiedName();
					String simpleName = classTypes[0].getName();
					aliasMap.put(simpleName, qualifiedName);
				}
			}
			else if ("addSimpleAliases".equals(invokedMethod))
			{
				if (!declaredIn(node, MybatipseConstants.GUICE_MYBATIS_MODULE))
					return false;

				@SuppressWarnings("rawtypes")
				List args = node.arguments();
				if (args.size() == 1)
				{
					Expression expression = (Expression)args.get(0);
					ITypeBinding argType = expression.resolveTypeBinding();
					if ("java.lang.String".equals(argType.getQualifiedName()))
					{
						Object constantArg = expression.resolveConstantExpressionValue();
						if (constantArg != null)
						{
							packages.add((String)constantArg);
						}
						else
						{
							Activator.log(Status.INFO, "TypeAlias detection failed: " + node.toString()
								+ " Only a literal parameter is supported for addSimpleAliases(String).");
						}
					}
					else if ("java.util.Collection<java.lang.Class<?>>"
						.equals(argType.getQualifiedName()))
					{
						Activator.log(Status.INFO, "TypeAlias detection failed. "
							+ "addSimpleAliases(Collection<Class<?>>) is not supported.");
					}
				}
				else if (args.size() == 2)
				{
					Activator.log(Status.INFO, "TypeAlias detection failed. "
						+ "addSimpleAliases(String, Test) is not supported.");
				}
			}
			return true;
		}

		private boolean declaredIn(MethodInvocation node, String qualifiedName)
		{
			IMethodBinding methodBinding = node.resolveMethodBinding();
			return methodBinding != null
				&& qualifiedName.equals(methodBinding.getDeclaringClass().getQualifiedName());
		}
	}

	private class MethodSearchRequestor extends SearchRequestor
	{
		private Set<ITypeRoot> typeRoots;

		private MethodSearchRequestor(Set<ITypeRoot> typeRoots)
		{
			this.typeRoots = typeRoots;
		}

		@Override
		public void acceptSearchMatch(SearchMatch match) throws CoreException
		{
			if (match.getAccuracy() == SearchMatch.A_INACCURATE)
				return;

			IMethod element = (IMethod)match.getElement();
			ITypeRoot typeRoot = element.isBinary() ? element.getClassFile()
				: element.getCompilationUnit();
			if (declaredTypes.contains(typeRoot.findPrimaryType().getFullyQualifiedName()))
				return;
			typeRoots.add(typeRoot);
		}
	}

	private class SpringConfigNamespaceContext implements NamespaceContext
	{
		@Override
		public String getNamespaceURI(String prefix)
		{
			if (prefix == null)
				throw new NullPointerException("Prefix cannot be null.");
			else if ("beans".equals(prefix))
				return "http://www.springframework.org/schema/beans";
			else if ("xml".equals(prefix))
				return XMLConstants.XML_NS_URI;
			return XMLConstants.NULL_NS_URI;
		}

		@Override
		public String getPrefix(String namespaceURI)
		{
			throw new UnsupportedOperationException();
		}

		@SuppressWarnings("rawtypes")
		@Override
		public Iterator getPrefixes(String namespaceURI)
		{
			throw new UnsupportedOperationException();
		}
	}
}
