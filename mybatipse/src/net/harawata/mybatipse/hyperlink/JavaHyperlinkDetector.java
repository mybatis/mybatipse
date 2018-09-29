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

package net.harawata.mybatipse.hyperlink;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.xpath.XPathExpressionException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMNode;

import net.harawata.mybatipse.Activator;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.HasSelectAnnotation;
import net.harawata.mybatipse.mybatis.JavaMapperUtil.ResultsAnnotationWithId;
import net.harawata.mybatipse.mybatis.MapperNamespaceCache;
import net.harawata.mybatipse.mybatis.MybatipseXmlUtil;
import net.harawata.mybatipse.util.XpathUtil;

/**
 * @author Iwao AVE!
 */
@SuppressWarnings("restriction")
public class JavaHyperlinkDetector extends HyperlinkDetector
{
	@Override
	public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region,
		boolean canShowMultipleHyperlinks)
	{
		IHyperlink[] links = null;
		ITextEditor editor = (ITextEditor)getAdapter(ITextEditor.class);
		if (editor == null)
			return links;
		IEditorInput input = editor.getEditorInput();
		IJavaElement element = JavaUI.getEditorInputJavaElement(input);

		if (element == null)
			return links;
		ITypeRoot typeRoot = (ITypeRoot)element.getAdapter(ITypeRoot.class);
		if (typeRoot == null)
			return links;
		try
		{
			IJavaElement[] srcElements = typeRoot.codeSelect(region.getOffset(), region.getLength());
			if (srcElements.length == 1)
			{
				IJavaElement srcElement = srcElements[0];
				switch (srcElement.getElementType())
				{
					case IJavaElement.METHOD:
						IMethod method = (IMethod)srcElement;
						links = getLinks(method.getDeclaringType(), null,
							"//*[@id='" + srcElement.getElementName() + "']", region);
						break;
					case IJavaElement.TYPE:
						links = getLinks((IType)srcElement, null, "//mapper", region);
						break;
					default:
						break;
				}
			}
			else if (srcElements.length == 0)
			{
				// Annotation value?
				final ASTParser parser = ASTParser.newParser(AST.JLS8);
				parser.setKind(ASTParser.K_COMPILATION_UNIT);
				parser.setSource(typeRoot);
				parser.setResolveBindings(true);
				parser.setIgnoreMethodBodies(true);
				final CompilationUnit astUnit = (CompilationUnit)parser.createAST(null);
				AnnotationValueVisitor visitor = new AnnotationValueVisitor(typeRoot.getJavaProject(),
					region.getOffset());
				astUnit.accept(visitor);
				if (visitor.getHyperlink() != null)
				{
					links = new IHyperlink[]{
						visitor.getHyperlink()
					};
				}
			}
		}
		catch (JavaModelException e)
		{
			Activator.log(Status.ERROR, e.getMessage(), e);
		}
		return links;
	}

	private IHyperlink[] getLinks(IType type, IType triggerType, String expression,
		IRegion srcRegion) throws JavaModelException
	{
		if (type.isInterface() && (triggerType == null || type.equals(triggerType)))
		{
			IJavaProject project = type.getJavaProject();
			if (project == null)
				return null;
			List<IHyperlink> hyperlinks = new ArrayList<>();
			for (IFile mapperFile : MapperNamespaceCache.getInstance()
				.get(project, type.getFullyQualifiedName(), null))
			{
				IDOMDocument mapperDocument = MybatipseXmlUtil.getMapperDocument(mapperFile);
				if (mapperDocument == null)
					continue;
				try
				{
					IDOMNode domNode = (IDOMNode)XpathUtil.xpathNode(mapperDocument, expression);
					if (domNode != null)
					{
						Region destRegion = new Region(domNode.getStartOffset(),
							domNode.getEndOffset() - domNode.getStartOffset());
						String label = "Open <" + domNode.getNodeName() + "/> in "
							+ mapperFile.getFullPath();
						hyperlinks.add(new ToXmlHyperlink(mapperFile, srcRegion, label, destRegion));
					}
				}
				catch (XPathExpressionException e)
				{
					Activator.log(Status.ERROR, e.getMessage(), e);
				}
			}
			return hyperlinks.isEmpty() ? null
				: hyperlinks.toArray(new IHyperlink[hyperlinks.size()]);
		}
		return null;
	}

	private final class AnnotationValueVisitor extends ASTVisitor
	{
		private final int offset;

		private final IJavaProject project;

		private MethodDeclaration method;

		private String namespace;

		private String id;

		private IRegion linkRegion;

		private IHyperlink hyperlink;

		public AnnotationValueVisitor(IJavaProject project, int offset)
		{
			this.project = project;
			this.offset = offset;
		}

		@Override
		public boolean visit(MethodDeclaration node)
		{
			if (!isInRange(node, offset))
				return false;
			method = node;
			return true;
		}

		@Override
		public boolean visit(SingleMemberAnnotation anno)
		{
			if (!isInRange(anno, offset))
				return false;
			parseAnnotation(anno);
			return false;
		}

		@Override
		public boolean visit(NormalAnnotation anno)
		{
			if (!isInRange(anno, offset))
				return false;
			parseAnnotation(anno);
			return false;
		}

		@SuppressWarnings("unchecked")
		private void parseAnnotation(Annotation anno)
		{
			String name = anno.getTypeName().getFullyQualifiedName();
			if ("ResultMap".equals(name))
			{
				createHyperlink("resultMap",
					expressionAt(annotationValueAt(anno, "value", offset), offset));
			}
			else if ("Results".equals(name))
			{
				Expression expr = annotationValueAt(anno, "value", offset);
				if (expr == null)
					return;
				List<Expression> resultAnnos;
				if (expr.getNodeType() == Expression.ARRAY_INITIALIZER)
				{
					resultAnnos = ((ArrayInitializer)expr).expressions();
				}
				else
				{
					resultAnnos = Arrays.asList((Expression)expr);
				}
				for (Expression resultAnno : resultAnnos)
				{
					if (isInRange(resultAnno, offset))
					{
						createHyperlink("select",
							expressionAt(
								annotationValueAt((Annotation)annotationValueAt((Annotation)resultAnno,
									Arrays.asList("one", "many"), offset), "select", offset),
								offset));
						if (hyperlink == null)
						{
							Expression propertyName = annotationValueAt((Annotation)resultAnno, "property",
								offset);
							if (propertyName == null)
								return;
							String returnType = method.resolveBinding().getReturnType().getQualifiedName();
							if (returnType == null || "void".equals(returnType))
								return;
							try
							{
								hyperlink = linkToJavaProperty(project, returnType,
									(String)propertyName.resolveConstantExpressionValue(),
									new Region(propertyName.getStartPosition(), propertyName.getLength()));
							}
							catch (JavaModelException e)
							{
								Activator.log(Status.ERROR, e.getMessage(), e);
							}
						}
						break;
					}
				}
			}
		}

		private void createHyperlink(String target, Expression expression)
		{
			if (expression == null)
				return;

			String value = (String)expression.resolveConstantExpressionValue();
			int namespaceEnd = value.lastIndexOf('.');
			if (namespaceEnd > -1)
			{
				id = value.substring(namespaceEnd + 1);
				namespace = value.substring(0, namespaceEnd);
			}
			else
			{
				id = value;
				namespace = method.resolveBinding().getDeclaringClass().getBinaryName();
			}

			linkRegion = new Region(expression.getStartPosition(), expression.getLength());
			hyperlink = linkToXmlElement(project, target, namespace, id, linkRegion, null);
			if (hyperlink == null)
			{
				if ("select".equals(target))
				{
					hyperlink = linkToJavaMapperMethod(project, namespace, linkRegion,
						new HasSelectAnnotation(id, true));
				}
				else if ("resultMap".equals(target))
				{
					hyperlink = linkToJavaMapperMethod(project, namespace, linkRegion,
						new ResultsAnnotationWithId(id, true));
				}
			}
		}

		private IHyperlink linkToXmlElement(IJavaProject project, String targetElement,
			String namespace, String id, IRegion linkRegion, ITextViewer viewer)
		{
			for (IFile mapperXmlFile : MapperNamespaceCache.getInstance()
				.get(project, namespace, null))
			{
				IDOMDocument domDoc = MybatipseXmlUtil.getMapperDocument(mapperXmlFile);
				if (domDoc == null)
					return null;
				try
				{
					IDOMNode node = (IDOMNode)XpathUtil.xpathNode(domDoc,
						"//" + targetElement + "[@id='" + id + "']");
					if (node != null)
					{
						IRegion destRegion = new Region(node.getStartOffset(),
							node.getEndOffset() - node.getStartOffset());
						return new ToXmlHyperlink(mapperXmlFile, linkRegion, "Open declaration",
							destRegion);
					}
				}
				catch (XPathExpressionException e)
				{
					Activator.log(Status.ERROR, e.getMessage(), e);
				}
			}
			return null;
		}

		private Expression annotationValueAt(Annotation anno, String targetKey, final int offset)
		{
			return annotationValueAt(anno, Arrays.asList(targetKey), offset);
		}

		private Expression annotationValueAt(Annotation anno, List<String> targetKeys,
			final int offset)
		{
			if (anno == null)
				return null;
			if (anno.isSingleMemberAnnotation())
			{
				Expression value = ((SingleMemberAnnotation)anno).getValue();
				if (targetKeys.indexOf("value") > -1 && isInRange(value, offset))
					return value;
			}
			else if (anno.isNormalAnnotation())
			{
				@SuppressWarnings("unchecked")
				List<MemberValuePair> pairs = ((NormalAnnotation)anno).values();
				for (MemberValuePair pair : pairs)
				{
					SimpleName key = pair.getName();
					Expression value = pair.getValue();
					if (targetKeys.indexOf(key.getFullyQualifiedName()) > -1 && isInRange(value, offset))
						return value;
				}
			}
			return null;
		}

		@SuppressWarnings("unchecked")
		private Expression expressionAt(Expression value, int offset)
		{
			if (value == null)
				return null;

			if (value.getNodeType() == Expression.ARRAY_INITIALIZER)
			{
				ArrayInitializer arrayInitializer = (ArrayInitializer)value;
				for (Expression expression : (List<Expression>)arrayInitializer.expressions())
				{
					if (isInRange(expression, offset))
					{
						return expression;
					}
				}
			}
			else if (value.getNodeType() == Expression.STRING_LITERAL)
			{
				return value;
			}
			return null;
		}

		private boolean isInRange(ASTNode node, int offset)
		{
			int start = node.getStartPosition();
			return start <= offset && offset <= start + node.getLength();
		}

		public IHyperlink getHyperlink()
		{
			return hyperlink;
		}
	}
}
